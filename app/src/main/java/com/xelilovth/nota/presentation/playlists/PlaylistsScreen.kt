package com.xelilovth.nota.presentation.playlists

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.PlaylistGroupType
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.ui.components.TrackCover
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onPlayWithNotificationPermission: (() -> Unit) -> Unit = {},
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()

    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedCollection by remember { mutableStateOf<AutoPlaylist?>(null) }

    val activePlaylist = selectedPlaylist?.let { sel -> playlists.firstOrNull { it.id == sel.id } }

    when {
        activePlaylist != null -> {
            CustomPlaylistDetail(
                playlist = activePlaylist,
                viewModel = viewModel,
                onBack = { selectedPlaylist = null },
                onPlayWithNotificationPermission = onPlayWithNotificationPermission,
            )
        }
        selectedCollection != null -> {
            CollectionDetail(
                group = selectedCollection!!,
                onBack = { selectedCollection = null },
                onPlayAll = { onPlayWithNotificationPermission { viewModel.playCollection(selectedCollection!!) } },
                onShuffle = { onPlayWithNotificationPermission { viewModel.playCollection(selectedCollection!!, shuffle = true) } },
                onTrackClick = { track -> onPlayWithNotificationPermission { viewModel.playFromCollection(track, selectedCollection!!) } },
            )
        }
        else -> {
            PlaylistsContent(
                tracks = tracks,
                playlists = playlists,
                onCreatePlaylist = viewModel::createPlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
                onSelectPlaylist = { selectedPlaylist = it },
                onSelectCollection = { selectedCollection = it },
            )
        }
    }
}

@Composable
fun PlaylistsContent(
    tracks: List<Track> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    initialPlaylists: List<String> = emptyList(),
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (Long) -> Unit = {},
    onSelectPlaylist: (Playlist) -> Unit = {},
    onSelectCollection: (AutoPlaylist) -> Unit = {},
) {
    val collections = remember(tracks) { groupTracksIntoPlaylists(tracks) }
    var customNames by remember(initialPlaylists) { mutableStateOf(initialPlaylists) }

    val displayPlaylists = remember(playlists, customNames) {
        if (playlists.isNotEmpty()) {
            playlists
        } else {
            customNames.mapIndexed { index, name ->
                Playlist(id = (index + 1).toLong(), name = name, trackCount = 0)
            }
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("My Playlists") },
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Collections") },
            )
        }

        if (selectedTabIndex == 0) {
            MyPlaylistsContent(
                playlists = displayPlaylists,
                onCreateClick = { showCreateDialog = true },
                onPlaylistClick = onSelectPlaylist,
                onDeleteClick = { playlistToDelete = it },
            )
        } else {
            CollectionsContent(
                collections = collections,
                onCollectionClick = onSelectCollection,
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                customNames = customNames + name
                onCreatePlaylist(name)
                showCreateDialog = false
            },
        )
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlistToDelete?.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.id?.let { onDeletePlaylist(it) }
                        customNames = customNames.filterNot { it == playlistToDelete?.name }
                        playlistToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MyPlaylistsContent(
    playlists: List<Playlist>,
    onCreateClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("My Playlists", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${playlists.size} custom playlists",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onCreateClick) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New Playlist", modifier = Modifier.padding(start = AppDimens.ElementSpacing))
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.SectionSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("No playlists yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Create custom playlists to organize your favorite tracks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onCreateClick) {
                        Text("Create Playlist")
                    }
                }
            }
        } else {
            items(playlists, key = Playlist::id) { playlist ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        TrackCover(
                            artworkUri = null,
                            size = AppDimens.CoverSizeMedium,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${playlist.trackCount} tracks",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { onDeleteClick(playlist) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete ${playlist.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Open ${playlist.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPlaylistDetail(
    playlist: Playlist,
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
) {
    val playlistTracks by viewModel.getPlaylistTracks(playlist.id).collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${playlistTracks.size} tracks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(vertical = AppDimens.ElementSpacing),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                Button(
                    onClick = { onPlayWithNotificationPermission { viewModel.playPlaylist(playlistTracks) } },
                    enabled = playlistTracks.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play all", modifier = Modifier.padding(start = AppDimens.ElementSpacing))
                }
                OutlinedButton(
                    onClick = { onPlayWithNotificationPermission { viewModel.playPlaylist(playlistTracks, shuffle = true) } },
                    enabled = playlistTracks.size > 1,
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Text("Shuffle", modifier = Modifier.padding(start = AppDimens.ElementSpacing))
                }
            }
        }

        if (playlistTracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.SectionSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("This playlist is empty", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add tracks from the Library or Now Playing screen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(playlistTracks, key = Track::id) { track ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onPlayWithNotificationPermission { viewModel.playFromPlaylist(track, playlistTracks) } }
                        .padding(vertical = AppDimens.ElementSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    TrackCover(
                        track = track,
                        size = AppDimens.CoverSizeMedium,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium)
                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { viewModel.removeTrackFromPlaylist(playlist.id, track.id) },
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove from playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play ${track.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionsContent(
    collections: List<AutoPlaylist>,
    onCollectionClick: (AutoPlaylist) -> Unit,
) {
    if (collections.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No collections yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Albums and folders from your local music library will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
                    Text("Collections", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Albums and folders found in your music library",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(collections, key = AutoPlaylist::id) { group ->
                CollectionRow(group) { onCollectionClick(group) }
            }
        }
    }
}

@Composable
private fun CollectionRow(group: AutoPlaylist, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            TrackCover(
                track = group.tracks.firstOrNull(),
                size = AppDimens.CoverSizeMedium,
            )
            Column(Modifier.weight(1f)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${group.tracks.size} tracks · ${group.type.label()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = "Open ${group.title}")
        }
    }
}

@Composable
private fun CollectionDetail(
    group: AutoPlaylist,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to collections")
                }
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${group.tracks.size} tracks · ${group.type.label()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(vertical = AppDimens.ElementSpacing),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                Button(onClick = onPlayAll, enabled = group.tracks.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play all", modifier = Modifier.padding(start = AppDimens.ElementSpacing))
                }
                OutlinedButton(onClick = onShuffle, enabled = group.tracks.size > 1) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Text("Shuffle", modifier = Modifier.padding(start = AppDimens.ElementSpacing))
                }
            }
        }
        items(group.tracks, key = Track::id) { track ->
            Row(
                Modifier.fillMaxWidth().clickable { onTrackClick(track) }.padding(vertical = AppDimens.ElementSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                TrackCover(
                    track = track,
                    size = AppDimens.CoverSizeMedium,
                )
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${track.title}")
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun PlaylistGroupType.label(): String = when (this) {
    PlaylistGroupType.ALBUM -> "Album"
    PlaylistGroupType.FOLDER -> "Folder"
}
