package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.local.HistoryDao
import com.xelilovth.nota.data.local.PlayHistoryEntity
import com.xelilovth.nota.domain.model.PlayHistoryEntry
import com.xelilovth.nota.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
) : HistoryRepository {

    override fun observeHistory(): Flow<List<PlayHistoryEntry>> {
        return historyDao.getAllHistory().map { list ->
            list.map { entity ->
                PlayHistoryEntry(
                    id = entity.id,
                    trackId = entity.trackId,
                    playedAt = entity.playedAt,
                )
            }
        }
    }

    override suspend fun recordPlay(trackId: Long) {
        historyDao.insertHistory(
            PlayHistoryEntity(
                trackId = trackId,
            )
        )
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }
}
