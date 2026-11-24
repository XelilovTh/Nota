package com.xelilovth.nota.presentation.home

import android.net.Uri
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
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
            override suspend fun scan() = libraryTracks
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
    fun emptyLibraryShowsNoTracksAndPlayDoesNothing() {
        val repository = FakeRepository()
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(emptyList<Track>())
            override suspend fun scan() = emptyList<Track>()
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library), repository,
            PlayTrackUseCase(repository), ToggleShuffleUseCase(repository),
        )

        viewModel.playFirstTrack()

        assertEquals(emptyList<Track>(), viewModel.tracks.value)
        assertEquals(emptyList<Long>(), repository.played)
    }
}
