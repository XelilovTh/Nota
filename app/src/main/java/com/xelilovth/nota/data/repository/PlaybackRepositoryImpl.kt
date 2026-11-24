package com.xelilovth.nota.data.repository

import androidx.annotation.OptIn
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishState()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startProgressUpdates() else progressJob?.cancel()
            publishState()
        }
    }

    init { player.addListener(listener); publishState() }

    override fun playTrack(track: Track, queue: List<Track>) {
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        val selectedIndex = queue.indexOfFirst { it.id == track.id }
        val tracks = if (selectedIndex >= 0) queue else listOf(track)
        val index = if (selectedIndex >= 0) selectedIndex else 0
        player.setMediaItems(tracks.map(::toMediaItem), index, 0L)
        player.prepare()
        player.play()
        publishState()
    }

    override fun togglePlayPause() {
        if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
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

    private fun publishState() {
        val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val track = libraryRepository.tracks.value.firstOrNull { it.id == mediaId }
        _playbackState.value = PlaybackStateMapper.map(
            playerState = player.playbackState,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it >= 0L } ?: 0L,
            track = track,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }
}
