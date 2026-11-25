package com.xelilovth.nota.presentation.playlists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val activeTrackId = playbackState.track?.id

    val customPlaylist = remember(customPlaylists, playlistId) {
        customPlaylists.firstOrNull { it.id == playlistId }
    }
    val autoPlaylist = remember(allTracks, playlistId, customPlaylist) {
        if (customPlaylist != null) null
        else groupTracksIntoPlaylists(allTracks).firstOrNull { it.id == playlistId }
    }
    val trackById = remember(allTracks) { allTracks.associateBy(Track::id) }

    val playlistTitle = customPlaylist?.name ?: autoPlaylist?.title ?: "Playlist"
    val playlistSubtitle = when {
        customPlaylist != null -> "custom"
        autoPlaylist != null -> autoPlaylist.type.name.lowercase()
        else -> ""
    }
    val playlistTracks = remember(customPlaylist, autoPlaylist, trackById) {
        when {
            customPlaylist != null -> customPlaylist.trackIds.mapNotNull(trackById::get)
            autoPlaylist != null -> autoPlaylist.tracks
            else -> emptyList()
        }
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.ElementSpacing, vertical = AppDimens.ElementSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlistTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (customPlaylist != null || autoPlaylist != null) {
                    val count = playlistTracks.size
                    val unit = if (count == 1) "track" else "tracks"
                    Text(
                        "$count $unit · $playlistSubtitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (customPlaylist != null) {
                IconButton(onClick = { showAddSongsSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add songs")
                }
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Rename playlist")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete playlist")
                }
            }
        }

        if (customPlaylist == null && autoPlaylist == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playlist not found", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(AppDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                item(key = "playlist_actions") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.ElementSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
                    ) {
                        val coverTrack = playlistTracks.firstOrNull()
                        Surface(
                            modifier = Modifier.size(AppDimens.PlaylistHeaderArtworkSize),
                            shape = CircleShape,
                            border = BorderStroke(AppDimens.BorderWidth, MaterialTheme.colorScheme.outline),
                        ) {
                            TrackArtwork(
                                trackId = coverTrack?.id,
                                trackUri = coverTrack?.uri,
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                iconPadding = AppDimens.ScreenPadding,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            Button(
                                onClick = { onPlayWithNotificationPermission { viewModel.playTracks(playlistTracks) } },
                                enabled = playlistTracks.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                                Text(" Play")
                            }
                            OutlinedButton(
                                onClick = { onPlayWithNotificationPermission { viewModel.playTracksShuffled(playlistTracks) } },
                                enabled = playlistTracks.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                                Text(" Shuffle")
                            }
                        }
                        if (customPlaylist != null && playlistTracks.isEmpty()) {
                            OutlinedButton(
                                onClick = { showAddSongsSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                                Text(" Add songs")
                            }
                        }
                    }
                }
                items(playlistTracks, key = Track::id, contentType = { "track" }) { track ->
                    val isCurrent = track.id == activeTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayWithNotificationPermission { viewModel.playTracks(playlistTracks, track) } }
                            .padding(vertical = AppDimens.RowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
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
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent && playbackState.isPlaying) {
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
                        if (customPlaylist != null) {
                            IconButton(
                                onClick = { viewModel.removeTrackFromPlaylist(customPlaylist.id, track.id) },
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove from playlist",
                                    modifier = Modifier.size(AppDimens.IconSizeSmall),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSongsSheet && customPlaylist != null) {
        ModalBottomSheet(
            onDismissRequest = { showAddSongsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.ScreenPadding)) {
                Text(
                    "Add songs to ${customPlaylist.name}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = AppDimens.ElementSpacing),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = AppDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    items(allTracks, key = Track::id, contentType = { "pick_track" }) { track ->
                        val added = track.id in customPlaylist.trackIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (added) {
                                        viewModel.removeTrackFromPlaylist(customPlaylist.id, track.id)
                                    } else {
                                        viewModel.addTrackToPlaylist(customPlaylist.id, track.id)
                                    }
                                }
                                .padding(vertical = AppDimens.RowVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (added) {
                                Icon(Icons.Filled.Check, contentDescription = "Added")
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = "Add")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && customPlaylist != null) {
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = customPlaylist.name,
            confirmLabel = "Save",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renamePlaylist(customPlaylist.id, newName)
                showRenameDialog = false
            },
        )
    }

    if (showDeleteDialog && customPlaylist != null) {
        DeletePlaylistDialog(
            playlistName = customPlaylist.name,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deletePlaylist(customPlaylist.id)
                onBack()
            },
        )
    }
}
