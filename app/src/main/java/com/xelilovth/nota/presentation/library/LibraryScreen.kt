package com.xelilovth.nota.presentation.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.playlists.AddToPlaylistSheet
import com.xelilovth.nota.ui.components.GlassDropdownMenu
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding

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
    var playlistTargetTrack by remember { mutableStateOf<Track?>(null) }
    var editTargetTrack by remember { mutableStateOf<Track?>(null) }
    var deleteTargetTrack by remember { mutableStateOf<Track?>(null) }
    var pendingDeleteTrackId by remember { mutableStateOf<Long?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteTrackId?.let { viewModel.confirmTrackDeleted(it) }
        }
        pendingDeleteTrackId = null
    }

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
                    contentPadding = PaddingValues(
                        start = AppDimens.ScreenPadding,
                        top = AppDimens.ScreenPadding,
                        end = AppDimens.ScreenPadding,
                        bottom = LocalBottomContentPadding.current,
                    ),
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
                                    GlassDropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Title") },
                                            trailingIcon = {
                                                if (sortOrder == LibrarySort.TITLE) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                                }
                                            },
                                            onClick = {
                                                viewModel.setSortOrder(LibrarySort.TITLE)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Artist") },
                                            trailingIcon = {
                                                if (sortOrder == LibrarySort.ARTIST) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                                }
                                            },
                                            onClick = {
                                                viewModel.setSortOrder(LibrarySort.ARTIST)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Duration") },
                                            trailingIcon = {
                                                if (sortOrder == LibrarySort.DURATION) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                                }
                                            },
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
                            onAddToPlaylist = { playlistTargetTrack = track },
                            onEditInfo = { editTargetTrack = track },
                            onDeleteFromDevice = { deleteTargetTrack = track },
                        )
                    }
                }
            }
        }
    }

    playlistTargetTrack?.let { target ->
        AddToPlaylistSheet(
            track = target,
            onDismiss = { playlistTargetTrack = null },
        )
    }

    editTargetTrack?.let { target ->
        var titleInput by remember(target.id) { mutableStateOf(target.title) }
        var artistInput by remember(target.id) { mutableStateOf(target.artist) }
        var albumInput by remember(target.id) { mutableStateOf(target.album) }
        Dialog(onDismissRequest = { editTargetTrack = null }) {
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
                    Text("Edit track info", style = MaterialTheme.typography.titleLarge)
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        subtle = true,
                    ) {
                        TextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Title") },
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
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        subtle = true,
                    ) {
                        TextField(
                            value = artistInput,
                            onValueChange = { artistInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Artist") },
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
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        subtle = true,
                    ) {
                        TextField(
                            value = albumInput,
                            onValueChange = { albumInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Album") },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.ElementSpacing),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.End),
                    ) {
                        TextButton(onClick = { editTargetTrack = null }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.editTrackMetadata(target.id, titleInput, artistInput, albumInput)
                                editTargetTrack = null
                            },
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    deleteTargetTrack?.let { target ->
        Dialog(onDismissRequest = { deleteTargetTrack = null }) {
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
                        "Delete \"${target.title}\" permanently from your device storage?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.End),
                    ) {
                        TextButton(onClick = { deleteTargetTrack = null }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                deleteTargetTrack = null
                                viewModel.deleteTrack(target) { res ->
                                    when (res) {
                                        is DeleteTrackResult.Deleted -> {
                                            Toast.makeText(context, "Track deleted", Toast.LENGTH_SHORT).show()
                                        }
                                        is DeleteTrackResult.RequiresPermission -> {
                                            pendingDeleteTrackId = target.id
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
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditInfo: () -> Unit,
    onDeleteFromDevice: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        subtle = true,
        onClick = onPlay,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.CardPadding,
                    top = AppDimens.RowVerticalPadding,
                    end = AppDimens.ElementSpacing,
                    bottom = AppDimens.RowVerticalPadding,
                ),
        ) {
            TrackArtwork(
                trackId = track.id,
                trackUri = track.uri,
                modifier = Modifier.size(AppDimens.ArtworkSmallSize),
            )
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
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Track options",
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                }
                GlassDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit info") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEditInfo()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete from device") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteFromDevice()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = LocalBottomContentPadding.current,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevated = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.CardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                content = content,
            )
        }
    }
}
