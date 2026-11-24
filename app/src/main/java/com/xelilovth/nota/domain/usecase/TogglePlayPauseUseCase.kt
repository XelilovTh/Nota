package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.PlaybackRepository
import javax.inject.Inject

class TogglePlayPauseUseCase @Inject constructor(private val repository: PlaybackRepository) {
    operator fun invoke() = repository.togglePlayPause()
}
