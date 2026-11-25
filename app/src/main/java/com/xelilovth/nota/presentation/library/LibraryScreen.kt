package com.xelilovth.nota.presentation.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    activeTrackId: Long? = null,
    isPlaying: Boolean = false,
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortedTracks = remember(tracks, sortOrder) {
        when (sortOrder) {
            LibrarySort.TITLE -> tracks.sortedBy { it.title.lowercase() }
            LibrarySort.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            LibrarySort.DURATION -> tracks.sortedByDescending { it.durationMs }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.scan()
    }
    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.scanIfEmpty() else permissionLauncher.launch(permission)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            !hasPermission -> CenteredMessage {
                Text("Audio permission is required", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (permissionDenied) "Permission was denied. Allow access to scan local audio files."
                    else "Allow access so Nota can find audio files on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { permissionLauncher.launch(permission) }, modifier = Modifier.padding(top = AppDimens.ElementSpacing)) {
                    Text("Try again")
                }
            }
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
            else -> {
                val listState = rememberLazyListState()
                val onPlayTrack: (Track) -> Unit = remember(viewModel, sortedTracks, onPlayWithNotificationPermission) {
                    { track -> onPlayWithNotificationPermission { viewModel.onTrackSelected(track, sortedTracks) } }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    item(key = "library_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.ElementSpacing),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Library", style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    "${sortedTracks.size} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onPlayWithNotificationPermission { viewModel.playShuffled(sortedTracks) } },
                                ) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle library")
                                }
                                Box {
                                    IconButton(onClick = { sortMenuExpanded = true }) {
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Title") },
                                            onClick = {
                                                viewModel.setSortOrder(LibrarySort.TITLE)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Artist") },
                                            onClick = {
                                                viewModel.setSortOrder(LibrarySort.ARTIST)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Duration") },
                                            onClick = {
                                                viewModel.setSortOrder(LibrarySort.DURATION)
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                                IconButton(onClick = viewModel::scan, enabled = !isScanning) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Rescan library")
                                }
                            }
                        }
                    }
                    items(sortedTracks, key = Track::id, contentType = { "track" }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == activeTrackId,
                            isPlaying = track.id == activeTrackId && isPlaying,
                            onPlay = { onPlayTrack(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = AppDimens.RowVerticalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${track.artist} · ${track.album}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent && isPlaying) {
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

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
