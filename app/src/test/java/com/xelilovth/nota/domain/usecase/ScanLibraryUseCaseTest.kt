package com.xelilovth.nota.domain.usecase

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
                uri = "content://media/external/audio/media/1",
                title = "Track",
                artist = "Artist",
                album = "Album",
                durationMs = 90_000L,
                folder = "Music",
            ),
        )
        var scanCalled = false
        val repository = object : LibraryRepository {
            override val tracks = MutableStateFlow(emptyList<Track>())
            override suspend fun scan(): List<Track> {
                scanCalled = true
                tracks.value = expected
                return expected
            }
        }

        val result = ScanLibraryUseCase(repository)()

        assertTrue(scanCalled)
        assertEquals(expected, result)
        assertEquals(expected, repository.tracks.value)
    }
}
