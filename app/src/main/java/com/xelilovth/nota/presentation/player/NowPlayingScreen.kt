package com.xelilovth.nota.presentation.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onEqualizer: () -> Unit,
    onSleepTimer: () -> Unit,
    onLyrics: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Now Playing", style = MaterialTheme.typography.headlineMedium)
        }
        Text(state.track?.title ?: "No track selected", style = MaterialTheme.typography.headlineSmall)
        Text(state.track?.artist.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.durationMs > 0L) {
            Slider(
                value = state.positionMs.coerceIn(0L, state.durationMs).toFloat(),
                onValueChange = { viewModel.seek(it.toLong()) },
                valueRange = 0f..state.durationMs.toFloat(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = viewModel::next) { Icon(Icons.Filled.SkipNext, "Next") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = viewModel::toggleShuffle) { Text(if (state.shuffle) "Shuffle ON" else "Shuffle OFF") }
            Button(onClick = viewModel::cycleRepeatMode) { Text("Repeat ${state.repeatMode.name}") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onEqualizer) { Text("Equalizer") }
            Button(onClick = onSleepTimer) { Text("Sleep Timer") }
            Button(onClick = onLyrics) { Text("Lyrics") }
        }
    }
}
