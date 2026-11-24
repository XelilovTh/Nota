package com.xelilovth.nota.domain.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackTest {

    @Test
    fun trackDerivesArtworkUriWhenAlbumIdIsPresent() {
        val track = Track(
            id = 1L,
            uri = Uri.parse("content://media/external/audio/media/1"),
            title = "Track Title",
            artist = "Artist Name",
            album = "Album Name",
            durationMs = 120_000L,
            folder = "Music",
            albumId = 88L,
        )

        assertEquals(88L, track.albumId)
        assertEquals(Uri.parse("content://media/external/audio/albumart/88"), track.artworkUri)
    }

    @Test
    fun trackWithoutAlbumIdHasNullArtworkUriByDefault() {
        val track = Track(
            id = 2L,
            uri = Uri.parse("content://media/external/audio/media/2"),
            title = "Track Title",
            artist = "Artist Name",
            album = "Album Name",
            durationMs = 120_000L,
            folder = "Music",
        )

        assertNull(track.albumId)
        assertNull(track.artworkUri)
    }

    @Test
    fun trackAllowsExplicitArtworkUriOverride() {
        val customUri = Uri.parse("content://custom/art")
        val track = Track(
            id = 3L,
            uri = Uri.parse("content://media/external/audio/media/3"),
            title = "Track Title",
            artist = "Artist Name",
            album = "Album Name",
            durationMs = 120_000L,
            folder = "Music",
            albumId = 88L,
            artworkUri = customUri,
        )

        assertEquals(customUri, track.artworkUri)
    }
}
