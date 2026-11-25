package com.xelilovth.nota.data.repository

import androidx.annotation.OptIn
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xelilovth.nota.data.media.PlaybackService
import com.xelilovth.nota.data.media.PlaybackStateMapper
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: ExoPlayer,
    private val libraryRepository: LibraryRepository,
) : PlaybackRepository {
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var cachedMediaId: Long? = null
    private var cachedTrack: Track? = null
    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishState()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startProgressUpdates() else progressJob?.cancel()
            publishState()
        }
        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
    }

    init { player.addListener(listener); publishState() }

    override fun playTrack(track: Track, queue: List<Track>) {
        if (queue.isEmpty()) return

        val selectedIndex = queue.indexOfFirst { it.id == track.id }
        val tracks = if (selectedIndex >= 0) queue else listOf(track)
        val mediaItems = tracks.map(::toMediaItem)
        if (mediaItems.isEmpty()) return

        val index = if (selectedIndex >= 0) selectedIndex else 0
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
        ensurePlaybackServiceStarted()
        publishState()
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

    override fun skipNext() { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); publishState() }
    override fun skipPrevious() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0L); publishState() }
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
        progressJob?.cancel()
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

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && player.isPlaying) {
                publishState()
                delay(500L)
            }
        }
    }

    private fun toMediaItem(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(track.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build(),
        )
        .build()

    internal fun handlePlayerError(error: PlaybackException) {
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
        val track = when {
            mediaId == null -> null
            mediaId == cachedMediaId && cachedTrack != null -> cachedTrack
            else -> libraryRepository.tracks.value.firstOrNull { it.id == mediaId }.also { found ->
                cachedMediaId = mediaId
                cachedTrack = found
            }
        }
        _playbackState.value = PlaybackStateMapper.map(
            playerState = player.playbackState,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it >= 0L } ?: 0L,
            track = track,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        ).copy(errorMessage = _playbackState.value.errorMessage)
    }
}
