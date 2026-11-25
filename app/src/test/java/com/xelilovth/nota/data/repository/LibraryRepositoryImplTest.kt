package com.xelilovth.nota.data.repository

import android.net.Uri
import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryImplTest {
    private class FakeSettingsRepository(
        initialHideShort: Boolean = false,
    ) : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings(hideShortTracks = initialHideShort))
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setHideShortTracks(hide: Boolean) {
            settings.value = settings.value.copy(hideShortTracks = hide)
        }
    }

    @Test
    fun scanPublishesTracksToStateFlow() = runTest {
        val expected = listOf(
            Track(
                id = 7L,
                uri = Uri.parse("content://media/external/audio/media/7"),
                title = "Song",
                artist = "Artist",
                album = "Album",
                durationMs = 120_000L,
                folder = "Music",
            ),
        )
        val repository = LibraryRepositoryImpl(
            object : AudioScanner {
                override suspend fun scan() = expected
            },
            FakeSettingsRepository(),
        )

        val result = repository.scan()

        assertEquals(expected, result)
        assertEquals(expected, repository.tracks.value)
    }

    @Test
    fun hideShortTracksFiltersAudioUnderThirtySeconds() = runTest {
        val shortTrack = Track(1L, Uri.parse("content://media/1"), "Beep", "A", "B", 5_000L, "Notif")
        val longTrack = Track(2L, Uri.parse("content://media/2"), "Song", "A", "B", 180_000L, "Music")
        val repository = LibraryRepositoryImpl(
            object : AudioScanner {
                override suspend fun scan() = listOf(shortTrack, longTrack)
            },
            FakeSettingsRepository(initialHideShort = true),
        )

        val result = repository.scan()

        assertEquals(listOf(longTrack), result)
    }
}
