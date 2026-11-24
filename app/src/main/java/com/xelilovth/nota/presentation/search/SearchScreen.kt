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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.presentation.home.CompactTrackRow
import com.xelilovth.nota.presentation.home.HomeViewModel
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun SearchScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val results = remember(tracks, query) {
        tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) ||
            it.album.contains(query, true) || it.folder.contains(query, true) }
    }
    Column(Modifier.fillMaxSize().padding(AppDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search your music") }, singleLine = true)
        LazyColumn(contentPadding = PaddingValues(vertical = AppDimens.ElementSpacing),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
            items(results, key = { it.id }) { track -> CompactTrackRow(track) { viewModel.play(track) } }
        }
    }
}
