package com.xelilovth.nota.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors
import com.xelilovth.nota.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun GlassAmbientBackground(
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val glassColors = LocalGlassColors.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val topRightCenter = Offset(x = size.width * 0.88f, y = size.height * 0.14f)
        val topRightRadius = size.minDimension * 0.72f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glassColors.ambientOrbPrimary,
                    glassColors.ambientOrbPrimary.copy(alpha = 0f),
                ),
                center = topRightCenter,
                radius = topRightRadius,
            ),
            radius = topRightRadius,
            center = topRightCenter,
        )

        val midLeftCenter = Offset(x = size.width * 0.10f, y = size.height * 0.48f)
        val midLeftRadius = size.minDimension * 0.68f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glassColors.ambientOrbSecondary,
                    glassColors.ambientOrbSecondary.copy(alpha = 0f),
                ),
                center = midLeftCenter,
                radius = midLeftRadius,
            ),
            radius = midLeftRadius,
            center = midLeftCenter,
        )

        val bottomRightCenter = Offset(x = size.width * 0.78f, y = size.height * 0.86f)
        val bottomRightRadius = size.minDimension * 0.65f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glassColors.ambientOrbPrimary,
                    glassColors.ambientOrbPrimary.copy(alpha = 0f),
                ),
                center = bottomRightCenter,
                radius = bottomRightRadius,
            ),
            radius = bottomRightRadius,
            center = bottomRightCenter,
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevated: Boolean = false,
    subtle: Boolean = false,
    blurBackdrop: Boolean = false,
    borderWidth: Dp = AppDimens.GlassBorderWidth,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassColors.current
    val hazeState = LocalHazeState.current
    val tint = when {
        elevated -> glass.surfaceElevatedTint
        subtle -> glass.surfaceSubtleTint
        else -> glass.surfaceTint
    }

    val backdropModifier = if (blurBackdrop && hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = tint,
                tint = HazeTint(color = tint),
                blurRadius = AppDimens.GlassBlurRadius,
            ),
        )
    } else {
        Modifier.background(tint)
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    val borderModifier = if (!(subtle && !glass.subtleRowBorder)) {
        val resolvedBorderWidth = if (subtle) AppDimens.GlassSubtleBorderWidth else borderWidth
        val borderBrush = remember(glass.borderHighlight, glass.borderEdge) {
            Brush.linearGradient(colors = listOf(glass.borderHighlight, glass.borderEdge))
        }
        Modifier.border(
            width = resolvedBorderWidth,
            brush = borderBrush,
            shape = shape,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(backdropModifier)
            .then(borderModifier)
            .then(clickModifier),
    ) {
        val onSurface = MaterialTheme.colorScheme.onSurface
        if (LocalContentColor.current == onSurface) {
            content()
        } else {
            CompositionLocalProvider(LocalContentColor provides onSurface) {
                content()
            }
        }
    }
}

fun Modifier.verticalEdgeFade(edgeHeight: Dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = edgeHeight.toPx()
        if (size.height > 0f && fadePx > 0f) {
            val fraction = (fadePx / size.height).coerceIn(0f, 0.45f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    fraction to Color.Black,
                    (1f - fraction) to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
