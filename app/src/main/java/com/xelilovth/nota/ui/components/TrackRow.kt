package com.xelilovth.nota.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun TrackRow(
    track: Track,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = AppDimens.ElementSpacing),
    ) {
        TrackCover(
            track = track,
            size = AppDimens.CoverSizeMedium,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay),
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "${track.artist} · ${track.album}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                track.folder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    Icons.Outlined.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play ${track.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = AppDimens.ElementSpacing),
        )
    }
}
