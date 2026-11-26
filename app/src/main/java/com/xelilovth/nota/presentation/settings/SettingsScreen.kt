package com.xelilovth.nota.presentation.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.R
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sleepSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    var showGeminiKeyDialog by remember { mutableStateOf(false) }

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
