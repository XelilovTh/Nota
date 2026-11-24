package com.xelilovth.nota.presentation.home

import android.net.Uri
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.PlaylistGroupType
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private class FakeRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        val played = mutableListOf<Long>()
        val queues = mutableListOf<List<Long>>()
        override fun playTrack(track: Track, queue: List<Track>) { played += track.id; queues += queue.map { it.id } }
        override fun togglePlayPause() = Unit
        override fun skipNext() = Unit
        override fun skipPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() { playbackState.value = playbackState.value.copy(shuffle = !playbackState.value.shuffle) }
        override fun cycleRepeatMode() = Unit
    }

    private fun track(id: Long) = Track(id, Uri.parse("content://audio/$id"), "Track", "Artist", "Album", 1000L, "Music")

    @Test
    fun playActionStartsFirstLibraryTrack() {
        val repository = FakeRepository()
        val libraryTracks = listOf(track(11), track(12))
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(libraryTracks)
            override suspend fun scan(force: Boolean) = libraryTracks
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library),
            repository,
            PlayTrackUseCase(repository),
            ToggleShuffleUseCase(repository),
        )

        viewModel.playFirstTrack()

        assertEquals(listOf(11L), repository.played)
        assertEquals(listOf(11L, 12L), repository.queues.single())
    }

    @Test
    fun playCollectionQueuesOnlyTracksFromSelectedCollection() {
        val repository = FakeRepository()
        val libraryTracks = listOf(track(11), track(12), track(13))
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(libraryTracks)
            override suspend fun scan(force: Boolean) = libraryTracks
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library), repository,
            PlayTrackUseCase(repository), ToggleShuffleUseCase(repository),
        )
        val collection = AutoPlaylist("album:test", "Test album", PlaylistGroupType.ALBUM, listOf(track(12), track(13)))

        viewModel.playCollection(collection)

        assertEquals(listOf(12L), repository.played)
        assertEquals(listOf(12L, 13L), repository.queues.single())
    }

    @Test
    fun playFromCollectionStartsAtSelectedTrackAndKeepsOnlyFollowingCollectionTracks() {
        val repository = FakeRepository()
        val libraryTracks = listOf(track(11), track(12), track(13))
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(libraryTracks)
            override suspend fun scan(force: Boolean) = libraryTracks
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library), repository,
            PlayTrackUseCase(repository), ToggleShuffleUseCase(repository),
        )
        val collection = AutoPlaylist("album:test", "Test album", PlaylistGroupType.ALBUM, listOf(track(11), track(12), track(13)))

        viewModel.playFromCollection(track(12), collection)

        assertEquals(listOf(12L), repository.played)
        assertEquals(listOf(12L, 13L), repository.queues.single())
    }

    @Test
    fun playCollectionWithShuffleUsesOnlyCollectionTracks() {
        val repository = FakeRepository()
        val libraryTracks = listOf(track(11), track(12), track(13))
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(libraryTracks)
            override suspend fun scan(force: Boolean) = libraryTracks
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library), repository,
            PlayTrackUseCase(repository), ToggleShuffleUseCase(repository),
        )
        val collection = AutoPlaylist("album:test", "Test album", PlaylistGroupType.ALBUM, listOf(track(12), track(13)))

        viewModel.playCollection(collection, shuffle = true)

        assertEquals(1, repository.played.size)
        assertEquals(setOf(12L, 13L), repository.queues.single().toSet())
        assertEquals(2, repository.queues.single().size)
        assertEquals(repository.queues.single().first(), repository.played.single())
    }

    @Test
    fun emptyLibraryShowsNoTracksAndPlayDoesNothing() {
        val repository = FakeRepository()
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(emptyList<Track>())
            override suspend fun scan(force: Boolean) = emptyList<Track>()
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library), repository,
            PlayTrackUseCase(repository), ToggleShuffleUseCase(repository),
        )

        viewModel.playFirstTrack()

        assertEquals(emptyList<Track>(), viewModel.tracks.value)
        assertEquals(emptyList<Long>(), repository.played)
    }

    @Test
    fun playFavoritesPlaysFirstFavoriteAndQueuesAllFavorites() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeRepository()
            val libraryTracks = listOf(track(10), track(20), track(30))
            val library = object : LibraryRepository {
                override val tracks = MutableStateFlow(libraryTracks)
                override suspend fun scan(force: Boolean) = libraryTracks
            }
            val favoritesRepo = object : com.xelilovth.nota.domain.repository.FavoritesRepository {
                override fun observeFavoriteTrackIds() = MutableStateFlow(setOf(20L, 30L))
                override suspend fun toggleFavorite(trackId: Long) = Unit
                override suspend fun isFavorite(trackId: Long) = true
            }

            val viewModel = HomeViewModel(
                ObserveTracksUseCase(library), repository,
                PlayTrackUseCase(repository), ToggleShuffleUseCase(repository),
                observeFavoritesUseCase = com.xelilovth.nota.domain.usecase.ObserveFavoritesUseCase(favoritesRepo),
            )

            testScheduler.advanceUntilIdle()

            viewModel.playFavorites(shuffle = false)

            assertEquals(listOf(20L), repository.played)
            assertEquals(listOf(20L, 30L), repository.queues.single())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
