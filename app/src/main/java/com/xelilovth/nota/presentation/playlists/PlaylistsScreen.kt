package com.xelilovth.nota.presentation.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.AutoPlaylist
import com.xelilovth.nota.domain.model.groupTracksIntoPlaylists
import com.xelilovth.nota.presentation.home.HomeViewModel
import com.xelilovth.nota.presentation.home.PlaylistGroupRow
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun PlaylistsScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onPlayWithNotificationPermission: (() -> Unit) -> Unit = {},
) {
    val tracks = viewModel.tracks.collectAsStateWithLifecycle().value
    val groups = remember(tracks) { groupTracksIntoPlaylists(tracks) }
    if (groups.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No playlists yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Albums and folders from your library will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val listState = rememberLazyListState()
        val onPlayGroup: (AutoPlaylist) -> Unit = remember(viewModel, onPlayWithNotificationPermission) {
            { group -> onPlayWithNotificationPermission { viewModel.playPlaylist(group) } }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = AppDimens.ElementSpacing)) {
                    Text("Playlists", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${groups.size} albums & folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(groups, key = AutoPlaylist::id, contentType = { "playlist" }) { group ->
                PlaylistGroupRow(group) { onPlayGroup(group) }
            }
        }
    }
}
