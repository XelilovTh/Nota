package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackMetadataOverride
import com.xelilovth.nota.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryImplTest {
    private class FakeSettingsRepository(
        initialHideShort: Boolean = false,
    ) : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings(hideShortTracks = initialHideShort))
        override val recentTrackIds = MutableStateFlow<List<Long>>(emptyList())
        override val excludedFolders = MutableStateFlow<Set<String>>(emptySet())
        override val metadataOverrides = MutableStateFlow<Map<Long, TrackMetadataOverride>>(emptyMap())

        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setHideShortTracks(hide: Boolean) {
            settings.value = settings.value.copy(hideShortTracks = hide)
        }
        override fun addRecentTrack(trackId: Long) {
            recentTrackIds.value = (listOf(trackId) + recentTrackIds.value.filterNot { it == trackId }).take(10)
        }
        override fun setFolderExcluded(folder: String, excluded: Boolean) {
            excludedFolders.value = if (excluded) excludedFolders.value + folder else excludedFolders.value - folder
        }
        override fun setTrackMetadataOverride(trackId: Long, override: TrackMetadataOverride?) {
            metadataOverrides.value = if (override == null) {
                metadataOverrides.value - trackId
            } else {
                metadataOverrides.value + (trackId to override)
            }
        }
    }

    @Test
    fun scanPublishesTracksToStateFlow() = runTest {
        val expected = listOf(
            Track(
                id = 7L,
                uri = "content://media/external/audio/media/7",
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
            RuntimeEnvironment.getApplication(),
        )

        val result = repository.scan()

        assertEquals(expected, result)
        assertEquals(expected, repository.tracks.value)
        assertEquals(listOf("Music"), repository.allDiscoveredFolders.value)
    }

    @Test
    fun hideShortTracksFiltersAudioUnderThirtySeconds() = runTest {
        val shortTrack = Track(1L, "content://media/1", "Beep", "A", "B", 5_000L, "Notif")
        val longTrack = Track(2L, "content://media/2", "Song", "A", "B", 180_000L, "Music")
        val repository = LibraryRepositoryImpl(
            object : AudioScanner {
                override suspend fun scan() = listOf(shortTrack, longTrack)
            },
            FakeSettingsRepository(initialHideShort = true),
            RuntimeEnvironment.getApplication(),
        )

        val result = repository.scan()

        assertEquals(listOf(longTrack), result)
        assertEquals(listOf("Music", "Notif"), repository.allDiscoveredFolders.value)
    }

    @Test
    fun excludedFoldersAndMetadataOverridesAndDeviceDeleteUpdateTracks() = runTest {
        val track1 = Track(1L, "content://media/1", "Song 1", "Artist 1", "Album 1", 180_000L, "Music")
        val track2 = Track(2L, "content://media/2", "Song 2", "Artist 2", "Album 2", 180_000L, "Downloads")
        val settingsRepo = FakeSettingsRepository()
        val repository = LibraryRepositoryImpl(
            object : AudioScanner {
                override suspend fun scan() = listOf(track1, track2)
            },
            settingsRepo,
            RuntimeEnvironment.getApplication(),
        )

        repository.scan()
        settingsRepo.setFolderExcluded("Downloads", true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(listOf(track1), repository.tracks.value)

        settingsRepo.setTrackMetadataOverride(1L, TrackMetadataOverride("New Title", "New Artist", "New Album"))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("New Title", repository.tracks.value.single().title)
        assertEquals("New Artist", repository.tracks.value.single().artist)
        assertEquals("New Album", repository.tracks.value.single().album)

        repository.onTrackDeletedFromDevice(1L)
        assertEquals(emptyList<Track>(), repository.tracks.value)
        assertEquals(listOf("Downloads"), repository.allDiscoveredFolders.value)
    }
}
