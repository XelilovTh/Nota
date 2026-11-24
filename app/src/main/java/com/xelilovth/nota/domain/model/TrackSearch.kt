package com.xelilovth.nota.domain.model

fun filterTracks(tracks: List<Track>, query: String): List<Track> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return tracks

    return tracks.filter { track ->
        track.title.contains(normalizedQuery, ignoreCase = true) ||
            track.artist.contains(normalizedQuery, ignoreCase = true) ||
            track.album.contains(normalizedQuery, ignoreCase = true)
    }
}
