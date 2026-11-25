package com.xelilovth.nota.data.media

import androidx.media3.common.Player
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackStateMapperTest {
    @Test fun mapsReadyPlayingAndRepeatOne() {
        val track = Track(3L, "content://audio/3", "Song", "Artist", "Album", 900L, "Music")
        val state = PlaybackStateMapper.map(Player.STATE_READY, true, 120L, 900L, track, true, Player.REPEAT_MODE_ONE)
        assertEquals(track, state.track)
        assertTrue(state.isPlaying)
        assertEquals(120L, state.positionMs)
        assertEquals(900L, state.durationMs)
        assertTrue(state.shuffle)
        assertEquals(RepeatMode.ONE, state.repeatMode)
    }

    @Test fun endedPlayerIsNotReportedAsPlayingAndUnknownRepeatMapsOff() {
        val state = PlaybackStateMapper.map(Player.STATE_ENDED, true, -1L, -1L, null, false, 99)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }
}
