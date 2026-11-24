package com.xelilovth.nota.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.CycleRepeatModeUseCase
import com.xelilovth.nota.domain.usecase.ObserveFavoritesUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.RecordPlayHistoryUseCase
import com.xelilovth.nota.domain.usecase.SeekToUseCase
import com.xelilovth.nota.domain.usecase.SkipNextUseCase
import com.xelilovth.nota.domain.usecase.SkipPreviousUseCase
import com.xelilovth.nota.domain.usecase.ToggleFavoriteUseCase
import com.xelilovth.nota.domain.usecase.TogglePlayPauseUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    repository: PlaybackRepository,
    private val playTrack: PlayTrackUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val skipNextUseCase: SkipNextUseCase,
    private val skipPreviousUseCase: SkipPreviousUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val toggleShuffleUseCase: ToggleShuffleUseCase,
    private val cycleRepeatModeUseCase: CycleRepeatModeUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase? = null,
    private val recordPlayHistoryUseCase: RecordPlayHistoryUseCase? = null,
    private val observeFavoritesUseCase: ObserveFavoritesUseCase? = null,
) : ViewModel() {
    val playbackState: StateFlow<PlaybackState> = repository.playbackState

    val favoriteTrackIds: StateFlow<Set<Long>> = observeFavoritesUseCase?.invoke()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        ?: MutableStateFlow(emptySet())

    val isCurrentTrackFavorite: StateFlow<Boolean> = combine(playbackState, favoriteTrackIds) { state, favs ->
        val trackId = state.track?.id ?: return@combine false
        favs.contains(trackId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var lastRecordedTrackId: Long? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.track ?: return@collect
                if (state.isPlaying && track.id != lastRecordedTrackId) {
                    lastRecordedTrackId = track.id
                    recordPlayHistoryUseCase?.invoke(track.id)
                }
            }
        }
    }

    fun onRowClick(track: Track, libraryTracks: List<Track>) {
        val selectedIndex = libraryTracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) libraryTracks.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
        if (track.id != lastRecordedTrackId) {
            lastRecordedTrackId = track.id
            viewModelScope.launch {
                recordPlayHistoryUseCase?.invoke(track.id)
            }
        }
    }

    fun onPlayIconClick(track: Track, libraryTracks: List<Track>) = onRowClick(track, libraryTracks)
    fun togglePlayPause() = togglePlayPauseUseCase()
    fun next() = skipNextUseCase()
    fun previous() = skipPreviousUseCase()
    fun seek(positionMs: Long) = seekToUseCase(positionMs)
    fun toggleShuffle() = toggleShuffleUseCase()
    fun cycleRepeatMode() = cycleRepeatModeUseCase()

    fun toggleFavorite(trackId: Long? = playbackState.value.track?.id) {
        val id = trackId ?: return
        viewModelScope.launch {
            toggleFavoriteUseCase?.invoke(id)
        }
    }
}
