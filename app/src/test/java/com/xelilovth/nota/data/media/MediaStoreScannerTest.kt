package com.xelilovth.nota.data.media

import android.database.MatrixCursor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
}
