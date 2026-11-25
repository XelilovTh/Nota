package com.xelilovth.nota.domain.model

import androidx.compose.runtime.Immutable

enum class PlaylistGroupType { ALBUM, FOLDER }

@Immutable
data class AutoPlaylist(
    val id: String,
    val title: String,
    val type: PlaylistGroupType,
    val tracks: List<Track>,
)

fun groupTracksIntoPlaylists(tracks: List<Track>): List<AutoPlaylist> {
    return tracks.groupBy { track ->
        val album = track.album.trim()
        if (album.isNotEmpty() && !album.equals("Unknown album", ignoreCase = true)) {
            PlaylistGroupType.ALBUM to album
        } else {
            PlaylistGroupType.FOLDER to track.folder.ifBlank { "Unknown folder" }
        }
    }.map { (key, groupedTracks) ->
        AutoPlaylist(
            id = "${key.first.name.lowercase()}:${key.second.lowercase()}",
            title = key.second,
            type = key.first,
            tracks = groupedTracks,
        )
    }.sortedWith(compareBy({ it.title.lowercase() }, { it.type.name }))
}
