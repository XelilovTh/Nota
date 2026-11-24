package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.local.FavoriteDao
import com.xelilovth.nota.data.local.FavoriteEntity
import com.xelilovth.nota.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
) : FavoritesRepository {

    override fun observeFavoriteTrackIds(): Flow<Set<Long>> {
        return favoriteDao.getAllFavoriteTrackIds().map { it.toSet() }
    }

    override suspend fun toggleFavorite(trackId: Long) {
        if (favoriteDao.isFavoriteOnce(trackId)) {
            favoriteDao.deleteFavoriteByTrackId(trackId)
        } else {
            favoriteDao.insertFavorite(FavoriteEntity(trackId = trackId))
        }
    }

    override suspend fun isFavorite(trackId: Long): Boolean {
        return favoriteDao.isFavoriteOnce(trackId)
    }
}
