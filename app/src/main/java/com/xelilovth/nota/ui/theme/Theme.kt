package com.xelilovth.nota.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AppColors.LightPrimary,
    onPrimary = AppColors.LightOnPrimary,
    secondary = AppColors.LightPrimary,
    onSecondary = AppColors.LightOnPrimary,
    secondaryContainer = AppColors.LightSurfaceContainerHigh,
    onSecondaryContainer = AppColors.LightOnSurface,
    background = AppColors.LightBackground,
    onBackground = AppColors.LightOnSurface,
    surface = AppColors.LightSurface,
    onSurface = AppColors.LightOnSurface,
    surfaceVariant = AppColors.LightSurfaceVariant,
    onSurfaceVariant = AppColors.LightOnSurfaceVariant,
    outline = AppColors.LightOutline,
    outlineVariant = AppColors.LightOutline,
    surfaceContainerLowest = AppColors.LightSurfaceContainerLowest,
    surfaceContainerLow = AppColors.LightSurfaceContainerLow,
    surfaceContainer = AppColors.LightSurfaceContainer,
    surfaceContainerHigh = AppColors.LightSurfaceContainerHigh,
    surfaceContainerHighest = AppColors.LightSurfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.DarkPrimary,
    onPrimary = AppColors.DarkOnPrimary,
    secondary = AppColors.DarkPrimary,
    onSecondary = AppColors.DarkOnPrimary,
    secondaryContainer = AppColors.DarkSurfaceContainerHigh,
    onSecondaryContainer = AppColors.DarkOnSurface,
    background = AppColors.DarkBackground,
    onBackground = AppColors.DarkOnSurface,
    surface = AppColors.DarkSurface,
    onSurface = AppColors.DarkOnSurface,
    surfaceVariant = AppColors.DarkSurfaceVariant,
    onSurfaceVariant = AppColors.DarkOnSurfaceVariant,
    outline = AppColors.DarkOutline,
    outlineVariant = AppColors.DarkOutline,
    surfaceContainerLowest = AppColors.DarkSurfaceContainerLowest,
    surfaceContainerLow = AppColors.DarkSurfaceContainerLow,
    surfaceContainer = AppColors.DarkSurfaceContainer,
    surfaceContainerHigh = AppColors.DarkSurfaceContainerHigh,
    surfaceContainerHighest = AppColors.DarkSurfaceContainerHighest,
)

@Composable
fun NotaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
