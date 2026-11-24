package com.xelilovth.nota.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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

    private class FakeLibraryRepository : LibraryRepository {
        override val tracks = MutableStateFlow(emptyList<Track>())
        override suspend fun scan(): List<Track> = tracks.value
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
}
