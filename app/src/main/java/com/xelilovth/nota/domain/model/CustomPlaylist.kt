package com.xelilovth.nota.domain.model


enum class PlaylistCollectionType { PLAYLIST, ALBUM, FOLDER }

data class CustomPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val type: PlaylistCollectionType = PlaylistCollectionType.PLAYLIST,
)
