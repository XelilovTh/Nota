package com.xelilovth.nota.domain.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)

class AutoPlaylistGroupingTest {
    private fun track(id: Long, album: String, folder: String) =
        Track(id, Uri.parse("content://audio/$id"), "Track $id", "Artist", album, 1000L, folder)

    @Test
    fun groupsTracksByAlbumAndUsesFolderWhenAlbumIsUnknown() {
        val groups = groupTracksIntoPlaylists(listOf(
            track(1, "Blue", "Music/A"),
            track(2, "Blue", "Music/B"),
            track(3, "Unknown album", "Music/Podcasts"),
            track(4, "", "Music/Podcasts"),
        ))
        assertEquals(2, groups.first { it.title == "Blue" }.tracks.size)
        val folderGroup = groups.first { it.title == "Music/Podcasts" }
        assertEquals(PlaylistGroupType.FOLDER, folderGroup.type)
        assertEquals(2, folderGroup.tracks.size)
        assertEquals(2, groups.size)
    }

    @Test
    fun emptyLibraryProducesNoAutomaticPlaylists() {
        assertTrue(groupTracksIntoPlaylists(emptyList()).isEmpty())
    }
}
