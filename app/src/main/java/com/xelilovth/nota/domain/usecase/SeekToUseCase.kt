package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.PlaybackRepository
import javax.inject.Inject

class SeekToUseCase @Inject constructor(private val repository: PlaybackRepository) {
    operator fun invoke(positionMs: Long) = repository.seekTo(positionMs)
}
