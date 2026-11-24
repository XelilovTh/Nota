package com.xelilovth.nota.data.repository

import androidx.room.Room
import com.xelilovth.nota.data.local.NotaDatabase
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
class HistoryRepositoryImplTest {

    private lateinit var database: NotaDatabase
    private lateinit var repository: HistoryRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HistoryRepositoryImpl(database.historyDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordPlayAndObserveHistory() = runTest {
        repository.recordPlay(101L)
        repository.recordPlay(202L)

        val history = repository.observeHistory().first()
        assertEquals(2, history.size)
        assertEquals(202L, history[0].trackId)
        assertEquals(101L, history[1].trackId)

        repository.clearHistory()
        assertTrue(repository.observeHistory().first().isEmpty())
    }
}
