package com.xelilovth.nota.presentation.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    state: PlaybackState,
    viewModel: PlayerViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val duration = state.durationMs.coerceAtLeast(0L)
    val progress = if (duration > 0L) (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
            dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
        ) {
            viewModel.dismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.padding(horizontal = AppDimens.FloatingNavHorizontalPadding),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize())
        },
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.FloatingMiniPlayerHeight),
            shape = MaterialTheme.shapes.large,
            elevated = true,
            onClick = onOpen,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.MiniPlayerPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    TrackArtwork(
                        trackId = track.id,
                        trackUri = track.uri,
                        modifier = Modifier.size(AppDimens.ArtworkSmallSize),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                    }
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                        )
                    }
                    IconButton(onClick = viewModel::next) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(AppDimens.MiniPlayerProgressHeight),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
