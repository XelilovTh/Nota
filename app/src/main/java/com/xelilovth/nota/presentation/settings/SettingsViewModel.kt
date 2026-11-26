package com.xelilovth.nota.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.data.media.SleepTimerManager
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.ScanLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val scanLibrary: ScanLibraryUseCase,
    observeTracks: ObserveTracksUseCase,
    private val sleepTimerManager: SleepTimerManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
    val discoveredFolders: StateFlow<List<String>> = libraryRepository.allDiscoveredFolders
    val excludedFolders: StateFlow<Set<String>> = settingsRepository.excludedFolders
    val tracks = observeTracks()
    val sleepTimerRemainingSeconds: StateFlow<Long?> = sleepTimerManager.remainingSeconds

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setHideShortTracks(hide: Boolean) = settingsRepository.setHideShortTracks(hide)

    fun setFolderExcluded(folder: String, excluded: Boolean) =
        settingsRepository.setFolderExcluded(folder, excluded)

    fun setCrossfadeSeconds(seconds: Int) = settingsRepository.setCrossfadeSeconds(seconds)

    fun setOfflineStorageLocation(location: OfflineStorageLocation) =
        settingsRepository.setOfflineStorageLocation(location)

    fun setStreamCacheSizeMb(sizeMb: Int) =
        settingsRepository.setStreamCacheSizeMb(sizeMb)

    fun setGeminiApiKey(key: String) = settingsRepository.setGeminiApiKey(key.trim())

    fun findDuplicateGroups(): List<List<Track>> {
        return tracks.value
            .groupBy { it.title.trim().lowercase() to it.artist.trim().lowercase() }
            .values
            .filter { it.size > 1 }
    }

    suspend fun deleteTrack(trackId: Long): DeleteTrackResult =
        libraryRepository.deleteTrack(trackId)

    fun confirmTrackDeleted(trackId: Long) =
        libraryRepository.onTrackDeletedFromDevice(trackId)

    fun rescanLibrary() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanLibrary()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun startSleepTimer(minutes: Int) = sleepTimerManager.start(minutes)

    fun cancelSleepTimer() = sleepTimerManager.cancel()
}
