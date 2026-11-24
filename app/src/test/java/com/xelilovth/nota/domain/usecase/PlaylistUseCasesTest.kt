package com.xelilovth.nota.domain.usecase

import android.net.Uri
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistUseCasesTest {

    private class FakePlaylistRepository : PlaylistRepository {
        val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
        val tracksFlow = MutableStateFlow<List<Track>>(emptyList())
        val calls = mutableListOf<String>()

        override fun observePlaylists(): Flow<List<Playlist>> = playlistsFlow

        override fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> {
            calls += "getPlaylistTracks:$playlistId"
            return tracksFlow
        }

        override suspend fun createPlaylist(name: String): Long {
            calls += "createPlaylist:$name"
            return 42L
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            calls += "deletePlaylist:$playlistId"
        }

        override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
            calls += "addTrackToPlaylist:$playlistId:$trackId"
        }

        override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
            calls += "removeTrackFromPlaylist:$playlistId:$trackId"
        }
    }

    @Test
    fun getPlaylistsUseCaseObservesRepository() = runTest {
        val repository = FakePlaylistRepository()
        val expected = listOf(
            Playlist(id = 1L, name = "Favorites", trackCount = 3, createdAt = 1000L),
            Playlist(id = 2L, name = "Chill", trackCount = 0, createdAt = 2000L),
        )
        repository.playlistsFlow.value = expected

        val useCase = GetPlaylistsUseCase(repository)
        val result = useCase().first()

        assertEquals(expected, result)
    }

    @Test
    fun createPlaylistUseCaseDelegatesToRepository() = runTest {
        val repository = FakePlaylistRepository()
        val useCase = CreatePlaylistUseCase(repository)

        val id = useCase("My Playlist")

        assertEquals(42L, id)
        assertEquals(listOf("createPlaylist:My Playlist"), repository.calls)
    }

    @Test
    fun deletePlaylistUseCaseDelegatesToRepository() = runTest {
        val repository = FakePlaylistRepository()
        val useCase = DeletePlaylistUseCase(repository)

        useCase(5L)

        assertEquals(listOf("deletePlaylist:5"), repository.calls)
    }

    @Test
    fun addTrackToPlaylistUseCaseDelegatesToRepository() = runTest {
        val repository = FakePlaylistRepository()
        val useCase = AddTrackToPlaylistUseCase(repository)

        useCase(playlistId = 7L, trackId = 100L)

        assertEquals(listOf("addTrackToPlaylist:7:100"), repository.calls)
    }

    @Test
    fun removeTrackFromPlaylistUseCaseDelegatesToRepository() = runTest {
        val repository = FakePlaylistRepository()
        val useCase = RemoveTrackFromPlaylistUseCase(repository)

        useCase(playlistId = 7L, trackId = 100L)

        assertEquals(listOf("removeTrackFromPlaylist:7:100"), repository.calls)
    }

    @Test
    fun getPlaylistTracksUseCaseObservesRepository() = runTest {
        val repository = FakePlaylistRepository()
        val expectedTracks = listOf(
            Track(
                id = 10L,
                uri = Uri.parse("content://audio/10"),
                title = "Song A",
                artist = "Artist A",
                album = "Album A",
                durationMs = 180000L,
                folder = "Music",
            ),
        )
        repository.tracksFlow.value = expectedTracks

        val useCase = GetPlaylistTracksUseCase(repository)
        val result = useCase(playlistId = 3L).first()

        assertEquals(expectedTracks, result)
        assertEquals(listOf("getPlaylistTracks:3"), repository.calls)
    }
}
