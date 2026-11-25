package com.xelilovth.nota.presentation.player

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xelilovth.nota.R
import com.xelilovth.nota.domain.model.LyricLine
import com.xelilovth.nota.domain.model.LyricsSyncType
import com.xelilovth.nota.domain.model.TrackLyrics
import kotlinx.coroutines.delay

@Composable
internal fun LyricsPanel(
    state: LyricsUiState,
    positionMs: Long,
    isPlaying: Boolean,
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
                isPlaying = isPlaying,
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
    isPlaying: Boolean,
) {
    val lines = lyrics.lines
    var smoothPositionMs by remember(lyrics, positionMs) { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, lyrics) {
        val anchorRealtimeMs = SystemClock.elapsedRealtime()
        smoothPositionMs = positionMs
        if (isPlaying) {
            while (true) {
                delay(32L)
                smoothPositionMs = positionMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs).coerceAtLeast(0L)
            }
        }
    }

    val activeLineIndex = if (lyrics.syncType == LyricsSyncType.PLAIN) {
        -1
    } else {
        lines.indexOfLast { line ->
            val start = line.startMs
            start != null && start <= smoothPositionMs &&
                (line.endMs == null || smoothPositionMs < line.endMs)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeLineIndex, lyrics.syncType) {
        if (activeLineIndex >= 0) {
            listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            val isActive = index == activeLineIndex
            val alphaTarget = when {
                lyrics.syncType == LyricsSyncType.PLAIN -> 0.72f
                isActive -> 1f
                else -> 0.27f
            }
            val alpha by animateFloatAsState(alphaTarget, label = "lyric-line-alpha")
            val color by animateColorAsState(
                MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                label = "lyric-line-color",
            )
            if (isActive && lyrics.syncType == LyricsSyncType.WORD && line.words.isNotEmpty()) {
                SyncedWordLine(
                    line = line,
                    positionMs = smoothPositionMs,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                )
            } else {
                Text(
                    text = line.text,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    color = color,
                    fontSize = when {
                        lyrics.syncType == LyricsSyncType.PLAIN -> 19.sp
                        isActive -> 23.sp
                        else -> 18.sp
                    },
                    lineHeight = 32.sp,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedWordLine(
    line: LyricLine,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val activeWordIndex = line.words.indices.lastOrNull { index ->
        val word = line.words[index]
        val nextWordStart = line.words.getOrNull(index + 1)?.startMs
        word.startMs <= positionMs &&
            (word.endMs ?: nextWordStart ?: line.endMs)?.let { positionMs < it } != false
    } ?: -1

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        line.words.forEachIndexed { index, word ->
            val isCurrentWord = index == activeWordIndex
            val wordColor by animateColorAsState(
                targetValue = if (isCurrentWord) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                animationSpec = tween(durationMillis = 170),
                label = "lyric-word-color",
            )
            val backgroundAlpha by animateFloatAsState(
                targetValue = if (isCurrentWord) 0.17f else 0f,
                animationSpec = tween(durationMillis = 170),
                label = "lyric-word-highlight",
            )
            val wordSize by animateFloatAsState(
                targetValue = if (isCurrentWord) 22f else 20f,
                animationSpec = tween(durationMillis = 170),
                label = "lyric-word-size",
            )
            Text(
                text = word.text,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = backgroundAlpha),
                        shape = RoundedCornerShape(5.dp),
                    )
                    .padding(horizontal = 1.dp),
                color = wordColor,
                fontSize = wordSize.sp,
                lineHeight = 32.sp,
                fontWeight = if (isCurrentWord) FontWeight.ExtraBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun LyricsSwipeHint(modifier: Modifier = Modifier) {
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
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Swipe up to show lyrics",
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        translationY = -progress * 7.dp.toPx()
                        alpha = 0.58f + progress * 0.42f
                    },
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
