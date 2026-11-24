package com.xelilovth.nota.data.repository

import androidx.room.Room
import com.xelilovth.nota.data.local.NotaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositoryImplTest {

    private lateinit var database: NotaDatabase
    private lateinit var repository: FavoritesRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FavoritesRepositoryImpl(database.favoriteDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun toggleFavoriteAddsAndRemovesTrack() = runTest {
        assertFalse(repository.isFavorite(10L))

        repository.toggleFavorite(10L)
        assertTrue(repository.isFavorite(10L))
        assertEquals(setOf(10L), repository.observeFavoriteTrackIds().first())

        repository.toggleFavorite(10L)
        assertFalse(repository.isFavorite(10L))
        assertEquals(emptySet<Long>(), repository.observeFavoriteTrackIds().first())
    }
}
