package com.xelilovth.nota.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.xelilovth.nota.ui.theme.AppDimens
import com.xelilovth.nota.ui.theme.LocalGlassColors
import kotlin.math.roundToInt

data class LiquidNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun LiquidGlassBottomBar(
    tabs: List<LiquidNavTab>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    val glass = LocalGlassColors.current
    val tabCount = tabs.size
    val clampedSelectedIndex = selectedIndex.coerceIn(0, tabCount - 1)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.FloatingNavHorizontalPadding)
            .height(AppDimens.FloatingNavHeight),
        shape = CircleShape,
        elevated = true,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.FloatingNavInnerPadding),
        ) {
            val tabWidthDp = maxWidth / tabCount
            val tabWidthPx = with(LocalDensity.current) { tabWidthDp.toPx() }.coerceAtLeast(1f)

            var isDragging by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(clampedSelectedIndex.toFloat()) }

            LaunchedEffect(clampedSelectedIndex, isDragging) {
                if (!isDragging) {
                    dragFraction = clampedSelectedIndex.toFloat()
                }
            }

            val interactionSources = remember(tabCount) {
                List(tabCount) { MutableInteractionSource() }
            }
            val pressedStates = interactionSources.map { it.collectIsPressedAsState().value }
            val anyTabPressed = pressedStates.any { it }
            val isInteracting = isDragging || anyTabPressed

            val settledIndex by animateFloatAsState(
                targetValue = clampedSelectedIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "liquidLensIndex",
            )
            val animatedIndex = if (isDragging) dragFraction else settledIndex

            val lensScale by animateFloatAsState(
                targetValue = if (isInteracting) 1.12f else 1.0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                label = "liquidLensScale",
            )

            val lensBorderWidth = if (isInteracting) {
                AppDimens.GlassLensBorderWidth
            } else {
                AppDimens.GlassBorderWidth
            }

            val lensFillBrush = Brush.verticalGradient(
                colors = listOf(
                    glass.liquidLensFill,
                    glass.liquidLensFill.copy(alpha = (glass.liquidLensFill.alpha * 0.85f).coerceIn(0f, 1f)),
                ),
            )
            val lensRimBrush = Brush.linearGradient(
                colors = listOf(
                    glass.liquidLensRimBright,
                    glass.liquidLensRimSoft,
                    glass.liquidLensRimBright,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabCount, tabWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragFraction = ((offset.x / tabWidthPx) - 0.5f)
                                    .coerceIn(0f, (tabCount - 1).toFloat())
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragFraction = (dragFraction + dragAmount / tabWidthPx)
                                    .coerceIn(0f, (tabCount - 1).toFloat())
                            },
                            onDragEnd = {
                                val target = dragFraction.roundToInt().coerceIn(0, tabCount - 1)
                                isDragging = false
                                onSelectTab(target)
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                        )
                    },
            ) {
                // Liquid Glass Lens Pill
                Box(
                    modifier = Modifier
                        .width(tabWidthDp)
                        .fillMaxHeight()
                        .offset { IntOffset((animatedIndex * tabWidthPx).roundToInt(), 0) }
                        .graphicsLayer {
                            scaleX = lensScale
                            scaleY = lensScale
                        }
                        .clip(CircleShape)
                        .background(brush = lensFillBrush)
                        .border(
                            width = lensBorderWidth,
                            brush = lensRimBrush,
                            shape = CircleShape,
                        ),
                )

                // Tab icons and labels rendered above the lens
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val closestIndex = animatedIndex.roundToInt().coerceIn(0, tabCount - 1)
                    tabs.forEachIndexed { index, tab ->
                        val isClosest = closestIndex == index
                        val contentColor by animateColorAsState(
                            targetValue = if (isClosest) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = "liquidTabContentColor",
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .semantics {
                                    contentDescription = tab.label
                                    selected = (index == clampedSelectedIndex)
                                }
                                .clickable(
                                    interactionSource = interactionSources[index],
                                    indication = null,
                                    onClick = { onSelectTab(index) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(AppDimens.IconSizeMedium),
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
