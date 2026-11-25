package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackUseCasesTest {
    private class FakeRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        val calls = mutableListOf<String>()
        override fun playTrack(track: Track, queue: List<Track>) { calls += "play:${track.id}:${queue.joinToString(",") { it.id.toString() }}" }
        override fun togglePlayPause() { calls += "toggle" }
        override fun skipNext() { calls += "next" }
        override fun skipPrevious() { calls += "previous" }
        override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
        override fun toggleShuffle() { calls += "shuffle" }
        override fun cycleRepeatMode() { calls += "repeat" }
        override fun stopPlayback() { calls += "stop" }
    }

    @Test fun eachUseCaseDelegatesItsOperation() {
        val repository = FakeRepository()
        val track = Track(11L, "content://audio/11", "Song", "Artist", "Album", 1L, "Music")
        PlayTrackUseCase(repository)(track, listOf(track))
        TogglePlayPauseUseCase(repository)()
        SkipNextUseCase(repository)()
        SkipPreviousUseCase(repository)()
        SeekToUseCase(repository)(250L)
        ToggleShuffleUseCase(repository)()
        CycleRepeatModeUseCase(repository)()
        assertEquals(listOf("play:11:11", "toggle", "next", "previous", "seek:250", "shuffle", "repeat"), repository.calls)
    }
}
