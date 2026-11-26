package com.xelilovth.nota.presentation.playlists

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.PlaylistCollectionType
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onOpenPlaylist: (String) -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val hiddenAutoGroupIds by viewModel.hiddenAutoGroupIds.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val mostPlayedTracks by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()
    val groups = remember(tracks, hiddenAutoGroupIds) { groupTracksIntoPlaylists(tracks).filterNot { it.id in hiddenAutoGroupIds } }
    val userPlaylists = customPlaylists.filter { it.type == PlaylistCollectionType.PLAYLIST }
    val userCollections = customPlaylists.filter { it.type != PlaylistCollectionType.PLAYLIST }
    val trackById = remember(tracks) { tracks.associateBy(Track::id) }

    var showCreateMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf(PlaylistCollectionType.PLAYLIST) }
    var renameTarget by remember { mutableStateOf<CustomPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomPlaylist?>(null) }

    val listState = rememberLazyListState()

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
        item(key = "playlists_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.ElementSpacing),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Playlists", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${userPlaylists.size} playlists · ${userCollections.size + groups.size} albums & folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showCreateMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create playlist or album")
                }
            }
        }

        item(key = "smart_collections") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                SmartCollectionCard(
                    modifier = Modifier.weight(1f),
                    title = "Favorites",
                    subtitle = "${favoriteTracks.size} tracks",
                    icon = if (favoriteTracks.isNotEmpty()) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    onClick = { viewModel.playTracks(favoriteTracks) },
                )
                SmartCollectionCard(
                    modifier = Modifier.weight(1f),
                    title = "Most Played",
                    subtitle = "${mostPlayedTracks.size} tracks",
                    icon = Icons.Outlined.GraphicEq,
                    onClick = { viewModel.playTracks(mostPlayedTracks) },
                )
            }
        }

        if (userPlaylists.isNotEmpty()) {
            item(key = "custom_section") {
                Text(
                    "My Playlists",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimens.ElementSpacing),
                )
            }
            items(userPlaylists, key = CustomPlaylist::id, contentType = { "custom_playlist" }) { playlist ->
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

        if (userCollections.isNotEmpty() || groups.isNotEmpty()) {
            item(key = "auto_section") {
                Text(
                    "Albums & Folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimens.SectionSpacing),
                )
            }
            items(userCollections, key = CustomPlaylist::id, contentType = { "managed_collection" }) { collection ->
                val resolvedTracks = remember(collection.trackIds, trackById) { collection.trackIds.mapNotNull(trackById::get) }
                CustomPlaylistRow(
                    playlist = collection,
                    coverTrack = resolvedTracks.firstOrNull(),
                    trackCount = resolvedTracks.size,
                    onClick = { onOpenPlaylist(collection.id) },
                    onRename = { renameTarget = collection },
                    onDelete = { deleteTarget = collection },
                    renameLabel = "Rename",
                )
            }
            items(groups, key = AutoPlaylist::id, contentType = { "auto_collection" }) { group ->
                val autoCollection = remember(group) {
                    CustomPlaylist(
                        group.id, group.title, group.tracks.map(Track::id),
                        if (group.type == com.xelilovth.nota.domain.model.PlaylistGroupType.ALBUM) PlaylistCollectionType.ALBUM else PlaylistCollectionType.FOLDER,
                    )
                }
                CustomPlaylistRow(
                    playlist = autoCollection,
                    coverTrack = group.tracks.firstOrNull(),
                    trackCount = group.tracks.size,
                    onClick = { onOpenPlaylist(group.id) },
                    onRename = { onOpenPlaylist(viewModel.editAutoGroup(group).id) },
                    onDelete = { viewModel.deleteAutoGroup(group.id) },
                    renameLabel = "Edit",
                )
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

    if (showCreateMenu) {
        Dialog(onDismissRequest = { showCreateMenu = false }) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, elevated = true, blurBackdrop = true) {
                Column(modifier = Modifier.fillMaxWidth().padding(AppDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
                    Text("Create new", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { createType = PlaylistCollectionType.PLAYLIST; showCreateMenu = false; showCreateDialog = true }) { Text("New Playlist") }
                    TextButton(onClick = { createType = PlaylistCollectionType.ALBUM; showCreateMenu = false; showCreateDialog = true }) { Text("New Album") }
                    TextButton(onClick = { createType = PlaylistCollectionType.FOLDER; showCreateMenu = false; showCreateDialog = true }) { Text("New Folder") }
                    TextButton(onClick = { showCreateMenu = false }) { Text("Cancel") }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = when (createType) { PlaylistCollectionType.PLAYLIST -> "New Playlist"; PlaylistCollectionType.ALBUM -> "New Album"; PlaylistCollectionType.FOLDER -> "New Folder" },
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createCollection(name, createType)
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = "Rename collection",
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
private fun SmartCollectionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        subtle = false,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(AppDimens.IconSizeMedium),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    renameLabel: String = "Rename",
) {
    var menuExpanded by remember { mutableStateOf(false) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        subtle = false,
        onClick = onClick,
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
                    "$trackCount $unit · ${playlist.type.name.lowercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Collection options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(renameLabel) },
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
