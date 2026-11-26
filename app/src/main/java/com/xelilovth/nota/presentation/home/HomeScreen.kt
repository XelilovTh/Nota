package com.xelilovth.nota.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val mostPlayedTracks by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()
    val listenDurationsMs by viewModel.listenDurationsMs.collectAsStateWithLifecycle()
    val activeTrackId = playbackState.track?.id
    val isScanning by libraryViewModel.isScanning.collectAsStateWithLifecycle()
    var favoritesExpanded by rememberSaveable { mutableStateOf(false) }
    var mostPlayedExpanded by rememberSaveable { mutableStateOf(false) }

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
                blurBackdrop = true,
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
        } else {
            if (recentlyPlayed.isNotEmpty()) {
                item { SectionHeading("Recently Played") }
                items(recentlyPlayed, key = { "recent:${it.id}" }, contentType = { "track" }) { track ->
                    CompactTrackRow(
                        track = track,
                        isCurrent = track.id == activeTrackId,
                        isPlaying = track.id == activeTrackId && playbackState.isPlaying,
                        isFavorite = track.id in favoriteTrackIds,
                        onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                    ) {
                        onPlayWithNotificationPermission { viewModel.play(track) }
                    }
                }
            } else {
                item { SectionHeading("Recently Played") }
                item {
                    Text(
                        "Tracks you play will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (favoriteTracks.isNotEmpty()) {
                item(key = "favorites_section") {
                    val unit = if (favoriteTracks.size == 1) "track" else "tracks"
                    CollapsibleHomeCollectionCard(
                        title = "Favorites",
                        subtitle = "${favoriteTracks.size} $unit",
                        tracks = favoriteTracks,
                        expanded = favoritesExpanded,
                        onToggleExpand = { favoritesExpanded = !favoritesExpanded },
                        onPlayAll = { onPlayWithNotificationPermission { viewModel.playTracks(favoriteTracks) } },
                        activeTrackId = activeTrackId,
                        isPlaying = playbackState.isPlaying,
                        favoriteTrackIds = favoriteTrackIds,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onTrackClick = { track ->
                            onPlayWithNotificationPermission { viewModel.playTracks(favoriteTracks, track) }
                        },
                    )
                }
            }
            if (mostPlayedTracks.isNotEmpty()) {
                item(key = "most_played_section") {
                    CollapsibleHomeCollectionCard(
                        title = "Most Played",
                        subtitle = "Top ${mostPlayedTracks.size}",
                        tracks = mostPlayedTracks,
                        expanded = mostPlayedExpanded,
                        onToggleExpand = { mostPlayedExpanded = !mostPlayedExpanded },
                        onPlayAll = { onPlayWithNotificationPermission { viewModel.playTracks(mostPlayedTracks) } },
                        activeTrackId = activeTrackId,
                        isPlaying = playbackState.isPlaying,
                        favoriteTrackIds = favoriteTrackIds,
                        listenDurationsMs = listenDurationsMs,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onTrackClick = { track ->
                            onPlayWithNotificationPermission { viewModel.playTracks(mostPlayedTracks, track) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleHomeCollectionCard(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayAll: () -> Unit,
    activeTrackId: Long?,
    isPlaying: Boolean,
    favoriteTrackIds: Set<Long>,
    listenDurationsMs: Map<Long, Long>? = null,
    onToggleFavorite: (Long) -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    val canExpand = tracks.size > COLLAPSED_COLLECTION_LIMIT
    val visibleTracks = if (expanded || !canExpand) tracks else tracks.take(COLLAPSED_COLLECTION_LIMIT)

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (canExpand) Modifier.clickable(onClick = onToggleExpand) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (canExpand) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppDimens.IconSizeMedium),
                        )
                    }
                }
                TextButton(onClick = onPlayAll) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                    Text("Play all")
                }
            }
            visibleTracks.forEach { track ->
                CompactTrackRow(
                    track = track,
                    isCurrent = track.id == activeTrackId,
                    isPlaying = track.id == activeTrackId && isPlaying,
                    isFavorite = track.id in favoriteTrackIds,
                    onToggleFavorite = { onToggleFavorite(track.id) },
                    listenDurationMs = listenDurationsMs?.get(track.id),
                ) {
                    onTrackClick(track)
                }
            }
            if (canExpand) {
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        if (expanded) "Show less" else "Show all (${tracks.size})",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                }
            }
        }
    }
}

private const val COLLAPSED_COLLECTION_LIMIT = 2

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
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    listenDurationMs: Long? = null,
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
            if (listenDurationMs != null && listenDurationMs > 0L) {
                Row(
                    modifier = Modifier.padding(end = AppDimens.ElementSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ShapeExtraSmall),
                ) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = "Listening time",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                    Text(
                        formatDuration(listenDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                }
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
