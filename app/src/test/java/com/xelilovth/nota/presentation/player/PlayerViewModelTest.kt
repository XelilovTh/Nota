package com.xelilovth.nota.presentation.player

import android.net.Uri
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {
    private class FakeRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        val calls = mutableListOf<String>()
        override fun playTrack(track: Track) { calls += "play:${track.id}" }
        override fun togglePlayPause() { calls += "toggle" }
        override fun skipNext() { calls += "next" }
        override fun skipPrevious() { calls += "previous" }
        override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
        override fun toggleShuffle() { calls += "shuffle" }
        override fun cycleRepeatMode() { calls += "repeat" }
    }

    @Test fun playerActionsAreForwardedToRepository() {
        val repository = FakeRepository()
        val viewModel = PlayerViewModel(
            repository, PlayTrackUseCase(repository), TogglePlayPauseUseCase(repository),
            SkipNextUseCase(repository), SkipPreviousUseCase(repository), SeekToUseCase(repository),
            ToggleShuffleUseCase(repository), CycleRepeatModeUseCase(repository),
        )
        val track = Track(7L, Uri.parse("content://audio/7"), "Track", "Artist", "Album", 1000L, "Music")

        viewModel.play(track)
        viewModel.togglePlayPause()
        viewModel.next()
        viewModel.previous()
        viewModel.seek(420L)
        viewModel.toggleShuffle()
        viewModel.cycleRepeatMode()

        assertEquals(listOf("play:7", "toggle", "next", "previous", "seek:420", "shuffle", "repeat"), repository.calls)
        assertEquals(repository.playbackState, viewModel.playbackState)
    }
}
