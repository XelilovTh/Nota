package com.xelilovth.nota.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val playTrack: PlayTrackUseCase,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val localTracks: StateFlow<List<Track>> = observeTracks()
    val downloadProgress: StateFlow<Map<Long, Int>> = onlineMusicRepository.downloadProgress
    val downloadedOnlineIds: StateFlow<Set<String>> = onlineMusicRepository.downloadedOnlineIds

    private val _onlineResults = MutableStateFlow<List<Track>>(emptyList())
    val onlineResults: StateFlow<List<Track>> = _onlineResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineError = MutableStateFlow<String?>(null)
    val onlineError: StateFlow<String?> = _onlineError.asStateFlow()

    private var searchJob: Job? = null

    fun play(track: Track, queue: List<Track>) {
        playTrack(track, queue)
    }

    fun searchOnline(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isBlank()) {
            _onlineResults.value = emptyList()
            _isSearchingOnline.value = false
            _onlineError.value = null
            return
        }
        searchJob = viewModelScope.launch {
            _isSearchingOnline.value = true
            _onlineError.value = null
            val result = onlineMusicRepository.searchOnline(trimmed)
            _isSearchingOnline.value = false
            result.onSuccess { tracks ->
                _onlineResults.value = tracks
            }.onFailure {
                _onlineError.value = "Could not load online results"
            }
        }
    }

    fun downloadForOffline(track: Track, onResult: (Result<Track>) -> Unit) {
        viewModelScope.launch {
            val location = settingsRepository.settings.value.offlineStorageLocation
            onResult(onlineMusicRepository.downloadForOffline(track, location))
        }
    }
}
