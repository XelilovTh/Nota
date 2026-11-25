package com.xelilovth.nota.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folder: String,
)
