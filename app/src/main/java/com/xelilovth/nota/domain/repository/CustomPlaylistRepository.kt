package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.PlaylistCollectionType
import kotlinx.coroutines.flow.StateFlow

interface CustomPlaylistRepository {
    val playlists: StateFlow<List<CustomPlaylist>>
    val hiddenAutoGroupIds: StateFlow<Set<String>>
    fun createPlaylist(name: String): CustomPlaylist
    fun createCollection(name: String, type: PlaylistCollectionType, trackIds: List<Long> = emptyList()): CustomPlaylist
    fun hideAutoGroup(groupId: String)
    fun renamePlaylist(playlistId: String, newName: String)
    fun deletePlaylist(playlistId: String)
    fun addTrack(playlistId: String, trackId: Long)
    fun removeTrack(playlistId: String, trackId: Long)
}
