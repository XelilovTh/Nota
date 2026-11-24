package com.xelilovth.nota.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.GetPlaylistsUseCase
import com.xelilovth.nota.domain.usecase.ObserveFavoritesUseCase
import com.xelilovth.nota.domain.usecase.ObserveHistoryUseCase
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playTrack: PlayTrackUseCase,
    private val toggleShuffle: ToggleShuffleUseCase,
    private val observeHistoryUseCase: ObserveHistoryUseCase? = null,
    private val observeFavoritesUseCase: ObserveFavoritesUseCase? = null,
    private val getPlaylistsUseCase: GetPlaylistsUseCase? = null,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()
    val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState
    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    private var lastTrackId: Long? = null

    val recentlyPlayed: StateFlow<List<Track>> = if (observeHistoryUseCase != null) {
        combine(tracks, observeHistoryUseCase()) { allTracks, history ->
            val trackMap = allTracks.associateBy { it.id }
            history.sortedByDescending { it.playedAt }
                .mapNotNull { trackMap[it.trackId] }
                .distinctBy { it.id }
                .take(10)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        _recentlyPlayed.asStateFlow()
    }

    val favorites: StateFlow<List<Track>> = if (observeFavoritesUseCase != null) {
        combine(tracks, observeFavoritesUseCase()) { allTracks, favIds ->
            allTracks.filter { it.id in favIds }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    } else {
        MutableStateFlow(emptyList())
    }

    val playlists: StateFlow<List<Playlist>> = getPlaylistsUseCase?.invoke()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        ?: MutableStateFlow(emptyList())

    init {
        if (observeHistoryUseCase == null) {
            viewModelScope.launch {
                playbackState.collect { state ->
                    val track = state.track ?: return@collect
                    if (track.id != lastTrackId) {
                        lastTrackId = track.id
                        _recentlyPlayed.value = (listOf(track) + _recentlyPlayed.value.filterNot { it.id == track.id }).take(10)
                    }
                }
            }
        }
    }

    fun playFirstTrack() { tracks.value.firstOrNull()?.let(::play) }

    fun playShuffledLibrary() {
        val library = tracks.value
        if (library.isEmpty()) return
        if (!playbackState.value.shuffle) toggleShuffle()
        playTrack(library.random(), library)
    }

    fun playCollection(group: AutoPlaylist, shuffle: Boolean = false) {
        if (group.tracks.isEmpty()) return
        val queue = if (shuffle) group.tracks.shuffled() else group.tracks
        playTrack(queue.first(), queue)
    }

    fun playFromCollection(track: Track, group: AutoPlaylist) {
        val selectedIndex = group.tracks.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) group.tracks.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }

    fun playFavorites(shuffle: Boolean = false) {
        val favTracks = favorites.value
        if (favTracks.isEmpty()) return
        val queue = if (shuffle) favTracks.shuffled() else favTracks
        playTrack(queue.first(), queue)
    }

    fun playTrackList(track: Track, trackList: List<Track>) {
        val selectedIndex = trackList.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) trackList.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }

    fun play(track: Track) {
        val library = tracks.value
        val selectedIndex = library.indexOfFirst { it.id == track.id }
        val queue = if (selectedIndex >= 0) library.drop(selectedIndex) else listOf(track)
        playTrack(track, queue)
    }
}
