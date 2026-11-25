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
        var scanCalls = 0
        override suspend fun scan(): List<Track> {
            scanCalls++
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
        override fun stopPlayback() = Unit
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
    fun scanIfEmptyDoesNotRescanWhenAlreadyPopulatedOrAttempted() {
        val populatedRepo = FakeLibraryRepository(listOf(track(1L)))
        val populatedVm = LibraryViewModel(
            ObserveTracksUseCase(populatedRepo),
            ScanLibraryUseCase(populatedRepo),
            PlayTrackUseCase(FakePlaybackRepository()),
        )

        populatedVm.scanIfEmpty()
        assertEquals(0, populatedRepo.scanCalls)

        val emptyRepo = FakeLibraryRepository(emptyList())
        val emptyVm = LibraryViewModel(
            ObserveTracksUseCase(emptyRepo),
            ScanLibraryUseCase(emptyRepo),
            PlayTrackUseCase(FakePlaybackRepository()),
        )

        emptyVm.scanIfEmpty()
        emptyVm.scanIfEmpty()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(1, emptyRepo.scanCalls)
    }

    @Test
    fun sortOrderAndPlayShuffledUpdateStateAndQueue() {
        val libraryTracks = listOf(track(1L), track(2L), track(3L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()
        val viewModel = LibraryViewModel(
            ObserveTracksUseCase(libraryRepository),
            ScanLibraryUseCase(libraryRepository),
            PlayTrackUseCase(playbackRepository),
        )

        viewModel.setSortOrder(LibrarySort.DURATION)
        assertEquals(LibrarySort.DURATION, viewModel.sortOrder.value)

        viewModel.playShuffled(libraryTracks)
        assertEquals(libraryTracks.map(Track::id).toSet(), playbackRepository.queue.map(Track::id).toSet())
    }
}
