package com.xelilovth.nota.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playTrack: PlayTrackUseCase,
    private val toggleShuffle: ToggleShuffleUseCase,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()
    val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState
    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()
    private var lastTrackId: Long? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.track ?: return@collect
                if (track.id != lastTrackId) {
                    lastTrackId = track.id
                    _recentlyPlayed.value = (listOf(track) + _recentlyPlayed.value.filterNot { it.id == track.id }).take(10)
                }
            }
        }
    }

    fun playFirstTrack() { tracks.value.firstOrNull()?.let(::play) }
    fun playShuffledLibrary() {
        val library = tracks.value
        if (library.isEmpty()) return
        if (!playbackState.value.shuffle) toggleShuffle()
        playTrack(library.random(), library)
    }
    fun play(track: Track) {
        val library = tracks.value
        val selectedIndex = library.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) library.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }
}
