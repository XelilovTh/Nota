package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.PlayHistoryEntry
import com.xelilovth.nota.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryUseCasesTest {

    private class FakeHistoryRepository : HistoryRepository {
        val historyFlow = MutableStateFlow<List<PlayHistoryEntry>>(emptyList())
        val calls = mutableListOf<String>()

        override fun observeHistory(): Flow<List<PlayHistoryEntry>> = historyFlow

        override suspend fun recordPlay(trackId: Long) {
            calls += "recordPlay:$trackId"
        }

        override suspend fun clearHistory() {
            calls += "clearHistory"
        }
    }

    @Test
    fun observeHistoryUseCaseReturnsEntries() = runTest {
        val repository = FakeHistoryRepository()
        val expected = listOf(
            PlayHistoryEntry(id = 1L, trackId = 100L, playedAt = 1000L),
            PlayHistoryEntry(id = 2L, trackId = 200L, playedAt = 2000L),
        )
        repository.historyFlow.value = expected

        val useCase = ObserveHistoryUseCase(repository)
        val result = useCase().first()

        assertEquals(expected, result)
    }

    @Test
    fun recordPlayHistoryUseCaseDelegatesToRepository() = runTest {
        val repository = FakeHistoryRepository()
        val useCase = RecordPlayHistoryUseCase(repository)

        useCase(555L)

        assertEquals(listOf("recordPlay:555"), repository.calls)
    }
}
