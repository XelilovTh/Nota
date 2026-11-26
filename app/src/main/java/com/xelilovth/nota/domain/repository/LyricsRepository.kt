package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackLyrics

interface LyricsRepository {
    suspend fun getLyrics(track: Track): TrackLyrics?
    suspend fun importLocalLyrics(track: Track, uriString: String): TrackLyrics? = null
    suspend fun generateLyricsWithGemini(track: Track, apiKey: String): TrackLyrics? = null
    suspend fun deleteLyrics(trackId: Long) = Unit
    suspend fun getSyncOffsetMs(trackId: Long): Long = 0L
    suspend fun setSyncOffsetMs(trackId: Long, offsetMs: Long) = Unit
}
