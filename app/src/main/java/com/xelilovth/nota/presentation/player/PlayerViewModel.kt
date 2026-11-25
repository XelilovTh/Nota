package com.xelilovth.nota.presentation.player

import androidx.lifecycle.ViewModel
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.CycleRepeatModeUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.SeekToUseCase
import com.xelilovth.nota.domain.usecase.SkipNextUseCase
import com.xelilovth.nota.domain.usecase.SkipPreviousUseCase
import com.xelilovth.nota.domain.usecase.TogglePlayPauseUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlaybackRepository,
    private val playTrack: PlayTrackUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val skipNextUseCase: SkipNextUseCase,
    private val skipPreviousUseCase: SkipPreviousUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val toggleShuffleUseCase: ToggleShuffleUseCase,
    private val cycleRepeatModeUseCase: CycleRepeatModeUseCase,
) : ViewModel() {
    val playbackState: StateFlow<PlaybackState> = repository.playbackState
    fun onRowClick(track: Track, libraryTracks: List<Track>) {
        val queue = if (libraryTracks.any { it.id == track.id }) libraryTracks else listOf(track)
        playTrack(track, queue)
    }

    fun onPlayIconClick(track: Track, libraryTracks: List<Track>) = onRowClick(track, libraryTracks)
    fun togglePlayPause() = togglePlayPauseUseCase()
    fun next() = skipNextUseCase()
    fun previous() = skipPreviousUseCase()
    fun seek(positionMs: Long) = seekToUseCase(positionMs)
    fun toggleShuffle() = toggleShuffleUseCase()
    fun cycleRepeatMode() = cycleRepeatModeUseCase()
    fun dismiss() = repository.stopPlayback()
}
