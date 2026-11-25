package com.xelilovth.nota.presentation.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.xelilovth.nota.R
import com.xelilovth.nota.domain.model.LyricLine
import com.xelilovth.nota.domain.model.LyricsSyncType
import com.xelilovth.nota.domain.model.TrackLyrics

@Composable
internal fun LyricsPanel(
    state: LyricsUiState,
    positionMs: Long,
    onCollapse: () -> Unit,
    onOpenGenius: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    var dragDistance = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            dragDistance += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragDistance > 48.dp.toPx()) onCollapse()
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 5.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f), CircleShape),
            )
        }
        when {
            state.isLoading || (!state.notFound && state.lyrics == null) -> LoadingLyrics()
            state.lyrics?.instrumental == true -> EmptyLyrics(
                titleRes = R.string.lyrics_instrumental,
                subtitleRes = R.string.lyrics_instrumental_description,
                onOpenGenius = null,
            )
            state.lyrics == null -> EmptyLyrics(
                titleRes = R.string.lyrics_not_available,
                subtitleRes = R.string.lyrics_not_found_description,
                onOpenGenius = onOpenGenius,
            )
            else -> LyricsContent(
                lyrics = state.lyrics,
                positionMs = positionMs,
            )
        }
    }
}

@Composable
private fun LoadingLyrics() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.lyrics_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyLyrics(
    titleRes: Int,
    subtitleRes: Int,
    onOpenGenius: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(subtitleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onOpenGenius != null) {
            TextButton(onClick = onOpenGenius) {
                Text(stringResource(R.string.lyrics_search_genius))
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lyrics: TrackLyrics,
    positionMs: Long,
) {
    val lines = lyrics.lines
    val activeLineIndex = if (lyrics.syncType == LyricsSyncType.PLAIN) {
        -1
    } else {
        lines.indexOfLast { it.startMs != null && it.startMs <= positionMs }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeLineIndex, lyrics.syncType) {
        if (activeLineIndex >= 0) listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.lyrics_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                when (lyrics.syncType) {
                    LyricsSyncType.WORD -> stringResource(R.string.lyrics_word_synced, lyrics.source.label)
                    LyricsSyncType.LINE -> stringResource(R.string.lyrics_line_synced, lyrics.source.label)
                    LyricsSyncType.PLAIN -> stringResource(R.string.lyrics_plain, lyrics.source.label)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val isActive = index == activeLineIndex
                val alphaTarget = when {
                    lyrics.syncType == LyricsSyncType.PLAIN -> 0.88f
                    isActive -> 1f
                    else -> 0.34f
                }
                val alpha by animateFloatAsState(alphaTarget, label = "lyric-line-alpha")
                val color by animateColorAsState(
                    MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                    label = "lyric-line-color",
                )
                Text(
                    text = lyricText(line, isActive, lyrics.syncType, positionMs),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    color = color,
                    fontSize = when {
                        lyrics.syncType == LyricsSyncType.PLAIN -> 19.sp
                        isActive -> 22.sp
                        else -> 18.sp
                    },
                    lineHeight = 30.sp,
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

private fun lyricText(
    line: LyricLine,
    isActive: Boolean,
    syncType: LyricsSyncType,
    positionMs: Long,
) = buildAnnotatedString {
    if (isActive && syncType == LyricsSyncType.WORD && line.words.isNotEmpty()) {
        val activeWordIndex = line.words.indexOfLast { it.startMs <= positionMs }
        line.words.forEachIndexed { index, word ->
            if (index == activeWordIndex) {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(word.text) }
            } else {
                append(word.text)
            }
        }
    } else {
        append(line.text)
    }
}

@Composable
internal fun LyricsSwipeHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            stringResource(R.string.lyrics_swipe_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
            letterSpacing = 1.2.sp,
        )
    }
}
