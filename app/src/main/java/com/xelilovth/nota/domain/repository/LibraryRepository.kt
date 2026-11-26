package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {
    val tracks: StateFlow<List<Track>>
    val allDiscoveredFolders: StateFlow<List<String>> get() = MutableStateFlow(emptyList())
    suspend fun scan(): List<Track>
    suspend fun deleteTrack(trackId: Long): DeleteTrackResult = DeleteTrackResult.Failed
    fun onTrackDeletedFromDevice(trackId: Long) = Unit
}
