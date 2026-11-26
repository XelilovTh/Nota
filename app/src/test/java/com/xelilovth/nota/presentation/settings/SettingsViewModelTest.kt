package com.xelilovth.nota.presentation.settings

import com.xelilovth.nota.data.media.SleepTimerManager
import com.xelilovth.nota.data.repository.SettingsRepositoryImpl
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.ScanLibraryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private class FakePlaybackRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        override fun playTrack(track: Track, queue: List<Track>) = Unit
        override fun togglePlayPause() = Unit
        override fun skipNext() = Unit
        override fun skipPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeatMode() = Unit
        override fun stopPlayback() = Unit
    }

    private class FakeLibraryRepository : LibraryRepository {
        override val tracks = MutableStateFlow(
            listOf(Track(1L, "content://audio/1", "Song", "Artist", "Album", 120_000L, "Music")),
        )
        var scanCount = 0
        override suspend fun scan(): List<Track> {
            scanCount++
            return tracks.value
        }
    }

    @Test
    fun themeAndShortAudioSettingsPersistAndUpdateState() {
        val context = RuntimeEnvironment.getApplication()
        val settingsRepo = SettingsRepositoryImpl(context)
        val libraryRepo = FakeLibraryRepository()
        val sleepTimer = SleepTimerManager(FakePlaybackRepository())
        val viewModel = SettingsViewModel(
            settingsRepo,
            libraryRepo,
            ScanLibraryUseCase(libraryRepo),
            ObserveTracksUseCase(libraryRepo),
            sleepTimer,
        )

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setHideShortTracks(true)

        assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        assertTrue(viewModel.settings.value.hideShortTracks)

        val reloadedRepo = SettingsRepositoryImpl(context)
        assertEquals(ThemeMode.DARK, reloadedRepo.settings.value.themeMode)
        assertTrue(reloadedRepo.settings.value.hideShortTracks)
    }

    @Test
    fun sleepTimerAndRescanWorkCorrectly() {
        val context = RuntimeEnvironment.getApplication()
        val settingsRepo = SettingsRepositoryImpl(context)
        val libraryRepo = FakeLibraryRepository()
        val sleepTimer = SleepTimerManager(FakePlaybackRepository())
        val viewModel = SettingsViewModel(
            settingsRepo,
            libraryRepo,
            ScanLibraryUseCase(libraryRepo),
            ObserveTracksUseCase(libraryRepo),
            sleepTimer,
        )

        viewModel.startSleepTimer(15)
        assertEquals(900L, viewModel.sleepTimerRemainingSeconds.value)

        viewModel.cancelSleepTimer()
        assertNull(viewModel.sleepTimerRemainingSeconds.value)

        viewModel.rescanLibrary()
        ShadowLooper.idleMainLooper()
        assertEquals(1, libraryRepo.scanCount)
    }
}
