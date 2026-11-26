package com.xelilovth.nota.presentation.playlists

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.xelilovth.nota.ui.components.GlassBottomSheet
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding
import com.xelilovth.nota.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val mostPlayedTracks by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val activeTrackId = playbackState.track?.id

    val isSmartFavorites = playlistId == "smart:favorites"
    val isSmartMostPlayed = playlistId == "smart:most_played"
    val isSmartPlaylist = isSmartFavorites || isSmartMostPlayed

    val customPlaylist = remember(customPlaylists, playlistId, isSmartPlaylist) {
        if (isSmartPlaylist) null else customPlaylists.firstOrNull { it.id == playlistId }
    }
    val autoPlaylist = remember(allTracks, playlistId, customPlaylist, isSmartPlaylist) {
        if (isSmartPlaylist || customPlaylist != null) null
        else groupTracksIntoPlaylists(allTracks).firstOrNull { it.id == playlistId }
    }
    val trackById = remember(allTracks) { allTracks.associateBy(Track::id) }

    val playlistTitle = when {
        isSmartFavorites -> "Favorites"
        isSmartMostPlayed -> "Most Played"
        customPlaylist != null -> customPlaylist.name
        autoPlaylist != null -> autoPlaylist.title
        else -> "Playlist"
    }
    val playlistSubtitle = when {
        isSmartPlaylist -> "smart playlist"
        customPlaylist != null -> customPlaylist.type.name.lowercase()
        autoPlaylist != null -> autoPlaylist.type.name.lowercase()
        else -> ""
    }
    val playlistTracks = remember(
        isSmartFavorites,
        isSmartMostPlayed,
        favoriteTracks,
        mostPlayedTracks,
        customPlaylist,
        autoPlaylist,
        trackById,
    ) {
        when {
            isSmartFavorites -> favoriteTracks
            isSmartMostPlayed -> mostPlayedTracks
            customPlaylist != null -> customPlaylist.trackIds.mapNotNull(trackById::get)
            autoPlaylist != null -> autoPlaylist.tracks
            else -> emptyList()
        }
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val detailHazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(state = detailHazeState)) {
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
                if (isSmartPlaylist || customPlaylist != null || autoPlaylist != null) {
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
                    Icon(Icons.Outlined.Edit, contentDescription = "Rename collection")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete collection")
                }
            }
        }

        if (!isSmartPlaylist && customPlaylist == null && autoPlaylist == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Collection not found", style = MaterialTheme.typography.titleMedium)
            }
        } else {
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
                item(key = "playlist_actions") {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        elevated = true,
                        blurBackdrop = true,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
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
                }
                items(playlistTracks, key = Track::id, contentType = { "track" }) { track ->
                    val isCurrent = track.id == activeTrackId
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        subtle = true,
                        onClick = { onPlayWithNotificationPermission { viewModel.playTracks(playlistTracks, track) } },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
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
        }

        CompositionLocalProvider(LocalHazeState provides detailHazeState) {
            GlassBottomSheet(
                visible = showAddSongsSheet && customPlaylist != null,
                onDismissRequest = { showAddSongsSheet = false },
            ) {
                if (customPlaylist != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.ScreenPadding)) {
                        Text(
                            "Add songs to ${customPlaylist.name}",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = AppDimens.ElementSpacing),
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            items(allTracks, key = Track::id, contentType = { "pick_track" }) { track ->
                                val added = track.id in customPlaylist.trackIds
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    subtle = true,
                                    onClick = {
                                        if (added) {
                                            viewModel.removeTrackFromPlaylist(customPlaylist.id, track.id)
                                        } else {
                                            viewModel.addTrackToPlaylist(customPlaylist.id, track.id)
                                        }
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
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
            }
        }
    }

    if (showRenameDialog && customPlaylist != null) {
        PlaylistNameDialog(
            title = "Rename collection",
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
