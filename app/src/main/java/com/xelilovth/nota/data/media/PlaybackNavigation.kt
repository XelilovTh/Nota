package com.xelilovth.nota.data.media

import androidx.media3.common.Player

/**
 * Single source of truth for the "Previous" button behavior, shared by
 * [PlaybackService] (notification buttons) and PlaybackRepositoryImpl (in-app UI),
 * so both act identically instead of drifting into two different rules.
 */
internal object PlaybackNavigation {
    private const val RESTART_THRESHOLD_MS = 3_000L

    /** True when Previous should restart the current track instead of moving to the previous one. */
    fun shouldRestartCurrentTrack(player: Player): Boolean =
        player.currentPosition > RESTART_THRESHOLD_MS || !player.hasPreviousMediaItem()

    fun skipPrevious(player: Player) {
        if (shouldRestartCurrentTrack(player)) player.seekTo(0L)
        else player.seekToPreviousMediaItem()
    }
}
