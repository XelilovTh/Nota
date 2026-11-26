package com.xelilovth.nota.presentation.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.R
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.data.media.StreamCacheHolder
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.components.formatDuration
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val discoveredFolders by viewModel.discoveredFolders.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sleepSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    val duplicateGroups = remember(tracks) { viewModel.findDuplicateGroups() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var showFolderFilterDialog by remember { mutableStateOf(false) }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var pendingDeleteTrackId by remember { mutableStateOf<Long?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteTrackId?.let { viewModel.confirmTrackDeleted(it) }
        }
        pendingDeleteTrackId = null
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            SectionLabel("Appearance")
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.ElementSpacing),
                shape = MaterialTheme.shapes.large,
                subtle = false,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setThemeMode(mode) },
                                border = null,
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.DARK -> "Dark"
                                        },
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
            }
        }

        item {
            SectionLabel("Library")
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.ElementSpacing),
                shape = MaterialTheme.shapes.large,
                subtle = false,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide short audio", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ignore clips under 30 seconds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.hideShortTracks,
                            onCheckedChange = viewModel::setHideShortTracks,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isScanning, onClick = viewModel::rescanLibrary)
                            .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rescan library", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (isScanning) "Scanning device storage..." else "${tracks.size} tracks available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AppDimens.IconSizeMedium),
                                strokeWidth = AppDimens.BorderWidth,
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Rescan library")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFolderFilterDialog = true }
                            .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Folder filter", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (excludedFolders.isEmpty()) "All folders included" else "${excludedFolders.size} hidden",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.Folder, contentDescription = "Folder filter")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDuplicatesDialog = true }
                            .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val count = duplicateGroups.size
                            Text("Find duplicates", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (count == 0) "No duplicates found" else "$count duplicate ${if (count == 1) "group" else "groups"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Find duplicates")
                    }
                }
            }
        }

        item {
            SectionLabel("Playback")
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.ElementSpacing),
                shape = MaterialTheme.shapes.large,
                subtle = false,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        Text("Crossfade", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            listOf(0 to "Off", 2 to "2s", 4 to "4s").forEach { (seconds, label) ->
                                val selected = settings.crossfadeSeconds == seconds
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setCrossfadeSeconds(seconds) },
                                    border = null,
                                    label = {
                                        Text(
                                            label,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSleepTimer)
                            .padding(AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sleep Timer", style = MaterialTheme.typography.titleMedium)
                            Text(
                                formatSleepStatus(sleepSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.Bedtime, contentDescription = "Sleep Timer")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGeminiKeyDialog = true }
                            .padding(AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.lyrics_gemini_key_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (geminiApiKey.isNotBlank()) "Configured" else "Not set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = stringResource(R.string.lyrics_gemini_key_title),
                        )
                    }
                }
            }
        }

        item {
            SectionLabel("Online & Offline Storage")
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.ElementSpacing),
                shape = MaterialTheme.shapes.large,
                subtle = false,
            ) {
                var cacheBytes by remember { mutableStateOf(StreamCacheHolder.getCacheSizeBytes(context)) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        Text("Offline download location", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (settings.offlineStorageLocation == OfflineStorageLocation.PUBLIC_MUSIC) {
                                "Saved to Music/Nota on device storage"
                            } else {
                                "Saved privately inside Nota"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            listOf(
                                OfflineStorageLocation.APP_INTERNAL to "App Internal",
                                OfflineStorageLocation.PUBLIC_MUSIC to "Music / Nota",
                            ).forEach { (location, label) ->
                                val selected = settings.offlineStorageLocation == location
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setOfflineStorageLocation(location) },
                                    border = null,
                                    label = {
                                        Text(
                                            label,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                    ) {
                        Text("Stream cache limit", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            listOf(250 to "250 MB", 500 to "500 MB", 1024 to "1 GB").forEach { (sizeMb, label) ->
                                val selected = settings.streamCacheSizeMb == sizeMb
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setStreamCacheSizeMb(sizeMb) },
                                    border = null,
                                    label = {
                                        Text(
                                            label,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                StreamCacheHolder.clearCache(context)
                                cacheBytes = StreamCacheHolder.getCacheSizeBytes(context)
                                Toast.makeText(context, "Stream cache cleared", Toast.LENGTH_SHORT).show()
                            }
                            .padding(AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear stream cache", style = MaterialTheme.typography.titleMedium)
                            val cacheMb = cacheBytes / (1024L * 1024L)
                            Text(
                                "Currently using $cacheMb MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear stream cache")
                    }
                }
            }
        }

        item {
            SectionLabel("About")
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.ElementSpacing),
                shape = MaterialTheme.shapes.large,
                subtle = false,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Nota", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version 0.1.0 · Minimalist local audio player",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showFolderFilterDialog) {
        Dialog(onDismissRequest = { showFolderFilterDialog = false }) {
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
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Folder filter", style = MaterialTheme.typography.titleLarge)
                    if (discoveredFolders.isEmpty()) {
                        Text(
                            "No folders discovered yet. Rescan your library first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        ) {
                            items(discoveredFolders, key = { it }) { folder ->
                                val isExcluded = folder in excludedFolders
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setFolderExcluded(folder, !isExcluded) }
                                        .padding(vertical = AppDimens.RowVerticalPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                                ) {
                                    Checkbox(
                                        checked = !isExcluded,
                                        onCheckedChange = { checked ->
                                            viewModel.setFolderExcluded(folder, !checked)
                                        },
                                    )
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { showFolderFilterDialog = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    if (showDuplicatesDialog) {
        Dialog(onDismissRequest = { showDuplicatesDialog = false }) {
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
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    Text("Duplicate tracks", style = MaterialTheme.typography.titleLarge)
                    if (duplicateGroups.isEmpty()) {
                        Text(
                            "No duplicates found in your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                        ) {
                            items(duplicateGroups, key = { group -> group.first().id }) { group ->
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    subtle = true,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(AppDimens.CardPadding),
                                        verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                                    ) {
                                        group.forEach { track ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = track.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        text = "${track.artist} · ${track.folder}/${android.net.Uri.parse(track.uri).lastPathSegment.orEmpty()} · ${formatDuration(track.durationMs)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            when (val res = viewModel.deleteTrack(track.id)) {
                                                                is DeleteTrackResult.Deleted -> {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Track deleted",
                                                                        Toast.LENGTH_SHORT,
                                                                    ).show()
                                                                }
                                                                is DeleteTrackResult.RequiresPermission -> {
                                                                    pendingDeleteTrackId = track.id
                                                                    deleteLauncher.launch(
                                                                        IntentSenderRequest.Builder(res.intentSender).build(),
                                                                    )
                                                                }
                                                                is DeleteTrackResult.Failed -> {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Could not delete track",
                                                                        Toast.LENGTH_SHORT,
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.DeleteOutline,
                                                        contentDescription = "Delete duplicate track",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { showDuplicatesDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    if (showGeminiKeyDialog) {
        var keyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
        Dialog(onDismissRequest = { showGeminiKeyDialog = false }) {
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
                    Text(
                        stringResource(R.string.lyrics_gemini_key_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        subtle = true,
                    ) {
                        TextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.lyrics_gemini_key_hint)) },
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
                        TextButton(onClick = { showGeminiKeyDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                viewModel.setGeminiApiKey("")
                                showGeminiKeyDialog = false
                            },
                        ) {
                            Text("Clear")
                        }
                        Button(
                            onClick = {
                                viewModel.setGeminiApiKey(keyInput)
                                showGeminiKeyDialog = false
                            },
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun formatSleepStatus(remainingSeconds: Long?): String {
    if (remainingSeconds == null) return "Off"
    val mins = remainingSeconds / 60L
    val secs = remainingSeconds % 60L
    return "Pauses in $mins:${secs.toString().padStart(2, '0')}"
}
