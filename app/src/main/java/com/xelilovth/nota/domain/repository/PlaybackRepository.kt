package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlaybackRepository {
    val playbackState: StateFlow<PlaybackState>
    fun playTrack(track: Track, queue: List<Track>)
    fun togglePlayPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeatMode()
    fun stopPlayback()
    fun setLyricsSyncActive(active: Boolean) = Unit
}
