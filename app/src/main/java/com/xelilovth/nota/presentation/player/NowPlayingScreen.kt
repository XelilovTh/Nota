package com.xelilovth.nota.presentation.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.playlists.AddToPlaylistSheet
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val ARC_START_ANGLE = 135f
private const val ARC_SWEEP_ANGLE = 270f

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
    val position = state.positionMs.coerceIn(0L, duration)
    val playerProgress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else playerProgress
    val displayPositionMs = if (isDragging && duration > 0L) (dragProgress * duration).toLong() else position

    var showRemaining by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.PlayerBottomSafePadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back")
            }
            Text(
                state.track?.album?.takeIf {
                    it.isNotBlank() && !it.equals("Unknown album", ignoreCase = true)
                } ?: "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = AppDimens.ElementSpacing),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (state.track != null) {
                IconButton(onClick = { showAddToPlaylist = true }) {
                    Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = "Add to playlist")
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(modifier = Modifier.weight(0.22f))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            Text(
                state.track?.title ?: "No track selected",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.track?.artist.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SectionSpacing))

        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showRemaining = !showRemaining },
        ) {
            Text(
                formatDuration(displayPositionMs),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "|",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val rightTime = if (showRemaining && duration > 0L) {
                "-${formatDuration((duration - displayPositionMs).coerceAtLeast(0L))}"
            } else {
                formatDuration(duration)
            }
            Text(
                rightTime,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SectionSpacing))

        CircularArcPlayerArt(
            track = state.track,
            progress = displayProgress,
            enabled = duration > 0L,
            onProgressChange = { next ->
                isDragging = true
                dragProgress = next
            },
            onSeekFinish = { finalProgress ->
                isDragging = false
                if (duration > 0L) {
                    viewModel.seek((finalProgress * duration).toLong())
                }
            },
            modifier = Modifier.fillMaxWidth(AppDimens.PlayerArtworkFraction).aspectRatio(1f),
        )

        Spacer(modifier = Modifier.weight(0.28f))

        GlassSurface(
            shape = CircleShape,
            elevated = true,
            blurBackdrop = true,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = AppDimens.CardPadding,
                    vertical = AppDimens.ElementSpacing,
                ),
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
        }

        Spacer(modifier = Modifier.weight(0.25f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            GlassSurface(
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                subtle = true,
                blurBackdrop = true,
                onClick = { showQueueSheet = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppDimens.CardPadding,
                            vertical = AppDimens.RowVerticalPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                    Text(
                        "Queue (${queueTracks.size})",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            GlassSurface(
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                subtle = true,
                blurBackdrop = true,
                onClick = onSleepTimer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppDimens.PlayerActionHorizontalPadding,
                            vertical = AppDimens.RowVerticalPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                    val sleepLabel = if (sleepTimerRemainingSeconds != null) {
                        formatDuration(sleepTimerRemainingSeconds * 1_000L)
                    } else {
                        "Sleep Timer"
                    }
                    Text(
                        sleepLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = LocalGlassColors.current.surfaceElevatedTint,
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
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            subtle = !isCurrent,
                            onClick = {
                                viewModel.onRowClick(track, queueTracks)
                                showQueueSheet = false
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = AppDimens.CardPadding,
                                        vertical = AppDimens.RowVerticalPadding,
                                    ),
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

    val activeTrack = state.track
    if (showAddToPlaylist && activeTrack != null) {
        AddToPlaylistSheet(
            track = activeTrack,
            onDismiss = { showAddToPlaylist = false },
        )
    }
}

@Composable
private fun CircularArcPlayerArt(
    track: Track?,
    progress: Float,
    enabled: Boolean,
    onProgressChange: (Float) -> Unit,
    onSeekFinish: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = if (LocalGlassColors.current.subtleRowBorder) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    }
    var latestProgress by remember { mutableFloatStateOf(progress) }
    latestProgress = progress

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = touchOffsetToArcProgress(offset, size.width.toFloat(), size.height.toFloat())
                    onSeekFinish(fraction)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val fraction = touchOffsetToArcProgress(offset, size.width.toFloat(), size.height.toFloat())
                        latestProgress = fraction
                        onProgressChange(fraction)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = touchOffsetToArcProgress(change.position, size.width.toFloat(), size.height.toFloat())
                        latestProgress = fraction
                        onProgressChange(fraction)
                    },
                    onDragEnd = { onSeekFinish(latestProgress) },
                    onDragCancel = { onSeekFinish(latestProgress) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = AppDimens.CircularSeekbarStroke.toPx()
            val thumbRadiusPx = AppDimens.CircularSeekbarThumbRadius.toPx()
            val inset = thumbRadiusPx + strokePx
            val diameter = min(size.width, size.height) - inset * 2f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = ARC_START_ANGLE,
                sweepAngle = ARC_SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            val clamped = progress.coerceIn(0f, 1f)
            if (clamped > 0f) {
                drawArc(
                    color = activeColor,
                    startAngle = ARC_START_ANGLE,
                    sweepAngle = ARC_SWEEP_ANGLE * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val theta = Math.toRadians((ARC_START_ANGLE + ARC_SWEEP_ANGLE * clamped).toDouble())
            val thumbCenter = Offset(
                x = center.x + (radius * cos(theta)).toFloat(),
                y = center.y + (radius * sin(theta)).toFloat(),
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadiusPx,
                center = thumbCenter,
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize().padding(AppDimens.CircularSeekbarRingGap + AppDimens.CircularSeekbarThumbRadius),
            shape = CircleShape,
            border = BorderStroke(AppDimens.BorderWidth, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            TrackArtwork(
                trackId = track?.id,
                trackUri = track?.uri,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                iconPadding = AppDimens.ScreenPadding,
            )
        }
    }
}

private fun touchOffsetToArcProgress(offset: Offset, width: Float, height: Float): Float {
    val cx = width / 2f
    val cy = height / 2f
    val angleDeg = Math.toDegrees(atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble())).toFloat()
    val normalized = (angleDeg + 360f) % 360f
    val relative = (normalized - ARC_START_ANGLE + 360f) % 360f
    return when {
        relative <= ARC_SWEEP_ANGLE -> (relative / ARC_SWEEP_ANGLE).coerceIn(0f, 1f)
        relative < ARC_SWEEP_ANGLE + 45f -> 1f
        else -> 0f
    }
}
