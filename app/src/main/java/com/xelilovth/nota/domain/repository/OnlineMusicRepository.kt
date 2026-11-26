package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface OnlineMusicRepository {
    val offlineTracks: StateFlow<List<Track>> get() = MutableStateFlow(emptyList())
    val downloadProgress: StateFlow<Map<Long, Int>> get() = MutableStateFlow(emptyMap())
    val downloadedOnlineIds: StateFlow<Set<String>> get() = MutableStateFlow(emptySet())

    suspend fun searchOnline(query: String): Result<List<Track>>
    suspend fun resolveStreamUrl(onlineId: String): String?
    suspend fun downloadForOffline(track: Track, location: OfflineStorageLocation): Result<Track>
    suspend fun removeOfflineTrack(track: Track)
    fun getCachedOnlineTracks(): List<Track> = emptyList()
    fun cacheOnlineTrack(track: Track) = Unit
}
