package com.xelilovth.nota.presentation.library

import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackMetadataOverride
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
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
        var deletedIds = mutableListOf<Long>()
        override suspend fun scan(): List<Track> {
            scanCalls++
            return tracks.value
        }
        override fun onTrackDeletedFromDevice(trackId: Long) {
            deletedIds.add(trackId)
            tracks.value = tracks.value.filterNot { it.id == trackId }
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override val recentTrackIds = MutableStateFlow<List<Long>>(emptyList())
        override val metadataOverrides = MutableStateFlow<Map<Long, TrackMetadataOverride>>(emptyMap())
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setHideShortTracks(hide: Boolean) = Unit
        override fun addRecentTrack(trackId: Long) = Unit
        override fun setTrackMetadataOverride(trackId: Long, override: TrackMetadataOverride?) {
            metadataOverrides.value = if (override == null) {
                metadataOverrides.value - trackId
            } else {
                metadataOverrides.value + (trackId to override)
            }
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
        id, "content://audio/$id", "Track $id", "Artist", "Album", 1_000L, "Music",
    )

    private fun createViewModel(
        libraryRepository: LibraryRepository,
        playbackRepository: PlaybackRepository = FakePlaybackRepository(),
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
    ) = LibraryViewModel(
        ObserveTracksUseCase(libraryRepository),
        ScanLibraryUseCase(libraryRepository),
        PlayTrackUseCase(playbackRepository),
        libraryRepository,
        settingsRepository,
    )

    @Test
    fun selectingLibraryRowKeepsTheFullQueueIncludingEarlierTracks() {
        val libraryTracks = listOf(track(1L), track(2L), track(3L), track(4L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()
        val viewModel = createViewModel(libraryRepository, playbackRepository)

        viewModel.onTrackSelected(libraryTracks[1])

        assertEquals(libraryTracks[1], playbackRepository.selectedTrack)
        assertEquals(listOf(1L, 2L, 3L, 4L), playbackRepository.queue.map(Track::id))
    }

    @Test
    fun selectingTrackOutsideCurrentLibraryPlaysItAlone() {
        val libraryTracks = listOf(track(1L), track(2L))
        val libraryRepository = FakeLibraryRepository(libraryTracks)
        val playbackRepository = FakePlaybackRepository()
        val viewModel = createViewModel(libraryRepository, playbackRepository)

        viewModel.onTrackSelected(track(9L))

        assertEquals(listOf(9L), playbackRepository.queue.map(Track::id))
    }

    @Test
    fun scanIfEmptyDoesNotRescanWhenAlreadyPopulatedOrAttempted() {
        val populatedRepo = FakeLibraryRepository(listOf(track(1L)))
        val populatedVm = createViewModel(populatedRepo)

        populatedVm.scanIfEmpty()
        assertEquals(0, populatedRepo.scanCalls)

        val emptyRepo = FakeLibraryRepository(emptyList())
        val emptyVm = createViewModel(emptyRepo)

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
        val viewModel = createViewModel(libraryRepository, playbackRepository)

        viewModel.setSortOrder(LibrarySort.DURATION)
        assertEquals(LibrarySort.DURATION, viewModel.sortOrder.value)

        viewModel.playShuffled(libraryTracks)
        assertEquals(libraryTracks.map(Track::id).toSet(), playbackRepository.queue.map(Track::id).toSet())
    }

    @Test
    fun findDuplicatesEditMetadataAndConfirmDeletedWork() {
        val t1 = Track(1L, "content://audio/1", "Same Song", "Same Artist", "Album A", 1_000L, "Music")
        val t2 = Track(2L, "content://audio/2", " same song ", "SAME ARTIST", "Album B", 2_000L, "Downloads")
        val t3 = Track(3L, "content://audio/3", "Unique", "Artist", "Album C", 3_000L, "Music")
        val libraryRepo = FakeLibraryRepository(listOf(t1, t2, t3))
        val settingsRepo = FakeSettingsRepository()
        val viewModel = createViewModel(libraryRepo, settingsRepository = settingsRepo)

        val duplicates = viewModel.findDuplicates(listOf(t1, t2, t3))
        assertEquals(1, duplicates.size)
        assertEquals(listOf(1L, 2L), duplicates.single().map(Track::id))

        viewModel.editTrackMetadata(1L, "Edited", "Edited Artist", "Edited Album")
        assertEquals(
            TrackMetadataOverride("Edited", "Edited Artist", "Edited Album"),
            settingsRepo.metadataOverrides.value[1L],
        )

        viewModel.confirmTrackDeleted(2L)
        assertEquals(listOf(2L), libraryRepo.deletedIds)
        assertEquals(listOf(1L, 3L), libraryRepo.tracks.value.map(Track::id))
    }
}
