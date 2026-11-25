package com.xelilovth.nota.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.AppRoute
import com.xelilovth.nota.presentation.library.LibraryViewModel
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding
import com.xelilovth.nota.ui.theme.LocalGlassColors

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
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
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val activeTrackId = playbackState.track?.id
    val isScanning by libraryViewModel.isScanning.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) libraryViewModel.scan()
    }
    LaunchedEffect(Unit) {
        if (hasPermission) libraryViewModel.scanIfEmpty() else permissionLauncher.launch(permission)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPadding,
            top = AppDimens.ScreenPadding,
            end = AppDimens.ScreenPadding,
            bottom = LocalBottomContentPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Nota", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (tracks.isEmpty()) "Local audio" else "${tracks.size} tracks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
                    GlassSurface(
                        modifier = Modifier.size(AppDimens.ArtworkSmallSize),
                        shape = CircleShape,
                        subtle = true,
                        onClick = { navController.navigate(AppRoute.homeActionRoute("search") ?: AppRoute.SEARCH) },
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    GlassSurface(
                        modifier = Modifier.size(AppDimens.ArtworkSmallSize),
                        shape = CircleShape,
                        subtle = true,
                        onClick = { navController.navigate(AppRoute.homeActionRoute("settings") ?: AppRoute.SETTINGS) },
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevated = true,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Quick Play", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (tracks.isEmpty()) "Your music will appear here after scanning."
                        else "Play your library in order or shuffled.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
                        Button(
                            onClick = { onPlayWithNotificationPermission(viewModel::playFirstTrack) },
                            enabled = tracks.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                            Text(" Play")
                        }
                        OutlinedButton(
                            onClick = { onPlayWithNotificationPermission(viewModel::playShuffledLibrary) },
                            enabled = tracks.isNotEmpty(),
                            border = BorderStroke(AppDimens.BorderWidth, LocalGlassColors.current.borderHighlight),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                            Text(" Shuffle")
                        }
                    }
                }
            }
        }
        if (!hasPermission) {
            item {
                EmptyMessage(
                    "Music access needed",
                    if (permissionDenied) "Allow audio access to scan music on this device."
                    else "Allow access to find your local music.",
                ) { permissionLauncher.launch(permission) }
            }
        } else if (isScanning && tracks.isEmpty()) {
            item { CircularProgressIndicator() }
        } else if (tracks.isEmpty()) {
            item {
                EmptyMessage(
                    "Your library is empty",
                    "Add audio files to your device, then scan your library.",
                ) { libraryViewModel.scan() }
            }
        } else if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeading("Recently Played") }
            items(recentlyPlayed, key = { "recent:${it.id}" }, contentType = { "track" }) { track ->
                CompactTrackRow(
                    track = track,
                    isCurrent = track.id == activeTrackId,
                    isPlaying = track.id == activeTrackId && playbackState.isPlaying,
                ) {
                    onPlayWithNotificationPermission { viewModel.play(track) }
                }
            }
        } else {
            item {
                SectionHeading("Recently Played")
            }
            item {
                Text(
                    "Tracks you play will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String, message: String, action: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = AppDimens.SectionSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = action) { Text(if (title == "Music access needed") "Grant access" else "Scan again") }
    }
}

@Composable
internal fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun CompactTrackRow(
    track: Track,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        subtle = true,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
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
        }
    }
}

@Composable
internal fun PlaylistGroupRow(group: AutoPlaylist, onClick: () -> Unit) {
    val coverTrack = group.tracks.firstOrNull()
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        subtle = true,
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
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val count = group.tracks.size
                val unit = if (count == 1) "track" else "tracks"
                Text(
                    "$count $unit · ${group.type.name.lowercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
