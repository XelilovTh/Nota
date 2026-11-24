package com.xelilovth.nota.data.media

import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreScannerTest {
    @Test
    fun emptyAudioCursorReturnsEmptyList() = runTest {
        val emptyCursor = MatrixCursor(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
            ),
        )
        val scanner = MediaStoreScanner(
            audioMediaStore = object : AudioMediaStore {
                override fun queryAudio() = emptyCursor
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        assertTrue(scanner.scan().isEmpty())
    }

    @Test
    fun nullAudioCursorReturnsEmptyList() = runTest {
        val scanner = MediaStoreScanner(
            audioMediaStore = object : AudioMediaStore {
                override fun queryAudio() = null
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        assertTrue(scanner.scan().isEmpty())
    }

    @Test
    fun scansTracksAndFormatsArtworkUriFromAlbumId() = runTest {
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
            ),
        ).apply {
            addRow(arrayOf(1L, "Test Title", "Test Artist", "Test Album", 42L, 180_000L))
            addRow(arrayOf(2L, "No Art Song", "Artist 2", "Album 2", null, 200_000L))
        }

        val scanner = MediaStoreScanner(
            audioMediaStore = object : AudioMediaStore {
                override fun queryAudio() = cursor
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val tracks = scanner.scan()
        assertEquals(2, tracks.size)

        val firstTrack = tracks[0]
        assertEquals(1L, firstTrack.id)
        assertEquals(42L, firstTrack.albumId)
        assertEquals(
            Uri.parse("content://media/external/audio/albumart/42"),
            firstTrack.artworkUri,
        )

        val secondTrack = tracks[1]
        assertEquals(2L, secondTrack.id)
        assertNull(secondTrack.albumId)
        assertNull(secondTrack.artworkUri)
    }
}
