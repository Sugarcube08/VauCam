

package org.vaucam.ui.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

// Realme signature yellow accent
val RealmeYellow = Color(0xFFFFCC00)
val RealmeDarkBg = Color(0xCC111111)

/**
 * Realme Camera-style Circular Zoom Wheel with:
 * 1. Compact circular quick-stops buttons at rest (0.6x, 1x, 2x, 5x, etc.)
 * 2. Smoothly expandable circular dial arc wheel with graduated tick marks and logarithmic zoom mapping
 * 3. Central Realme Yellow indicator needle and lens callout badge
 * 4. Precise drag interaction with tactile haptic clicks
 * 5. Auto-collapse after 3 seconds of inactivity
 */
@Composable
fun CircularZoomWheel(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    zoomOptions: List<Float>,
    uiRotationDegrees: Float,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isWheelExpanded by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-collapse after 3 seconds of inactivity when expanded
    LaunchedEffect(isWheelExpanded, lastInteractionTime) {
        if (isWheelExpanded) {
            delay(3000)
            if (System.currentTimeMillis() - lastInteractionTime >= 2800) {
                isWheelExpanded = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expanded Circular Arc Wheel View
        AnimatedVisibility(
            visible = isWheelExpanded,
            enter = fadeIn(tween(200)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(180)) + scaleOut(tween(180))
        ) {
            RealmeArcZoomDial(
                currentZoom = currentZoom,
                minZoom = minZoom,
                maxZoom = maxZoom,
                zoomOptions = zoomOptions,
                uiRotationDegrees = uiRotationDegrees,
                onZoomChanged = { zoom ->
                    lastInteractionTime = System.currentTimeMillis()
                    onZoomChanged(zoom)
                },
                onCollapse = { isWheelExpanded = false }
            )
        }

        // Collapsed Compact Circular Zoom Buttons
        AnimatedVisibility(
            visible = !isWheelExpanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150))
        ) {
            RealmeCompactZoomSelector(
                currentZoom = currentZoom,
                zoomOptions = zoomOptions,
                uiRotationDegrees = uiRotationDegrees,
                onSelectZoom = { zoom ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onZoomChanged(zoom)
                },
                onExpandWheel = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lastInteractionTime = System.currentTimeMillis()
                    isWheelExpanded = true
                }
            )
        }
    }
}

/**
 * Compact circular button strip (e.g. 0.6 | 1X | 2X | 5X) matching Realme's home camera bar.
 */
@Composable
private fun RealmeCompactZoomSelector(
    currentZoom: Float,
    zoomOptions: List<Float>,
    uiRotationDegrees: Float,
    onSelectZoom: (Float) -> Unit,
    onExpandWheel: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onExpandWheel() },
                    onDrag = { _, _ -> }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomOptions.forEach { zoom ->
            val isSelected = abs(currentZoom - zoom) < 0.12f
            val label = if (zoom < 1f) {
                String.format(Locale.US, "%.1f", zoom)
            } else if (zoom % 1f == 0f) {
                "${zoom.toInt()}X"
            } else {
                "${zoom}X"
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .rotate(uiRotationDegrees)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) RealmeYellow else Color.Transparent
                    )
                    .clickable {
                        if (isSelected) {
                            onExpandWheel()
                        } else {
                            onSelectZoom(zoom)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Realme-style circular arc zoom wheel with graduated ticks, center apex indicator,
 * and logarithmic radial scrolling.
 */
@Composable
private fun RealmeArcZoomDial(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    zoomOptions: List<Float>,
    uiRotationDegrees: Float,
    onZoomChanged: (Float) -> Unit,
    onCollapse: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val effectiveMin = minZoom.coerceAtLeast(0.5f)
    val effectiveMax = maxZoom.coerceAtMost(30f)

    // Logarithmic progress 0.0 .. 1.0
    val logMin = ln(effectiveMin)
    val logMax = ln(effectiveMax)

    fun zoomToProgress(z: Float): Float {
        val clamped = z.coerceIn(effectiveMin, effectiveMax)
        return ((ln(clamped) - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
    }

    fun progressToZoom(p: Float): Float {
        val clampedP = p.coerceIn(0f, 1f)
        val z = exp(logMin + clampedP * (logMax - logMin))
        return ((z * 10f).roundToInt() / 10f).coerceIn(effectiveMin, effectiveMax)
    }

    var lastTickZoom by remember { mutableFloatStateOf(currentZoom) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(RealmeDarkBg, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(top = 10.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header: Current Zoom Magnifier Badge + Close Icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Lens Magnifier Badge in Center
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                    .border(1.5.dp, RealmeYellow, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .rotate(uiRotationDegrees),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", currentZoom),
                    color = RealmeYellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "X",
                    color = RealmeYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Close / Collapse Button
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Collapse Zoom Wheel",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Circular Arc Dial Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .pointerInput(currentZoom, effectiveMin, effectiveMax) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Sensitivity: Horizontal swipe across 300px traverses full range
                        val deltaProgress = -dragAmount.x / 420f
                        val currentProg = zoomToProgress(currentZoom)
                        val newProg = (currentProg + deltaProgress).coerceIn(0f, 1f)
                        val newZoom = progressToZoom(newProg)

                        if (abs(newZoom - lastTickZoom) >= 0.1f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastTickZoom = newZoom
                        }
                        onZoomChanged(newZoom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(86.dp)) {
                val canvasWidth = size.width

                // Draw graduated ticks along horizontal curved dial
                val currentProg = zoomToProgress(currentZoom)
                val tickSpacingPx = canvasWidth / 24f

                // Central indicator line
                val centerX = canvasWidth / 2f
                val pointerTop = 4.dp.toPx()
                val pointerBottom = 34.dp.toPx()

                // Draw graduated tick marks
                for (i in -30..30) {
                    val tickProg = (currentProg + i * 0.018f).coerceIn(0f, 1f)
                    val tickZoom = progressToZoom(tickProg)
                    val tickX = centerX + i * tickSpacingPx

                    if (tickX in 0f..canvasWidth) {
                        // Fade edges
                        val distFromCenter = abs(tickX - centerX) / (canvasWidth / 2f)
                        val alpha = (1f - distFromCenter * distFromCenter).coerceIn(0f, 1f)

                        val isMajorStop = zoomOptions.any { abs(it - tickZoom) < 0.06f } ||
                            (tickZoom % 1f == 0f && tickZoom in setOf(1f, 2f, 5f, 10f, 20f))

                        val tickHeight = if (isMajorStop) 22.dp.toPx() else 11.dp.toPx()
                        val tickColor = if (isMajorStop) {
                            RealmeYellow.copy(alpha = alpha)
                        } else {
                            Color.White.copy(alpha = alpha * 0.45f)
                        }

                        // Curve curvature offset
                        val curveOffsetY = ((tickX - centerX) * (tickX - centerX)) / (canvasWidth * 2.8f)

                        drawLine(
                            color = tickColor,
                            start = Offset(tickX, 14.dp.toPx() + curveOffsetY),
                            end = Offset(tickX, 14.dp.toPx() + tickHeight + curveOffsetY),
                            strokeWidth = if (isMajorStop) 2.2.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Center Indicator Needle in Realme Yellow
                drawLine(
                    color = RealmeYellow,
                    start = Offset(centerX, pointerTop),
                    end = Offset(centerX, pointerBottom),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Needle triangular indicator dot
                drawCircle(
                    color = RealmeYellow,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, pointerTop)
                )
            }
        }

        // Quick Preset Stops Row along the bottom of the wheel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            zoomOptions.forEach { preset ->
                val isSelected = abs(currentZoom - preset) < 0.1f
                val label = if (preset < 1f) "${preset}X" else "${preset.toInt()}X"

                Text(
                    text = label,
                    color = if (isSelected) RealmeYellow else Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    modifier = Modifier
                        .rotate(uiRotationDegrees)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onZoomChanged(preset)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
