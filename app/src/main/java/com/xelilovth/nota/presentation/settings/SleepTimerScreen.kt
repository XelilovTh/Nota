package com.xelilovth.nota.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xelilovth.nota.ui.components.GlassSurface
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalBottomContentPadding

private val SLEEP_PRESETS_MINUTES = listOf(15, 30, 45, 60)

@Composable
fun SleepTimerScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val remainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    var customMinutesInput by remember { mutableStateOf("") }
    val parsedMinutes = customMinutesInput.toIntOrNull()?.takeIf { it in 1..480 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = LocalBottomContentPadding.current,
            ),
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

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevated = true,
            blurBackdrop = true,
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
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    subtle = true,
                    onClick = { viewModel.startSleepTimer(minutes) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppDimens.CardPadding,
                                vertical = AppDimens.RowVerticalPadding,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "$minutes minutes",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                subtle = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = AppDimens.CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
                ) {
                    TextField(
                        value = customMinutesInput,
                        onValueChange = { input ->
                            customMinutesInput = input.filter { it.isDigit() }.take(3)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Custom minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    Button(
                        enabled = parsedMinutes != null,
                        onClick = {
                            parsedMinutes?.let {
                                viewModel.startSleepTimer(it)
                                customMinutesInput = ""
                            }
                        },
                    ) {
                        Text("Set")
                    }
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
