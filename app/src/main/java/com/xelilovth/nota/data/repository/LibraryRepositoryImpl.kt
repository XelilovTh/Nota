package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.local.TrackDao
import com.xelilovth.nota.data.local.toDomain
import com.xelilovth.nota.data.local.toEntity
import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.di.IoDispatcher
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val scanner: AudioScanner,
    private val trackDao: TrackDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryRepository {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val scanMutex = Mutex()

    init {
        CoroutineScope(ioDispatcher).launch {
            trackDao.getAllTracks().collect { entities ->
                val domainTracks = entities.map { it.toDomain() }
                if (domainTracks.isNotEmpty() || _tracks.value.isEmpty()) {
                    _tracks.value = domainTracks
                }
            }
        }
    }

    override suspend fun scan(force: Boolean): List<Track> = withContext(ioDispatcher) {
        scanMutex.withLock {
            if (!force && _tracks.value.isNotEmpty()) {
                return@withContext _tracks.value
            }

            // Check if DB already has tracks before hitting MediaStore if not forcing
            if (!force) {
                val dbTracks = trackDao.getAllTracksOnce().map { it.toDomain() }
                if (dbTracks.isNotEmpty()) {
                    _tracks.value = dbTracks
                    return@withContext dbTracks
                }
            }

            val scannedTracks = scanner.scan()
            if (scannedTracks.isNotEmpty()) {
                trackDao.clearTracks()
                trackDao.insertTracks(scannedTracks.map { it.toEntity() })
                _tracks.value = scannedTracks
            } else if (force) {
                trackDao.clearTracks()
                _tracks.value = emptyList()
            }
            return@withContext _tracks.value
        }
    }
}
