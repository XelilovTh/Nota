package com.xelilovth.nota.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.xelilovth.nota.domain.model.Playlist
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.presentation.AppRoute
import com.xelilovth.nota.presentation.library.LibraryViewModel
import com.xelilovth.nota.ui.components.TrackCover
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onPlayWithNotificationPermission: (() -> Unit) -> Unit = {},
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var permissionDenied by remember { mutableStateOf(false) }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isScanning by libraryViewModel.isScanning.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) libraryViewModel.scan(force = false)
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            libraryViewModel.scan(force = false)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    HomeContent(
        tracks = tracks,
        recentlyPlayed = recentlyPlayed,
        favorites = favorites,
        playlists = playlists,
        hasPermission = hasPermission,
        isScanning = isScanning,
        permissionDenied = permissionDenied,
        onPlayFirstTrack = { onPlayWithNotificationPermission(viewModel::playFirstTrack) },
        onPlayShuffled = { onPlayWithNotificationPermission(viewModel::playShuffledLibrary) },
        onPlayTrack = { track -> onPlayWithNotificationPermission { viewModel.play(track) } },
        onPlayFavorites = { shuffle -> onPlayWithNotificationPermission { viewModel.playFavorites(shuffle) } },
        onNavigateToSearch = { navController.navigate(AppRoute.homeActionRoute("search") ?: AppRoute.SEARCH) },
        onNavigateToSettings = { navController.navigate(AppRoute.homeActionRoute("settings") ?: AppRoute.SETTINGS) },
        onNavigateToPlaylists = { navController.navigate(AppRoute.PLAYLISTS) },
        onScanAgain = { libraryViewModel.scan(force = true) },
        onRequestPermission = { permissionLauncher.launch(permission) },
    )
}

@Composable
fun HomeContent(
    tracks: List<Track>,
    recentlyPlayed: List<Track> = emptyList(),
    favorites: List<Track> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    hasPermission: Boolean = true,
    isScanning: Boolean = false,
    permissionDenied: Boolean = false,
    onPlayFirstTrack: () -> Unit = {},
    onPlayShuffled: () -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    onPlayFavorites: ((Boolean) -> Unit)? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onScanAgain: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Good Morning", style = MaterialTheme.typography.headlineMedium)
                    Text("Ready to listen?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = onNavigateToSearch) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Play", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (tracks.isEmpty()) "Your music will appear here after scanning." else "${tracks.size} tracks in your library",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
                        Button(onClick = onPlayFirstTrack, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Filled.PlayArrow, null); Text("Play")
                        }
                        OutlinedButton(onClick = onPlayShuffled, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Filled.Shuffle, null); Text("Shuffle")
                        }
                    }
                }
            }
        }
        if (!hasPermission) {
            item { EmptyMessage("Music access needed", if (permissionDenied) "Allow audio access to scan music on this device." else "Allow access to find your local music.") { onRequestPermission() } }
        } else if (isScanning && tracks.isEmpty()) {
            item { CircularProgressIndicator() }
        } else if (tracks.isEmpty()) {
            item { EmptyMessage("Your library is empty", "Add audio files to your device, then scan your library.") { onScanAgain() } }
        } else {
            if (favorites.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            SectionHeading("Favorites")
                        }
                        if (onPlayFavorites != null) {
                            Row {
                                IconButton(onClick = { onPlayFavorites(false) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play favorites")
                                }
                                IconButton(onClick = { onPlayFavorites(true) }) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle favorites")
                                }
                            }
                        }
                    }
                }
                items(favorites.take(5), key = { "fav:${it.id}" }) { track ->
                    CompactTrackRow(track) { onPlayTrack(track) }
                }
            }

            if (recentlyPlayed.isNotEmpty()) {
                item { SectionHeading("Recently Played") }
                items(recentlyPlayed, key = { "recent:${it.id}" }) { track ->
                    CompactTrackRow(track) { onPlayTrack(track) }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeading("Playlists")
                    Text(
                        "See all",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToPlaylists() }.padding(AppDimens.ElementSpacing),
                    )
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPlaylists() },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.CardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                            ) {
                                Text(
                                    "No playlists yet",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Create your first playlist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onNavigateToPlaylists) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Create playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            } else {
                items(playlists.take(5), key = { "custom_playlist:${it.id}" }) { playlist ->
                    CustomPlaylistRow(playlist) { onNavigateToPlaylists() }
                }
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
internal fun SectionHeading(title: String) { Text(title, style = MaterialTheme.typography.titleLarge) }

@Composable
internal fun CompactTrackRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = AppDimens.ElementSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        TrackCover(
            track = track,
            size = AppDimens.CoverSizeMedium,
        )
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${track.title}")
    }
}

@Composable
internal fun CustomPlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            TrackCover(
                artworkUri = null,
                size = AppDimens.CoverSizeMedium,
            )
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${playlist.trackCount} tracks · Playlist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
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

