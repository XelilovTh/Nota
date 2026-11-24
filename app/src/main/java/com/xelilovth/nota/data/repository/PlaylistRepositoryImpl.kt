package com.xelilovth.nota.data.repository

import com.xelilovth.nota.data.local.PlaylistDao
import com.xelilovth.nota.data.local.PlaylistEntity
import com.xelilovth.nota.data.local.PlaylistTrackCrossRef
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val libraryRepository: LibraryRepository,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> {
        return combine(
            playlistDao.getAllPlaylists(),
            playlistDao.getAllPlaylistTracks(),
        ) { playlists, tracks ->
            val countMap = tracks.groupingBy { it.playlistId }.eachCount()
            playlists.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    trackCount = countMap[entity.id] ?: 0,
                    createdAt = entity.createdAt,
                )
            }
        }
    }

    override fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> {
        return combine(
            playlistDao.getTrackIdsForPlaylist(playlistId),
            libraryRepository.tracks,
        ) { trackIds, allTracks ->
            val trackMap = allTracks.associateBy { it.id }
            trackIds.mapNotNull { trackMap[it] }
        }
    }

    override suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.insertTrackToPlaylist(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
            )
        )
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }
}
