package com.xelilovth.nota.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TrackDaoTest {
    private lateinit var database: NotaDatabase
    private lateinit var trackDao: TrackDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trackDao = database.trackDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetAllTracks() = runTest {
        assertTrue(trackDao.getAllTracks().first().isEmpty())

        val tracks = listOf(
            TrackEntity(
                id = 1L,
                uri = "content://media/external/audio/media/1",
                title = "Song B",
                artist = "Artist",
                album = "Album",
                durationMs = 120_000L,
                folder = "Music",
                albumId = 10L,
                artworkUri = "content://media/external/audio/albumart/10",
            ),
            TrackEntity(
                id = 2L,
                uri = "content://media/external/audio/media/2",
                title = "Song A",
                artist = "Artist",
                album = "Album",
                durationMs = 150_000L,
                folder = "Music",
                albumId = 11L,
                artworkUri = null,
            ),
        )

        trackDao.insertTracks(tracks)

        val retrieved = trackDao.getAllTracks().first()
        assertEquals(2, retrieved.size)
        assertEquals("Song A", retrieved[0].title)
        assertEquals("Song B", retrieved[1].title)

        val once = trackDao.getAllTracksOnce()
        assertEquals(2, once.size)
        assertEquals("Song A", once[0].title)
    }

    @Test
    fun clearTracksRemovesAll() = runTest {
        val track = TrackEntity(
            id = 1L,
            uri = "content://media/external/audio/media/1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 120_000L,
            folder = "Music",
            albumId = null,
            artworkUri = null,
        )
        trackDao.insertTracks(listOf(track))
        assertEquals(1, trackDao.getAllTracksOnce().size)

        trackDao.clearTracks()
        assertTrue(trackDao.getAllTracksOnce().isEmpty())
    }
}
