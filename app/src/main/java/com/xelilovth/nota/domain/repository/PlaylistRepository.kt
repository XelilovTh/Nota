package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
}
