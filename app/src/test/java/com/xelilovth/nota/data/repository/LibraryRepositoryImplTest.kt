package com.xelilovth.nota.data.repository

import android.net.Uri
import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryImplTest {
    @Test
    fun scanPublishesTracksToStateFlow() = runTest {
        val expected = listOf(
            Track(
                id = 7L,
                uri = Uri.parse("content://media/external/audio/media/7"),
                title = "Song",
                artist = "Artist",
                album = "Album",
                durationMs = 120_000L,
                folder = "Music",
            ),
        )
        val repository = LibraryRepositoryImpl(object : AudioScanner {
            override suspend fun scan() = expected
        })

        repository.scan()

        assertEquals(expected, repository.tracks.value)
    }
}
