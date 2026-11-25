package com.xelilovth.nota.presentation.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalGlassColors.current.surfaceElevatedTint,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.ScreenPadding)) {
            Text(
                "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                track.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = AppDimens.SectionSpacing),
            )
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.ElementSpacing),
                shape = MaterialTheme.shapes.medium,
                subtle = true,
                onClick = { showCreateDialog = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New playlist", style = MaterialTheme.typography.titleMedium)
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = AppDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                items(customPlaylists, key = CustomPlaylist::id) { playlist ->
                    val alreadyAdded = track.id in playlist.trackIds
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        subtle = true,
                        onClick = {
                            if (alreadyAdded) {
                                viewModel.removeTrackFromPlaylist(playlist.id, track.id)
                            } else {
                                viewModel.addTrackToPlaylist(playlist.id, track.id)
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
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val count = playlist.trackIds.size
                                val unit = if (count == 1) "track" else "tracks"
                                Text("$count $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (alreadyAdded) {
                                Icon(Icons.Filled.Check, contentDescription = "Added")
                            }
                        }
                    }
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
                val created = viewModel.createPlaylist(name)
                viewModel.addTrackToPlaylist(created.id, track.id)
                showCreateDialog = false
            },
        )
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
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
                Text(title, style = MaterialTheme.typography.titleLarge)
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    subtle = true,
                ) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Playlist name") },
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(name) },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
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
                Text("Delete playlist?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Remove \"$playlistName\" from your playlists?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = onConfirm) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}
