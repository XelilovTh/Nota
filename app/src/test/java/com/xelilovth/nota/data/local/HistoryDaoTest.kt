package com.xelilovth.nota.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {
    private lateinit var database: NotaDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveRecentHistory() = runTest {
        historyDao.insertHistory(PlayHistoryEntity(trackId = 10L, playedAt = 1000L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 20L, playedAt = 2000L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 30L, playedAt = 1500L))

        val recent = historyDao.getRecentHistory(limit = 2).first()
        assertEquals(2, recent.size)
        assertEquals(20L, recent[0].trackId)
        assertEquals(30L, recent[1].trackId)

        val recentTrackIds = historyDao.getRecentTrackIds(limit = 10).first()
        assertEquals(listOf(20L, 30L, 10L), recentTrackIds)
    }

    @Test
    fun getTopPlayedTrackIds() = runTest {
        // Track 1 played 3 times, Track 2 played 1 time, Track 3 played 2 times
        historyDao.insertHistory(PlayHistoryEntity(trackId = 1L, playedAt = 100L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 2L, playedAt = 200L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 3L, playedAt = 300L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 1L, playedAt = 400L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 3L, playedAt = 500L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 1L, playedAt = 600L))

        val topPlayed = historyDao.getTopPlayedTrackIds(limit = 3).first()
        assertEquals(listOf(1L, 3L, 2L), topPlayed)
    }

    @Test
    fun deleteHistoryOlderThanAndClear() = runTest {
        historyDao.insertHistory(PlayHistoryEntity(trackId = 1L, playedAt = 100L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 2L, playedAt = 200L))
        historyDao.insertHistory(PlayHistoryEntity(trackId = 3L, playedAt = 300L))

        val deletedCount = historyDao.deleteHistoryOlderThan(250L)
        assertEquals(2, deletedCount)

        val remaining = historyDao.getAllHistory().first()
        assertEquals(1, remaining.size)
        assertEquals(3L, remaining.first().trackId)

        historyDao.clearHistory()
        assertTrue(historyDao.getAllHistory().first().isEmpty())
    }
}
