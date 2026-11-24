package com.xelilovth.nota.domain.repository

import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun observeFavoriteTrackIds(): Flow<Set<Long>>
    suspend fun toggleFavorite(trackId: Long)
    suspend fun isFavorite(trackId: Long): Boolean
}
