package com.xelilovth.nota.presentation.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onSleepTimer: () -> Unit,
    onLyrics: () -> Unit,
    onSettings: () -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    val position = state.positionMs.coerceIn(0L, duration)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Now Playing", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Surface(
            modifier = Modifier.size(AppDimens.PlayerArtworkSize),
            shape = RoundedCornerShape(AppDimens.ShapeLarge),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(AppDimens.BorderWidth, MaterialTheme.colorScheme.outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = "Album art placeholder",
                    modifier = Modifier.size(AppDimens.PlayerArtworkIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            Text(
                state.track?.title ?: "No track selected",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.track?.artist.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = position.toFloat(),
                onValueChange = { viewModel.seek(it.toLong()) },
                valueRange = 0f..sliderMax,
                enabled = duration > 0L,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlaybackTime(position), style = MaterialTheme.typography.labelMedium)
                Text(formatPlaybackTime(duration), style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.PlayerControlSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(AppDimens.IconSizeLarge))
            }
            Surface(
                onClick = viewModel::togglePlayPause,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(AppDimens.PlayButtonSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(AppDimens.IconSizeLarge),
                    )
                }
            }
            IconButton(onClick = viewModel::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(AppDimens.IconSizeLarge))
            }
            IconButton(onClick = viewModel::cycleRepeatMode) {
                Icon(
                    if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat ${state.repeatMode.name.lowercase()}",
                    tint = if (state.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onLyrics, modifier = Modifier.weight(1f)) { Text("Lyrics") }
            TextButton(
                onClick = onSleepTimer,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = AppDimens.PlayerActionHorizontalPadding),
            ) { Text("Sleep Timer", maxLines = 1) }
        }
    }
}

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
