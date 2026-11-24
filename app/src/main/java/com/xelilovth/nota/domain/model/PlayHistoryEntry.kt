package com.xelilovth.nota.domain.model

data class PlayHistoryEntry(
    val id: Long = 0L,
    val trackId: Long,
    val playedAt: Long = System.currentTimeMillis(),
)
