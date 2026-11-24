package com.xelilovth.nota.data.repository

import android.net.Uri
import com.xelilovth.nota.data.local.TrackDao
import com.xelilovth.nota.data.local.TrackEntity
import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryImplTest {

    private class FakeTrackDao : TrackDao {
        val tracksFlow = MutableStateFlow<List<TrackEntity>>(emptyList())
        val dbTracks = mutableListOf<TrackEntity>()
        var clearCount = 0

        override fun getAllTracks(): Flow<List<TrackEntity>> = tracksFlow

        override suspend fun getAllTracksOnce(): List<TrackEntity> = dbTracks.toList()

        override suspend fun insertTracks(tracks: List<TrackEntity>) {
            dbTracks.addAll(tracks)
            tracksFlow.value = dbTracks.toList()
        }

        override suspend fun clearTracks() {
            clearCount++
            dbTracks.clear()
            tracksFlow.value = emptyList()
        }
    }

    private fun testTrack(id: Long, title: String) = Track(
        id = id,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 120_000L,
        folder = "Music",
    )

    @Test
    fun scanPublishesTracksToStateFlowAndSavesToDb() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val expected = listOf(testTrack(7L, "Song"))
        val fakeDao = FakeTrackDao()
        var scannerCallCount = 0
        val fakeScanner = object : AudioScanner {
            override suspend fun scan(): List<Track> {
                scannerCallCount++
                return expected
            }
        }
        val repository = LibraryRepositoryImpl(
            scanner = fakeScanner,
            trackDao = fakeDao,
            ioDispatcher = testDispatcher,
        )

        val result = repository.scan(force = true)

        assertEquals(1, scannerCallCount)
        assertEquals(expected, result)
        assertEquals(expected, repository.tracks.value)
        assertEquals(1, fakeDao.dbTracks.size)
        assertEquals(7L, fakeDao.dbTracks[0].id)
    }

    @Test
    fun scanWithoutForceUsesCachedTracksWhenMemoryIsNotEmpty() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val initialTracks = listOf(testTrack(1L, "Song 1"))
        val fakeDao = FakeTrackDao()
        var scannerCallCount = 0
        val fakeScanner = object : AudioScanner {
            override suspend fun scan(): List<Track> {
                scannerCallCount++
                return listOf(testTrack(2L, "Song 2"))
            }
        }
        val repository = LibraryRepositoryImpl(
            scanner = fakeScanner,
            trackDao = fakeDao,
            ioDispatcher = testDispatcher,
        )

        // First scan (force=true) caches to memory and DB
        repository.scan(force = true)
        assertEquals(1, scannerCallCount)

        // Second scan (force=false) should immediately return cached tracks without calling scanner
        val result = repository.scan(force = false)
        assertEquals(1, scannerCallCount)
        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
    }

    @Test
    fun scanWithoutForceLoadsFromDbWhenMemoryIsEmpty() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeDao = FakeTrackDao()
        // Pre-populate DB
        fakeDao.dbTracks.add(
            TrackEntity(
                id = 99L,
                uri = "content://media/external/audio/media/99",
                title = "Cached Song",
                artist = "Artist",
                album = "Album",
                durationMs = 100_000L,
                folder = "Music",
                albumId = null,
                artworkUri = null,
            )
        )
        var scannerCallCount = 0
        val fakeScanner = object : AudioScanner {
            override suspend fun scan(): List<Track> {
                scannerCallCount++
                return listOf(testTrack(100L, "Fresh Song"))
            }
        }
        val repository = LibraryRepositoryImpl(
            scanner = fakeScanner,
            trackDao = fakeDao,
            ioDispatcher = testDispatcher,
        )

        // Scan without force should load from DB and NOT call scanner
        val result = repository.scan(force = false)
        assertEquals(0, scannerCallCount)
        assertEquals(1, result.size)
        assertEquals(99L, result[0].id)
        assertEquals("Cached Song", result[0].title)
    }
}
