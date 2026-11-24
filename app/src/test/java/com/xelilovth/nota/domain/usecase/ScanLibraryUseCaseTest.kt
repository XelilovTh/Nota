package com.xelilovth.nota.domain.usecase

import android.net.Uri
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanLibraryUseCaseTest {
    @Test
    fun invokesRepositoryScanAndReturnsTracks() = runTest {
        val expected = listOf(
            Track(
                id = 1L,
                uri = Uri.parse("content://media/external/audio/media/1"),
                title = "Track",
                artist = "Artist",
                album = "Album",
                durationMs = 90_000L,
                folder = "Music",
            ),
        )
        var scanCalled = false
        var passedForce: Boolean? = null
        val repository = object : LibraryRepository {
            override val tracks = MutableStateFlow(emptyList<Track>())
            override suspend fun scan(force: Boolean): List<Track> {
                scanCalled = true
                passedForce = force
                tracks.value = expected
                return expected
            }
        }

        val result = ScanLibraryUseCase(repository)(force = true)

        assertTrue(scanCalled)
        assertEquals(true, passedForce)
        assertEquals(expected, result)
        assertEquals(expected, repository.tracks.value)
    }
}
