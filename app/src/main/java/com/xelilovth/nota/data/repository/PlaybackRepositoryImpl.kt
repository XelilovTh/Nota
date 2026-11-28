package com.xelilovth.nota.data.repository

import androidx.annotation.OptIn
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
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
import com.xelilovth.nota.data.media.StreamCacheHolder
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
import kotlinx.coroutines.runBlocking
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

    private val prefs = context.getSharedPreferences(PLAYBACK_PREFS_NAME, Context.MODE_PRIVATE)
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var resolveJob: Job? = null
    private val activeOnlineTracks = ConcurrentHashMap<Long, Track>()
    private val restoredTracksById = ConcurrentHashMap<Long, Track>()
    private val resolvedStreamUris = ConcurrentHashMap<Long, String>()
    private var cachedMediaId: Long? = null
    private var cachedTrack: Track? = null
    private var lastRecordedRecentTrackId: Long? = null
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
                savePositionSnapshot()
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
            publishState()
            savePlaybackSnapshot(player.currentPosition.coerceAtLeast(0L))
        }
        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
    }

    init {
        StreamCacheHolder.onlineUriResolver = { onlineUri ->
            val onlineId = OnlineMusicRepositoryImpl.extractOnlineId(onlineUri)
            if (onlineId != null) {
                try {
                    runBlocking { onlineMusicRepository?.resolveStreamUrl(onlineId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
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
        onlineMusicRepository?.let { onlineRepo ->
            scope.launch {
                onlineRepo.offlineTracks.collect {
                    publishState()
                }
            }
        }
        if (player.mediaItemCount == 0) {
            restorePlaybackSnapshot()
        } else {
            publishState()
        }
    }

    override fun playTrack(track: Track, queue: List<Track>) {
        playTrackInternal(track, queue, 0L)
    }

    private fun playTrackInternal(track: Track, queue: List<Track>, startPositionMs: Long) {
        if (queue.isEmpty()) return

        val offlineById = onlineMusicRepository?.offlineTracks?.value.orEmpty().associateBy(Track::id)
        val offlineByOnlineId = onlineMusicRepository?.offlineTracks?.value.orEmpty()
            .mapNotNull { item -> item.onlineId?.let { it to item } }
            .toMap()
        val normalizedQueue = queue.map { item ->
            offlineById[item.id]
                ?: item.onlineId?.let(offlineByOnlineId::get)
                ?: item
        }
        val selectedIndex = normalizedQueue.indexOfFirst { it.id == track.id }
        val tracks = if (selectedIndex >= 0) normalizedQueue else listOf(
            offlineById[track.id] ?: track.onlineId?.let(offlineByOnlineId::get) ?: track,
        )
        tracks.forEach { item ->
            if (item.isOnline) {
                activeOnlineTracks[item.id] = item
                onlineMusicRepository?.cacheOnlineTrack(item)
            }
        }
        val index = if (selectedIndex >= 0) selectedIndex else 0
        val activeTrack = tracks[index]
        val clampedStartMs = startPositionMs.coerceAtLeast(0L)
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
                positionMs = clampedStartMs,
                durationMs = activeTrack.durationMs,
                errorMessage = null,
                queue = tracks,
            )
            savePlaybackSnapshot(clampedStartMs, activeTrack, tracks)
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
                player.setMediaItems(mediaItems, index, clampedStartMs)
                player.prepare()
                player.play()
                ensurePlaybackServiceStarted()
                publishState()
                savePlaybackSnapshot(clampedStartMs)
            }
            return
        }

        val mediaItems = tracks.map { item ->
            toMediaItem(item, resolvedStreamUris[item.id] ?: item.uri)
        }
        if (mediaItems.isEmpty()) return

        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        player.setMediaItems(mediaItems, index, clampedStartMs)
        player.prepare()
        player.play()
        ensurePlaybackServiceStarted()
        publishState()
        savePlaybackSnapshot(clampedStartMs)
    }

    override fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            savePositionSnapshot()
            publishState()
            return
        }

        val activeTrack = resolveCurrentTrack() ?: _playbackState.value.track ?: cachedTrack
        if (player.mediaItemCount == 0) {
            if (activeTrack != null) {
                val queue = _playbackState.value.queue.ifEmpty { listOf(activeTrack) }
                val resumePos = _playbackState.value.positionMs.coerceAtLeast(0L)
                playTrackInternal(activeTrack, queue, resumePos)
            } else {
                publishState()
            }
            return
        }

        val currentItemUri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        val needsPrepare = player.playbackState == Player.STATE_IDLE || player.playerError != null
        val isEnded = player.playbackState == Player.STATE_ENDED
        val needsOnlineReResolve = activeTrack != null &&
            (activeTrack.isOnline || activeTrack.uri.startsWith(OnlineMusicRepositoryImpl.ONLINE_URI_PREFIX)) &&
            (
                player.playerError != null ||
                    (
                        needsPrepare && (
                            resolvedStreamUris[activeTrack.id] == null ||
                                currentItemUri.startsWith(OnlineMusicRepositoryImpl.ONLINE_URI_PREFIX)
                            )
                        )
                )
        val upgradedToOffline = activeTrack != null &&
            !activeTrack.isOnline &&
            !activeTrack.uri.startsWith(OnlineMusicRepositoryImpl.ONLINE_URI_PREFIX) &&
            currentItemUri != activeTrack.uri &&
            needsPrepare

        if (needsOnlineReResolve || upgradedToOffline) {
            if (player.playerError != null && activeTrack != null) {
                resolvedStreamUris.remove(activeTrack.id)
            }
            val resumePos = if (isEnded) 0L else player.currentPosition.coerceAtLeast(0L)
            val queue = resolveCurrentQueue().ifEmpty { listOfNotNull(activeTrack) }
            if (activeTrack != null && queue.isNotEmpty()) {
                playTrackInternal(activeTrack, queue, resumePos)
                return
            }
        }

        if (isEnded) {
            player.seekTo(player.currentMediaItemIndex.coerceAtLeast(0), 0L)
        }
        if (needsPrepare) {
            player.prepare()
        }
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        ensurePlaybackServiceStarted()
        player.play()
        publishState()
    }

    override fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            if (crossfadeSeconds > 0 && player.isPlaying) startFadeIn()
        }
        publishState()
        savePlaybackSnapshot(player.currentPosition.coerceAtLeast(0L))
    }
    override fun skipPrevious() {
        PlaybackNavigation.skipPrevious(player)
        if (crossfadeSeconds > 0 && player.isPlaying) startFadeIn()
        publishState()
        savePlaybackSnapshot(player.currentPosition.coerceAtLeast(0L))
    }
    override fun seekTo(positionMs: Long) {
        val coerced = positionMs.coerceAtLeast(0L)
        player.seekTo(coerced)
        savePositionSnapshot(coerced)
        publishState()
    }
    override fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        publishState()
        savePlaybackSnapshot(player.currentPosition.coerceAtLeast(0L))
    }
    override fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishState()
        savePlaybackSnapshot(player.currentPosition.coerceAtLeast(0L))
    }

    override fun stopPlayback() {
        flushListeningTime(resetTick = true)
        progressJob?.cancel()
        fadeJob?.cancel()
        resolveJob?.cancel()
        player.volume = 1.0f
        cachedMediaId = null
        cachedTrack = null
        player.stop()
        player.clearMediaItems()
        clearPlaybackSnapshot()
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
            savePositionSnapshot()
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
        savePositionSnapshot()
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

    private fun buildTracksById(): Map<Long, Track> {
        val onlineById = onlineMusicRepository?.getCachedOnlineTracks().orEmpty().associateBy(Track::id)
        val offlineById = onlineMusicRepository?.offlineTracks?.value.orEmpty().associateBy(Track::id)
        return restoredTracksById +
            onlineById +
            activeOnlineTracks +
            offlineById +
            libraryRepository.tracks.value.associateBy(Track::id)
    }

    private fun resolveCurrentTrack(tracksById: Map<Long, Track> = buildTracksById()): Track? {
        val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        val latest = tracksById[mediaId]
        return when {
            latest != null -> latest.also { found ->
                cachedMediaId = mediaId
                cachedTrack = found
            }
            mediaId == cachedMediaId && cachedTrack != null -> cachedTrack
            else -> null
        }
    }

    private fun resolveCurrentQueue(tracksById: Map<Long, Track> = buildTracksById()): List<Track> =
        (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(tracksById::get)
        }

    private fun publishState() {
        val tracksById = buildTracksById()
        val track = resolveCurrentTrack(tracksById)
        if (track != null && track.id != lastRecordedRecentTrackId) {
            lastRecordedRecentTrackId = track.id
            settingsRepository.addRecentTrack(track.id)
        }
        val queue = resolveCurrentQueue(tracksById)
        _playbackState.value = PlaybackStateMapper.map(
            playerState = player.playbackState,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it > 0L } ?: track?.durationMs ?: 0L,
            track = track,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        ).copy(errorMessage = _playbackState.value.errorMessage, queue = queue)
    }

    private fun savePositionSnapshot(positionOverrideMs: Long? = null) {
        val track = resolveCurrentTrack() ?: _playbackState.value.track ?: cachedTrack ?: return
        val positionMs = (positionOverrideMs ?: player.currentPosition).coerceAtLeast(0L)
        savePlaybackSnapshot(positionMs, track, resolveCurrentQueue())
    }

    private fun savePlaybackSnapshot(
        positionMs: Long,
        activeTrackOverride: Track? = null,
        queueOverride: List<Track>? = null,
    ) {
        val activeTrack = activeTrackOverride ?: resolveCurrentTrack() ?: _playbackState.value.track ?: cachedTrack ?: return
        val rawQueue = queueOverride?.takeIf { it.isNotEmpty() } ?: resolveCurrentQueue().ifEmpty { listOf(activeTrack) }
        val boundedQueue = boundQueueAroundTrack(rawQueue, activeTrack.id, MAX_PERSISTED_QUEUE_ITEMS)
        val payload = JSONObject().apply {
            put("activeTrackId", activeTrack.id)
            put("positionMs", positionMs.coerceAtLeast(0L))
            put("shuffle", player.shuffleModeEnabled)
            put("repeatMode", player.repeatMode)
            put(
                "queue",
                JSONArray().apply {
                    boundedQueue.forEach { put(trackToJson(it)) }
                },
            )
        }
        prefs.edit().putString(KEY_PLAYBACK_SNAPSHOT, payload.toString()).apply()
    }

    private fun clearPlaybackSnapshot() {
        restoredTracksById.clear()
        prefs.edit().remove(KEY_PLAYBACK_SNAPSHOT).apply()
    }

    private fun restorePlaybackSnapshot() {
        val raw = prefs.getString(KEY_PLAYBACK_SNAPSHOT, null)
        if (raw.isNullOrBlank()) {
            publishState()
            return
        }
        val snapshot = runCatching { JSONObject(raw) }.getOrNull()
        if (snapshot == null) {
            publishState()
            return
        }
        val activeTrackId = snapshot.optLong("activeTrackId", Long.MIN_VALUE)
        if (activeTrackId == Long.MIN_VALUE) {
            publishState()
            return
        }
        val savedPositionMs = snapshot.optLong("positionMs", 0L).coerceAtLeast(0L)
        val savedShuffle = snapshot.optBoolean("shuffle", false)
        val savedRepeatMode = snapshot.optInt("repeatMode", Player.REPEAT_MODE_OFF)
        val queueArray = snapshot.optJSONArray("queue")
        if (queueArray == null || queueArray.length() == 0) {
            publishState()
            return
        }

        val offlineById = onlineMusicRepository?.offlineTracks?.value.orEmpty().associateBy(Track::id)
        val offlineByOnlineId = onlineMusicRepository?.offlineTracks?.value.orEmpty()
            .mapNotNull { item -> item.onlineId?.let { it to item } }
            .toMap()

        val restoredQueue = (0 until queueArray.length()).mapNotNull { idx ->
            val parsed = jsonToTrack(queueArray.optJSONObject(idx) ?: return@mapNotNull null) ?: return@mapNotNull null
            val upgraded = offlineById[parsed.id] ?: parsed.onlineId?.let(offlineByOnlineId::get) ?: parsed
            if (isRestorableTrack(upgraded)) upgraded else null
        }
        val index = restoredQueue.indexOfFirst { it.id == activeTrackId }
        if (index < 0) {
            publishState()
            return
        }

        val activeTrack = restoredQueue[index]
        restoredTracksById.clear()
        restoredQueue.forEach { item ->
            restoredTracksById[item.id] = item
            if (item.isOnline) {
                activeOnlineTracks[item.id] = item
            }
        }
        cachedMediaId = activeTrack.id
        cachedTrack = activeTrack
        lastRecordedRecentTrackId = activeTrack.id
        player.shuffleModeEnabled = savedShuffle
        player.repeatMode = savedRepeatMode
        player.setMediaItems(restoredQueue.map { toMediaItem(it) }, index, savedPositionMs)
        publishState()
    }

    private fun isRestorableTrack(track: Track): Boolean {
        val parsed = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return false
        return when (parsed.scheme) {
            "file" -> parsed.path?.let { File(it).exists() } == true
            else -> track.uri.isNotBlank()
        }
    }

    private fun boundQueueAroundTrack(queue: List<Track>, activeTrackId: Long, maxItems: Int): List<Track> {
        if (queue.size <= maxItems) return queue
        val activeIndex = queue.indexOfFirst { it.id == activeTrackId }.coerceAtLeast(0)
        val half = maxItems / 2
        val start = (activeIndex - half).coerceIn(0, (queue.size - maxItems).coerceAtLeast(0))
        val end = (start + maxItems).coerceAtMost(queue.size)
        return queue.subList(start, end)
    }

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("uri", track.uri)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("durationMs", track.durationMs)
        put("folder", track.folder)
        put("isOnline", track.isOnline)
        if (track.onlineId != null) put("onlineId", track.onlineId)
        if (track.artworkUrl != null) put("artworkUrl", track.artworkUrl)
    }

    private fun jsonToTrack(obj: JSONObject): Track? {
        val uri = obj.optString("uri").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = obj.optLong("id"),
            uri = uri,
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            durationMs = obj.optLong("durationMs"),
            folder = obj.optString("folder"),
            isOnline = obj.optBoolean("isOnline", false),
            onlineId = obj.optString("onlineId").takeIf { it.isNotBlank() },
            artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val LISTEN_FLUSH_THRESHOLD_MS = 2_000L
        const val PLAYBACK_PREFS_NAME = "nota_playback_state"
        const val KEY_PLAYBACK_SNAPSHOT = "playback_snapshot"
        const val MAX_PERSISTED_QUEUE_ITEMS = 100
    }
}
