package com.xelilovth.nota.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.ui.theme.AppDimens

@Composable
fun OfflineDownloadDialog(
    onDismiss: () -> Unit,
    onSelectLocation: (OfflineStorageLocation) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
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
                Text(
                    text = "Save for offline",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Choose where to store this track",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AppDimens.ElementSpacing))
                OfflineStorageOptionRow(
                    title = "App Internal",
                    subtitle = "Saved privately inside Nota",
                    icon = Icons.Outlined.Lock,
                    onClick = { onSelectLocation(OfflineStorageLocation.APP_INTERNAL) },
                )
                OfflineStorageOptionRow(
                    title = "Music / Nota",
                    subtitle = "Saved to device Music/Nota folder",
                    icon = Icons.Outlined.Folder,
                    onClick = { onSelectLocation(OfflineStorageLocation.PUBLIC_MUSIC) },
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun OfflineStorageOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        subtle = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ElementSpacing),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.IconSizeMedium),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
