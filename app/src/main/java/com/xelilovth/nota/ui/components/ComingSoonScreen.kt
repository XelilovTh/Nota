package com.xelilovth.nota.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xelilovth.nota.ui.theme.AppDimens

data class PlaceholderAction(val label: String, val onClick: () -> Unit)

@Composable
fun ComingSoonScreen(
    title: String,
    onBack: () -> Unit,
    actions: List<PlaceholderAction> = emptyList(),
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SectionSpacing),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        Text("Coming soon", style = MaterialTheme.typography.titleLarge)
        Text("This feature is planned for a future update.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions.forEach { action ->
            Button(onClick = action.onClick, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
        }
    }
}
