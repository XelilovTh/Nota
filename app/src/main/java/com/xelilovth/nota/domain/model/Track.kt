package com.xelilovth.nota.domain.model

import android.content.ContentUris
import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folder: String,
    val albumId: Long? = null,
    val artworkUri: Uri? = albumId?.let {
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it)
    },
)
