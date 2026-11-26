package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackLyrics

interface LyricsRepository {
    suspend fun getLyrics(track: Track): TrackLyrics?
    suspend fun importLocalLyrics(track: Track, uriString: String): TrackLyrics? = null
    suspend fun getSyncOffsetMs(trackId: Long): Long = 0L
    suspend fun setSyncOffsetMs(trackId: Long, offsetMs: Long) = Unit
}
