package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.PlayHistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(): Flow<List<PlayHistoryEntry>>
    suspend fun recordPlay(trackId: Long)
    suspend fun clearHistory()
}
