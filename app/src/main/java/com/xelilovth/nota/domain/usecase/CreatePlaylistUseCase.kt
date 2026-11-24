package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.PlaylistRepository
import javax.inject.Inject

class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(name: String): Long = playlistRepository.createPlaylist(name)
}
