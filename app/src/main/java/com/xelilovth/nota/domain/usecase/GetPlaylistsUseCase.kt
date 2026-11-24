package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> = playlistRepository.observePlaylists()
}
