package com.xelilovth.nota.presentation.player

import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.repository.LyricsRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import com.xelilovth.nota.domain.model.TrackLyrics
import com.xelilovth.nota.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {
    private class FakeRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        val calls = mutableListOf<String>()
        var playedTrack: Track? = null
        var playedQueue: List<Track> = emptyList()

        override fun playTrack(track: Track, queue: List<Track>) {
            calls += "play:${track.id}:${queue.joinToString(",") { it.id.toString() }}"
            playedTrack = track
            playedQueue = queue
        }
        override fun togglePlayPause() { calls += "toggle" }
        override fun skipNext() { calls += "next" }
        override fun skipPrevious() { calls += "previous" }
        override fun seekTo(positionMs: Long) { playbackState.value = playbackState.value.copy(positionMs = positionMs); calls += "seek:$positionMs" }
        override fun toggleShuffle() {
            playbackState.value = playbackState.value.copy(shuffle = !playbackState.value.shuffle)
            calls += "shuffle"
        }
        override fun cycleRepeatMode() {
            val nextMode = when (playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            playbackState.value = playbackState.value.copy(repeatMode = nextMode)
            calls += "repeat:${nextMode.name}"
        }
        override fun stopPlayback() {
            playbackState.value = PlaybackState()
            calls += "stop"
        }
        override fun setLyricsSyncActive(active: Boolean) {
            calls += "lyricsSync:$active"
        }
    }

    private fun track(id: Long) = Track(
        id, "content://audio/$id", "Track $id", "Artist", "Album", 1000L, "Music",
    )

    private class FakeLyricsRepository : LyricsRepository {
        var nextLyrics: TrackLyrics? = null
        var importedLyrics: TrackLyrics? = null
        var geminiLyrics: TrackLyrics? = null
        var groqLyrics: TrackLyrics? = null
        var lastTranscribedTrack: Track? = null
        val deletedTrackIds = mutableListOf<Long>()
        val offsets = mutableMapOf<Long, Long>()

        override suspend fun getLyrics(track: Track): TrackLyrics? = nextLyrics
        override suspend fun importLocalLyrics(track: Track, uriString: String): TrackLyrics? = importedLyrics
        override suspend fun generateLyricsWithGemini(track: Track, apiKey: String): TrackLyrics? {
            lastTranscribedTrack = track
            return geminiLyrics
        }
        override suspend fun generateLyricsWithGroq(track: Track, apiKey: String): TrackLyrics? {
            lastTranscribedTrack = track
            return groqLyrics
        }
        override suspend fun deleteLyrics(trackId: Long) { deletedTrackIds += trackId }
        override suspend fun getSyncOffsetMs(trackId: Long): Long = offsets[trackId] ?: 0L
        override suspend fun setSyncOffsetMs(trackId: Long, offsetMs: Long) { offsets[trackId] = offsetMs }
    }

    private class FakeOnlineMusicRepository : OnlineMusicRepository {
        override val offlineTracks = MutableStateFlow<List<Track>>(emptyList())
        override suspend fun searchOnline(query: String): Result<List<Track>> = Result.success(emptyList())
        override suspend fun resolveStreamUrl(onlineId: String): String? = null
        override suspend fun downloadForOffline(track: Track, location: OfflineStorageLocation): Result<Track> =
            Result.failure(UnsupportedOperationException())
        override suspend fun removeOfflineTrack(track: Track) = Unit
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override val recentTrackIds = MutableStateFlow<List<Long>>(emptyList())
        private val _geminiApiKey = MutableStateFlow("")
        override val geminiApiKey = _geminiApiKey

        override fun setThemeMode(mode: ThemeMode) {
            settings.value = settings.value.copy(themeMode = mode)
        }
        override fun setHideShortTracks(hide: Boolean) {
            settings.value = settings.value.copy(hideShortTracks = hide)
        }
        override fun addRecentTrack(trackId: Long) {
            recentTrackIds.value = (listOf(trackId) + recentTrackIds.value.filterNot { it == trackId }).take(10)
        }
        override fun setGeminiApiKey(key: String) {
            _geminiApiKey.value = key
        }
    }

    private class FakeLibraryRepository : com.xelilovth.nota.domain.repository.LibraryRepository {
        override val tracks = MutableStateFlow(emptyList<Track>())
        override suspend fun scan(): List<Track> = emptyList()
    }

    private fun viewModel(repository: FakeRepository) = PlayerViewModel(
        repository, FakeLibraryRepository(), FakeLyricsRepository(), FakeSettingsRepository(), PlayTrackUseCase(repository), TogglePlayPauseUseCase(repository),
        SkipNextUseCase(repository), SkipPreviousUseCase(repository), SeekToUseCase(repository),
        ToggleShuffleUseCase(repository), CycleRepeatModeUseCase(repository),
    )

    @Test fun playingTrackFromLibraryPreservesTheFullQueueAndSelectedIndex() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        val libraryTracks = listOf(track(1L), track(2L), track(3L), track(4L))
        val selectedTrack = libraryTracks[2]

        viewModel.onRowClick(selectedTrack, libraryTracks)

        assertEquals(selectedTrack, repository.playedTrack)
        assertEquals(listOf(1L, 2L, 3L, 4L), repository.playedQueue.map(Track::id))
        assertEquals("play:3:1,2,3,4", repository.calls.single())
    }

    @Test fun rowAndPlayIconSelectionProduceTheSamePlaybackStateAndQueue() {
        val rowRepository = FakeRepository()
        val iconRepository = FakeRepository()
        val rowViewModel = viewModel(rowRepository)
        val iconViewModel = viewModel(iconRepository)
        val libraryTracks = listOf(track(1L), track(2L), track(3L), track(4L))
        val selectedTrack = libraryTracks[2]

        rowViewModel.onRowClick(selectedTrack, libraryTracks)
        iconViewModel.onPlayIconClick(selectedTrack, libraryTracks)

        assertEquals(rowRepository.playbackState.value, iconRepository.playbackState.value)
        assertEquals(rowRepository.playedTrack, iconRepository.playedTrack)
        assertEquals(rowRepository.playedQueue, iconRepository.playedQueue)
        assertEquals(selectedTrack, rowRepository.playedQueue[2])
        assertEquals(selectedTrack, iconRepository.playedQueue[2])
        assertEquals(listOf(1L, 2L, 3L, 4L), rowRepository.playedQueue.map(Track::id))
    }

    @Test fun shuffleButtonTogglesOnAndOff() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.toggleShuffle()
        assertTrue(viewModel.playbackState.value.shuffle)

        viewModel.toggleShuffle()
        assertFalse(viewModel.playbackState.value.shuffle)
    }

    @Test fun repeatButtonCyclesOffAllOneOff() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        assertEquals(RepeatMode.OFF, viewModel.playbackState.value.repeatMode)
        viewModel.cycleRepeatMode()
        assertEquals(RepeatMode.ALL, viewModel.playbackState.value.repeatMode)
        viewModel.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, viewModel.playbackState.value.repeatMode)
        viewModel.cycleRepeatMode()
        assertEquals(RepeatMode.OFF, viewModel.playbackState.value.repeatMode)
    }

    @Test fun playerActionsAreForwardedToRepository() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.togglePlayPause()
        viewModel.next()
        viewModel.previous()
        viewModel.seek(420L)

        assertEquals(listOf("toggle", "next", "previous", "seek:420"), repository.calls)
        assertEquals(420L, viewModel.playbackState.value.positionMs)
        assertEquals(repository.playbackState, viewModel.playbackState)

        viewModel.dismiss()
        assertEquals("stop", repository.calls.last())
        assertEquals(0L, viewModel.playbackState.value.positionMs)
    }

    @Test fun lyricsSyncActiveOffsetAndLocalImportUpdateState() {
        val repository = FakeRepository()
        val lyricsRepository = FakeLyricsRepository().apply {
            offsets[7L] = 500L
            importedLyrics = TrackLyrics(
                lines = listOf(com.xelilovth.nota.domain.model.LyricLine("Local line", 1000L, 3000L)),
                source = com.xelilovth.nota.domain.model.LyricsSource.LOCAL_FILE,
                syncType = com.xelilovth.nota.domain.model.LyricsSyncType.LINE,
            )
        }
        val settingsRepository = FakeSettingsRepository()
        val viewModel = PlayerViewModel(
            repository, FakeLibraryRepository(), lyricsRepository, settingsRepository, PlayTrackUseCase(repository), TogglePlayPauseUseCase(repository),
            SkipNextUseCase(repository), SkipPreviousUseCase(repository), SeekToUseCase(repository),
            ToggleShuffleUseCase(repository), CycleRepeatModeUseCase(repository),
        )
        val sampleTrack = track(7L)

        viewModel.setLyricsSyncActive(true)
        viewModel.loadLyrics(sampleTrack)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(500L, viewModel.lyricsState.value.syncOffsetMs)
        assertTrue(viewModel.lyricsState.value.notFound)

        viewModel.adjustLyricsOffset(7L, -1000L)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(-500L, viewModel.lyricsState.value.syncOffsetMs)
        assertEquals(-500L, lyricsRepository.offsets[7L])

        viewModel.importLocalLyrics(sampleTrack, "content://docs/song.lrc")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(viewModel.lyricsState.value.notFound)
        assertEquals(com.xelilovth.nota.domain.model.LyricsSource.LOCAL_FILE, viewModel.lyricsState.value.lyrics?.source)
        assertTrue(repository.calls.contains("lyricsSync:true"))
    }

    @Test fun geminiGenerationApiKeyAndDeleteLyricsUpdateState() {
        val repository = FakeRepository()
        val lyricsRepository = FakeLyricsRepository().apply {
            geminiLyrics = TrackLyrics(
                lines = listOf(com.xelilovth.nota.domain.model.LyricLine("Gemini line", 500L, 2500L)),
                source = com.xelilovth.nota.domain.model.LyricsSource.GEMINI,
                syncType = com.xelilovth.nota.domain.model.LyricsSyncType.LINE,
            )
        }
        val settingsRepository = FakeSettingsRepository()
        val viewModel = PlayerViewModel(
            repository, FakeLibraryRepository(), lyricsRepository, settingsRepository, PlayTrackUseCase(repository), TogglePlayPauseUseCase(repository),
            SkipNextUseCase(repository), SkipPreviousUseCase(repository), SeekToUseCase(repository),
            ToggleShuffleUseCase(repository), CycleRepeatModeUseCase(repository),
        )
        val sampleTrack = track(11L)

        viewModel.saveGeminiApiKey("  test-key  ")
        assertEquals("test-key", viewModel.geminiApiKey.value)

        viewModel.generateLyricsWithGemini(sampleTrack)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(viewModel.lyricsState.value.notFound)
        assertEquals(com.xelilovth.nota.domain.model.LyricsSource.GEMINI, viewModel.lyricsState.value.lyrics?.source)
        assertEquals(null, viewModel.lyricsState.value.errorMessage)

        viewModel.deleteLyrics(11L)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(viewModel.lyricsState.value.notFound)
        assertEquals(null, viewModel.lyricsState.value.lyrics)
        assertEquals(listOf(11L), lyricsRepository.deletedTrackIds)
    }

    @Test fun transcribeWithProviderUsesDownloadedOfflineCopyWithoutSwitchingAwayFromOnlineTrack() {
        val repository = FakeRepository()
        val onlineTrack = Track(
            id = -5001L,
            uri = "nota://online/vid1",
            title = "Online Song",
            artist = "Artist",
            album = "Online",
            durationMs = 180_000L,
            folder = "Online",
            isOnline = true,
            onlineId = "vid1",
        )
        val offlineTrack = onlineTrack.copy(
            uri = "file:///data/user/0/com.xelilovth.nota/files/offline_tracks/vid1.m4a",
            folder = "Offline",
            isOnline = false,
        )
        val lyricsRepository = FakeLyricsRepository().apply {
            geminiLyrics = TrackLyrics(
                lines = listOf(com.xelilovth.nota.domain.model.LyricLine("Transcribed", 1000L, 3000L)),
                source = com.xelilovth.nota.domain.model.LyricsSource.GEMINI,
                syncType = com.xelilovth.nota.domain.model.LyricsSyncType.LINE,
            )
        }
        val settingsRepository = FakeSettingsRepository()
        val onlineRepository = FakeOnlineMusicRepository()
        val viewModel = PlayerViewModel(
            repository,
            FakeLibraryRepository(),
            lyricsRepository,
            settingsRepository,
            PlayTrackUseCase(repository),
            TogglePlayPauseUseCase(repository),
            SkipNextUseCase(repository),
            SkipPreviousUseCase(repository),
            SeekToUseCase(repository),
            ToggleShuffleUseCase(repository),
            CycleRepeatModeUseCase(repository),
            onlineRepository,
        )
        viewModel.saveGeminiApiKey("gemini-key")

        viewModel.transcribeWithProvider(onlineTrack, AiLyricsProvider.GEMINI)
        assertEquals("Download track offline first to transcribe with AI", viewModel.lyricsState.value.errorMessage)

        onlineRepository.offlineTracks.value = listOf(offlineTrack)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(null, viewModel.lyricsState.value.errorMessage)

        // Even if the UI still passes the original onlineTrack snapshot, ViewModel resolves the offline file.
        viewModel.transcribeWithProvider(onlineTrack, AiLyricsProvider.GEMINI)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(offlineTrack, lyricsRepository.lastTranscribedTrack)
        assertEquals(com.xelilovth.nota.domain.model.LyricsSource.GEMINI, viewModel.lyricsState.value.lyrics?.source)
        assertEquals(null, viewModel.lyricsState.value.errorMessage)
    }

    @Test fun groqAndAiTranscribeAlsoRequireDownloadWhenOnlineTrackNotDownloaded() {
        val repository = FakeRepository()
        val onlineTrack = Track(
            id = -5002L,
            uri = "nota://online/vid2",
            title = "Stream Song",
            artist = "Artist",
            album = "Online",
            durationMs = 120_000L,
            folder = "Online",
            isOnline = true,
            onlineId = "vid2",
        )
        val viewModel = viewModel(repository)

        viewModel.generateLyricsWithGroq(onlineTrack, "groq-key")
        assertEquals("Download track offline first to transcribe with AI", viewModel.lyricsState.value.errorMessage)

        viewModel.generateLyricsWithAi(onlineTrack)
        assertEquals("Download track offline first to transcribe with AI", viewModel.lyricsState.value.errorMessage)
    }
}
