package com.xelilovth.nota.presentation.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onPlayWithNotificationPermission: (() -> Unit) -> Unit = {},
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
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.scan()
    }
    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.scan() else permissionLauncher.launch(permission)
    }

    LibraryContent(
        tracks = tracks,
        hasPermission = hasPermission,
        isScanning = isScanning,
        permissionDenied = permissionDenied,
        favoriteTrackIds = favoriteTrackIds,
        onTrackClick = { track -> onPlayWithNotificationPermission { viewModel.onTrackSelected(track) } },
        onScanAgain = viewModel::scan,
        onRequestPermission = { permissionLauncher.launch(permission) },
        onToggleFavorite = viewModel::toggleFavorite,
        onAddToPlaylist = { trackToAddToPlaylist = it },
    )

    trackToAddToPlaylist?.let { targetTrack ->
        AddToPlaylistDialog(
            track = targetTrack,
            playlists = playlists,
            onDismiss = { trackToAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, targetTrack.id)
                trackToAddToPlaylist = null
            },
            onCreateAndAdd = { name ->
                viewModel.createPlaylistAndAddTrack(name, targetTrack.id)
                trackToAddToPlaylist = null
            },
        )
    }
}

@Composable
fun LibraryContent(
    tracks: List<Track>,
    hasPermission: Boolean = true,
    isScanning: Boolean = false,
    permissionDenied: Boolean = false,
    favoriteTrackIds: Set<Long> = emptySet(),
    onTrackClick: (Track) -> Unit = {},
    onScanAgain: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onToggleFavorite: (Long) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            !hasPermission -> CenteredMessage {
                Text("Audio permission is required", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (permissionDenied) "Permission was denied. Allow access to scan local audio files."
                    else "Allow access so Nota can find audio files on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestPermission, modifier = Modifier.padding(top = AppDimens.ElementSpacing)) {
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
                Button(onClick = onScanAgain, modifier = Modifier.padding(top = AppDimens.ElementSpacing)) {
                    Text("Scan again")
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(AppDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                items(tracks, key = Track::id) { track ->
                    val isFav = favoriteTrackIds.contains(track.id)
                    TrackRow(
                        track = track,
                        isFavorite = isFav,
                        onPlay = { onTrackClick(track) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = AppDimens.ElementSpacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${track.artist} · ${track.album}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(track.folder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                Icons.Outlined.PlaylistAdd,
                contentDescription = "Add to playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play ${track.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = AppDimens.ElementSpacing),
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(playlists.isEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                Text(
                    "Track: ${track.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isCreatingNew || playlists.isEmpty()) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "Choose a playlist:",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        items(playlists, key = Playlist::id) { playlist ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSelectPlaylist(playlist) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(AppDimens.ElementSpacing),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                                ) {
                                    Icon(
                                        Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "${playlist.trackCount} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { isCreatingNew = true },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("+ New Playlist")
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew || playlists.isEmpty()) {
                Button(
                    onClick = { onCreateAndAdd(newPlaylistName) },
                    enabled = newPlaylistName.isNotBlank(),
                ) {
                    Text("Create & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
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
