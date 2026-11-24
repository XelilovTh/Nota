package com.xelilovth.nota.domain.usecase

import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.model.Track
import javax.inject.Inject

class PlayTrackUseCase @Inject constructor(private val repository: PlaybackRepository) {
    operator fun invoke(track: Track, queue: List<Track>) = repository.playTrack(track, queue)
}
