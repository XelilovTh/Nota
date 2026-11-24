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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.PlaylistGroupType
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.presentation.home.HomeViewModel
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun PlaylistsScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val groups = remember(tracks) { groupTracksIntoPlaylists(tracks) }
    var selectedGroup by remember { mutableStateOf<AutoPlaylist?>(null) }

    val activeGroup = selectedGroup?.let { selected -> groups.firstOrNull { it.id == selected.id } }
    if (activeGroup != null) {
        CollectionDetail(
            group = activeGroup,
            onBack = { selectedGroup = null },
            onPlayAll = { onPlayWithNotificationPermission { viewModel.playCollection(activeGroup) } },
            onShuffle = { onPlayWithNotificationPermission { viewModel.playCollection(activeGroup, shuffle = true) } },
            onTrackClick = { track -> onPlayWithNotificationPermission { viewModel.playFromCollection(track, activeGroup) } },
        )
    } else {
        if (groups.isEmpty()) {
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
                items(groups, key = AutoPlaylist::id) { group ->
                    CollectionRow(group) { selectedGroup = group }
                }
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
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
                Button(onClick = onPlayAll, enabled = group.tracks.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play all")
                }
                OutlinedButton(onClick = onShuffle, enabled = group.tracks.size > 1) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Text("Shuffle")
                }
            }
        }
        items(group.tracks, key = Track::id) { track ->
            Row(
                Modifier.fillMaxWidth().clickable { onTrackClick(track) }.padding(vertical = AppDimens.ElementSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${track.title}")
            }
        }
    }
}

private fun PlaylistGroupType.label(): String = when (this) {
    PlaylistGroupType.ALBUM -> "Album"
    PlaylistGroupType.FOLDER -> "Folder"
}
