package com.xelilovth.nota.presentation.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    viewModel: PlayerViewModel,
    queueTracks: List<Track> = emptyList(),
    sleepTimerRemainingSeconds: Long? = null,
    onBack: () -> Unit,
    onSleepTimer: () -> Unit,
    onSettings: () -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    val position = state.positionMs.coerceIn(0L, duration)
    var showRemaining by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
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

        Spacer(modifier = Modifier.weight(0.6f))

        Surface(
            modifier = Modifier.fillMaxWidth(AppDimens.PlayerArtworkFraction).aspectRatio(1f),
            shape = RoundedCornerShape(AppDimens.ShapeLarge),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(AppDimens.BorderWidth, MaterialTheme.colorScheme.outline),
        ) {
            TrackArtwork(
                trackId = state.track?.id,
                trackUri = state.track?.uri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(AppDimens.ShapeLarge),
                iconPadding = AppDimens.ScreenPadding,
            )
        }

        Spacer(modifier = Modifier.weight(0.6f))

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
            val subtitle = state.track?.let { track ->
                if (track.album.isNotBlank() && !track.album.equals("Unknown album", ignoreCase = true)) {
                    "${track.artist} · ${track.album}"
                } else {
                    track.artist
                }
            }.orEmpty()
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.weight(0.4f))

        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = position.toFloat(),
                onValueChange = { viewModel.seek(it.toLong()) },
                valueRange = 0f..sliderMax,
                enabled = duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position), style = MaterialTheme.typography.labelMedium)
                val endTimeLabel = if (showRemaining && duration > 0L) {
                    "-${formatDuration((duration - position).coerceAtLeast(0L))}"
                } else {
                    formatDuration(duration)
                }
                Text(
                    endTimeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { showRemaining = !showRemaining },
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))

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

        Spacer(modifier = Modifier.weight(0.3f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { showQueueSheet = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                Text(" Queue (${queueTracks.size})", maxLines = 1)
            }
            TextButton(
                onClick = onSleepTimer,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = AppDimens.PlayerActionHorizontalPadding),
            ) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                val sleepLabel = if (sleepTimerRemainingSeconds != null) {
                    " ${formatDuration(sleepTimerRemainingSeconds * 1_000L)}"
                } else {
                    " Sleep Timer"
                }
                Text(sleepLabel, maxLines = 1)
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.ScreenPadding)) {
                Text(
                    "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = AppDimens.ElementSpacing),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    items(queueTracks, key = Track::id, contentType = { "queue_track" }) { track ->
                        val isCurrent = track.id == state.track?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onRowClick(track, queueTracks)
                                    showQueueSheet = false
                                }
                                .padding(vertical = AppDimens.ElementSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
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
                            if (isCurrent) {
                                Icon(
                                    Icons.Outlined.GraphicEq,
                                    contentDescription = "Playing",
                                    modifier = Modifier.size(AppDimens.IconSizeSmall),
                                )
                            } else {
                                Text(
                                    formatDuration(track.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
