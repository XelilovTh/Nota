package com.xelilovth.nota.data.local

import android.net.Uri
import com.xelilovth.nota.domain.model.Track

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    uri = uri.toString(),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    folder = folder,
    albumId = albumId,
    artworkUri = artworkUri?.toString(),
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    uri = Uri.parse(uri),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    folder = folder,
    albumId = albumId,
    artworkUri = artworkUri?.let { Uri.parse(it) },
)
