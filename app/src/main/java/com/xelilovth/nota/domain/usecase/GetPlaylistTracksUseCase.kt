package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPlaylistTracksUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(playlistId: Long): Flow<List<Track>> =
        playlistRepository.getPlaylistTracks(playlistId)
}
