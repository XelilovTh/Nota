package com.xelilovth.nota.presentation.playlists

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.presentation.home.PlaylistGroupRow
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onOpenPlaylist: (String) -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val groups = remember(tracks) { groupTracksIntoPlaylists(tracks) }
    val trackById = remember(tracks) { tracks.associateBy(Track::id) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CustomPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomPlaylist?>(null) }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        item(key = "playlists_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.ElementSpacing),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Playlists", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${customPlaylists.size} custom · ${groups.size} albums & folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create playlist")
                }
            }
        }

        if (customPlaylists.isNotEmpty()) {
            item(key = "custom_section") {
                Text(
                    "My Playlists",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimens.ElementSpacing),
                )
            }
            items(customPlaylists, key = CustomPlaylist::id, contentType = { "custom_playlist" }) { playlist ->
                val resolvedTracks = remember(playlist.trackIds, trackById) {
                    playlist.trackIds.mapNotNull(trackById::get)
                }
                CustomPlaylistRow(
                    playlist = playlist,
                    coverTrack = resolvedTracks.firstOrNull(),
                    trackCount = resolvedTracks.size,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onRename = { renameTarget = playlist },
                    onDelete = { deleteTarget = playlist },
                )
            }
        }

        if (groups.isNotEmpty()) {
            item(key = "auto_section") {
                Text(
                    "Albums & Folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimens.SectionSpacing),
                )
            }
            items(groups, key = AutoPlaylist::id, contentType = { "playlist" }) { group ->
                PlaylistGroupRow(group) { onOpenPlaylist(group.id) }
            }
        } else if (customPlaylists.isEmpty()) {
            item(key = "empty_playlists") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = AppDimens.SectionSpacing),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No playlists yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tap + to create a playlist, or scan your library for albums.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = target.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renamePlaylist(target.id, newName)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        DeletePlaylistDialog(
            playlistName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deletePlaylist(target.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun CustomPlaylistRow(
    playlist: CustomPlaylist,
    coverTrack: Track?,
    trackCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(AppDimens.BorderWidth, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
        ) {
            TrackArtwork(
                trackId = coverTrack?.id,
                trackUri = coverTrack?.uri,
                modifier = Modifier.size(AppDimens.ArtworkSmallSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val unit = if (trackCount == 1) "track" else "tracks"
                Text(
                    "$trackCount $unit",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
