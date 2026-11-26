package com.xelilovth.nota.presentation.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.xelilovth.nota.R
import com.xelilovth.nota.domain.model.LyricLine
import com.xelilovth.nota.domain.model.LyricsSyncType
import com.xelilovth.nota.domain.model.TrackLyrics
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.verticalEdgeFade
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors
import java.util.Locale

private const val OFFSET_STEP_MS = 500L

@Composable
internal fun LyricsPanel(
    state: LyricsUiState,
    positionMs: Long,
    isPlaying: Boolean,
    onCollapse: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onOpenGenius: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showOffsetControls = state.lyrics != null &&
        !state.lyrics.instrumental &&
        state.lyrics.syncType != LyricsSyncType.PLAIN

    GlassSurface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        elevated = false,
        blurBackdrop = false,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LyricsHeader(
                syncOffsetMs = state.syncOffsetMs,
                showOffsetControls = showOffsetControls,
                onCollapse = onCollapse,
                onAdjustOffset = onAdjustOffset,
                onImportLyrics = onImportLyrics,
            )

            when {
                state.isLoading || (!state.notFound && state.lyrics == null) -> LoadingLyrics()
                state.lyrics?.instrumental == true -> EmptyLyrics(
                    titleRes = R.string.lyrics_instrumental,
                    subtitleRes = R.string.lyrics_instrumental_description,
                    onImportLyrics = onImportLyrics,
                    onOpenGenius = null,
                )
                state.lyrics == null -> EmptyLyrics(
                    titleRes = R.string.lyrics_not_available,
                    subtitleRes = R.string.lyrics_not_found_description,
                    onImportLyrics = onImportLyrics,
                    onOpenGenius = onOpenGenius,
                )
                else -> LyricsContent(
                    lyrics = state.lyrics,
                    positionMs = positionMs,
                    syncOffsetMs = state.syncOffsetMs,
                    isPlaying = isPlaying,
                    onSeekTo = onSeekTo,
                )
            }
        }
    }
}

@Composable
private fun LyricsHeader(
    syncOffsetMs: Long,
    showOffsetControls: Boolean,
    onCollapse: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onImportLyrics: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.LyricsHeaderHeight)
            .padding(horizontal = AppDimens.ElementSpacing)
            .pointerInput(Unit) {
                var dragDistance = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragDistance > AppDimens.LyricsDragThreshold.toPx()) onCollapse()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(AppDimens.LyricsToolbarButtonSize),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Collapse lyrics",
                modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = AppDimens.LyricsHandleWidth, height = AppDimens.LyricsHandleThickness)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f), CircleShape),
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ShapeExtraSmall),
        ) {
            if (showOffsetControls) {
                IconButton(
                    onClick = { onAdjustOffset(-OFFSET_STEP_MS) },
                    modifier = Modifier.size(AppDimens.LyricsToolbarButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Decrease lyrics sync offset",
                        modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatSyncOffset(syncOffsetMs),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = { onAdjustOffset(OFFSET_STEP_MS) },
                    modifier = Modifier.size(AppDimens.LyricsToolbarButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Increase lyrics sync offset",
                        modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onImportLyrics,
                modifier = Modifier.size(AppDimens.LyricsToolbarButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.UploadFile,
                    contentDescription = "Import .lrc",
                    modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatSyncOffset(syncOffsetMs: Long): String {
    val seconds = syncOffsetMs / 1000f
    return if (syncOffsetMs > 0L) {
        String.format(Locale.US, "+%.1fs", seconds)
    } else {
        String.format(Locale.US, "%.1fs", seconds)
    }
}

@Composable
private fun LoadingLyrics() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            strokeWidth = AppDimens.MiniPlayerProgressHeight,
            modifier = Modifier.size(AppDimens.LyricsLoadingSpinnerSize),
        )
        Spacer(Modifier.height(AppDimens.SectionSpacing))
        Text(stringResource(R.string.lyrics_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyLyrics(
    titleRes: Int,
    subtitleRes: Int,
    onImportLyrics: () -> Unit,
    onOpenGenius: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.LyricsEmptyPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.LyricsEmptyIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(AppDimens.SectionSpacing))
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(AppDimens.ElementSpacing))
        Text(
            stringResource(subtitleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppDimens.ElementSpacing))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onImportLyrics) {
                Text(stringResource(R.string.lyrics_import_lrc))
            }
            if (onOpenGenius != null) {
                TextButton(onClick = onOpenGenius) {
                    Text(stringResource(R.string.lyrics_search_genius))
                }
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lyrics: TrackLyrics,
    positionMs: Long,
    syncOffsetMs: Long,
    @Suppress("UNUSED_PARAMETER") isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    val lines = lyrics.lines
    val effectivePositionMs = (positionMs + syncOffsetMs).coerceAtLeast(0L)

    val activeLineIndex = if (lyrics.syncType == LyricsSyncType.PLAIN) {
        -1
    } else {
        lines.indexOfLast { line ->
            val start = line.startMs
            start != null && start <= effectivePositionMs &&
                (line.endMs == null || effectivePositionMs < line.endMs)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            val centerOffset = (listState.layoutInfo.viewportSize.height / 3).coerceAtLeast(0)
            listState.animateScrollToItem(activeLineIndex, scrollOffset = -centerOffset)
        }
    }

    val isLightGlass = LocalGlassColors.current.subtleRowBorder

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .verticalEdgeFade(AppDimens.LyricsEdgeFadeHeight),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = AppDimens.LyricsContentHorizontalPadding,
            vertical = AppDimens.LyricsContentVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.LyricsLineSpacing),
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            val isActive = index == activeLineIndex
            val clickableModifier = if (lyrics.syncType != LyricsSyncType.PLAIN && line.startMs != null) {
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        line.startMs.let { start ->
                            onSeekTo((start - syncOffsetMs).coerceAtLeast(0L))
                        }
                    }
                    .padding(vertical = AppDimens.ShapeExtraSmall)
            } else {
                Modifier
            }

            if (isActive && lyrics.syncType == LyricsSyncType.WORD && line.words.isNotEmpty()) {
                SyncedWordLine(
                    line = line,
                    effectivePositionMs = effectivePositionMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(clickableModifier),
                )
            } else {
                val alphaTarget = when {
                    lyrics.syncType == LyricsSyncType.PLAIN -> 0.72f
                    isActive -> 1f
                    isLightGlass -> 0.45f
                    else -> 0.32f
                }
                val alpha by animateFloatAsState(
                    targetValue = alphaTarget,
                    animationSpec = tween(durationMillis = 150),
                    label = "lyric-line-alpha",
                )
                Text(
                    text = line.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(clickableModifier),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    fontSize = when {
                        lyrics.syncType == LyricsSyncType.PLAIN -> AppDimens.LyricsPlainFontSize
                        isActive -> AppDimens.LyricsActiveLineFontSize
                        else -> AppDimens.LyricsInactiveLineFontSize
                    },
                    lineHeight = AppDimens.LyricsLineHeight,
                    textAlign = TextAlign.Center,
                    fontWeight = when {
                        isActive && lyrics.syncType == LyricsSyncType.LINE -> FontWeight.Bold
                        isActive -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                )
            }
        }
    }
}

@Composable
private fun SyncedWordLine(
    line: LyricLine,
    effectivePositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val activeWordIndex = line.words.indices.lastOrNull { index ->
        val word = line.words[index]
        val nextWordStart = line.words.getOrNull(index + 1)?.startMs
        word.startMs <= effectivePositionMs &&
            (word.endMs ?: nextWordStart ?: line.endMs)?.let { effectivePositionMs < it } != false
    } ?: -1

    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val upcomingAlpha = if (LocalGlassColors.current.subtleRowBorder) 0.45f else 0.35f

    val annotatedLine = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            val style = when {
                index < activeWordIndex -> SpanStyle(
                    color = onSurface.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
                index == activeWordIndex -> SpanStyle(
                    color = primary,
                    fontWeight = FontWeight.ExtraBold,
                )
                else -> SpanStyle(
                    color = onSurface.copy(alpha = upcomingAlpha),
                    fontWeight = FontWeight.Medium,
                )
            }
            withStyle(style) {
                append(word.text.trim())
            }
        }
    }

    Text(
        text = annotatedLine,
        modifier = modifier,
        fontSize = AppDimens.LyricsWordFontSize,
        lineHeight = AppDimens.LyricsLineHeight,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun LyricsSwipeHint(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "lyrics-swipe-arrow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "lyrics-swipe-arrow-motion",
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.ShapeExtraSmall),
            modifier = Modifier.padding(bottom = AppDimens.ShapeExtraSmall),
        ) {
            GlassSurface(
                modifier = Modifier.size(AppDimens.LyricsSwipeHintSize),
                shape = CircleShape,
                subtle = true,
                blurBackdrop = true,
                onClick = onExpand,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Swipe up to show lyrics",
                        modifier = Modifier
                            .size(AppDimens.LyricsSwipeArrowSize)
                            .graphicsLayer {
                                translationY = -progress * AppDimens.LyricsSwipeTravel.toPx()
                                alpha = 0.58f + progress * 0.42f
                            },
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
