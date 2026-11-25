package com.xelilovth.nota.presentation.player

import android.net.Uri
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {
    private class FakeRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        val calls = mutableListOf<String>()
        var playedTrack: Track? = null
        var playedQueue: List<Track> = emptyList()

        override fun playTrack(track: Track, queue: List<Track>) {
            calls += "play:${track.id}:${queue.joinToString(",") { it.id.toString() }}"
            playedTrack = track
            playedQueue = queue
        }
        override fun togglePlayPause() { calls += "toggle" }
        override fun skipNext() { calls += "next" }
        override fun skipPrevious() { calls += "previous" }
        override fun seekTo(positionMs: Long) { playbackState.value = playbackState.value.copy(positionMs = positionMs); calls += "seek:$positionMs" }
        override fun toggleShuffle() {
            playbackState.value = playbackState.value.copy(shuffle = !playbackState.value.shuffle)
            calls += "shuffle"
        }
        override fun cycleRepeatMode() {
            val nextMode = when (playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            playbackState.value = playbackState.value.copy(repeatMode = nextMode)
            calls += "repeat:${nextMode.name}"
        }
        override fun stopPlayback() {
            playbackState.value = PlaybackState()
            calls += "stop"
        }
    }

    private fun track(id: Long) = Track(
        id, Uri.parse("content://audio/$id"), "Track $id", "Artist", "Album", 1000L, "Music",
    )

    private fun viewModel(repository: FakeRepository) = PlayerViewModel(
        repository, PlayTrackUseCase(repository), TogglePlayPauseUseCase(repository),
        SkipNextUseCase(repository), SkipPreviousUseCase(repository), SeekToUseCase(repository),
        ToggleShuffleUseCase(repository), CycleRepeatModeUseCase(repository),
    )

    @Test fun playingTrackFromLibraryUsesSelectedTrackAndRemainingTracksAsQueue() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        val libraryTracks = listOf(track(1L), track(2L), track(3L), track(4L))
        val selectedTrack = libraryTracks[2]

        viewModel.onRowClick(selectedTrack, libraryTracks)

        assertEquals(selectedTrack, repository.playedTrack)
        assertEquals(listOf(3L, 4L), repository.playedQueue.map(Track::id))
        assertEquals("play:3:3,4", repository.calls.single())
    }

    @Test fun rowAndPlayIconSelectionProduceTheSamePlaybackStateAndQueue() {
        val rowRepository = FakeRepository()
        val iconRepository = FakeRepository()
        val rowViewModel = viewModel(rowRepository)
        val iconViewModel = viewModel(iconRepository)
        val libraryTracks = listOf(track(1L), track(2L), track(3L), track(4L))
        val selectedTrack = libraryTracks[2]

        rowViewModel.onRowClick(selectedTrack, libraryTracks)
        iconViewModel.onPlayIconClick(selectedTrack, libraryTracks)

        assertEquals(rowRepository.playbackState.value, iconRepository.playbackState.value)
        assertEquals(rowRepository.playedTrack, iconRepository.playedTrack)
        assertEquals(rowRepository.playedQueue, iconRepository.playedQueue)
        assertEquals(selectedTrack, rowRepository.playedQueue.first())
        assertEquals(selectedTrack, iconRepository.playedQueue.first())
        assertEquals(listOf(3L, 4L), rowRepository.playedQueue.map(Track::id))
    }

    @Test fun shuffleButtonTogglesOnAndOff() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.toggleShuffle()
        assertTrue(viewModel.playbackState.value.shuffle)

        viewModel.toggleShuffle()
        assertFalse(viewModel.playbackState.value.shuffle)
    }

    @Test fun repeatButtonCyclesOffAllOneOff() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        assertEquals(RepeatMode.OFF, viewModel.playbackState.value.repeatMode)
        viewModel.cycleRepeatMode()
        assertEquals(RepeatMode.ALL, viewModel.playbackState.value.repeatMode)
        viewModel.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, viewModel.playbackState.value.repeatMode)
        viewModel.cycleRepeatMode()
        assertEquals(RepeatMode.OFF, viewModel.playbackState.value.repeatMode)
    }

    @Test fun playerActionsAreForwardedToRepository() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.togglePlayPause()
        viewModel.next()
        viewModel.previous()
        viewModel.seek(420L)

        assertEquals(listOf("toggle", "next", "previous", "seek:420"), repository.calls)
        assertEquals(420L, viewModel.playbackState.value.positionMs)
        assertEquals(repository.playbackState, viewModel.playbackState)

        viewModel.dismiss()
        assertEquals("stop", repository.calls.last())
        assertEquals(0L, viewModel.playbackState.value.positionMs)
    }
}
