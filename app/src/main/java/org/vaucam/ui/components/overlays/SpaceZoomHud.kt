package org.vaucam.ui.components.overlays

import org.vaucam.ui.theme.VauCamBrand
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@Composable
fun SpaceZoomHud(
    zoomLevel: Float,
    modifier: Modifier = Modifier
) {
    if (zoomLevel < 20f) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var isDeviceSteady by remember { mutableStateOf(true) }
    var isZoomLocked by remember { mutableStateOf(false) }
    var stabilityProgress by remember { mutableFloatStateOf(0f) }

    // Reset stability lock when zoom ratio changes significantly
    LaunchedEffect(zoomLevel) {
        isZoomLocked = false
        stabilityProgress = 0f
    }

    // Monitor real device motion via Gyroscope (or Accelerometer fallback)
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val activeSensor = gyroSensor ?: accelSensor

        var avgAx = 0f
        var avgAy = 0f
        var avgAz = 9.8f
        var initialized = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val wx = values[0]
                    val wy = values[1]
                    val wz = values[2]
                    val angularSpeed = sqrt(wx * wx + wy * wy + wz * wz)
                    if (isZoomLocked) {
                        if (angularSpeed > 0.16f) {
                            isZoomLocked = false
                            isDeviceSteady = false
                            stabilityProgress = 0f
                        }
                    } else {
                        isDeviceSteady = angularSpeed < 0.08f
                    }
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = values[0]
                    val ay = values[1]
                    val az = values[2]
                    if (!initialized) {
                        avgAx = ax
                        avgAy = ay
                        avgAz = az
                        initialized = true
                    } else {
                        avgAx = avgAx * 0.85f + ax * 0.15f
                        avgAy = avgAy * 0.85f + ay * 0.15f
                        avgAz = avgAz * 0.85f + az * 0.15f
                    }
                    val dx = ax - avgAx
                    val dy = ay - avgAy
                    val dz = az - avgAz
                    val deltaAccel = sqrt(dx * dx + dy * dy + dz * dz)
                    if (isZoomLocked) {
                        if (deltaAccel > 0.55f) {
                            isZoomLocked = false
                            isDeviceSteady = false
                            stabilityProgress = 0f
                        }
                    } else {
                        isDeviceSteady = deltaAccel < 0.28f
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (activeSensor != null) {
            sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Accumulate 1.5s of genuine device stability to activate Zoom Lock
    LaunchedEffect(isDeviceSteady, isZoomLocked) {
        if (isZoomLocked) {
            stabilityProgress = 1f
            return@LaunchedEffect
        }
        if (!isDeviceSteady) {
            stabilityProgress = 0f
            return@LaunchedEffect
        }
        val tickMs = 50L
        val targetDurationMs = 1500L
        var elapsed = (stabilityProgress * targetDurationMs).toLong()
        while (elapsed < targetDurationMs && isDeviceSteady) {
            delay(tickMs)
            elapsed += tickMs
            stabilityProgress = (elapsed.toFloat() / targetDurationMs).coerceIn(0f, 1f)
        }
        if (elapsed >= targetDurationMs && isDeviceSteady) {
            isZoomLocked = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val hudColor = if (isZoomLocked) VauCamBrand.Accent else Color.White

    Box(modifier = modifier.fillMaxSize()) {
        // Center Aiming Reticle with Stability Progress Arc
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val radiusPx = 40f
                // Base target ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = radiusPx,
                    style = Stroke(width = 2f)
                )
                // Stability lock progress arc
                if (stabilityProgress > 0f) {
                    drawArc(
                        color = VauCamBrand.Accent,
                        startAngle = -90f,
                        sweepAngle = 360f * stabilityProgress,
                        useCenter = false,
                        topLeft = Offset(size.width / 2f - radiusPx, size.height / 2f - radiusPx),
                        size = Size(radiusPx * 2f, radiusPx * 2f),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }
                // Crosshair lines
                drawLine(
                    color = hudColor,
                    start = Offset(size.width / 2f, size.height / 2f - 20f),
                    end = Offset(size.width / 2f, size.height / 2f - 5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = hudColor,
                    start = Offset(size.width / 2f, size.height / 2f + 5f),
                    end = Offset(size.width / 2f, size.height / 2f + 20f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = hudColor,
                    start = Offset(size.width / 2f - 20f, size.height / 2f),
                    end = Offset(size.width / 2f - 5f, size.height / 2f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = hudColor,
                    start = Offset(size.width / 2f + 5f, size.height / 2f),
                    end = Offset(size.width / 2f + 20f, size.height / 2f),
                    strokeWidth = 2f
                )
            }
        }

        // HUD Status Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 96.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                .border(1.dp, hudColor, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = hudColor,
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp)
                ) {}
                Text(
                    text = if (isZoomLocked) "ZOOM LOCK ACTIVE" else "SPACE ZOOM • HOLD STEADY",
                    color = hudColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
