package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.FavoritesRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    suspend operator fun invoke(trackId: Long) = favoritesRepository.toggleFavorite(trackId)
}
