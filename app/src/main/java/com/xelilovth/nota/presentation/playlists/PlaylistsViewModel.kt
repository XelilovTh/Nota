package com.xelilovth.nota.presentation.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.usecase.AddTrackToPlaylistUseCase
import com.xelilovth.nota.domain.usecase.CreatePlaylistUseCase
import com.xelilovth.nota.domain.usecase.DeletePlaylistUseCase
import com.xelilovth.nota.domain.usecase.GetPlaylistTracksUseCase
import com.xelilovth.nota.domain.usecase.GetPlaylistsUseCase
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.RemoveTrackFromPlaylistUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    getPlaylistsUseCase: GetPlaylistsUseCase,
    observeTracksUseCase: ObserveTracksUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val addTrackToPlaylistUseCase: AddTrackToPlaylistUseCase,
    private val removeTrackFromPlaylistUseCase: RemoveTrackFromPlaylistUseCase,
    private val getPlaylistTracksUseCase: GetPlaylistTracksUseCase,
    private val playTrackUseCase: PlayTrackUseCase,
    private val toggleShuffleUseCase: ToggleShuffleUseCase? = null,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = getPlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks: StateFlow<List<Track>> = observeTracksUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            createPlaylistUseCase(name.trim())
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            addTrackToPlaylistUseCase(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            removeTrackFromPlaylistUseCase(playlistId, trackId)
        }
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> {
        return getPlaylistTracksUseCase(playlistId)
    }

    fun playPlaylist(tracks: List<Track>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val queue = if (shuffle) tracks.shuffled() else tracks
        playTrackUseCase(queue.first(), queue)
    }

    fun playFromPlaylist(track: Track, tracks: List<Track>) {
        val selectedIndex = tracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) tracks.drop(selectedIndex) else listOf(track)
        playTrackUseCase(track, queue)
    }

    fun playCollection(group: AutoPlaylist, shuffle: Boolean = false) {
        if (group.tracks.isEmpty()) return
        val queue = if (shuffle) group.tracks.shuffled() else group.tracks
        playTrackUseCase(queue.first(), queue)
    }

    fun playFromCollection(track: Track, group: AutoPlaylist) {
        val selectedIndex = group.tracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) group.tracks.drop(selectedIndex) else listOf(track)
        playTrackUseCase(track, queue)
    }
}
