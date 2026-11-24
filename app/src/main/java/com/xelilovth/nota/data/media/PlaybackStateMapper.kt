package com.xelilovth.nota.data.media

import androidx.media3.common.Player
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track

internal object PlaybackStateMapper {
    fun map(
        playerState: Int?,
        isPlaying: Boolean?,
        positionMs: Long?,
        durationMs: Long?,
        track: Track?,
        shuffle: Boolean?,
        repeatMode: Int?,
    ): PlaybackState = PlaybackState(
        track = track,
        isPlaying = (playerState ?: Player.STATE_IDLE) == Player.STATE_READY && (isPlaying == true),
        positionMs = (positionMs ?: 0L).coerceAtLeast(0L),
        durationMs = (durationMs ?: 0L).coerceAtLeast(0L),
        shuffle = shuffle == true,
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        },
    )
}
