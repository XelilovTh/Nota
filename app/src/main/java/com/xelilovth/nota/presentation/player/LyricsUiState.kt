package com.xelilovth.nota.presentation.player

import com.xelilovth.nota.domain.model.TrackLyrics

data class LyricsUiState(
    val trackId: Long? = null,
    val isLoading: Boolean = false,
    val lyrics: TrackLyrics? = null,
    val notFound: Boolean = false,
    val syncOffsetMs: Long = 0L,
    val showTranslation: Boolean = false,
    val isTranslating: Boolean = false,
    val errorMessage: String? = null,
)
