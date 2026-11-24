package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.HistoryRepository
import javax.inject.Inject

class RecordPlayHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(trackId: Long) = historyRepository.recordPlay(trackId)
}
