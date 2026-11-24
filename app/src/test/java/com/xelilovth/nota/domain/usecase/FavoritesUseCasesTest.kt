package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesUseCasesTest {

    private class FakeFavoritesRepository : FavoritesRepository {
        val favoritesFlow = MutableStateFlow<Set<Long>>(emptySet())
        val calls = mutableListOf<String>()

        override fun observeFavoriteTrackIds(): Flow<Set<Long>> = favoritesFlow

        override suspend fun toggleFavorite(trackId: Long) {
            calls += "toggleFavorite:$trackId"
        }

        override suspend fun isFavorite(trackId: Long): Boolean {
            return favoritesFlow.value.contains(trackId)
        }
    }

    @Test
    fun observeFavoritesUseCaseReturnsTrackIds() = runTest {
        val repository = FakeFavoritesRepository()
        val expected = setOf(10L, 20L, 30L)
        repository.favoritesFlow.value = expected

        val useCase = ObserveFavoritesUseCase(repository)
        val result = useCase().first()

        assertEquals(expected, result)
    }

    @Test
    fun toggleFavoriteUseCaseDelegatesToRepository() = runTest {
        val repository = FakeFavoritesRepository()
        val useCase = ToggleFavoriteUseCase(repository)

        useCase(101L)

        assertEquals(listOf("toggleFavorite:101"), repository.calls)
    }
}
