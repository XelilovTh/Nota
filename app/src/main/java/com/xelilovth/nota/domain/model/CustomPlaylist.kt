package com.xelilovth.nota.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CustomPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
)
