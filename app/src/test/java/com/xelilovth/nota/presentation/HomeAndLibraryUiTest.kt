package com.xelilovth.nota.presentation

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.home.HomeContent
import com.xelilovth.nota.presentation.library.LibraryContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeAndLibraryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleTracks() = listOf(
        Track(
            id = 1L,
            uri = Uri.parse("content://audio/1"),
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationMs = 354000L,
            folder = "Music/Rock",
        ),
        Track(
            id = 2L,
            uri = Uri.parse("content://audio/2"),
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            durationMs = 391000L,
            folder = "Music/Rock",
        ),
    )

    @Test
    fun libraryContent_displaysTracksAndSimulatesTouch() {
        val tracks = sampleTracks()
        var clickedTrack: Track? = null

        composeTestRule.setContent {
            LibraryContent(
                tracks = tracks,
                hasPermission = true,
                onTrackClick = { clickedTrack = it },
            )
        }

        // Verify tracks appear on screen
        composeTestRule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hotel California").assertIsDisplayed()

        // Simulate touch on the second track
        composeTestRule.onNodeWithText("Hotel California").performClick()

        // Verify callback was triggered with selected track
        assertEquals(tracks[1], clickedTrack)
    }

    @Test
    fun libraryContent_emptyState_handlesScanClick() {
        var scanRequested = false

        composeTestRule.setContent {
            LibraryContent(
                tracks = emptyList(),
                hasPermission = true,
                onScanAgain = { scanRequested = true },
            )
        }

        composeTestRule.onNodeWithText("Your library is empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan again").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan again").performClick()

        assertTrue(scanRequested)
    }

    @Test
    fun homeContent_displaysTracksAndHandlesPlayAction() {
        val tracks = sampleTracks()
        var playFirstInvoked = false
        var playShuffledInvoked = false
        var trackPlayed: Track? = null

        composeTestRule.setContent {
            HomeContent(
                tracks = tracks,
                recentlyPlayed = listOf(tracks[0]),
                hasPermission = true,
                onPlayFirstTrack = { playFirstInvoked = true },
                onPlayShuffled = { playShuffledInvoked = true },
                onPlayTrack = { trackPlayed = it },
            )
        }

        // Verify Home screen greeting & track count
        composeTestRule.onNodeWithText("Good Morning").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 tracks in your library").assertIsDisplayed()

        // Simulate touch on Play button
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Play").performClick()
        assertTrue(playFirstInvoked)

        // Simulate touch on Shuffle button
        composeTestRule.onNodeWithText("Shuffle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shuffle").performClick()
        assertTrue(playShuffledInvoked)

        // Verify recently played track appears and handles click
        composeTestRule.onNodeWithText("Recently Played").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bohemian Rhapsody").performClick()
        assertEquals(tracks[0], trackPlayed)
    }
}
