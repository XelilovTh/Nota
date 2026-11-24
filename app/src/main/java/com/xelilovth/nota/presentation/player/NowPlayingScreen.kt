package com.xelilovth.nota.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.components.TrackCover
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.AppShapes

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onLyrics: () -> Unit = {},
    onSettings: () -> Unit = {},
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val currentIsFavorite by viewModel.isCurrentTrackFavorite.collectAsStateWithLifecycle()
    val effectiveIsFavorite = isFavorite ?: currentIsFavorite
    val effectiveToggleFavorite = onToggleFavorite ?: { viewModel.toggleFavorite() }
    val upcomingQueue by viewModel.upcomingQueue.collectAsStateWithLifecycle()

    NowPlayingContent(
        state = state,
        isFavorite = effectiveIsFavorite,
        onToggleFavorite = effectiveToggleFavorite,
        onTogglePlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onSeek = viewModel::seek,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onBack = onBack,
        onEqualizer = onEqualizer,
        onSleepTimer = onSleepTimer,
        onLyrics = onLyrics,
        onSettings = onSettings,
        upcomingTracks = upcomingQueue,
        onSelectUpcomingTrack = { track -> viewModel.onRowClick(track, upcomingQueue) },
    )
}

@Composable
fun NowPlayingContent(
    state: PlaybackState,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    onBack: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onLyrics: () -> Unit = {},
    onSettings: () -> Unit = {},
    upcomingTracks: List<Track> = emptyList(),
    onSelectUpcomingTrack: (Track) -> Unit = {},
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    val position = state.positionMs.coerceIn(0L, duration)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Üst başlıq (Header)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = state.track != null,
                    modifier = Modifier.testTag("favorite_button"),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Favorite active" else "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 2. Albom Örtüyü (Artwork)
        TrackCover(
            track = state.track,
            size = AppDimens.CoverSizeLarge,
            shape = AppShapes.large,
            contentDescription = "Album art placeholder",
        )

        // 3. Shuffle & Repeat Sırası
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCycleRepeatMode) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat ${state.repeatMode.name.lowercase()}",
                    tint = if (state.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 4. Pill Progress Slider & Timestamps
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = position.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..sliderMax,
                enabled = duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatPlaybackTime(position),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatRemainingTime(position, duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 5. Mahnı Adı və İfaçı
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.TabSpacing),
        ) {
            Text(
                text = state.track?.title ?: "No track selected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = state.track?.artist.orEmpty(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        // 6. Əsas Nəqliyyat Düymələri (Transport Controls)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.PlayerControlSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(AppDimens.LargeIconControlSize),
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(AppDimens.TransportIconSize),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(AppDimens.PlayPauseButtonSize),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(AppDimens.PlayPauseIconSize),
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(AppDimens.LargeIconControlSize),
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(AppDimens.TransportIconSize),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 7. Zərif Düymələr/Tablar (Lyrics, EQ, Sleep Timer, Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onLyrics, modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lyrics",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onEqualizer, modifier = Modifier.weight(1f)) {
                Text(
                    text = "EQ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = onSleepTimer,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = AppDimens.PlayerActionHorizontalPadding),
            ) {
                Text(
                    text = "Sleep Timer",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // 8. Aşağı Bölmə (PLAYING NEXT növbə siyahısı)
        val filteredQueue = upcomingTracks.filter { it.id != state.track?.id }
        if (state.track != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PLAYING NEXT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filteredQueue.isNotEmpty()) {
                    filteredQueue.take(5).forEach { track ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectUpcomingTrack(track) },
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AppDimens.QueueItemPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                            ) {
                                TrackCover(
                                    track = track,
                                    size = AppDimens.CoverSizeSmall,
                                    shape = AppShapes.small,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.Menu,
                                    contentDescription = "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatRemainingTime(positionMs: Long, durationMs: Long): String {
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    val totalSeconds = remainingMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "-$minutes:${seconds.toString().padStart(2, '0')}"
}
