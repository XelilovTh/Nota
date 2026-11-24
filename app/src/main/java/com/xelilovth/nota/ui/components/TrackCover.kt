package com.xelilovth.nota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.AppShapes

/**
 * Reusable album cover art component displaying the track's cover image via Coil AsyncImage.
 * If the image is missing or fails to load, falls back to a music note icon on a surfaceVariant background.
 */
@Composable
fun TrackCover(
    modifier: Modifier = Modifier,
    artworkUri: Any? = null,
    size: Dp = AppDimens.CoverSizeMedium,
    shape: Shape = when (size) {
        AppDimens.CoverSizeSmall -> AppShapes.small
        AppDimens.CoverSizeLarge -> AppShapes.large
        else -> AppShapes.medium
    },
    iconSize: Dp = when (size) {
        AppDimens.CoverSizeSmall -> AppDimens.CoverIconSizeSmall
        AppDimens.CoverSizeLarge -> AppDimens.CoverIconSizeLarge
        else -> AppDimens.CoverIconSizeMedium
    },
    contentDescription: String? = null,
) {
    var isLoaded by remember(artworkUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    isLoaded = state is AsyncImagePainter.State.Success
                },
            )
        }

        if (!isLoaded) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = contentDescription ?: "Music cover placeholder",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Overload of [TrackCover] accepting a [Track] object.
 */
@Composable
fun TrackCover(
    track: Track?,
    modifier: Modifier = Modifier,
    size: Dp = AppDimens.CoverSizeMedium,
    shape: Shape = when (size) {
        AppDimens.CoverSizeSmall -> AppShapes.small
        AppDimens.CoverSizeLarge -> AppShapes.large
        else -> AppShapes.medium
    },
    iconSize: Dp = when (size) {
        AppDimens.CoverSizeSmall -> AppDimens.CoverIconSizeSmall
        AppDimens.CoverSizeLarge -> AppDimens.CoverIconSizeLarge
        else -> AppDimens.CoverIconSizeMedium
    },
    contentDescription: String? = null,
) {
    TrackCover(
        modifier = modifier,
        artworkUri = track?.artworkUri,
        size = size,
        shape = shape,
        iconSize = iconSize,
        contentDescription = contentDescription,
    )
}
