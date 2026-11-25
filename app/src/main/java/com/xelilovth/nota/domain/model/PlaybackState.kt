package com.xelilovth.nota.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null,
)

enum class RepeatMode { OFF, ALL, ONE }
