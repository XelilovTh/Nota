package com.xelilovth.nota.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xelilovth.nota.data.media.PlaybackService
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
}
