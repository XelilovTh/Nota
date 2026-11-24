package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.PlayHistoryEntry
import com.xelilovth.nota.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    operator fun invoke(): Flow<List<PlayHistoryEntry>> = historyRepository.observeHistory()
}
