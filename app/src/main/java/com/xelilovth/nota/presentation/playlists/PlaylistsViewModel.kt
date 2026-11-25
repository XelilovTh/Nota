package com.xelilovth.nota.presentation.playlists

import androidx.lifecycle.ViewModel
import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.CustomPlaylistRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val customPlaylistRepository: CustomPlaylistRepository,
    observeTracks: ObserveTracksUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playTrack: PlayTrackUseCase,
    private val toggleShuffle: ToggleShuffleUseCase,
) : ViewModel() {
    val customPlaylists: StateFlow<List<CustomPlaylist>> = customPlaylistRepository.playlists
    val tracks: StateFlow<List<Track>> = observeTracks()
    val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState

    fun createPlaylist(name: String): CustomPlaylist = customPlaylistRepository.createPlaylist(name)

    fun renamePlaylist(playlistId: String, newName: String) =
        customPlaylistRepository.renamePlaylist(playlistId, newName)

    fun deletePlaylist(playlistId: String) = customPlaylistRepository.deletePlaylist(playlistId)

    fun addTrackToPlaylist(playlistId: String, trackId: Long) =
        customPlaylistRepository.addTrack(playlistId, trackId)

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) =
        customPlaylistRepository.removeTrack(playlistId, trackId)

    fun playTracks(playlistTracks: List<Track>, startTrack: Track? = null) {
        if (playlistTracks.isEmpty()) return
        val selected = startTrack ?: playlistTracks.first()
        val index = playlistTracks.indexOfFirst { it.id == selected.id }
        val queue = if (index >= 0) playlistTracks.drop(index) else playlistTracks
        playTrack(selected, queue)
    }

    fun playTracksShuffled(playlistTracks: List<Track>) {
        if (playlistTracks.isEmpty()) return
        if (!playbackState.value.shuffle) toggleShuffle()
        playTrack(playlistTracks.random(), playlistTracks)
    }
}
