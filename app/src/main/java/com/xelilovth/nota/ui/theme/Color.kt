package com.xelilovth.nota.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

object AppColors {
    val LightPrimary = Color(0xFF111111)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF111111)
    val LightSurfaceVariant = Color(0xFFF2F2F2)
    val LightOnSurfaceVariant = Color(0xFF666666)
    val LightOutline = Color(0xFFE5E5E5)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFFAFAFA)
    val LightSurfaceContainer = Color(0xFFF5F5F5)
    val LightSurfaceContainerHigh = Color(0xFFEEEEEE)
    val LightSurfaceContainerHighest = Color(0xFFE5E5E5)

    val DarkPrimary = Color(0xFFFFFFFF)
    val DarkOnPrimary = Color(0xFF0A0A0A)
    val DarkBackground = Color(0xFF0A0A0A)
    val DarkSurface = Color(0xFF121212)
    val DarkOnSurface = Color(0xFFFAFAFA)
    val DarkSurfaceVariant = Color(0xFF1E1E1E)
    val DarkOnSurfaceVariant = Color(0xFFA0A0A0)
    val DarkOutline = Color(0xFF282828)
    val DarkSurfaceContainerLowest = Color(0xFF0A0A0A)
    val DarkSurfaceContainerLow = Color(0xFF121212)
    val DarkSurfaceContainer = Color(0xFF181818)
    val DarkSurfaceContainerHigh = Color(0xFF202020)
    val DarkSurfaceContainerHighest = Color(0xFF282828)
}

@Immutable
data class GlassColors(
    val surfaceTint: Color,
    val surfaceElevatedTint: Color,
    val surfaceSubtleTint: Color,
    val borderHighlight: Color,
    val borderEdge: Color,
    val ambientOrbPrimary: Color,
    val ambientOrbSecondary: Color,
    val liquidLensFill: Color,
    val liquidLensRimBright: Color,
    val liquidLensRimSoft: Color,
)

val LightGlassColors = GlassColors(
    surfaceTint = Color(0x99FFFFFF),
    surfaceElevatedTint = Color(0xC2FFFFFF),
    surfaceSubtleTint = Color(0x66F5F5F5),
    borderHighlight = Color(0xE6FFFFFF),
    borderEdge = Color(0x1F000000),
    ambientOrbPrimary = Color(0x2E111111),
    ambientOrbSecondary = Color(0x1A555555),
    liquidLensFill = Color(0xD9FFFFFF),
    liquidLensRimBright = Color(0xFFFFFFFF),
    liquidLensRimSoft = Color(0x26000000),
)

val DarkGlassColors = GlassColors(
    surfaceTint = Color(0x99161616),
    surfaceElevatedTint = Color(0xBD1A1A1A),
    surfaceSubtleTint = Color(0x66141414),
    borderHighlight = Color(0x40FFFFFF),
    borderEdge = Color(0x14FFFFFF),
    ambientOrbPrimary = Color(0x1AFFFFFF),
    ambientOrbSecondary = Color(0x129E9E9E),
    liquidLensFill = Color(0x36FFFFFF),
    liquidLensRimBright = Color(0x80FFFFFF),
    liquidLensRimSoft = Color(0x1AFFFFFF),
)
