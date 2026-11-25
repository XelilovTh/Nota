package com.xelilovth.nota.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ScanLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val scanLibrary: ScanLibraryUseCase,
    private val playTrack: PlayTrackUseCase,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
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

    fun onTrackSelected(track: Track) {
        val libraryTracks = tracks.value
        val selectedIndex = libraryTracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) libraryTracks.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }
}
