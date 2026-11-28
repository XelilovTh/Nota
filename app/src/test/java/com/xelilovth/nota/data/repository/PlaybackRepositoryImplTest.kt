package com.xelilovth.nota.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xelilovth.nota.data.media.PlaybackService
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class PlaybackRepositoryImplTest {
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedService: Intent? = null

        override fun startForegroundService(service: Intent): ComponentName {
            startedService = service
            return ComponentName(packageName, PlaybackService::class.java.name)
        }
    }

    private class FakeLibraryRepository(initialTracks: List<Track> = emptyList()) : LibraryRepository {
        override val tracks = MutableStateFlow(initialTracks)
        override suspend fun scan(): List<Track> = tracks.value
    }

    private class FakeSettingsRepository(
        initialSettings: AppSettings = AppSettings(),
    ) : SettingsRepository {
        override val settings = MutableStateFlow(initialSettings)
        override val recentTrackIds = MutableStateFlow<List<Long>>(emptyList())
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setHideShortTracks(hide: Boolean) = Unit
        override fun addRecentTrack(trackId: Long) = Unit
    }

    private class FakeOnlineMusicRepository(
        private val resolvedUrl: String? = "https://example.com/audio.m4a",
    ) : OnlineMusicRepository {
        override val offlineTracks = MutableStateFlow<List<Track>>(emptyList())
        override suspend fun searchOnline(query: String): Result<List<Track>> = Result.success(emptyList())
        override suspend fun resolveStreamUrl(onlineId: String): String? = resolvedUrl
        override suspend fun downloadForOffline(track: Track, location: OfflineStorageLocation): Result<Track> =
            Result.failure(UnsupportedOperationException())
        override suspend fun removeOfflineTrack(track: Track) = Unit
    }

    private val player: ExoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun startingMiddleTrackKeepsEarlierTracksInQueue() {
        val application = RuntimeEnvironment.getApplication()
        val tracks = (1L..3L).map { id ->
            Track(id, "content://audio/$id", "Song $id", "Artist", "Album", 10_000L, "Music")
        }
        val context = RecordingContext(application)
        val repository = PlaybackRepositoryImpl(
            context,
            player,
            FakeLibraryRepository(tracks),
            FakeSettingsRepository(),
        )

        repository.playTrack(tracks[1], tracks)

        assertEquals(3, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(listOf(1L, 2L, 3L), repository.playbackState.value.queue.map(Track::id))
    }

    @Test
    fun emptyQueueDoesNotStartPlaybackServiceOrSetMediaItems() {
        val context = RecordingContext(RuntimeEnvironment.getApplication())
        val repository = PlaybackRepositoryImpl(
            context,
            player,
            FakeLibraryRepository(),
            FakeSettingsRepository(),
        )
        val track = Track(
            1L,
            "content://audio/1",
            "Song",
            "Artist",
            "Album",
            1_000L,
            "Music",
        )

        repository.playTrack(track, emptyList())

        assertNull(context.startedService)
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    @Config(sdk = [33])
    fun deniedNotificationPermissionDoesNotPreventStartingPlayback() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val context = RecordingContext(application)
        val repository = PlaybackRepositoryImpl(
            context,
            player,
            FakeLibraryRepository(),
            FakeSettingsRepository(),
        )
        val track = Track(
            2L,
            "content://audio/2",
            "Allowed Song",
            "Artist",
            "Album",
            1_000L,
            "Music",
        )

        repository.playTrack(track, listOf(track))

        assertEquals(
            PackageManager.PERMISSION_DENIED,
            application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        assertEquals(PlaybackService::class.java.name, context.startedService?.component?.className)
        assertEquals("2", player.currentMediaItem?.mediaId)
    }

    @Test
    fun playerErrorIsKeptInPlaybackStateAndStopsPlayingState() {
        val repository = PlaybackRepositoryImpl(
            RuntimeEnvironment.getApplication(),
            player,
            FakeLibraryRepository(),
            FakeSettingsRepository(),
        )
        val error = PlaybackException(
            "decoder exploded",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
        )

        repository.listener.onPlayerError(error)

        assertFalse(repository.playbackState.value.isPlaying)
        assertEquals("decoder exploded", repository.playbackState.value.errorMessage)
    }

    @Test
    fun startForegroundServiceIllegalStateDoesNotCrashPlayTrack() {
        val throwingContext = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startForegroundService(service: Intent): ComponentName {
                throw IllegalStateException("Background start not allowed")
            }
        }
        val repository = PlaybackRepositoryImpl(
            throwingContext,
            player,
            FakeLibraryRepository(),
            FakeSettingsRepository(),
        )
        val track = Track(
            3L,
            "content://audio/3",
            "Resilient Song",
            "Artist",
            "Album",
            1_000L,
            "Music",
        )

        repository.playTrack(track, listOf(track))

        assertEquals("3", player.currentMediaItem?.mediaId)
    }

    @Test
    fun playbackStateUpdatesActiveTrackToOfflineAfterDownload() {
        val application = RuntimeEnvironment.getApplication()
        val onlineRepository = FakeOnlineMusicRepository()
        val onlineTrack = Track(
            id = -9001L,
            uri = "nota://online/vid9",
            title = "Online Track",
            artist = "Artist",
            album = "Online",
            durationMs = 150_000L,
            folder = "Online",
            isOnline = true,
            onlineId = "vid9",
        )
        val offlineTrack = onlineTrack.copy(
            uri = "file:///data/user/0/com.xelilovth.nota/files/offline_tracks/vid9.m4a",
            folder = "Offline",
            isOnline = false,
        )
        val repository = PlaybackRepositoryImpl(
            RecordingContext(application),
            player,
            FakeLibraryRepository(),
            FakeSettingsRepository(),
            onlineRepository,
        )

        repository.playTrack(onlineTrack, listOf(onlineTrack))
        ShadowLooper.idleMainLooper()
        assertEquals("nota://online/vid9", repository.playbackState.value.track?.uri)
        assertTrue(repository.playbackState.value.track?.isOnline == true)

        onlineRepository.offlineTracks.value = listOf(offlineTrack)
        ShadowLooper.idleMainLooper()
        assertEquals(offlineTrack.uri, repository.playbackState.value.track?.uri)
        assertFalse(repository.playbackState.value.track?.isOnline == true)
    }

    @Test
    fun persistedPlaybackSnapshotRestoresOnRestartAndTogglePlayPauseStartsPlayback() {
        val application = RuntimeEnvironment.getApplication()
        val track = Track(
            42L,
            "content://audio/42",
            "Saved Song",
            "Artist",
            "Album",
            180_000L,
            "Music",
        )
        val firstRepo = PlaybackRepositoryImpl(
            RecordingContext(application),
            player,
            FakeLibraryRepository(listOf(track)),
            FakeSettingsRepository(),
        )
        firstRepo.playTrack(track, listOf(track))
        firstRepo.seekTo(12_345L)

        val secondPlayer = ExoPlayer.Builder(application).build()
        try {
            val secondContext = RecordingContext(application)
            val restoredRepo = PlaybackRepositoryImpl(
                secondContext,
                secondPlayer,
                FakeLibraryRepository(),
                FakeSettingsRepository(),
            )

            assertEquals(42L, restoredRepo.playbackState.value.track?.id)
            assertFalse(restoredRepo.playbackState.value.isPlaying)
            assertEquals(12_345L, restoredRepo.playbackState.value.positionMs)
            assertEquals(Player.STATE_IDLE, secondPlayer.playbackState)

            restoredRepo.togglePlayPause()

            assertTrue(secondPlayer.playWhenReady)
            assertTrue(secondPlayer.playbackState != Player.STATE_IDLE)
            assertNotNull(secondContext.startedService)
        } finally {
            secondPlayer.release()
        }
    }

    @Test
    fun togglePlayPauseRecoversWhenPlayerWasStoppedIntoIdleState() {
        val application = RuntimeEnvironment.getApplication()
        val track = Track(
            55L,
            "content://audio/55",
            "Stopped Song",
            "Artist",
            "Album",
            90_000L,
            "Music",
        )
        val context = RecordingContext(application)
        val repository = PlaybackRepositoryImpl(
            context,
            player,
            FakeLibraryRepository(listOf(track)),
            FakeSettingsRepository(),
        )

        repository.playTrack(track, listOf(track))
        player.stop()
        context.startedService = null
        assertEquals(Player.STATE_IDLE, player.playbackState)

        repository.togglePlayPause()

        assertTrue(player.playWhenReady)
        assertTrue(player.playbackState != Player.STATE_IDLE)
        assertNotNull(context.startedService)
    }

    @Test
    fun stopPlaybackClearsPersistedSnapshot() {
        val application = RuntimeEnvironment.getApplication()
        val track = Track(
            77L,
            "content://audio/77",
            "Dismissed Song",
            "Artist",
            "Album",
            90_000L,
            "Music",
        )
        val firstRepo = PlaybackRepositoryImpl(
            RecordingContext(application),
            player,
            FakeLibraryRepository(listOf(track)),
            FakeSettingsRepository(),
        )
        firstRepo.playTrack(track, listOf(track))
        firstRepo.stopPlayback()

        val secondPlayer = ExoPlayer.Builder(application).build()
        try {
            val restoredRepo = PlaybackRepositoryImpl(
                RecordingContext(application),
                secondPlayer,
                FakeLibraryRepository(),
                FakeSettingsRepository(),
            )
            assertNull(restoredRepo.playbackState.value.track)
            assertEquals(0, secondPlayer.mediaItemCount)
        } finally {
            secondPlayer.release()
        }
    }
}
