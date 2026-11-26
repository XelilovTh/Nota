package com.xelilovth.nota.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.data.media.SleepTimerManager
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
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
    private val scanLibrary: ScanLibraryUseCase,
    observeTracks: ObserveTracksUseCase,
    private val sleepTimerManager: SleepTimerManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
    val tracks = observeTracks()
    val sleepTimerRemainingSeconds: StateFlow<Long?> = sleepTimerManager.remainingSeconds

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setHideShortTracks(hide: Boolean) = settingsRepository.setHideShortTracks(hide)

    fun setGeminiApiKey(key: String) = settingsRepository.setGeminiApiKey(key.trim())

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
