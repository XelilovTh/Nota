package com.xelilovth.nota.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishState()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            try {
                if (isPlaying) startProgressUpdates() else progressJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "onIsPlayingChanged error", e)
            }
            publishState()
        }
        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
    }

    init {
        try {
            player.addListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add player listener", e)
        }
        publishState()
    }

    override fun playTrack(track: Track, queue: List<Track>) {
        if (queue.isEmpty()) return

        val selectedIndex = queue.indexOfFirst { it.id == track.id }
        val tracks = if (selectedIndex >= 0) queue else listOf(track)
        val mediaItems = tracks.map(::toMediaItem)
        if (mediaItems.isEmpty()) return

        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Service start error", e)
            try {
                context.startService(Intent(context, PlaybackService::class.java))
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback startService error", fallbackError)
            }
        }

        try {
            val index = if (selectedIndex >= 0) selectedIndex else 0
            _playbackState.value = _playbackState.value.copy(errorMessage = null)
            player.setMediaItems(mediaItems, index, 0L)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "Player setup error", e)
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                errorMessage = e.message ?: "Playback start failed",
            )
        }
        publishState()
    }

    override fun togglePlayPause() {
        try {
            if (player.mediaItemCount > 0) {
                if (player.isPlaying) player.pause() else player.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePlayPause error", e)
        }
        publishState()
    }

    override fun skipNext() {
        try {
            if (player.mediaItemCount > 0 && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        } catch (e: Exception) {
            Log.e(TAG, "skipNext error", e)
        }
        publishState()
    }

    override fun skipPrevious() {
        try {
            if (player.mediaItemCount > 0) {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "skipPrevious error", e)
        }
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        try {
            if (player.mediaItemCount > 0) {
                val safePosition = positionMs.coerceAtLeast(0L)
                val duration = player.duration
                val targetPosition = if (duration > 0L) safePosition.coerceAtMost(duration) else safePosition
                player.seekTo(targetPosition)
            }
        } catch (e: Exception) {
            Log.e(TAG, "seekTo error", e)
        }
        publishState()
    }

    override fun toggleShuffle() {
        try {
            player.shuffleModeEnabled = !player.shuffleModeEnabled
        } catch (e: Exception) {
            Log.e(TAG, "toggleShuffle error", e)
        }
        publishState()
    }

    override fun cycleRepeatMode() {
        try {
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        } catch (e: Exception) {
            Log.e(TAG, "cycleRepeatMode error", e)
        }
        publishState()
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
                .setArtworkUri(track.artworkUri)
                .build(),
        )
        .build()

    internal fun handlePlayerError(error: PlaybackException?) {
        try {
            progressJob?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling progressJob", e)
        }
        val errorMessage = error?.message ?: error?.let { "Playback failed (${it.errorCode})" } ?: "Playback error"
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            errorMessage = errorMessage,
        )
    }

    private fun publishState() {
        try {
            val mediaItem = player.currentMediaItem
            val mediaId = mediaItem?.mediaId?.toLongOrNull()
            val track = if (mediaId != null) {
                libraryRepository.tracks.value.firstOrNull { it.id == mediaId }
            } else {
                null
            }
            val pos = try { player.currentPosition } catch (e: Exception) { 0L }
            val dur = try { player.duration } catch (e: Exception) { 0L }
            val safeDuration = if (dur >= 0L) dur else 0L
            val isPlaying = try { player.isPlaying } catch (e: Exception) { false }
            val playerState = try { player.playbackState } catch (e: Exception) { Player.STATE_IDLE }
            val shuffle = try { player.shuffleModeEnabled } catch (e: Exception) { false }
            val repeatMode = try { player.repeatMode } catch (e: Exception) { Player.REPEAT_MODE_OFF }

            _playbackState.value = PlaybackStateMapper.map(
                playerState = playerState,
                isPlaying = isPlaying,
                positionMs = pos,
                durationMs = safeDuration,
                track = track,
                shuffle = shuffle,
                repeatMode = repeatMode,
            ).copy(errorMessage = _playbackState.value.errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "publishState error", e)
        }
    }

    companion object {
        private const val TAG = "Playback"
    }
}
