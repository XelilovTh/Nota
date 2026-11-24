package com.xelilovth.nota.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xelilovth.nota.presentation.playlists.PlaylistsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistsScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_createPlaylistFlow_addsPlaylistToScreen() {
        var createdPlaylistName: String? = null

        composeTestRule.setContent {
            PlaylistsContent(
                tracks = emptyList(),
                initialPlaylists = emptyList(),
                onCreatePlaylist = { createdPlaylistName = it },
            )
        }

        // Verify empty state shows "Create Playlist" button
        composeTestRule.onNodeWithText("Create Playlist").assertIsDisplayed()

        // Tap "Create Playlist" to open dialog
        composeTestRule.onNodeWithText("Create Playlist").performClick()

        // Enter playlist name
        composeTestRule.onNodeWithText("Playlist name").performTextInput("Favorites 2026")

        // Tap "Create" button in dialog
        composeTestRule.onNodeWithText("Create").performClick()

        // Verify newly created playlist is visible on screen
        composeTestRule.onNodeWithText("Favorites 2026").assertIsDisplayed()
        assertEquals("Favorites 2026", createdPlaylistName)
    }

    @Test
    fun populatedPlaylists_newPlaylistButton_createsAndDisplaysPlaylist() {
        var createdPlaylistName: String? = null

        composeTestRule.setContent {
            PlaylistsContent(
                tracks = emptyList(),
                initialPlaylists = listOf("Oldies"),
                onCreatePlaylist = { createdPlaylistName = it },
            )
        }

        // Verify existing playlist is visible
        composeTestRule.onNodeWithText("Oldies").assertIsDisplayed()

        // Tap "New Playlist" in header
        composeTestRule.onNodeWithText("New Playlist").performClick()

        // Type playlist name in dialog
        composeTestRule.onNodeWithText("Playlist name").performTextInput("Rock Classics")

        // Confirm creation
        composeTestRule.onNodeWithText("Create").performClick()

        // Assert both old and new playlists are displayed
        composeTestRule.onNodeWithText("Oldies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rock Classics").assertIsDisplayed()
        assertEquals("Rock Classics", createdPlaylistName)
    }

    @Test
    fun cancelPlaylistCreation_doesNotAddPlaylist() {
        var createdPlaylistName: String? = null

        composeTestRule.setContent {
            PlaylistsContent(
                tracks = emptyList(),
                initialPlaylists = listOf("Road Trip"),
                onCreatePlaylist = { createdPlaylistName = it },
            )
        }

        composeTestRule.onNodeWithText("New Playlist").performClick()
        composeTestRule.onNodeWithText("Playlist name").performTextInput("Never Created")
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Road Trip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Never Created").assertDoesNotExist()
        assertNull(createdPlaylistName)
    }
}
