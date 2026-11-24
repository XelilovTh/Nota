package com.xelilovth.nota.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.usecase.AddTrackToPlaylistUseCase
import com.xelilovth.nota.domain.usecase.CreatePlaylistUseCase
import com.xelilovth.nota.domain.usecase.GetPlaylistsUseCase
import com.xelilovth.nota.domain.usecase.ObserveFavoritesUseCase
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ScanLibraryUseCase
import com.xelilovth.nota.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val scanLibrary: ScanLibraryUseCase,
    private val playTrack: PlayTrackUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase? = null,
    private val observeFavoritesUseCase: ObserveFavoritesUseCase? = null,
    private val getPlaylistsUseCase: GetPlaylistsUseCase? = null,
    private val createPlaylistUseCase: CreatePlaylistUseCase? = null,
    private val addTrackToPlaylistUseCase: AddTrackToPlaylistUseCase? = null,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val favoriteTrackIds: StateFlow<Set<Long>> = observeFavoritesUseCase?.invoke()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        ?: MutableStateFlow(emptySet())

    val playlists: StateFlow<List<Playlist>> = getPlaylistsUseCase?.invoke()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        ?: MutableStateFlow(emptyList())

    fun scan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanLibrary()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun onTrackSelected(track: Track) {
        val libraryTracks = tracks.value
        val selectedIndex = libraryTracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) libraryTracks.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            toggleFavoriteUseCase?.invoke(trackId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            addTrackToPlaylistUseCase?.invoke(playlistId, trackId)
        }
    }

    fun createPlaylistAndAddTrack(name: String, trackId: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = createPlaylistUseCase?.invoke(name.trim())
            if (playlistId != null && playlistId > 0) {
                addTrackToPlaylistUseCase?.invoke(playlistId, trackId)
            }
        }
    }
}
