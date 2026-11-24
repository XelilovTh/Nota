package com.xelilovth.nota.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: PlayHistoryEntity): Long

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun getAllHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<PlayHistoryEntity>>

    @Query("SELECT trackId FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentTrackIds(limit: Int = 50): Flow<List<Long>>

    @Query("SELECT trackId FROM play_history GROUP BY trackId ORDER BY COUNT(id) DESC LIMIT :limit")
    fun getTopPlayedTrackIds(limit: Int = 50): Flow<List<Long>>

    @Delete
    suspend fun deleteHistory(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE playedAt < :timestamp")
    suspend fun deleteHistoryOlderThan(timestamp: Long): Int

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
