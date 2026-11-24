package com.xelilovth.nota.presentation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.PlaybackRepository
import com.xelilovth.nota.domain.usecase.CycleRepeatModeUseCase
import com.xelilovth.nota.domain.usecase.PlayTrackUseCase
import com.xelilovth.nota.domain.usecase.SeekToUseCase
import com.xelilovth.nota.domain.usecase.SkipNextUseCase
import com.xelilovth.nota.domain.usecase.SkipPreviousUseCase
import com.xelilovth.nota.domain.usecase.TogglePlayPauseUseCase
import com.xelilovth.nota.domain.usecase.ToggleShuffleUseCase
import com.xelilovth.nota.presentation.player.NowPlayingContent
import com.xelilovth.nota.presentation.player.NowPlayingScreen
import com.xelilovth.nota.presentation.player.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NowPlayingScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakePlaybackRepository : PlaybackRepository {
        override val playbackState = MutableStateFlow(PlaybackState())
        val calls = mutableListOf<String>()

        override fun playTrack(track: Track, queue: List<Track>) { calls += "play:${track.id}" }
        override fun togglePlayPause() { calls += "togglePlayPause" }
        override fun skipNext() { calls += "skipNext" }
        override fun skipPrevious() { calls += "skipPrevious" }
        override fun seekTo(positionMs: Long) { calls += "seekTo:$positionMs" }
        override fun toggleShuffle() { calls += "toggleShuffle" }
        override fun cycleRepeatMode() { calls += "cycleRepeatMode" }
    }

    private fun testTrack() = Track(
        id = 42L,
        uri = Uri.parse("content://audio/42"),
        title = "Stairway to Heaven",
        artist = "Led Zeppelin",
        album = "Led Zeppelin IV",
        durationMs = 482000L,
        folder = "Music",
    )

    private fun createPlayerViewModel(repository: FakePlaybackRepository) = PlayerViewModel(
        repository,
        PlayTrackUseCase(repository),
        TogglePlayPauseUseCase(repository),
        SkipNextUseCase(repository),
        SkipPreviousUseCase(repository),
        SeekToUseCase(repository),
        ToggleShuffleUseCase(repository),
        CycleRepeatModeUseCase(repository),
    )

    @Test
    fun nowPlayingScreen_displaysTrackInfoAndHandlesControls() {
        val repository = FakePlaybackRepository()
        val viewModel = createPlayerViewModel(repository)
        val track = testTrack()
        val playbackState = PlaybackState(track = track, isPlaying = false, durationMs = track.durationMs)
        var favoriteToggled = false

        composeTestRule.setContent {
            NowPlayingScreen(
                state = playbackState,
                viewModel = viewModel,
                isFavorite = false,
                onToggleFavorite = { favoriteToggled = true },
            )
        }

        // Assert track title and artist are displayed
        composeTestRule.onNodeWithText("Stairway to Heaven").assertIsDisplayed()
        composeTestRule.onNodeWithText("Led Zeppelin").assertIsDisplayed()

        // Click Play button
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        assertTrue(repository.calls.contains("togglePlayPause"))

        // Click Favorite button
        composeTestRule.onNodeWithTag("favorite_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorite_button").performClick()
        assertTrue(favoriteToggled)

        // Click Next button
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        assertTrue(repository.calls.contains("skipNext"))

        // Click Previous button
        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        assertTrue(repository.calls.contains("skipPrevious"))
    }

    @Test
    fun favoriteButton_togglesStateAndUpdatesUI() {
        val track = testTrack()

        composeTestRule.setContent {
            var isFavorite by remember { mutableStateOf(false) }
            var isPlaying by remember { mutableStateOf(false) }

            NowPlayingContent(
                state = PlaybackState(track = track, isPlaying = isPlaying, durationMs = track.durationMs),
                isFavorite = isFavorite,
                onToggleFavorite = { isFavorite = !isFavorite },
                onTogglePlayPause = { isPlaying = !isPlaying },
            )
        }

        // Initial state: not favorite, not playing
        composeTestRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()

        // Tap Favorite
        composeTestRule.onNodeWithContentDescription("Favorite").performClick()

        // State changes: now favorite
        composeTestRule.onNodeWithContentDescription("Favorite active").assertIsDisplayed()

        // Tap Favorite again
        composeTestRule.onNodeWithContentDescription("Favorite active").performClick()

        // Restores inactive favorite state
        composeTestRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()

        // Tap Play
        composeTestRule.onNodeWithContentDescription("Play").performClick()

        // Now playing: Pause button should be visible
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }
}
