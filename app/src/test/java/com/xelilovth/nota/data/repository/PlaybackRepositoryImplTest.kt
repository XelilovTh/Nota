package com.xelilovth.nota.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xelilovth.nota.data.media.PlaybackService
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class PlaybackRepositoryImplTest {
    private class RecordingContext(
        base: Context,
        private val throwOnForegroundStart: Boolean = false,
    ) : ContextWrapper(base) {
        var startedService: Intent? = null
        var startedNormalService: Intent? = null

        override fun startForegroundService(service: Intent): ComponentName {
            if (throwOnForegroundStart) {
                throw IllegalStateException("Foreground service start not allowed")
            }
            startedService = service
            return ComponentName(packageName, PlaybackService::class.java.name)
        }

        override fun startService(service: Intent): ComponentName? {
            startedNormalService = service
            return super.startService(service)
        }
    }

    private class FakeLibraryRepository : LibraryRepository {
        override val tracks = MutableStateFlow(emptyList<Track>())
        override suspend fun scan(force: Boolean): List<Track> = tracks.value
    }

    private val player: ExoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun emptyQueueDoesNotStartPlaybackServiceOrSetMediaItems() {
        val context = RecordingContext(RuntimeEnvironment.getApplication())
        val repository = PlaybackRepositoryImpl(context, player, FakeLibraryRepository())
        val track = Track(
            1L,
            Uri.parse("content://audio/1"),
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
        val repository = PlaybackRepositoryImpl(context, player, FakeLibraryRepository())
        val track = Track(
            2L,
            Uri.parse("content://audio/2"),
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
    fun playTrackPreservesArtworkUriInMediaMetadata() {
        val context = RecordingContext(RuntimeEnvironment.getApplication())
        val repository = PlaybackRepositoryImpl(context, player, FakeLibraryRepository())
        val artworkUri = Uri.parse("content://media/external/audio/albumart/99")
        val track = Track(
            99L,
            Uri.parse("content://audio/99"),
            "Artwork Song",
            "Artist",
            "Album",
            1_000L,
            "Music",
            albumId = 99L,
            artworkUri = artworkUri,
        )

        repository.playTrack(track, listOf(track))

        assertEquals(artworkUri, player.currentMediaItem?.mediaMetadata?.artworkUri)
    }

    @Test
    fun emptyPlayerSeekOperationsDoNotThrowOrCrash() {
        val context = RecordingContext(RuntimeEnvironment.getApplication())
        val repository = PlaybackRepositoryImpl(context, player, FakeLibraryRepository())

        // Ensure these operations on empty player do not throw any exception
        repository.seekTo(5000L)
        repository.skipNext()
        repository.skipPrevious()
        repository.togglePlayPause()
        repository.toggleShuffle()
        repository.cycleRepeatMode()

        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun foregroundServiceStartExceptionFallsBackToStartService() {
        val context = RecordingContext(RuntimeEnvironment.getApplication(), throwOnForegroundStart = true)
        val repository = PlaybackRepositoryImpl(context, player, FakeLibraryRepository())
        val track = Track(
            5L,
            Uri.parse("content://audio/5"),
            "Fallback Song",
            "Artist",
            "Album",
            1_000L,
            "Music",
        )

        repository.playTrack(track, listOf(track))

        assertNull(context.startedService)
        assertEquals(PlaybackService::class.java.name, context.startedNormalService?.component?.className)
        assertEquals("5", player.currentMediaItem?.mediaId)
    }
}
