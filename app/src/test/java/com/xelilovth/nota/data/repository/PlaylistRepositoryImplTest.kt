package com.xelilovth.nota.data.repository

import android.net.Uri
import androidx.room.Room
import com.xelilovth.nota.data.local.NotaDatabase
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class PlaylistRepositoryImplTest {

    private lateinit var database: NotaDatabase
    private lateinit var repository: PlaylistRepositoryImpl
    private val tracksState = MutableStateFlow<List<Track>>(emptyList())

    private val fakeLibraryRepository = object : LibraryRepository {
        override val tracks: StateFlow<List<Track>> = tracksState
        override suspend fun scan(): List<Track> = tracksState.value
    }

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaylistRepositoryImpl(database.playlistDao(), fakeLibraryRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createAndObservePlaylists() = runTest {
        val id1 = repository.createPlaylist("Favorites")
        val id2 = repository.createPlaylist("Rock")
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        repository.addTrackToPlaylist(id1, 101L)
        repository.addTrackToPlaylist(id1, 102L)

        val playlists = repository.observePlaylists().first()
        assertEquals(2, playlists.size)

        val favorites = playlists.first { it.id == id1 }
        val rock = playlists.first { it.id == id2 }
        assertEquals("Favorites", favorites.name)
        assertEquals(2, favorites.trackCount)
        assertEquals("Rock", rock.name)
        assertEquals(0, rock.trackCount)
    }

    @Test
    fun deletePlaylistRemovesIt() = runTest {
        val id = repository.createPlaylist("To Delete")
        assertEquals(1, repository.observePlaylists().first().size)

        repository.deletePlaylist(id)
        assertEquals(0, repository.observePlaylists().first().size)
    }

    @Test
    fun getPlaylistTracksMapsIdsToTrackModels() = runTest {
        val t1 = Track(1L, Uri.parse("content://1"), "Song 1", "Artist 1", "Album 1", 100L, "F")
        val t2 = Track(2L, Uri.parse("content://2"), "Song 2", "Artist 2", "Album 2", 200L, "F")
        val t3 = Track(3L, Uri.parse("content://3"), "Song 3", "Artist 3", "Album 3", 300L, "F")
        tracksState.value = listOf(t1, t2, t3)

        val playlistId = repository.createPlaylist("Mix")
        repository.addTrackToPlaylist(playlistId, 1L)
        repository.addTrackToPlaylist(playlistId, 3L)

        val tracks = repository.getPlaylistTracks(playlistId).first()
        assertEquals(listOf(t1, t3), tracks)

        repository.removeTrackFromPlaylist(playlistId, 1L)
        val tracksAfterRemove = repository.getPlaylistTracks(playlistId).first()
        assertEquals(listOf(t3), tracksAfterRemove)
    }
}
