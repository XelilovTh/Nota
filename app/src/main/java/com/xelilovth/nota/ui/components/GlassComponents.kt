package com.xelilovth.nota.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors
import com.xelilovth.nota.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

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
fun BreathingAmbientBackground(
    isPlaying: Boolean,
    trackId: Long?,
    trackUri: String?,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val glassColors = LocalGlassColors.current
    val isDark = !glassColors.subtleRowBorder
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "breathingAmbient")
    val infiniteBreath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "infiniteBreath",
    )
    val breathWeight by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "breathWeight",
    )
    val breath = 0.5f + (infiniteBreath - 0.5f) * breathWeight

    var artworkBitmap by remember(trackId, trackUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(trackId, trackUri) {
        if (trackUri == null) {
            artworkBitmap = null
            return@LaunchedEffect
        }
        artworkBitmap = withContext(Dispatchers.IO) {
            loadSmallArtworkThumbnail(context, trackUri)?.asImageBitmap()
        }
    }

    val grayscaleFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val bitmap = artworkBitmap
        if (bitmap != null) {
            val baseAlpha = if (isDark) 0.12f else 0.08f
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = grayscaleFilter,
                alpha = baseAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1.04f + (breath - 0.5f) * 0.06f
                        scaleX = scale
                        scaleY = scale
                    }
                    .blur(AppDimens.AmbientArtworkBlur),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.55f),
                                backgroundColor.copy(alpha = 0.15f),
                                backgroundColor.copy(alpha = 0.72f),
                            ),
                        ),
                    ),
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val delta = breath - 0.5f

            val topRightCenter = Offset(
                x = size.width * (0.88f - delta * 0.05f),
                y = size.height * (0.14f + delta * 0.04f),
            )
            val topRightRadius = size.minDimension * (0.72f + delta * 0.10f)
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

            val midLeftCenter = Offset(
                x = size.width * (0.10f + delta * 0.05f),
                y = size.height * (0.48f - delta * 0.05f),
            )
            val midLeftRadius = size.minDimension * (0.68f - delta * 0.08f)
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

            val bottomRightCenter = Offset(
                x = size.width * (0.78f + delta * 0.04f),
                y = size.height * (0.86f - delta * 0.04f),
            )
            val bottomRightRadius = size.minDimension * (0.65f + delta * 0.08f)
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
}

@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveformVisualizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val playWeight by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "wavePlayWeight",
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.78f else 0.28f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "waveAlpha",
    )
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = barAlpha)

    val phaseOffsets = remember { floatArrayOf(0.0f, 1.35f, 2.7f, 0.85f, 2.15f) }
    val idleHeights = remember { floatArrayOf(0.28f, 0.44f, 0.56f, 0.38f, 0.26f) }

    Canvas(
        modifier = modifier.size(
            width = AppDimens.VisualizerWidth,
            height = AppDimens.VisualizerHeight,
        ),
    ) {
        val barWidthPx = AppDimens.VisualizerBarWidth.toPx()
        val barSpacingPx = AppDimens.VisualizerBarSpacing.toPx()
        val barCount = phaseOffsets.size
        val totalWidth = barCount * barWidthPx + (barCount - 1) * barSpacingPx
        val startX = (size.width - totalWidth) / 2f + barWidthPx / 2f
        val maxStrokeSpan = (size.height - barWidthPx).coerceAtLeast(0f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val wave = 0.5f + 0.5f * sin(phase + phaseOffsets[i])
            val activeFraction = 0.24f + wave * 0.76f
            val fraction = idleHeights[i] + (activeFraction - idleHeights[i]) * playWeight
            val span = maxStrokeSpan * fraction
            val x = startX + i * (barWidthPx + barSpacingPx)
            drawLine(
                color = barColor,
                start = Offset(x, centerY - span / 2f),
                end = Offset(x, centerY + span / 2f),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun loadSmallArtworkThumbnail(context: Context, trackUri: String): Bitmap? = runCatching {
    val uri = Uri.parse(trackUri)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
        }.getOrNull()?.let { return@runCatching it }
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val art = retriever.embeddedPicture ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateThumbnailSampleSize(bounds.outWidth, bounds.outHeight, 256, 256)
        }
        BitmapFactory.decodeByteArray(art, 0, art.size, decodeOptions)
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()

private fun calculateThumbnailSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
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
