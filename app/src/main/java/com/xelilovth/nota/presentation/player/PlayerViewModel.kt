package com.xelilovth.nota.presentation.player

import androidx.lifecycle.ViewModel
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackLyrics
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlaybackRepository,
    private val libraryRepository: LibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
    private val playTrack: PlayTrackUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val skipNextUseCase: SkipNextUseCase,
    private val skipPreviousUseCase: SkipPreviousUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val toggleShuffleUseCase: ToggleShuffleUseCase,
    private val cycleRepeatModeUseCase: CycleRepeatModeUseCase,
    private val onlineMusicRepository: OnlineMusicRepository?,
) : ViewModel() {
    constructor(
        repository: PlaybackRepository,
        libraryRepository: LibraryRepository,
        lyricsRepository: LyricsRepository,
        settingsRepository: SettingsRepository,
        playTrack: PlayTrackUseCase,
        togglePlayPauseUseCase: TogglePlayPauseUseCase,
        skipNextUseCase: SkipNextUseCase,
        skipPreviousUseCase: SkipPreviousUseCase,
        seekToUseCase: SeekToUseCase,
        toggleShuffleUseCase: ToggleShuffleUseCase,
        cycleRepeatModeUseCase: CycleRepeatModeUseCase,
    ) : this(
        repository,
        libraryRepository,
        lyricsRepository,
        settingsRepository,
        playTrack,
        togglePlayPauseUseCase,
        skipNextUseCase,
        skipPreviousUseCase,
        seekToUseCase,
        toggleShuffleUseCase,
        cycleRepeatModeUseCase,
        null,
    )

    val playbackState: StateFlow<PlaybackState> = repository.playbackState
    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
    val translationLanguage: StateFlow<String> = settingsRepository.lyricsTranslationLanguage
    val favoriteTrackIds: StateFlow<Set<Long>> = settingsRepository.favoriteTrackIds
    val downloadProgress: StateFlow<Map<Long, Int>> =
        onlineMusicRepository?.downloadProgress ?: MutableStateFlow(emptyMap())
    val downloadedOnlineIds: StateFlow<Set<String>> =
        onlineMusicRepository?.downloadedOnlineIds ?: MutableStateFlow(emptySet())
    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    private var lyricsJob: Job? = null
    private var lastCountedTrackId: Long? = null

    init {
        viewModelScope.launch {
            playbackState
                .map { it.track?.id to it.isPlaying }
                .distinctUntilChanged()
                .collect { (trackId, isPlaying) ->
                    if (isPlaying && trackId != null && trackId != lastCountedTrackId) {
                        lastCountedTrackId = trackId
                        settingsRepository.incrementPlayCount(trackId)
                    }
                }
        }
    }

    fun toggleFavorite(trackId: Long) = settingsRepository.toggleFavorite(trackId)

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

    fun toggleLyricsTranslation(track: Track, apiKeyOverride: String? = null) {
        val current = _lyricsState.value
        val currentLyrics = current.lyrics ?: return
        if (current.isTranslating) return
        if (current.showTranslation) {
            _lyricsState.value = current.copy(showTranslation = false)
            return
        }
        val targetLanguage = translationLanguage.value
        val cachedForLang = cachedLyricsForLanguage(currentLyrics, targetLanguage)
        if (cachedForLang != null) {
            _lyricsState.value = current.copy(lyrics = cachedForLang, showTranslation = true)
            return
        }
        val key = (apiKeyOverride ?: geminiApiKey.value).trim()
        translateLyricsInto(track, currentLyrics, key, targetLanguage)
    }

    fun setTranslationLanguage(language: String, track: Track?, apiKeyOverride: String? = null) {
        settingsRepository.setLyricsTranslationLanguage(language)
        val current = _lyricsState.value
        val currentLyrics = current.lyrics ?: return
        if (track == null || current.isTranslating) return
        val cachedForLang = cachedLyricsForLanguage(currentLyrics, language)
        if (cachedForLang != null) {
            _lyricsState.value = current.copy(lyrics = cachedForLang, showTranslation = true)
            return
        }
        val key = (apiKeyOverride ?: geminiApiKey.value).trim()
        translateLyricsInto(track, currentLyrics, key, language)
    }

    private fun cachedLyricsForLanguage(lyrics: TrackLyrics, language: String): TrackLyrics? {
        val hasDirectMap = lyrics.lines.any { !it.translations[language].isNullOrBlank() }
        val hasLegacyMatch = lyrics.translatedLanguage == language && lyrics.lines.any { !it.translatedText.isNullOrBlank() }
        if (!hasDirectMap && !hasLegacyMatch) return null
        val resolvedLines = lyrics.lines.map { line ->
            val textForLang = line.translations[language]
                ?: line.translatedText.takeIf { lyrics.translatedLanguage == language }
            line.copy(translatedText = textForLang)
        }
        return lyrics.copy(lines = resolvedLines, translatedLanguage = language)
    }

    private fun translateLyricsInto(
        track: Track,
        currentLyrics: TrackLyrics,
        apiKey: String,
        targetLanguage: String,
    ) {
        _lyricsState.value = _lyricsState.value.copy(isTranslating = true, errorMessage = null)
        viewModelScope.launch {
            val translated = runCatching {
                lyricsRepository.translateLyrics(
                    track = track,
                    lyrics = currentLyrics,
                    apiKey = apiKey,
                    targetLanguage = targetLanguage,
                )
            }.getOrNull()
            if (_lyricsState.value.trackId == track.id) {
                val latest = _lyricsState.value
                _lyricsState.value = latest.copy(
                    lyrics = translated ?: latest.lyrics,
                    showTranslation = translated != null,
                    isTranslating = false,
                    errorMessage = if (translated == null) "Could not translate lyrics" else null,
                )
            }
        }
    }

    fun deleteTrack(trackId: Long, onResult: (DeleteTrackResult) -> Unit) {
        viewModelScope.launch {
            onResult(libraryRepository.deleteTrack(trackId))
        }
    }

    fun confirmTrackDeleted(trackId: Long) {
        libraryRepository.onTrackDeletedFromDevice(trackId)
    }

    fun downloadTrackForOffline(track: Track, onResult: (Result<Track>) -> Unit) {
        val repo = onlineMusicRepository ?: return
        viewModelScope.launch {
            val location = settingsRepository.settings.value.offlineStorageLocation
            onResult(repo.downloadForOffline(track, location))
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
