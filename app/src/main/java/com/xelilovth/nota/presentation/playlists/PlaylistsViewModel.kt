package com.xelilovth.nota.presentation.playlists

import androidx.lifecycle.ViewModel
import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.PlaylistCollectionType
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
    val hiddenAutoGroupIds: StateFlow<Set<String>> = customPlaylistRepository.hiddenAutoGroupIds
    val tracks: StateFlow<List<Track>> = observeTracks()
    val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState

    fun createPlaylist(name: String): CustomPlaylist = customPlaylistRepository.createPlaylist(name)

    fun createCollection(name: String, type: PlaylistCollectionType): CustomPlaylist =
        customPlaylistRepository.createCollection(name, type)

    fun editAutoGroup(group: AutoPlaylist): CustomPlaylist {
        val collection = customPlaylistRepository.createCollection(
            name = group.title,
            type = if (group.type == com.xelilovth.nota.domain.model.PlaylistGroupType.ALBUM) PlaylistCollectionType.ALBUM else PlaylistCollectionType.FOLDER,
            trackIds = group.tracks.map(Track::id),
        )
        customPlaylistRepository.hideAutoGroup(group.id)
        return collection
    }

    fun deleteAutoGroup(groupId: String) = customPlaylistRepository.hideAutoGroup(groupId)

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
        val queue = if (playlistTracks.any { it.id == selected.id }) playlistTracks else listOf(selected)
        playTrack(selected, queue)
    }

    fun playTracksShuffled(playlistTracks: List<Track>) {
        if (playlistTracks.isEmpty()) return
        if (!playbackState.value.shuffle) toggleShuffle()
        playTrack(playlistTracks.random(), playlistTracks)
    }
}
