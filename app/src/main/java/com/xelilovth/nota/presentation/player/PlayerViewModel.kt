package com.xelilovth.nota.presentation.player

import androidx.lifecycle.ViewModel
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.LyricsRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import com.xelilovth.nota.domain.usecase.CycleRepeatModeUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.SeekToUseCase
import com.xelilovth.nota.domain.usecase.SkipNextUseCase
import com.xelilovth.nota.domain.usecase.SkipPreviousUseCase
import com.xelilovth.nota.domain.usecase.TogglePlayPauseUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlaybackRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
    private val playTrack: PlayTrackUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val skipNextUseCase: SkipNextUseCase,
    private val skipPreviousUseCase: SkipPreviousUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val toggleShuffleUseCase: ToggleShuffleUseCase,
    private val cycleRepeatModeUseCase: CycleRepeatModeUseCase,
) : ViewModel() {
    val playbackState: StateFlow<PlaybackState> = repository.playbackState
    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    private var lyricsJob: Job? = null

    fun loadLyrics(track: Track) {
        val current = _lyricsState.value
        if (current.trackId == track.id && (current.isLoading || current.lyrics != null || current.notFound)) return
        lyricsJob?.cancel()
        _lyricsState.value = LyricsUiState(trackId = track.id, isLoading = true)
        lyricsJob = viewModelScope.launch {
            val lyrics = runCatching { lyricsRepository.getLyrics(track) }.getOrNull()
            val offsetMs = runCatching { lyricsRepository.getSyncOffsetMs(track.id) }.getOrDefault(0L)
            if (_lyricsState.value.trackId == track.id) {
                _lyricsState.value = LyricsUiState(
                    trackId = track.id,
                    lyrics = lyrics,
                    notFound = lyrics == null,
                    syncOffsetMs = offsetMs,
                )
            }
        }
    }

    fun setLyricsSyncActive(active: Boolean) = repository.setLyricsSyncActive(active)

    fun importLocalLyrics(track: Track, uriString: String) {
        lyricsJob?.cancel()
        _lyricsState.value = _lyricsState.value.copy(trackId = track.id, isLoading = true, notFound = false)
        lyricsJob = viewModelScope.launch {
            val lyrics = runCatching { lyricsRepository.importLocalLyrics(track, uriString) }.getOrNull()
            val offsetMs = runCatching { lyricsRepository.getSyncOffsetMs(track.id) }.getOrDefault(0L)
            if (_lyricsState.value.trackId == track.id) {
                _lyricsState.value = LyricsUiState(
                    trackId = track.id,
                    lyrics = lyrics,
                    notFound = lyrics == null,
                    syncOffsetMs = offsetMs,
                )
            }
        }
    }

    fun saveGeminiApiKey(key: String) = settingsRepository.setGeminiApiKey(key.trim())

    fun generateLyricsWithGemini(track: Track, apiKeyOverride: String? = null) {
        val apiKey = (apiKeyOverride ?: geminiApiKey.value).trim()
        if (apiKey.isBlank()) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = "Gemini API key is required",
            )
            return
        }
        lyricsJob?.cancel()
        _lyricsState.value = _lyricsState.value.copy(
            trackId = track.id,
            isLoading = true,
            notFound = false,
            errorMessage = null,
        )
        lyricsJob = viewModelScope.launch {
            val lyrics = runCatching { lyricsRepository.generateLyricsWithGemini(track, apiKey) }.getOrNull()
            val offsetMs = runCatching { lyricsRepository.getSyncOffsetMs(track.id) }.getOrDefault(0L)
            if (_lyricsState.value.trackId == track.id) {
                _lyricsState.value = if (lyrics != null) {
                    LyricsUiState(
                        trackId = track.id,
                        lyrics = lyrics,
                        notFound = false,
                        syncOffsetMs = offsetMs,
                    )
                } else {
                    LyricsUiState(
                        trackId = track.id,
                        lyrics = _lyricsState.value.lyrics,
                        notFound = _lyricsState.value.lyrics == null,
                        syncOffsetMs = offsetMs,
                        errorMessage = "Could not transcribe lyrics with Gemini",
                    )
                }
            }
        }
    }

    fun deleteLyrics(trackId: Long) {
        lyricsJob?.cancel()
        _lyricsState.value = LyricsUiState(
            trackId = trackId,
            notFound = true,
            syncOffsetMs = _lyricsState.value.syncOffsetMs,
        )
        viewModelScope.launch {
            lyricsRepository.deleteLyrics(trackId)
        }
    }

    fun adjustLyricsOffset(trackId: Long, deltaMs: Long) {
        val current = _lyricsState.value
        if (current.trackId != trackId) return
        val updatedOffset = (current.syncOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        _lyricsState.value = current.copy(syncOffsetMs = updatedOffset)
        viewModelScope.launch {
            lyricsRepository.setSyncOffsetMs(trackId, updatedOffset)
        }
    }
    fun onRowClick(track: Track, libraryTracks: List<Track>) {
        val queue = if (libraryTracks.any { it.id == track.id }) libraryTracks else listOf(track)
        playTrack(track, queue)
    }

    fun onPlayIconClick(track: Track, libraryTracks: List<Track>) = onRowClick(track, libraryTracks)
    fun togglePlayPause() = togglePlayPauseUseCase()
    fun next() = skipNextUseCase()
    fun previous() = skipPreviousUseCase()
    fun seek(positionMs: Long) = seekToUseCase(positionMs)
    fun toggleShuffle() = toggleShuffleUseCase()
    fun cycleRepeatMode() = cycleRepeatModeUseCase()
    fun dismiss() = repository.stopPlayback()
}
