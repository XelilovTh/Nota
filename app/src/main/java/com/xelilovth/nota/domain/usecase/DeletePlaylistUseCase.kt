package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.PlaylistRepository
import javax.inject.Inject

class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long) = playlistRepository.deletePlaylist(playlistId)
}
