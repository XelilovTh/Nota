package com.xelilovth.nota.presentation.playlists

import android.net.Uri
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.PlaylistRepository
import com.xelilovth.nota.domain.usecase.AddTrackToPlaylistUseCase
import com.xelilovth.nota.domain.usecase.CreatePlaylistUseCase
import com.xelilovth.nota.domain.usecase.DeletePlaylistUseCase
import com.xelilovth.nota.domain.usecase.GetPlaylistTracksUseCase
import com.xelilovth.nota.domain.usecase.GetPlaylistsUseCase
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.RemoveTrackFromPlaylistUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakePlaylistRepository : PlaylistRepository {
        val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
        val tracksFlow = MutableStateFlow<List<Track>>(emptyList())
        val calls = mutableListOf<String>()

        override fun observePlaylists(): Flow<List<Playlist>> = playlistsFlow
        override fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> = tracksFlow
        override suspend fun createPlaylist(name: String): Long {
            calls += "createPlaylist:$name"
            return 1L
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

    private class FakePlaybackRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        var playedTrack: Track? = null
        var playedQueue: List<Track> = emptyList()

        override fun playTrack(track: Track, queue: List<Track>) {
            playedTrack = track
            playedQueue = queue
        }
        override fun togglePlayPause() = Unit
        override fun skipNext() = Unit
        override fun skipPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeatMode() = Unit
    }

    private class FakeLibraryRepository(initialTracks: List<Track>) : LibraryRepository {
        override val tracks = MutableStateFlow(initialTracks)
        override suspend fun scan(force: Boolean): List<Track> = tracks.value
    }

    private fun track(id: Long) = Track(
        id, Uri.parse("content://audio/$id"), "Track $id", "Artist", "Album", 1000L, "Music",
    )

    @Test
    fun createPlaylistDelegatesToUseCase() = runTest(testDispatcher) {
        val playlistRepo = FakePlaylistRepository()
        val libraryRepo = FakeLibraryRepository(emptyList())
        val playbackRepo = FakePlaybackRepository()

        val viewModel = PlaylistsViewModel(
            GetPlaylistsUseCase(playlistRepo),
            ObserveTracksUseCase(libraryRepo),
            CreatePlaylistUseCase(playlistRepo),
            DeletePlaylistUseCase(playlistRepo),
            AddTrackToPlaylistUseCase(playlistRepo),
            RemoveTrackFromPlaylistUseCase(playlistRepo),
            GetPlaylistTracksUseCase(playlistRepo),
            PlayTrackUseCase(playbackRepo),
        )

        viewModel.createPlaylist("My Rock List")
        viewModel.createPlaylist("   ") // Should be ignored
        advanceUntilIdle()

        assertEquals(listOf("createPlaylist:My Rock List"), playlistRepo.calls)
    }

    @Test
    fun deletePlaylistDelegatesToUseCase() = runTest(testDispatcher) {
        val playlistRepo = FakePlaylistRepository()
        val libraryRepo = FakeLibraryRepository(emptyList())
        val playbackRepo = FakePlaybackRepository()

        val viewModel = PlaylistsViewModel(
            GetPlaylistsUseCase(playlistRepo),
            ObserveTracksUseCase(libraryRepo),
            CreatePlaylistUseCase(playlistRepo),
            DeletePlaylistUseCase(playlistRepo),
            AddTrackToPlaylistUseCase(playlistRepo),
            RemoveTrackFromPlaylistUseCase(playlistRepo),
            GetPlaylistTracksUseCase(playlistRepo),
            PlayTrackUseCase(playbackRepo),
        )

        viewModel.deletePlaylist(42L)
        advanceUntilIdle()

        assertEquals(listOf("deletePlaylist:42"), playlistRepo.calls)
    }

    @Test
    fun addAndRemoveTrackDelegateToUseCases() = runTest(testDispatcher) {
        val playlistRepo = FakePlaylistRepository()
        val libraryRepo = FakeLibraryRepository(emptyList())
        val playbackRepo = FakePlaybackRepository()

        val viewModel = PlaylistsViewModel(
            GetPlaylistsUseCase(playlistRepo),
            ObserveTracksUseCase(libraryRepo),
            CreatePlaylistUseCase(playlistRepo),
            DeletePlaylistUseCase(playlistRepo),
            AddTrackToPlaylistUseCase(playlistRepo),
            RemoveTrackFromPlaylistUseCase(playlistRepo),
            GetPlaylistTracksUseCase(playlistRepo),
            PlayTrackUseCase(playbackRepo),
        )

        viewModel.addTrackToPlaylist(10L, 100L)
        viewModel.removeTrackFromPlaylist(10L, 100L)
        advanceUntilIdle()

        assertEquals(
            listOf("addTrackToPlaylist:10:100", "removeTrackFromPlaylist:10:100"),
            playlistRepo.calls,
        )
    }

    @Test
    fun playPlaylistStartsPlaybackWithAllTracks() = runTest(testDispatcher) {
        val playlistRepo = FakePlaylistRepository()
        val libraryRepo = FakeLibraryRepository(emptyList())
        val playbackRepo = FakePlaybackRepository()

        val viewModel = PlaylistsViewModel(
            GetPlaylistsUseCase(playlistRepo),
            ObserveTracksUseCase(libraryRepo),
            CreatePlaylistUseCase(playlistRepo),
            DeletePlaylistUseCase(playlistRepo),
            AddTrackToPlaylistUseCase(playlistRepo),
            RemoveTrackFromPlaylistUseCase(playlistRepo),
            GetPlaylistTracksUseCase(playlistRepo),
            PlayTrackUseCase(playbackRepo),
        )

        val tracks = listOf(track(1L), track(2L), track(3L))
        viewModel.playPlaylist(tracks)

        assertEquals(tracks.first(), playbackRepo.playedTrack)
        assertEquals(tracks, playbackRepo.playedQueue)
    }

    @Test
    fun playFromPlaylistQueuesRemainingTracks() = runTest(testDispatcher) {
        val playlistRepo = FakePlaylistRepository()
        val libraryRepo = FakeLibraryRepository(emptyList())
        val playbackRepo = FakePlaybackRepository()

        val viewModel = PlaylistsViewModel(
            GetPlaylistsUseCase(playlistRepo),
            ObserveTracksUseCase(libraryRepo),
            CreatePlaylistUseCase(playlistRepo),
            DeletePlaylistUseCase(playlistRepo),
            AddTrackToPlaylistUseCase(playlistRepo),
            RemoveTrackFromPlaylistUseCase(playlistRepo),
            GetPlaylistTracksUseCase(playlistRepo),
            PlayTrackUseCase(playbackRepo),
        )

        val tracks = listOf(track(1L), track(2L), track(3L))
        viewModel.playFromPlaylist(tracks[1], tracks)

        assertEquals(tracks[1], playbackRepo.playedTrack)
        assertEquals(listOf(tracks[1], tracks[2]), playbackRepo.playedQueue)
    }
}
