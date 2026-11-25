package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val scanner: AudioScanner,
    private val settingsRepository: SettingsRepository,
) : LibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rawTracks: List<Track> = emptyList()
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect {
                publishFilteredTracks()
            }
        }
    }

    override suspend fun scan(): List<Track> {
        rawTracks = scanner.scan()
        return publishFilteredTracks()
    }

    private fun publishFilteredTracks(): List<Track> {
        val filtered = if (settingsRepository.settings.value.hideShortTracks) {
            rawTracks.filter { it.durationMs >= SHORT_TRACK_THRESHOLD_MS }
        } else {
            rawTracks
        }
        _tracks.value = filtered
        return filtered
    }

    private companion object {
        const val SHORT_TRACK_THRESHOLD_MS = 30_000L
    }
}
