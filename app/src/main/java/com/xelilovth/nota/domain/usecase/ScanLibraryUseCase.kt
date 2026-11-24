package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import javax.inject.Inject

class ScanLibraryUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(): List<Track> = repository.scan()
}
