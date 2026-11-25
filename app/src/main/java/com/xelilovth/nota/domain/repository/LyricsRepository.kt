package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackLyrics

interface LyricsRepository {
    suspend fun getLyrics(track: Track): TrackLyrics?
}
