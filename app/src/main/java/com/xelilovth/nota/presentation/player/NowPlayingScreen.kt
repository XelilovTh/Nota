package com.xelilovth.nota.presentation.player

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.xelilovth.nota.R
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.RepeatMode
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.playlists.AddToPlaylistSheet
import com.xelilovth.nota.ui.components.AudioWaveformVisualizer
import com.xelilovth.nota.ui.components.GlassBottomSheet
import com.xelilovth.nota.ui.components.GlassDropdownMenu
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.OfflineDownloadDialog
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors
import com.xelilovth.nota.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val ARC_START_ANGLE = 135f
private const val ARC_SWEEP_ANGLE = 270f

private enum class PendingGeminiAction {
    TRANSCRIBE,
    TRANSLATE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    viewModel: PlayerViewModel,
    sleepTimerRemainingSeconds: Long? = null,
    onBack: () -> Unit,
    onSleepTimer: () -> Unit,
    onSettings: () -> Unit,
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: (Float) -> Unit = {},
    onVerticalDragCancel: () -> Unit = {},
    currentDragOffsetPx: () -> Float = { 0f },
) {
    val queueTracks = state.queue
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.positionMs.coerceIn(0L, duration)
    val playerProgress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var pendingSeekProgress by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(position, isDragging) {
        if (!isDragging && pendingSeekProgress != null) {
            kotlinx.coroutines.delay(180L)
            pendingSeekProgress = null
        }
    }
    val displayProgress = when {
        isDragging -> dragProgress
        pendingSeekProgress != null -> pendingSeekProgress!!
        else -> playerProgress
    }
    val displayPositionMs = when {
        (isDragging || pendingSeekProgress != null) && duration > 0L -> (displayProgress * duration).toLong()
        else -> position
    }

    var showRemaining by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    val lyricsUiState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val groqApiKey by viewModel.groqApiKey.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedOnlineIds by viewModel.downloadedOnlineIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var pendingGeminiAction by remember { mutableStateOf<PendingGeminiAction?>(null) }

    LaunchedEffect(state.track?.id) {
        val track = state.track
        if (track != null) viewModel.loadLyrics(track) else lyricsExpanded = false
    }
    DisposableEffect(lyricsExpanded) {
        viewModel.setLyricsSyncActive(lyricsExpanded)
        onDispose { viewModel.setLyricsSyncActive(false) }
    }
    val lrcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            state.track?.let { viewModel.importLocalLyrics(it, uri.toString()) }
        }
    }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showAiProviderChooser by remember { mutableStateOf(false) }
    var selectedAiProvider by remember { mutableStateOf(AiLyricsProvider.GEMINI) }
    var pendingDeleteTrackId by remember { mutableStateOf<Long?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteTrackId?.let { id ->
                viewModel.confirmTrackDeleted(id)
                Toast.makeText(context, "Track deleted", Toast.LENGTH_SHORT).show()
                if (queueTracks.size > 1) {
                    viewModel.next()
                } else {
                    viewModel.dismiss()
                    onBack()
                }
            }
        }
        pendingDeleteTrackId = null
    }

    val playerHazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(state = playerHazeState)
                .pointerInput(lyricsExpanded) {
                if (!lyricsExpanded) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || currentDragOffsetPx() > 0f) {
                                onVerticalDrag(dragAmount)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            onVerticalDragEnd(AppDimens.SwipeDismissThreshold.toPx())
                        },
                        onDragCancel = {
                            onVerticalDragCancel()
                        },
                    )
                }
            }
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
                val canSaveOffline = state.track.isOnline || state.track.onlineId != null
                val isDownloaded = state.track.onlineId != null && state.track.onlineId in downloadedOnlineIds
                val progress = downloadProgress[state.track.id]
                if (canSaveOffline) {
                    IconButton(
                        enabled = progress == null,
                        onClick = {
                            if (isDownloaded) {
                                Toast.makeText(context, "Already saved for offline listening", Toast.LENGTH_SHORT).show()
                            } else {
                                showDownloadDialog = true
                            }
                        },
                    ) {
                        if (progress != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AppDimens.IconSizeSmall),
                                strokeWidth = AppDimens.BorderWidth,
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.DownloadForOffline,
                                contentDescription = if (isDownloaded) "Saved offline" else "Save for offline",
                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val isFavorite = state.track.id in favoriteTrackIds
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleFavorite(state.track.id)
                }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showTrackMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    GlassDropdownMenu(
                        expanded = showTrackMenu,
                        onDismissRequest = { showTrackMenu = false },
                    ) {
                        if ((state.track.isOnline || state.track.onlineId != null) && !isDownloaded && progress == null) {
                            DropdownMenuItem(
                                text = { Text("Save for offline") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DownloadForOffline, contentDescription = null)
                                },
                                onClick = {
                                    showTrackMenu = false
                                    showDownloadDialog = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                showTrackMenu = false
                                showAddToPlaylist = true
                            },
                        )
                        if (!state.track.isOnline && state.track.onlineId == null) {
                            DropdownMenuItem(
                                text = { Text("Delete from device") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    showTrackMenu = false
                                    showDeleteConfirm = true
                                },
                            )
                        } else if (isDownloaded) {
                            DropdownMenuItem(
                                text = { Text("Remove offline download") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    showTrackMenu = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(modifier = Modifier.weight(if (lyricsExpanded) 0.04f else 0.22f))

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

        AnimatedVisibility(
            visible = !lyricsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                        "·",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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

                Spacer(modifier = Modifier.height(AppDimens.ElementSpacing))
                AudioWaveformVisualizer(isPlaying = state.isPlaying)
                Spacer(modifier = Modifier.height(AppDimens.ElementSpacing))

                CircularArcPlayerArt(
                    track = state.track,
                    progress = displayProgress,
                    enabled = duration > 0L,
                    onProgressChange = { next ->
                        isDragging = true
                        dragProgress = next
                    },
                    onSeekFinish = { finalProgress ->
                        pendingSeekProgress = finalProgress
                        isDragging = false
                        if (duration > 0L) {
                            viewModel.seek((finalProgress * duration).toLong())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(AppDimens.PlayerArtworkFraction).aspectRatio(1f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (lyricsExpanded) 0.72f else 0.28f)
                .pointerInput(lyricsExpanded, state.track?.id) {
                    if (!lyricsExpanded && state.track != null) {
                        var upwardDrag = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount < 0f && currentDragOffsetPx() == 0f) {
                                    upwardDrag += dragAmount
                                } else if (dragAmount > 0f || currentDragOffsetPx() > 0f) {
                                    upwardDrag = 0f
                                    onVerticalDrag(dragAmount)
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (currentDragOffsetPx() > 0f) {
                                    onVerticalDragEnd(AppDimens.SwipeDismissThreshold.toPx())
                                } else if (upwardDrag < -AppDimens.LyricsDragThreshold.toPx()) {
                                    lyricsExpanded = true
                                }
                                upwardDrag = 0f
                            },
                            onDragCancel = {
                                upwardDrag = 0f
                                if (currentDragOffsetPx() > 0f) {
                                    onVerticalDragCancel()
                                }
                            },
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = lyricsExpanded,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn() + slideInVertically { it / 5 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 5 })
                    } else {
                        (fadeIn() + slideInVertically { -it / 5 }) togetherWith
                            (fadeOut() + slideOutVertically { it / 5 })
                    }
                },
                label = "player-lyrics-transition",
            ) { expanded ->
                if (expanded) {
                    LyricsPanel(
                        state = lyricsUiState,
                        positionMs = position,
                        isPlaying = state.isPlaying,
                        translationLanguage = translationLanguage,
                        onCollapse = { lyricsExpanded = false },
                        onSeekTo = viewModel::seek,
                        onAdjustOffset = { delta ->
                            state.track?.let { viewModel.adjustLyricsOffset(it.id, delta) }
                        },
                        onImportLyrics = {
                            lrcLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        },
                        onGenerateWithGemini = {
                            if (state.track != null) {
                                showAiProviderChooser = true
                            }
                        },
                        onDeleteLyrics = {
                            state.track?.let { viewModel.deleteLyrics(it.id) }
                        },
                        onToggleTranslation = {
                            state.track?.let { viewModel.toggleLyricsTranslation(it) }
                        },
                        onSelectTranslationLanguage = { language ->
                            viewModel.setTranslationLanguage(language, state.track)
                        },
                        durationMs = duration,
                        onFetchOnlineLyrics = {
                            state.track?.let { viewModel.retryOnlineLyrics(it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = AppDimens.LyricsPanelMaxHeight),
                    )
                } else {
                    LyricsSwipeHint(
                        onExpand = { lyricsExpanded = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(visible = lyricsExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.height(AppDimens.ElementSpacing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text(
                        text = formatDuration(displayPositionMs),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = displayProgress,
                        onValueChange = { next ->
                            isDragging = true
                            dragProgress = next
                        },
                        onValueChangeFinished = {
                            pendingSeekProgress = dragProgress
                            isDragging = false
                            if (duration > 0L) {
                                viewModel.seek((dragProgress * duration).toLong())
                            }
                        },
                        enabled = duration > 0L,
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(AppDimens.LyricsScrubberHeight),
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.SectionSpacing))
            }
        }

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
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.previous()
                }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(AppDimens.IconSizeLarge))
                }
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.togglePlayPause()
                    },
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
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.next()
                }) {
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

        Spacer(modifier = Modifier.height(AppDimens.PlayerControlsBottomSpacing))

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

        Spacer(modifier = Modifier.height(AppDimens.SectionSpacing))
        }

        CompositionLocalProvider(LocalHazeState provides playerHazeState) {
            GlassBottomSheet(
                visible = showQueueSheet,
                onDismissRequest = { showQueueSheet = false },
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
            AddToPlaylistSheet(
                track = if (showAddToPlaylist) state.track else null,
                onDismiss = { showAddToPlaylist = false },
            )
        }
    }

    val activeTrack = state.track

    if (showDeleteConfirm && activeTrack != null) {
        Dialog(onDismissRequest = { showDeleteConfirm = false }) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevated = true,
                blurBackdrop = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
                ) {
                    Text("Delete from device", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Delete \"${activeTrack.title}\" permanently from your device storage?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.End),
                    ) {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.deleteTrack(activeTrack.id) { res ->
                                    when (res) {
                                        is DeleteTrackResult.Deleted -> {
                                            Toast.makeText(context, "Track deleted", Toast.LENGTH_SHORT).show()
                                            if (queueTracks.size > 1) {
                                                viewModel.next()
                                            } else {
                                                viewModel.dismiss()
                                                onBack()
                                            }
                                        }
                                        is DeleteTrackResult.RequiresPermission -> {
                                            pendingDeleteTrackId = activeTrack.id
                                            deleteLauncher.launch(
                                                IntentSenderRequest.Builder(res.intentSender).build(),
                                            )
                                        }
                                        is DeleteTrackResult.Failed -> {
                                            Toast.makeText(context, "Could not delete track", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDownloadDialog) {
        state.track?.let { target ->
            OfflineDownloadDialog(
                onDismiss = { showDownloadDialog = false },
                onSelectLocation = { location ->
                    showDownloadDialog = false
                    viewModel.downloadTrackForOffline(target, location) { result ->
                        val msg = if (result.isSuccess) {
                            "Saved for offline listening"
                        } else {
                            "Offline download failed"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    if (showAiProviderChooser) {
        val track = state.track
        Dialog(onDismissRequest = { showAiProviderChooser = false }) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevated = true,
                blurBackdrop = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Choose AI provider", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "If the selected provider fails, Nota will automatically fall back to the other if configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        Triple(AiLyricsProvider.GEMINI, "Gemini AI", geminiApiKey.isNotBlank()),
                        Triple(AiLyricsProvider.GROQ, "Groq Whisper", groqApiKey.isNotBlank()),
                    ).forEach { (provider, label, hasKey) ->
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            subtle = true,
                            onClick = {
                                showAiProviderChooser = false
                                selectedAiProvider = provider
                                if (track != null) {
                                    if (!hasKey) {
                                        pendingGeminiAction = PendingGeminiAction.TRANSCRIBE
                                        showGeminiKeyDialog = true
                                    } else {
                                        viewModel.transcribeWithProvider(track, provider)
                                    }
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (hasKey) "Configured" else "Add API key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showAiProviderChooser = false }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    if (showGeminiKeyDialog) {
        var selectedProvider by remember(selectedAiProvider) {
            mutableStateOf(if (selectedAiProvider == AiLyricsProvider.GROQ) 1 else 0)
        }
        var keyInput by remember(selectedProvider, geminiApiKey, groqApiKey) {
            mutableStateOf(if (selectedProvider == 0) geminiApiKey else groqApiKey)
        }
        Dialog(onDismissRequest = {
            showGeminiKeyDialog = false
            pendingGeminiAction = null
        }) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevated = true,
                blurBackdrop = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
                ) {
                    Text(
                        stringResource(R.string.lyrics_ai_keys_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        listOf("Gemini", "Groq").forEachIndexed { index, label ->
                            val isSelected = selectedProvider == index
                            GlassSurface(
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                elevated = isSelected,
                                subtle = !isSelected,
                                onClick = { selectedProvider = index },
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppDimens.RowVerticalPadding),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        subtle = true,
                    ) {
                        TextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    stringResource(
                                        if (selectedProvider == 0) R.string.lyrics_gemini_key_hint else R.string.lyrics_groq_key_hint,
                                    ),
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.End),
                    ) {
                        TextButton(onClick = {
                            showGeminiKeyDialog = false
                            pendingGeminiAction = null
                        }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val trimmedKey = keyInput.trim()
                                if (selectedProvider == 0) {
                                    viewModel.saveGeminiApiKey(trimmedKey)
                                } else {
                                    viewModel.saveGroqApiKey(trimmedKey)
                                }
                                showGeminiKeyDialog = false
                                val action = pendingGeminiAction
                                pendingGeminiAction = null
                                state.track?.let { track ->
                                    when (action) {
                                        PendingGeminiAction.TRANSLATE -> viewModel.toggleLyricsTranslation(track, trimmedKey)
                                        PendingGeminiAction.TRANSCRIBE, null -> {
                                            val provider = if (selectedProvider == 0) AiLyricsProvider.GEMINI else AiLyricsProvider.GROQ
                                            viewModel.transcribeWithProvider(track, provider, trimmedKey)
                                        }
                                    }
                                }
                            },
                            enabled = keyInput.isNotBlank(),
                        ) {
                            Text(
                                if (pendingGeminiAction == PendingGeminiAction.TRANSLATE) {
                                    "Save & Translate"
                                } else {
                                    "Save & Transcribe"
                                },
                            )
                        }
                    }
                }
            }
        }
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
                val strokePx = AppDimens.CircularSeekbarStroke.toPx()
                val thumbRadiusPx = AppDimens.CircularSeekbarThumbRadius.toPx()
                val hitSlopPx = AppDimens.CircularSeekbarHitSlop.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val inset = thumbRadiusPx + strokePx
                    val ringRadius = (min(w, h) - inset * 2f) / 2f
                    val dist = hypot(down.position.x - w / 2f, down.position.y - h / 2f)
                    if (dist < ringRadius - hitSlopPx || dist > ringRadius + hitSlopPx) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    var currentFraction = touchOffsetToArcProgress(down.position, w, h)
                    latestProgress = currentFraction
                    onProgressChange(currentFraction)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            onSeekFinish(latestProgress)
                            break
                        }
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                            currentFraction = touchOffsetToArcProgress(change.position, w, h)
                            latestProgress = currentFraction
                            onProgressChange(currentFraction)
                        }
                    }
                }
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
                artworkUrl = track?.artworkUrl,
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
