package com.xelilovth.nota.presentation.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.PlaybackState
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.player.PlayerViewModel
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.scan()
    }
    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.scan() else permissionLauncher.launch(permission)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !hasPermission -> PermissionRequired(permissionDenied) { permissionLauncher.launch(permission) }
                isScanning && tracks.isEmpty() -> CenteredMessage {
                    CircularProgressIndicator()
                    Text("Scanning your music library", modifier = Modifier.padding(top = AppDimens.ElementSpacing))
                }
                tracks.isEmpty() -> CenteredMessage {
                    Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
                    Text("Audio files on this device will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::scan, modifier = Modifier.padding(top = AppDimens.ElementSpacing)) {
                        Text("Scan again")
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    items(tracks, key = Track::id) { track -> TrackRow(track) { playerViewModel.play(track) } }
                }
            }
        }
        if (playbackState.track != null) PlaybackControls(playbackState, playerViewModel)
    }
}

@Composable
private fun PlaybackControls(state: PlaybackState, viewModel: PlayerViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(AppDimens.ScreenPadding)) {
        Text(state.track?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(state.track?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall)
        if (state.durationMs > 0L) {
            Slider(
                value = state.positionMs.coerceIn(0L, state.durationMs).toFloat(),
                onValueChange = { viewModel.seek(it.toLong()) },
                valueRange = 0f..state.durationMs.toFloat(),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.shuffle) "Shuffle on" else "Shuffle off", modifier = Modifier.padding(horizontal = AppDimens.ElementSpacing))
            IconButton(onClick = viewModel::previous) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play or pause")
            }
            IconButton(onClick = viewModel::next) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
            Text(state.repeatMode.name, modifier = Modifier.padding(horizontal = AppDimens.ElementSpacing))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
            Button(onClick = viewModel::toggleShuffle) { Text("Shuffle") }
            Button(onClick = viewModel::cycleRepeatMode) { Text("Repeat") }
        }
    }
}

@Composable
private fun PermissionRequired(denied: Boolean, onRetry: () -> Unit) {
    CenteredMessage {
        Text("Audio permission is required", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (denied) "Permission was denied. Allow access to scan local audio files."
            else "Allow access so Nota can find audio files on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = AppDimens.ElementSpacing)) { Text("Try again") }
    }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.ElementSpacing)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium)
            Text("${track.artist} · ${track.album}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(track.folder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${track.title}") }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
