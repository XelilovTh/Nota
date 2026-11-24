package com.xelilovth.nota.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistDaoTest {
    private lateinit var database: NotaDatabase
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrievePlaylist() = runTest {
        val id = playlistDao.insertPlaylist(PlaylistEntity(name = "Rock Classics", createdAt = 1000L))
        assertTrue(id > 0)

        val retrieved = playlistDao.getPlaylistById(id)
        assertNotNull(retrieved)
        assertEquals("Rock Classics", retrieved?.name)
        assertEquals(1000L, retrieved?.createdAt)

        val all = playlistDao.getAllPlaylists().first()
        assertEquals(1, all.size)
        assertEquals("Rock Classics", all.first().name)
    }

    @Test
    fun updatePlaylist() = runTest {
        val id = playlistDao.insertPlaylist(PlaylistEntity(name = "Initial Name"))
        val existing = playlistDao.getPlaylistById(id)!!
        val updated = existing.copy(name = "Updated Name")
        playlistDao.updatePlaylist(updated)

        val retrieved = playlistDao.getPlaylistById(id)
        assertEquals("Updated Name", retrieved?.name)
    }

    @Test
    fun deletePlaylistCascadesToTracks() = runTest {
        val playlistId = playlistDao.insertPlaylist(PlaylistEntity(name = "My Playlist"))
        playlistDao.insertTracksToPlaylist(
            listOf(
                PlaylistTrackCrossRef(playlistId = playlistId, trackId = 101L, addedAt = 10L),
                PlaylistTrackCrossRef(playlistId = playlistId, trackId = 102L, addedAt = 20L),
            ),
        )

        val countBefore = playlistDao.getTrackCountForPlaylist(playlistId).first()
        assertEquals(2, countBefore)

        playlistDao.deletePlaylistById(playlistId)

        assertNull(playlistDao.getPlaylistById(playlistId))
        val countAfter = playlistDao.getTrackCountForPlaylist(playlistId).first()
        assertEquals(0, countAfter)
    }

    @Test
    fun addAndRemoveTracksFromPlaylist() = runTest {
        val playlistId = playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites 2"))
        playlistDao.insertTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = 1L, addedAt = 100L))
        playlistDao.insertTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = 2L, addedAt = 200L))

        val trackIds = playlistDao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(1L, 2L), trackIds)

        playlistDao.removeTrackFromPlaylist(playlistId, 1L)
        val remaining = playlistDao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(2L), remaining)

        playlistDao.clearPlaylistTracks(playlistId)
        val empty = playlistDao.getTrackIdsForPlaylist(playlistId).first()
        assertTrue(empty.isEmpty())
    }
}
