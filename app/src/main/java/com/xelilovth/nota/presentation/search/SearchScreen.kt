package com.xelilovth.nota.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.domain.model.filterTracks
import com.xelilovth.nota.presentation.home.CompactTrackRow
import com.xelilovth.nota.presentation.home.HomeViewModel
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun SearchScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onPlayWithNotificationPermission: (() -> Unit) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var results by remember(tracks) { mutableStateOf(tracks) }
    LaunchedEffect(tracks, query) {
        if (query.isBlank()) {
            results = tracks
        } else {
            delay(80L)
            results = withContext(Dispatchers.Default) {
                filterTracks(tracks, query)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search title, artist or album") },
            singleLine = true,
        )
        when {
            tracks.isEmpty() -> Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            results.isEmpty() -> Text(
                "No results found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = AppDimens.ElementSpacing),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                items(results, key = { it.id }, contentType = { "track" }) { track ->
                    CompactTrackRow(track) { onPlayWithNotificationPermission { viewModel.play(track) } }
                }
            }
        }
    }
}
