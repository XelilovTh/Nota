package com.xelilovth.nota.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trackId: Long,
    val playedAt: Long = System.currentTimeMillis(),
)
