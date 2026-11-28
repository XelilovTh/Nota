package com.xelilovth.nota.presentation.player

import androidx.lifecycle.ViewModel
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.OfflineStorageLocation
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

enum class AiLyricsProvider { GEMINI, GROQ }

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
    val groqApiKey: StateFlow<String> = settingsRepository.groqApiKey
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
        onlineMusicRepository?.let { onlineRepo ->
            viewModelScope.launch {
                onlineRepo.offlineTracks.collect { offlineTracks ->
                    val currentLyrics = _lyricsState.value
                    if (currentLyrics.errorMessage != DOWNLOAD_REQUIRED_FOR_AI_MESSAGE) return@collect
                    val activeTrack = playbackState.value.track
                    val hasLocalAudio = offlineTracks.any { offline ->
                        !offline.uri.startsWith(ONLINE_URI_PREFIX) &&
                            (
                                offline.id == currentLyrics.trackId ||
                                    (activeTrack?.onlineId != null && offline.onlineId == activeTrack.onlineId)
                                )
                    }
                    if (hasLocalAudio) {
                        _lyricsState.value = currentLyrics.copy(errorMessage = null)
                    }
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

    fun saveGroqApiKey(key: String) = settingsRepository.setGroqApiKey(key.trim())

    private fun resolvePlayableAudioTrack(track: Track): Track {
        if (!track.uri.startsWith(ONLINE_URI_PREFIX)) return track
        return onlineMusicRepository?.offlineTracks?.value?.firstOrNull { offline ->
            !offline.uri.startsWith(ONLINE_URI_PREFIX) &&
                (offline.id == track.id || (track.onlineId != null && offline.onlineId == track.onlineId))
        } ?: track
    }

    fun transcribeWithProvider(
        track: Track,
        preferredProvider: AiLyricsProvider,
        apiKeyOverride: String? = null,
    ) {
        val targetTrack = resolvePlayableAudioTrack(track)
        if (targetTrack.uri.startsWith(ONLINE_URI_PREFIX)) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = DOWNLOAD_REQUIRED_FOR_AI_MESSAGE,
            )
            return
        }
        val geminiKey = if (preferredProvider == AiLyricsProvider.GEMINI && apiKeyOverride != null) {
            apiKeyOverride.trim()
        } else {
            geminiApiKey.value.trim()
        }
        val groqKey = if (preferredProvider == AiLyricsProvider.GROQ && apiKeyOverride != null) {
            apiKeyOverride.trim()
        } else {
            groqApiKey.value.trim()
        }
        val primaryKey = if (preferredProvider == AiLyricsProvider.GEMINI) geminiKey else groqKey
        if (primaryKey.isBlank()) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = "${if (preferredProvider == AiLyricsProvider.GEMINI) "Gemini" else "Groq"} API key is required",
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
            val primaryResult = runCatching {
                when (preferredProvider) {
                    AiLyricsProvider.GEMINI -> lyricsRepository.generateLyricsWithGemini(targetTrack, geminiKey)
                    AiLyricsProvider.GROQ -> lyricsRepository.generateLyricsWithGroq(targetTrack, groqKey)
                }
            }.getOrNull()

            val fallbackResult = if (primaryResult == null) {
                when (preferredProvider) {
                    AiLyricsProvider.GEMINI -> if (groqKey.isNotBlank()) {
                        runCatching { lyricsRepository.generateLyricsWithGroq(targetTrack, groqKey) }.getOrNull()
                    } else {
                        null
                    }
                    AiLyricsProvider.GROQ -> if (geminiKey.isNotBlank()) {
                        runCatching { lyricsRepository.generateLyricsWithGemini(targetTrack, geminiKey) }.getOrNull()
                    } else {
                        null
                    }
                }
            } else {
                null
            }

            val lyrics = primaryResult ?: fallbackResult
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
                        errorMessage = "AI transcription failed (check API key, audio size, or connection)",
                    )
                }
            }
        }
    }

    fun generateLyricsWithAi(track: Track) {
        val targetTrack = resolvePlayableAudioTrack(track)
        if (targetTrack.uri.startsWith(ONLINE_URI_PREFIX)) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = DOWNLOAD_REQUIRED_FOR_AI_MESSAGE,
            )
            return
        }
        val geminiKey = geminiApiKey.value.trim()
        val groqKey = groqApiKey.value.trim()
        if (geminiKey.isBlank() && groqKey.isBlank()) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = "Gemini or Groq API key is required",
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
            val lyrics = (if (geminiKey.isNotBlank()) {
                runCatching { lyricsRepository.generateLyricsWithGemini(targetTrack, geminiKey) }.getOrNull()
            } else {
                null
            }) ?: if (groqKey.isNotBlank()) {
                runCatching { lyricsRepository.generateLyricsWithGroq(targetTrack, groqKey) }.getOrNull()
            } else {
                null
            }
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
                        errorMessage = "Could not transcribe lyrics with AI",
                    )
                }
            }
        }
    }

    fun generateLyricsWithGroq(track: Track, apiKeyOverride: String? = null) {
        val targetTrack = resolvePlayableAudioTrack(track)
        if (targetTrack.uri.startsWith(ONLINE_URI_PREFIX)) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = DOWNLOAD_REQUIRED_FOR_AI_MESSAGE,
            )
            return
        }
        val apiKey = (apiKeyOverride ?: groqApiKey.value).trim()
        if (apiKey.isBlank()) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = "Groq API key is required",
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
            val lyrics = runCatching { lyricsRepository.generateLyricsWithGroq(targetTrack, apiKey) }.getOrNull()
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
                        errorMessage = "Could not transcribe lyrics with Groq",
                    )
                }
            }
        }
    }

    fun generateLyricsWithGemini(track: Track, apiKeyOverride: String? = null) {
        val targetTrack = resolvePlayableAudioTrack(track)
        if (targetTrack.uri.startsWith(ONLINE_URI_PREFIX)) {
            _lyricsState.value = _lyricsState.value.copy(
                trackId = track.id,
                isLoading = false,
                errorMessage = DOWNLOAD_REQUIRED_FOR_AI_MESSAGE,
            )
            return
        }
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
            val lyrics = runCatching { lyricsRepository.generateLyricsWithGemini(targetTrack, apiKey) }.getOrNull()
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

    fun retryOnlineLyrics(track: Track) {
        lyricsJob?.cancel()
        _lyricsState.value = LyricsUiState(trackId = track.id, isLoading = true)
        lyricsJob = viewModelScope.launch {
            val lyrics = runCatching { lyricsRepository.fetchOnlineLyrics(track) }.getOrNull()
            val offsetMs = lyricsRepository.getSyncOffsetMs(track.id)
            if (_lyricsState.value.trackId == track.id) {
                _lyricsState.value = LyricsUiState(
                    trackId = track.id,
                    lyrics = lyrics,
                    syncOffsetMs = offsetMs,
                    isLoading = false,
                    notFound = lyrics == null,
                )
            }
        }
    }

    fun downloadTrackForOffline(
        track: Track,
        location: OfflineStorageLocation,
        onResult: (Result<Track>) -> Unit,
    ) {
        val repo = onlineMusicRepository ?: return
        settingsRepository.setOfflineStorageLocation(location)
        viewModelScope.launch {
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

    private companion object {
        const val ONLINE_URI_PREFIX = "nota://"
        const val DOWNLOAD_REQUIRED_FOR_AI_MESSAGE = "Download track offline first to transcribe with AI"
    }
}
