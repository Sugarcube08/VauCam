package com.example.procamerax.ui.components.viewfinder

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun GridAndLevelOverlay(
    isGridEnabled: Boolean,
    isLevelerEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isGridEnabled && !isLevelerEnabled) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var tiltDegrees by remember { mutableFloatStateOf(0f) }
    var wasLevel by remember { mutableStateOf(false) }

    DisposableEffect(isLevelerEnabled) {
        if (!isLevelerEnabled) {
            onDispose {}
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    val gx = values[0]
                    val gy = values[1]
                    if (abs(gx) < 0.5f && abs(gy) < 0.5f) return

                    // Calculate roll angle in degrees (-180..180)
                    val rawAngle = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
                    // Relative tilt from nearest 90-degree cardinal orientation (-45..45)
                    val nearestCardinal = (rawAngle / 90f).roundToInt() * 90f
                    val relativeTilt = rawAngle - nearestCardinal
                    // Low-pass filter for smooth leveler motion
                    tiltDegrees = tiltDegrees * 0.8f + relativeTilt * 0.2f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (sensor != null) {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val isLevel = abs(tiltDegrees) <= 1.0f
    LaunchedEffect(isLevel, isLevelerEnabled) {
        if (isLevelerEnabled && isLevel && !wasLevel) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasLevel = isLevel
    }

    val animatedTilt by animateFloatAsState(
        targetValue = tiltDegrees,
        animationSpec = tween(durationMillis = 80),
        label = "horizonTilt"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isGridEnabled) {
                val gridColor = Color.White.copy(alpha = 0.35f)
                val strokeWidth = 1.dp.toPx()

                // Vertical rule-of-thirds lines
                val thirdW = size.width / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(thirdW, 0f),
                    end = Offset(thirdW, size.height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = gridColor,
                    start = Offset(thirdW * 2f, 0f),
                    end = Offset(thirdW * 2f, size.height),
                    strokeWidth = strokeWidth
                )

                // Horizontal rule-of-thirds lines
                val thirdH = size.height / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, thirdH),
                    end = Offset(size.width, thirdH),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, thirdH * 2f),
                    end = Offset(size.width, thirdH * 2f),
                    strokeWidth = strokeWidth
                )
            }

            if (isLevelerEnabled) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val levelColor = if (isLevel) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.85f)
                val referenceColor = Color.White.copy(alpha = 0.35f)
                val outerHalfLen = 68.dp.toPx()
                val innerGap = 18.dp.toPx()

                // Fixed horizontal reference ticks
                drawLine(
                    color = referenceColor,
                    start = Offset(center.x - outerHalfLen - 16.dp.toPx(), center.y),
                    end = Offset(center.x - outerHalfLen, center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = referenceColor,
                    start = Offset(center.x + outerHalfLen, center.y),
                    end = Offset(center.x + outerHalfLen + 16.dp.toPx(), center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Rotating horizon line
                rotate(degrees = animatedTilt, pivot = center) {
                    drawLine(
                        color = levelColor,
                        start = Offset(center.x - outerHalfLen, center.y),
                        end = Offset(center.x - innerGap, center.y),
                        strokeWidth = if (isLevel) 3.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = levelColor,
                        start = Offset(center.x + innerGap, center.y),
                        end = Offset(center.x + outerHalfLen, center.y),
                        strokeWidth = if (isLevel) 3.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = levelColor,
                        radius = 12.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        if (isLevelerEnabled) {
            val badgeColor = if (isLevel) Color(0xFF4CAF50) else Color.White
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 52.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%+.1f°", animatedTilt),
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
