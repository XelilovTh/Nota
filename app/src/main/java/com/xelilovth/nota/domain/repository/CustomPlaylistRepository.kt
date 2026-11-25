package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.CustomPlaylist
import kotlinx.coroutines.flow.StateFlow

interface CustomPlaylistRepository {
    val playlists: StateFlow<List<CustomPlaylist>>
    fun createPlaylist(name: String): CustomPlaylist
    fun renamePlaylist(playlistId: String, newName: String)
    fun deletePlaylist(playlistId: String)
    fun addTrack(playlistId: String, trackId: Long)
    fun removeTrack(playlistId: String, trackId: Long)
}
