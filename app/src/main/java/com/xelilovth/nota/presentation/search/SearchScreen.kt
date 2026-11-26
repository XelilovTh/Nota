package com.xelilovth.nota.presentation.search

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.filterTracks
import com.xelilovth.nota.presentation.home.CompactTrackRow
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.TrackArtwork
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class SearchSourceMode(val label: String) {
    DEVICE("Device"),
    ONLINE("Online"),
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    activeTrackId: Long? = null,
    isPlaying: Boolean = false,
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
) {
    val tracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val onlineResults by viewModel.onlineResults.collectAsStateWithLifecycle()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsStateWithLifecycle()
    val onlineError by viewModel.onlineError.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedOnlineIds by viewModel.downloadedOnlineIds.collectAsStateWithLifecycle()

    var sourceMode by rememberSaveable { mutableStateOf(SearchSourceMode.DEVICE) }
    var query by rememberSaveable { mutableStateOf("") }
    var localResults by remember(tracks) { mutableStateOf(tracks) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(tracks, query, sourceMode) {
        if (sourceMode != SearchSourceMode.DEVICE) return@LaunchedEffect
        if (query.isBlank()) {
            localResults = tracks
        } else {
            delay(80L)
            localResults = withContext(Dispatchers.Default) {
                filterTracks(tracks, query)
            }
        }
    }

    LaunchedEffect(query, sourceMode) {
        if (sourceMode != SearchSourceMode.ONLINE) return@LaunchedEffect
        if (query.isBlank()) {
            viewModel.searchOnline("")
        } else {
            delay(450L)
            viewModel.searchOnline(query)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPadding, vertical = AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            Text("Search", style = MaterialTheme.typography.headlineMedium)
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                elevated = false,
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (sourceMode == SearchSourceMode.ONLINE) {
                                "Search any song or artist online"
                            } else {
                                "Search title, artist or album"
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            if (sourceMode == SearchSourceMode.ONLINE) {
                                viewModel.searchOnline(query)
                            }
                        },
                    ),
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
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                SearchSourceMode.entries.forEach { mode ->
                    val selected = sourceMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { sourceMode = mode },
                        border = null,
                        label = {
                            Text(
                                mode.label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        when (sourceMode) {
            SearchSourceMode.DEVICE -> {
                when {
                    tracks.isEmpty() -> Text(
                        "Your library is empty",
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    localResults.isEmpty() -> Text(
                        "No results found",
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        val listState = rememberLazyListState()
                        val onPlayTrack: (Track) -> Unit = remember(viewModel, onPlayWithNotificationPermission, tracks) {
                            { track -> onPlayWithNotificationPermission { viewModel.play(track, tracks) } }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = AppDimens.ScreenPadding,
                                end = AppDimens.ScreenPadding,
                                bottom = LocalBottomContentPadding.current,
                            ),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            items(localResults, key = Track::id, contentType = { "track_row" }) { track ->
                                CompactTrackRow(
                                    track = track,
                                    isActive = track.id == activeTrackId,
                                    isPlaying = isPlaying,
                                    onClick = onPlayTrack,
                                )
                            }
                        }
                    }
                }
            }

            SearchSourceMode.ONLINE -> {
                when {
                    query.isBlank() -> Text(
                        "Type a song or artist name to stream online without downloading",
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    isSearchingOnline -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    onlineError != null -> Text(
                        onlineError.orEmpty(),
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    onlineResults.isEmpty() -> Text(
                        "No online results found",
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = AppDimens.ScreenPadding,
                                end = AppDimens.ScreenPadding,
                                bottom = LocalBottomContentPadding.current,
                            ),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            items(onlineResults, key = Track::id, contentType = { "online_track_row" }) { track ->
                                val isActive = track.id == activeTrackId
                                val isDownloaded = track.onlineId != null && track.onlineId in downloadedOnlineIds
                                val progress = downloadProgress[track.id]
                                OnlineTrackRow(
                                    track = track,
                                    isActive = isActive,
                                    isDownloaded = isDownloaded,
                                    downloadProgress = progress,
                                    onPlay = {
                                        onPlayWithNotificationPermission {
                                            viewModel.play(track, onlineResults)
                                        }
                                    },
                                    onDownload = {
                                        viewModel.downloadForOffline(track) { result ->
                                            val msg = if (result.isSuccess) {
                                                "Saved for offline listening"
                                            } else {
                                                "Offline download failed"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackRow(
    track: Track,
    isActive: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        subtle = !isActive,
        onClick = onPlay,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            TrackArtwork(
                trackId = track.id,
                trackUri = track.uri,
                artworkUrl = track.artworkUrl,
                modifier = Modifier.size(AppDimens.ArtworkSmallSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
            if (isActive) {
                Icon(
                    Icons.Outlined.GraphicEq,
                    contentDescription = "Now playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppDimens.IconSizeSmall),
                )
            } else if (track.durationMs > 0L) {
                Text(
                    formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                enabled = downloadProgress == null && !isDownloaded,
                onClick = onDownload,
            ) {
                if (downloadProgress != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                        strokeWidth = AppDimens.BorderWidth,
                    )
                } else {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.DownloadForOffline,
                        contentDescription = if (isDownloaded) "Saved offline" else "Save for offline",
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
