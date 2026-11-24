package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {
    val tracks: StateFlow<List<Track>>
    suspend fun scan(force: Boolean = false): List<Track>
}
