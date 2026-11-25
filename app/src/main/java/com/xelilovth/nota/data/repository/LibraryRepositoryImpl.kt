package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val scanner: AudioScanner,
    private val settingsRepository: SettingsRepository,
) : LibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rawTracks = MutableStateFlow<List<Track>>(emptyList())

    override val tracks: StateFlow<List<Track>> = combine(
        rawTracks,
        settingsRepository.settings,
    ) { list, settings ->
        if (settings.hideShortTracks) {
            list.filter { it.durationMs >= SHORT_TRACK_THRESHOLD_MS }
        } else {
            list
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun scan(): List<Track> {
        val scannedTracks = scanner.scan()
        rawTracks.value = scannedTracks
        return if (settingsRepository.settings.value.hideShortTracks) {
            scannedTracks.filter { it.durationMs >= SHORT_TRACK_THRESHOLD_MS }
        } else {
            scannedTracks
        }
    }

    private companion object {
        const val SHORT_TRACK_THRESHOLD_MS = 30_000L
    }
}
