package com.xelilovth.nota.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.ui.theme.AppDimens

private val SLEEP_PRESETS_MINUTES = listOf(15, 30, 45, 60)

@Composable
fun SleepTimerScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val remainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Sleep Timer", style = MaterialTheme.typography.headlineMedium)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(AppDimens.BorderWidth, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
            ) {
                Text(
                    if (remainingSeconds == null) "Timer is off" else formatSleepStatus(remainingSeconds),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Playback will pause automatically when the timer ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing)) {
            SLEEP_PRESETS_MINUTES.forEach { minutes ->
                OutlinedButton(
                    onClick = { viewModel.startSleepTimer(minutes) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("$minutes minutes")
                }
            }
            if (remainingSeconds != null) {
                Button(
                    onClick = viewModel::cancelSleepTimer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Turn off timer")
                }
            }
        }
    }
}
