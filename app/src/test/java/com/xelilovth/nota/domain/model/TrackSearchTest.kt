package com.xelilovth.nota.domain.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackSearchTest {
    private val tracks = listOf(
        Track(1L, Uri.parse("content://audio/1"), "Midnight Road", "Nova", "After Hours", 1_000L, "Electronic"),
        Track(2L, Uri.parse("content://audio/2"), "Blue Sky", "Night Drive", "Daylight", 2_000L, "Pop"),
        Track(3L, Uri.parse("content://audio/3"), "Morning", "Luna", "Night Drive", 3_000L, "Jazz"),
    )

    @Test
    fun filtersByTitleCaseInsensitively() {
        assertEquals(listOf(1L), filterTracks(tracks, "MIDNIGHT").map(Track::id))
    }

    @Test
    fun filtersByArtist() {
        assertEquals(listOf(1L), filterTracks(tracks, "Nova").map(Track::id))
    }

    @Test
    fun filtersByAlbum() {
        assertEquals(listOf(1L), filterTracks(tracks, "After Hours").map(Track::id))
    }

    @Test
    fun blankQueryReturnsAllTracks() {
        assertEquals(tracks, filterTracks(tracks, "  "))
    }

    @Test
    fun emptyLibraryReturnsNoResults() {
        assertEquals(emptyList<Track>(), filterTracks(emptyList(), "anything"))
    }

    @Test
    fun folderNameIsNotPartOfSearch() {
        assertEquals(emptyList<Track>(), filterTracks(tracks, "Electronic"))
    }
}
