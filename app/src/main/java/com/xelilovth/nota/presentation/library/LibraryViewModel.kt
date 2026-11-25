package com.xelilovth.nota.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ScanLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySort { TITLE, ARTIST, DURATION }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val scanLibrary: ScanLibraryUseCase,
    private val playTrack: PlayTrackUseCase,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _sortOrder = MutableStateFlow(LibrarySort.TITLE)
    val sortOrder: StateFlow<LibrarySort> = _sortOrder.asStateFlow()

    private var hasAttemptedScan = false

    fun scanIfEmpty() {
        if (tracks.value.isEmpty() && !hasAttemptedScan) {
            scan()
        }
    }

    fun scan() {
        if (_isScanning.value) return
        hasAttemptedScan = true
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanLibrary()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setSortOrder(order: LibrarySort) {
        _sortOrder.value = order
    }

    fun onTrackSelected(track: Track, orderedTracks: List<Track> = tracks.value) {
        val selectedIndex = orderedTracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) orderedTracks.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }

    fun playShuffled(orderedTracks: List<Track> = tracks.value) {
        if (orderedTracks.isEmpty()) return
        val shuffled = orderedTracks.shuffled()
        playTrack(shuffled.first(), shuffled)
    }
}
