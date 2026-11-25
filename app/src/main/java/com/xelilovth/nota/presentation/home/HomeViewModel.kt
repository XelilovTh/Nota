package com.xelilovth.nota.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import com.xelilovth.nota.domain.usecase.ObserveTracksUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playTrack: PlayTrackUseCase,
    private val toggleShuffle: ToggleShuffleUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = observeTracks()
    val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState
    val recentlyPlayed: StateFlow<List<Track>> = combine(tracks, settingsRepository.recentTrackIds) { libraryTracks, recentIds ->
        val byId = libraryTracks.associateBy(Track::id)
        recentIds.mapNotNull(byId::get)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var lastTrackId: Long? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.track ?: return@collect
                if (track.id != lastTrackId) {
                    lastTrackId = track.id
                    settingsRepository.addRecentTrack(track.id)
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
    fun play(track: Track) {
        val library = tracks.value
        val queue = if (library.any { it.id == track.id }) library else listOf(track)
        playTrack(track, queue)
    }

    fun playPlaylist(playlist: AutoPlaylist) {
        val firstTrack = playlist.tracks.firstOrNull() ?: return
        playTrack(firstTrack, playlist.tracks)
    }

    fun playPlaylistShuffled(playlist: AutoPlaylist) {
        if (playlist.tracks.isEmpty()) return
        if (!playbackState.value.shuffle) toggleShuffle()
        playTrack(playlist.tracks.random(), playlist.tracks)
    }

    fun playPlaylistTrack(playlist: AutoPlaylist, track: Track) {
        val queue = if (playlist.tracks.any { it.id == track.id }) playlist.tracks else listOf(track)
        playTrack(track, queue)
    }
}
