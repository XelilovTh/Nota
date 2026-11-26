package com.xelilovth.nota.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackMetadataOverride
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
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
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _sortOrder = MutableStateFlow(LibrarySort.TITLE)
    val sortOrder: StateFlow<LibrarySort> = _sortOrder.asStateFlow()

    private var hasAttemptedScan = false

    fun scanIfEmpty() {
        if (!hasAttemptedScan) {
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
        val queue = if (orderedTracks.any { it.id == track.id }) orderedTracks else listOf(track)
        playTrack(track, queue)
    }

    fun playShuffled(orderedTracks: List<Track> = tracks.value) {
        if (orderedTracks.isEmpty()) return
        val shuffled = orderedTracks.shuffled()
        playTrack(shuffled.first(), shuffled)
    }

    fun findDuplicates(tracks: List<Track>): List<List<Track>> {
        return tracks
            .groupBy { it.title.trim().lowercase() to it.artist.trim().lowercase() }
            .values
            .filter { it.size > 1 }
    }

    fun editTrackMetadata(trackId: Long, title: String, artist: String, album: String) {
        settingsRepository.setTrackMetadataOverride(
            trackId,
            TrackMetadataOverride(
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim(),
            ),
        )
    }

    fun deleteTrack(track: Track, onResult: (DeleteTrackResult) -> Unit) {
        viewModelScope.launch {
            onResult(libraryRepository.deleteTrack(track.id))
        }
    }

    fun confirmTrackDeleted(trackId: Long) {
        libraryRepository.onTrackDeletedFromDevice(trackId)
    }
}
