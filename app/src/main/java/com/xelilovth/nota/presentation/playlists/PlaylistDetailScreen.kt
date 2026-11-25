package com.xelilovth.nota.presentation.playlists

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.presentation.home.HomeViewModel
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val activeTrackId = playbackState.track?.id
    val playlist = remember(tracks, playlistId) {
        groupTracksIntoPlaylists(tracks).firstOrNull { it.id == playlistId }
    }

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
                    playlist?.title ?: "Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playlist != null) {
                    val count = playlist.tracks.size
                    val unit = if (count == 1) "track" else "tracks"
                    Text(
                        "$count $unit · ${playlist.type.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (playlist == null) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.ElementSpacing),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        Button(
                            onClick = { onPlayWithNotificationPermission { viewModel.playPlaylist(playlist) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                            Text(" Play")
                        }
                        OutlinedButton(
                            onClick = { onPlayWithNotificationPermission { viewModel.playPlaylistShuffled(playlist) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                            Text(" Shuffle")
                        }
                    }
                }
                items(playlist.tracks, key = Track::id, contentType = { "track" }) { track ->
                    val isCurrent = track.id == activeTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayWithNotificationPermission { viewModel.playPlaylistTrack(playlist, track) } }
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
                        if (isCurrent) {
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
            }
        }
    }
}
