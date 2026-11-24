package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val scanner: AudioScanner,
) : LibraryRepository {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    override suspend fun scan(): List<Track> {
        val scannedTracks = scanner.scan()
        _tracks.value = scannedTracks
        return scannedTracks
    }
}
