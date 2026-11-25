package com.xelilovth.nota.presentation.home

import android.net.Uri
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private class FakeSettingsRepository(
        initialRecentIds: List<Long> = emptyList(),
    ) : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override val recentTrackIds = MutableStateFlow(initialRecentIds)
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setHideShortTracks(hide: Boolean) = Unit
        override fun addRecentTrack(trackId: Long) {
            recentTrackIds.value = (listOf(trackId) + recentTrackIds.value.filterNot { it == trackId }).take(10)
        }
    }

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
        override fun stopPlayback() { playbackState.value = PlaybackState() }
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
            FakeSettingsRepository(),
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
            FakeSettingsRepository(),
        )

        viewModel.playFirstTrack()

        assertEquals(emptyList<Track>(), viewModel.tracks.value)
        assertEquals(emptyList<Long>(), repository.played)
    }

    @Test
    fun playPlaylistStartsFirstTrackAndSetsQueue() {
        val repository = FakeRepository()
        val track1 = track(101)
        val track2 = track(102)
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(listOf(track1, track2))
            override suspend fun scan() = listOf(track1, track2)
        }
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library),
            repository,
            PlayTrackUseCase(repository),
            ToggleShuffleUseCase(repository),
            FakeSettingsRepository(),
        )
        val playlist = com.xelilovth.nota.domain.model.AutoPlaylist(
            id = "album:test",
            title = "Test Album",
            type = com.xelilovth.nota.domain.model.PlaylistGroupType.ALBUM,
            tracks = listOf(track1, track2),
        )

        viewModel.playPlaylist(playlist)

        assertEquals(listOf(101L), repository.played)
        assertEquals(listOf(101L, 102L), repository.queues.single())
    }

    @Test
    fun recentlyPlayedMapsPersistedIdsAndUpdatesOnPlaybackChange() {
        val repository = FakeRepository()
        val track1 = track(1)
        val track2 = track(2)
        val track3 = track(3)
        val library = object : LibraryRepository {
            override val tracks = MutableStateFlow(listOf(track1, track2, track3))
            override suspend fun scan() = listOf(track1, track2, track3)
        }
        val settingsRepository = FakeSettingsRepository(initialRecentIds = listOf(2L, 999L, 1L))
        val viewModel = HomeViewModel(
            ObserveTracksUseCase(library),
            repository,
            PlayTrackUseCase(repository),
            ToggleShuffleUseCase(repository),
            settingsRepository,
        )

        assertEquals(listOf(track2, track1), viewModel.recentlyPlayed.value)

        repository.playbackState.value = PlaybackState(track = track3, isPlaying = true)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(3L, 2L, 999L, 1L), settingsRepository.recentTrackIds.value)
        assertEquals(listOf(track3, track2, track1), viewModel.recentlyPlayed.value)
    }
}
