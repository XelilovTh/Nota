package com.xelilovth.nota.presentation

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.components.TrackCover
import com.xelilovth.nota.ui.theme.AppDimens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackCoverUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trackCover_withNullArtwork_showsPlaceholder() {
        composeTestRule.setContent {
            TrackCover(
                artworkUri = null,
                contentDescription = "Test placeholder",
                size = AppDimens.CoverSizeSmall,
            )
        }

        composeTestRule.onNodeWithContentDescription("Test placeholder").assertIsDisplayed()
    }

    @Test
    fun trackCover_withTrackWithoutArtwork_showsPlaceholder() {
        val track = Track(
            id = 1L,
            uri = Uri.parse("content://audio/1"),
            title = "Test Track",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            folder = "Music",
        )

        composeTestRule.setContent {
            TrackCover(
                track = track,
                contentDescription = "Track cover",
                size = AppDimens.CoverSizeMedium,
            )
        }

        composeTestRule.onNodeWithContentDescription("Track cover").assertIsDisplayed()
    }

    @Test
    fun trackCover_largeSize_showsPlaceholder() {
        composeTestRule.setContent {
            TrackCover(
                artworkUri = null,
                contentDescription = "Large cover",
                size = AppDimens.CoverSizeLarge,
            )
        }

        composeTestRule.onNodeWithContentDescription("Large cover").assertIsDisplayed()
    }
}
