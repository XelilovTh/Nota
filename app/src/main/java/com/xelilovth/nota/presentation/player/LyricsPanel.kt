package com.xelilovth.nota.presentation.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
private val TRANSLATION_LANGUAGES = listOf("English", "Russian", "Azerbaijani", "Turkish")

@Composable
internal fun LyricsPanel(
    state: LyricsUiState,
    positionMs: Long,
    isPlaying: Boolean,
    translationLanguage: String,
    onCollapse: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onGenerateWithGemini: () -> Unit,
    onDeleteLyrics: () -> Unit,
    onToggleTranslation: () -> Unit,
    onSelectTranslationLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showOffsetControls = state.lyrics != null &&
        !state.lyrics.instrumental &&
        state.lyrics.syncType != LyricsSyncType.PLAIN
    val canTranslate = state.lyrics != null && !state.lyrics.instrumental

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
                hasLyrics = state.lyrics != null,
                canTranslate = canTranslate,
                showTranslation = state.showTranslation,
                isTranslating = state.isTranslating,
                translationLanguage = translationLanguage,
                onCollapse = onCollapse,
                onAdjustOffset = onAdjustOffset,
                onImportLyrics = onImportLyrics,
                onDeleteLyrics = onDeleteLyrics,
                onToggleTranslation = onToggleTranslation,
                onSelectTranslationLanguage = onSelectTranslationLanguage,
            )

            when {
                state.isLoading || (!state.notFound && state.lyrics == null) -> LoadingLyrics()
                state.lyrics?.instrumental == true -> EmptyLyrics(
                    titleRes = R.string.lyrics_instrumental,
                    subtitleRes = R.string.lyrics_instrumental_description,
                    errorMessage = state.errorMessage,
                    onImportLyrics = onImportLyrics,
                    onGenerateWithGemini = null,
                )
                state.lyrics == null -> EmptyLyrics(
                    titleRes = R.string.lyrics_not_available,
                    subtitleRes = R.string.lyrics_not_found_description,
                    errorMessage = state.errorMessage,
                    onImportLyrics = onImportLyrics,
                    onGenerateWithGemini = onGenerateWithGemini,
                )
                else -> LyricsContent(
                    lyrics = state.lyrics,
                    positionMs = positionMs,
                    syncOffsetMs = state.syncOffsetMs,
                    showTranslation = state.showTranslation,
                    isPlaying = isPlaying,
                    onSeekTo = onSeekTo,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricsHeader(
    syncOffsetMs: Long,
    showOffsetControls: Boolean,
    hasLyrics: Boolean,
    canTranslate: Boolean,
    showTranslation: Boolean,
    isTranslating: Boolean,
    translationLanguage: String,
    onCollapse: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onDeleteLyrics: () -> Unit,
    onToggleTranslation: () -> Unit,
    onSelectTranslationLanguage: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showLanguageMenu by remember { mutableStateOf(false) }
    Row(
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(AppDimens.LyricsToolbarButtonSize),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Collapse lyrics",
                modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
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
            if (canTranslate) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(AppDimens.LyricsToolbarButtonSize)
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = !isTranslating,
                                onClick = onToggleTranslation,
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showLanguageMenu = true
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                strokeWidth = AppDimens.MiniPlayerProgressHeight,
                                modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Translate,
                                contentDescription = "Translate lyrics",
                                modifier = Modifier.size(AppDimens.LyricsToolbarIconSize),
                                tint = if (showTranslation) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                    ) {
                        TRANSLATION_LANGUAGES.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                trailingIcon = if (language == translationLanguage) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(AppDimens.IconSizeSmall),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    showLanguageMenu = false
                                    onSelectTranslationLanguage(language)
                                },
                            )
                        }
                    }
                }
            }
            if (hasLyrics) {
                IconButton(
                    onClick = onDeleteLyrics,
                    modifier = Modifier.size(AppDimens.LyricsToolbarButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.lyrics_delete),
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
    errorMessage: String?,
    onImportLyrics: () -> Unit,
    onGenerateWithGemini: (() -> Unit)?,
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
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(AppDimens.ElementSpacing))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(AppDimens.SectionSpacing))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            if (onGenerateWithGemini != null) {
                Button(onClick = onGenerateWithGemini) {
                    Text(stringResource(R.string.lyrics_generate_gemini))
                }
            }
            TextButton(onClick = onImportLyrics) {
                Text(stringResource(R.string.lyrics_import_lrc))
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lyrics: TrackLyrics,
    positionMs: Long,
    syncOffsetMs: Long,
    showTranslation: Boolean,
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
            val layoutInfo = listState.layoutInfo
            val targetOffset = (layoutInfo.viewportSize.height / 3).coerceAtLeast(0)
            val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeLineIndex }
            if (visibleItem != null) {
                val delta = (visibleItem.offset - targetOffset).toFloat()
                if (kotlin.math.abs(delta) > 2f) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    )
                }
            } else {
                listState.animateScrollToItem(activeLineIndex, scrollOffset = -targetOffset)
            }
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

            val lineScale by animateFloatAsState(
                targetValue = if (isActive || lyrics.syncType == LyricsSyncType.PLAIN) 1.04f else 0.94f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "lyric-line-scale",
            )
            val scaleModifier = Modifier.graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(scaleModifier)
                    .then(clickableModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isActive && lyrics.syncType == LyricsSyncType.WORD && line.words.isNotEmpty()) {
                    SyncedWordLine(
                        line = line,
                        effectivePositionMs = effectivePositionMs,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val alphaTarget = when {
                        lyrics.syncType == LyricsSyncType.PLAIN -> 0.78f
                        isActive -> 1f
                        isLightGlass -> 0.45f
                        else -> 0.32f
                    }
                    val alpha by animateFloatAsState(
                        targetValue = alphaTarget,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        label = "lyric-line-alpha",
                    )
                    Text(
                        text = line.text,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        fontSize = when (lyrics.syncType) {
                            LyricsSyncType.PLAIN -> AppDimens.LyricsPlainFontSize
                            LyricsSyncType.WORD, LyricsSyncType.LINE -> AppDimens.LyricsWordFontSize
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
                if (showTranslation && !line.translatedText.isNullOrBlank()) {
                    Text(
                        text = line.translatedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.ShapeExtraSmall),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isActive) 0.9f else 0.55f,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
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
                    shadow = Shadow(
                        color = primary.copy(alpha = 0.42f),
                        blurRadius = 12f,
                    ),
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
