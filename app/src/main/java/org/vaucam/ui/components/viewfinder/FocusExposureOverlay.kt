package org.vaucam.ui.components.viewfinder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FocusExposureOverlay(
    focusPoint: Offset?,
    exposureIndex: Int,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    exposureStep: Float,
    onExposureIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val point = focusPoint ?: return
    var showOverlay by remember(point) { mutableStateOf(true) }
    var interactionTick by remember(point) { mutableIntStateOf(0) }
    var dragAccumulatorPx by remember(point) { mutableFloatStateOf(0f) }

    LaunchedEffect(point, interactionTick) {
        showOverlay = true
        delay(2500L)
        showOverlay = false
    }

    val ringAlpha by animateFloatAsState(
        targetValue = if (showOverlay) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "focusRingAlpha"
    )

    val ringScale by animateFloatAsState(
        targetValue = if (showOverlay) 1f else 1.25f,
        animationSpec = tween(durationMillis = 220),
        label = "focusRingScale"
    )

    if (ringAlpha <= 0f) return

    val hasExposureRange = maxExposureIndex > minExposureIndex
    val normalizedEv = if (hasExposureRange) {
        (exposureIndex - minExposureIndex).toFloat() / (maxExposureIndex - minExposureIndex).toFloat()
    } else {
        0.5f
    }
    val evValue = exposureIndex * exposureStep
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(ringAlpha)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radiusPx = 34.dp.toPx() * ringScale
            drawCircle(
                color = Color.Yellow,
                radius = radiusPx,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner focus ticks
            val tickLen = 6.dp.toPx()
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x, point.y - radiusPx),
                end = Offset(point.x, point.y - radiusPx + tickLen),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x, point.y + radiusPx - tickLen),
                end = Offset(point.x, point.y + radiusPx),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x - radiusPx, point.y),
                end = Offset(point.x - radiusPx + tickLen, point.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x + radiusPx - tickLen, point.y),
                end = Offset(point.x + radiusPx, point.y),
                strokeWidth = 2.dp.toPx()
            )

            // Draw Exposure Slider Track beside the focus ring
            if (hasExposureRange) {
                val sliderOffsetX = if (point.x + 70.dp.toPx() < size.width) {
                    point.x + 54.dp.toPx()
                } else {
                    point.x - 54.dp.toPx()
                }
                val trackHalfHeight = 52.dp.toPx()
                val topY = (point.y - trackHalfHeight).coerceAtLeast(24.dp.toPx())
                val bottomY = (point.y + trackHalfHeight).coerceAtMost(size.height - 24.dp.toPx())
                val thumbY = bottomY - normalizedEv * (bottomY - topY)

                // Inactive track
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(sliderOffsetX, topY),
                    end = Offset(sliderOffsetX, bottomY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Active fill from bottom to thumb
                drawLine(
                    color = Color.Yellow,
                    start = Offset(sliderOffsetX, thumbY),
                    end = Offset(sliderOffsetX, bottomY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (hasExposureRange) {
            val sliderAreaHeightPx = with(density) { 130.dp.toPx() }
            val isRightSide = point.x + with(density) { 70.dp.toPx() } < 1000f
            val offsetX = if (isRightSide) {
                (point.x + with(density) { 26.dp.toPx() }).roundToInt()
            } else {
                (point.x - with(density) { 82.dp.toPx() }).roundToInt()
            }
            val offsetY = (point.y - sliderAreaHeightPx / 2f).roundToInt()

            val trackHeightPx = with(density) { 104.dp.toPx() }
            val thumbOffsetYPx = ((0.5f - normalizedEv) * trackHeightPx).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(width = 56.dp, height = 130.dp)
                    .pointerInput(minExposureIndex, maxExposureIndex, exposureIndex) {
                        val pixelsPerStep = (trackHeightPx / (maxExposureIndex - minExposureIndex).coerceAtLeast(1)).coerceAtLeast(8f)
                        detectVerticalDragGestures(
                            onDragStart = {
                                interactionTick++
                                dragAccumulatorPx = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                interactionTick++
                                dragAccumulatorPx -= dragAmount
                                val stepDelta = (dragAccumulatorPx / pixelsPerStep).toInt()
                                if (stepDelta != 0) {
                                    dragAccumulatorPx -= stepDelta * pixelsPerStep
                                    val target = (exposureIndex + stepDelta).coerceIn(minExposureIndex, maxExposureIndex)
                                    onExposureIndexChanged(target)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure Compensation",
                    tint = Color.Yellow,
                    modifier = Modifier
                        .offset { IntOffset(0, thumbOffsetYPx) }
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(3.dp)
                )
            }

            if (exposureIndex != 0) {
                val badgeY = (point.y - with(density) { 64.dp.toPx() }).roundToInt().coerceAtLeast(16)
                val badgeX = (point.x - with(density) { 28.dp.toPx() }).roundToInt().coerceAtLeast(16)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(badgeX, badgeY) }
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%+.1f EV", evValue),
                        color = Color.Yellow,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
