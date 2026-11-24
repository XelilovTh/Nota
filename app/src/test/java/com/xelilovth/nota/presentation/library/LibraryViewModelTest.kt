package com.xelilovth.nota.presentation.library

import android.net.Uri
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ScanLibraryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    private class FakeLibraryRepository(initialTracks: List<Track>) : LibraryRepository {
        override val tracks = MutableStateFlow(initialTracks)
        var scanCallCount = 0
        var lastForce: Boolean? = null
        override suspend fun scan(force: Boolean): List<Track> {
            scanCallCount++
            lastForce = force
            return tracks.value
        }
    }

    private class FakePlaybackRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        var selectedTrack: Track? = null
        var queue: List<Track> = emptyList()

        override fun playTrack(track: Track, queue: List<Track>) {
            selectedTrack = track
            this.queue = queue
        }
        override fun togglePlayPause() = Unit
        override fun skipNext() = Unit
        override fun skipPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeatMode() = Unit
    }

    private fun track(id: Long) = Track(
        id, Uri.parse("content://audio/$id"), "Track $id", "Artist", "Album", 1_000L, "Music",
    )

    @Test
    fun selectingLibraryRowStartsTrackAndQueuesItWithFollowingTracks() {
        val libraryTracks = listOf(track(1L), track(2L), track(3L), track(4L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()
        val viewModel = LibraryViewModel(
            ObserveTracksUseCase(libraryRepository),
            ScanLibraryUseCase(libraryRepository),
            PlayTrackUseCase(playbackRepository),
        )

        viewModel.onTrackSelected(libraryTracks[1])

        assertEquals(libraryTracks[1], playbackRepository.selectedTrack)
        assertEquals(listOf(2L, 3L, 4L), playbackRepository.queue.map(Track::id))
    }

    @Test
    fun selectingTrackOutsideCurrentLibraryPlaysItAlone() {
        val libraryTracks = listOf(track(1L), track(2L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()
        val viewModel = LibraryViewModel(
            ObserveTracksUseCase(libraryRepository),
            ScanLibraryUseCase(libraryRepository),
            PlayTrackUseCase(playbackRepository),
        )

        viewModel.onTrackSelected(track(9L))

        assertEquals(listOf(9L), playbackRepository.queue.map(Track::id))
    }

    @Test
    fun toggleFavoriteAndAddToPlaylistInvokeUseCases() {
        val libraryTracks = listOf(track(1L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()

        val favCalls = mutableListOf<Long>()
        val favRepo = object : com.xelilovth.nota.domain.repository.FavoritesRepository {
            override fun observeFavoriteTrackIds() = MutableStateFlow(emptySet<Long>())
            override suspend fun toggleFavorite(trackId: Long) { favCalls += trackId }
            override suspend fun isFavorite(trackId: Long) = false
        }
        val playlistCalls = mutableListOf<String>()
        val playlistRepo = object : com.xelilovth.nota.domain.repository.PlaylistRepository {
            override fun observePlaylists() = MutableStateFlow(emptyList<com.xelilovth.nota.domain.model.Playlist>())
            override fun getPlaylistTracks(playlistId: Long) = MutableStateFlow(emptyList<Track>())
            override suspend fun createPlaylist(name: String) = 1L
            override suspend fun deletePlaylist(playlistId: Long) = Unit
            override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
                playlistCalls += "add:$playlistId:$trackId"
            }
            override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = Unit
        }

        val viewModel = LibraryViewModel(
            ObserveTracksUseCase(libraryRepository),
            ScanLibraryUseCase(libraryRepository),
            PlayTrackUseCase(playbackRepository),
            toggleFavoriteUseCase = com.xelilovth.nota.domain.usecase.ToggleFavoriteUseCase(favRepo),
            addTrackToPlaylistUseCase = com.xelilovth.nota.domain.usecase.AddTrackToPlaylistUseCase(playlistRepo),
        )

        viewModel.toggleFavorite(1L)
        viewModel.addTrackToPlaylist(10L, 1L)

        assertEquals(listOf(1L), favCalls)
        assertEquals(listOf("add:10:1"), playlistCalls)
    }

    @Test
    fun scanSkipsWhenAlreadyHasTracksAndNotForced() {
        val libraryTracks = listOf(track(1L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()
        val viewModel = LibraryViewModel(
            ObserveTracksUseCase(libraryRepository),
            ScanLibraryUseCase(libraryRepository),
            PlayTrackUseCase(playbackRepository),
        )

        viewModel.scan(force = false)
        assertEquals(0, libraryRepository.scanCallCount)

        viewModel.scan(force = true)
        assertEquals(1, libraryRepository.scanCallCount)
        assertEquals(true, libraryRepository.lastForce)
    }
}
