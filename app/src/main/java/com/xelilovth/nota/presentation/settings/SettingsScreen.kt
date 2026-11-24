package com.xelilovth.nota.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.xelilovth.nota.presentation.library.LibraryViewModel
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSleepTimer: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            Text(
                "Audio & Playback",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            SettingsCard {
                SettingsItem(
                    icon = Icons.Outlined.Equalizer,
                    title = "Equalizer",
                    subtitle = "Adjust audio frequencies and presets",
                    onClick = onNavigateToEqualizer,
                )
                SettingsItem(
                    icon = Icons.Outlined.Nightlight,
                    title = "Sleep Timer",
                    subtitle = "Automatically stop playback after set duration",
                    onClick = onNavigateToSleepTimer,
                )
                SettingsItem(
                    icon = Icons.Outlined.Lyrics,
                    title = "Lyrics",
                    subtitle = "Synchronized and embedded song lyrics",
                    onClick = onNavigateToLyrics,
                )
            }
        }

        item {
            Text(
                "Library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            SettingsCard {
                SettingsItem(
                    icon = Icons.Outlined.Refresh,
                    title = "Rescan Library",
                    subtitle = "Scan device storage for new and updated audio files",
                    onClick = { libraryViewModel.scan(force = true) },
                )
            }
        }

        item {
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            SettingsCard {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Nota",
                    subtitle = "Version 1.0.0 · Local Music Player",
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding)) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.ElementSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
