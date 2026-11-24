package com.xelilovth.nota.data.local

import androidx.room.Room
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
class FavoriteDaoTest {
    private lateinit var database: NotaDatabase
    private lateinit var favoriteDao: FavoriteDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, NotaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        favoriteDao = database.favoriteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndCheckFavorite() = runTest {
        assertFalse(favoriteDao.isFavorite(42L).first())
        assertFalse(favoriteDao.isFavoriteOnce(42L))

        favoriteDao.insertFavorite(FavoriteEntity(trackId = 42L, addedAt = 500L))

        assertTrue(favoriteDao.isFavorite(42L).first())
        assertTrue(favoriteDao.isFavoriteOnce(42L))
    }

    @Test
    fun deleteFavoriteByTrackId() = runTest {
        favoriteDao.insertFavorite(FavoriteEntity(trackId = 10L, addedAt = 100L))
        assertTrue(favoriteDao.isFavoriteOnce(10L))

        favoriteDao.deleteFavoriteByTrackId(10L)
        assertFalse(favoriteDao.isFavoriteOnce(10L))
    }

    @Test
    fun retrieveFavoritesOrderedByAddedAtDesc() = runTest {
        favoriteDao.insertFavorite(FavoriteEntity(trackId = 1L, addedAt = 100L))
        favoriteDao.insertFavorite(FavoriteEntity(trackId = 2L, addedAt = 300L))
        favoriteDao.insertFavorite(FavoriteEntity(trackId = 3L, addedAt = 200L))

        val orderedTrackIds = favoriteDao.getAllFavoriteTrackIds().first()
        assertEquals(listOf(2L, 3L, 1L), orderedTrackIds)

        favoriteDao.clearFavorites()
        assertTrue(favoriteDao.getAllFavoriteTrackIds().first().isEmpty())
    }
}
