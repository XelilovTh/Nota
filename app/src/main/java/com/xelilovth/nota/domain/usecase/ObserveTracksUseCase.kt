package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveTracksUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(): StateFlow<List<Track>> = repository.tracks
}
