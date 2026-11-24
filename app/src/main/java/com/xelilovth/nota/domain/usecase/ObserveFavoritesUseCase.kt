package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(): Flow<Set<Long>> = favoritesRepository.observeFavoriteTrackIds()
}
