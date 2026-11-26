package com.xelilovth.nota.data.repository

import androidx.annotation.OptIn
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xelilovth.nota.data.media.PlaybackService
import com.xelilovth.nota.data.media.PlaybackNavigation
import com.xelilovth.nota.data.media.PlaybackStateMapper
import com.xelilovth.nota.data.online.OnlineMusicRepositoryImpl
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: ExoPlayer,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val onlineMusicRepository: OnlineMusicRepository?,
) : PlaybackRepository {
    constructor(
        context: Context,
        player: ExoPlayer,
        libraryRepository: LibraryRepository,
        settingsRepository: SettingsRepository,
    ) : this(context, player, libraryRepository, settingsRepository, null)

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var resolveJob: Job? = null
    private val activeOnlineTracks = ConcurrentHashMap<Long, Track>()
    private val resolvedStreamUris = ConcurrentHashMap<Long, String>()
    private var cachedMediaId: Long? = null
    private var cachedTrack: Track? = null
    private var listeningTrackId: Long? = null
    private var lastListeningTickMs: Long = 0L
    private var accumulatedListenMs: Long = 0L
    @Volatile
    private var lyricsSyncActive = false
    @Volatile
    private var crossfadeSeconds: Int = settingsRepository.settings.value.crossfadeSeconds
    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishState()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startProgressUpdates()
            } else {
                flushListeningTime(resetTick = true)
                progressJob?.cancel()
                fadeJob?.cancel()
                player.volume = 1.0f
            }
            publishState()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushListeningTime(resetTick = !player.isPlaying)
            if (crossfadeSeconds > 0 && player.isPlaying) {
                startFadeIn()
            } else if (player.volume != 1.0f) {
                player.volume = 1.0f
            }
            maybeResolveCurrentOnlineItem(mediaItem)
            publishState()
        }
        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
    }

    init {
        player.addListener(listener)
        scope.launch {
            settingsRepository.settings
                .map { it.crossfadeSeconds }
                .distinctUntilChanged()
                .collect { seconds ->
                    crossfadeSeconds = seconds
                    if (seconds <= 0) {
                        fadeJob?.cancel()
                        player.volume = 1.0f
                    }
                }
        }
        publishState()
    }

    override fun playTrack(track: Track, queue: List<Track>) {
        if (queue.isEmpty()) return

        val selectedIndex = queue.indexOfFirst { it.id == track.id }
        val tracks = if (selectedIndex >= 0) queue else listOf(track)
        tracks.forEach { item ->
            if (item.isOnline) {
                activeOnlineTracks[item.id] = item
                onlineMusicRepository?.cacheOnlineTrack(item)
            }
        }
        val index = if (selectedIndex >= 0) selectedIndex else 0
        val activeTrack = tracks[index]
        resolveJob?.cancel()

        if (activeTrack.isOnline && activeTrack.uri.startsWith(OnlineMusicRepositoryImpl.ONLINE_URI_PREFIX)) {
            val onlineId = activeTrack.onlineId ?: OnlineMusicRepositoryImpl.extractOnlineId(activeTrack.uri)
            if (onlineId == null) {
                _playbackState.value = _playbackState.value.copy(errorMessage = "Invalid online track")
                return
            }
            cachedMediaId = activeTrack.id
            cachedTrack = activeTrack
            _playbackState.value = _playbackState.value.copy(
                track = activeTrack,
                isPlaying = false,
                errorMessage = null,
                queue = tracks,
            )
            resolveJob = scope.launch {
                val resolvedUri = try {
                    onlineMusicRepository?.resolveStreamUrl(onlineId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (resolvedUri.isNullOrBlank()) {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        errorMessage = "Could not load online stream",
                    )
                    return@launch
                }
                resolvedStreamUris[activeTrack.id] = resolvedUri
                val mediaItems = tracks.map { item ->
                    val effectiveUri = resolvedStreamUris[item.id] ?: item.uri
                    toMediaItem(item, effectiveUri)
                }
                player.setMediaItems(mediaItems, index, 0L)
                player.prepare()
                player.play()
                ensurePlaybackServiceStarted()
                publishState()
            }
            return
        }

        val mediaItems = tracks.map { item ->
            toMediaItem(item, resolvedStreamUris[item.id] ?: item.uri)
        }
        if (mediaItems.isEmpty()) return

        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
        ensurePlaybackServiceStarted()
        publishState()
    }

    private fun maybeResolveCurrentOnlineItem(mediaItem: MediaItem?) {
        val item = mediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        if (!uri.startsWith(OnlineMusicRepositoryImpl.ONLINE_URI_PREFIX)) return
        val trackId = item.mediaId.toLongOrNull() ?: return
        val onlineId = OnlineMusicRepositoryImpl.extractOnlineId(uri) ?: return
        val itemIndex = player.currentMediaItemIndex
        if (itemIndex < 0) return

        resolveJob?.cancel()
        resolveJob = scope.launch {
            val resolvedUri = try {
                onlineMusicRepository?.resolveStreamUrl(onlineId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@launch
            resolvedStreamUris[trackId] = resolvedUri
            val track = activeOnlineTracks[trackId] ?: return@launch
            if (player.currentMediaItemIndex == itemIndex) {
                player.replaceMediaItem(itemIndex, toMediaItem(track, resolvedUri))
                player.prepare()
                player.play()
                publishState()
            }
        }
    }

    override fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            ensurePlaybackServiceStarted()
            player.play()
        }
        publishState()
    }

    override fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            if (crossfadeSeconds > 0 && player.isPlaying) startFadeIn()
        }
        publishState()
    }
    override fun skipPrevious() {
        PlaybackNavigation.skipPrevious(player)
        if (crossfadeSeconds > 0 && player.isPlaying) startFadeIn()
        publishState()
    }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)); publishState() }
    override fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled; publishState() }
    override fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishState()
    }

    override fun stopPlayback() {
        flushListeningTime(resetTick = true)
        progressJob?.cancel()
        fadeJob?.cancel()
        player.volume = 1.0f
        cachedMediaId = null
        cachedTrack = null
        player.stop()
        player.clearMediaItems()
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        publishState()
        try {
            val stopIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_STOP
            }
            context.startService(stopIntent)
        } catch (_: IllegalStateException) {
            // Ignore if background service start is restricted
        }
    }

    override fun setLyricsSyncActive(active: Boolean) {
        lyricsSyncActive = active
        if (active) publishState()
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            listeningTrackId = player.currentMediaItem?.mediaId?.toLongOrNull()
            lastListeningTickMs = SystemClock.elapsedRealtime()
            accumulatedListenMs = 0L
            while (isActive && player.isPlaying) {
                accumulateAndMaybeFlushListeningTime()
                updateCrossfadeVolume()
                publishState()
                delay(if (lyricsSyncActive) 50L else 250L)
            }
        }
    }

    private fun accumulateAndMaybeFlushListeningTime() {
        val now = SystemClock.elapsedRealtime()
        val currentTrackId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (listeningTrackId != null && listeningTrackId != currentTrackId) {
            flushListeningTime(resetTick = false)
            return
        }
        if (currentTrackId == null) {
            listeningTrackId = null
            lastListeningTickMs = now
            accumulatedListenMs = 0L
            return
        }
        if (listeningTrackId == null) {
            listeningTrackId = currentTrackId
            lastListeningTickMs = now
            accumulatedListenMs = 0L
            return
        }
        if (lastListeningTickMs > 0L) {
            accumulatedListenMs += (now - lastListeningTickMs).coerceAtLeast(0L)
        }
        lastListeningTickMs = now
        if (accumulatedListenMs >= LISTEN_FLUSH_THRESHOLD_MS) {
            settingsRepository.addListenDuration(currentTrackId, accumulatedListenMs)
            accumulatedListenMs = 0L
        }
    }

    private fun flushListeningTime(resetTick: Boolean) {
        val trackId = listeningTrackId
        if (trackId != null && lastListeningTickMs > 0L) {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastListeningTickMs).coerceAtLeast(0L)
            val total = accumulatedListenMs + delta
            if (total > 0L) {
                settingsRepository.addListenDuration(trackId, total)
            }
        }
        accumulatedListenMs = 0L
        if (resetTick) {
            lastListeningTickMs = 0L
            listeningTrackId = null
        } else {
            lastListeningTickMs = SystemClock.elapsedRealtime()
            listeningTrackId = player.currentMediaItem?.mediaId?.toLongOrNull()
        }
    }

    private fun updateCrossfadeVolume() {
        val fadeSeconds = crossfadeSeconds
        if (fadeSeconds <= 0) {
            if (player.volume != 1.0f) player.volume = 1.0f
            return
        }
        if (fadeJob?.isActive == true) return

        val fadeDurationMs = fadeSeconds * 1_000L
        val durationMs = player.duration
        val remainingMs = if (durationMs > 0L) durationMs - player.currentPosition else 0L
        val canTransition = player.hasNextMediaItem() ||
            player.repeatMode == Player.REPEAT_MODE_ONE ||
            player.repeatMode == Player.REPEAT_MODE_ALL
        if (canTransition && remainingMs in 1..fadeDurationMs) {
            val progress = (remainingMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
            player.volume = (0.2f + 0.8f * progress).coerceIn(0.2f, 1.0f)
        } else if (player.volume != 1.0f) {
            player.volume = 1.0f
        }
    }

    private fun startFadeIn() {
        val fadeSeconds = crossfadeSeconds
        if (fadeSeconds <= 0) {
            player.volume = 1.0f
            return
        }
        fadeJob?.cancel()
        val fadeInDurationMs = (fadeSeconds * 500L).coerceIn(300L, 2_000L)
        val steps = 10
        val stepDelayMs = (fadeInDurationMs / steps).coerceAtLeast(20L)
        fadeJob = scope.launch {
            for (step in 0..steps) {
                if (!isActive || !player.isPlaying || crossfadeSeconds <= 0) {
                    player.volume = 1.0f
                    return@launch
                }
                player.volume = (0.2f + 0.8f * (step.toFloat() / steps)).coerceIn(0.2f, 1.0f)
                if (step < steps) delay(stepDelayMs)
            }
        }
    }

    private fun toMediaItem(track: Track, uriOverride: String = track.uri): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(uriOverride)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build(),
        )
        .build()

    internal fun handlePlayerError(error: PlaybackException) {
        flushListeningTime(resetTick = true)
        progressJob?.cancel()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            errorMessage = error.message ?: "Playback failed (${error.errorCode})",
        )
    }

    private fun ensurePlaybackServiceStarted() {
        try {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_UPDATE
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (_: IllegalStateException) {
            // Avoid crashing with ForegroundServiceStartNotAllowedException if called from background.
        }
    }

    private fun publishState() {
        val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val onlineById = onlineMusicRepository?.getCachedOnlineTracks().orEmpty().associateBy(Track::id)
        val tracksById = onlineById + activeOnlineTracks + libraryRepository.tracks.value.associateBy(Track::id)
        val track = when {
            mediaId == null -> null
            mediaId == cachedMediaId && cachedTrack != null -> cachedTrack
            else -> tracksById[mediaId].also { found ->
                cachedMediaId = mediaId
                cachedTrack = found
            }
        }
        val queue = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(tracksById::get)
        }
        _playbackState.value = PlaybackStateMapper.map(
            playerState = player.playbackState,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it >= 0L } ?: 0L,
            track = track,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        ).copy(errorMessage = _playbackState.value.errorMessage, queue = queue)
    }

    private companion object {
        const val LISTEN_FLUSH_THRESHOLD_MS = 2_000L
    }
}
