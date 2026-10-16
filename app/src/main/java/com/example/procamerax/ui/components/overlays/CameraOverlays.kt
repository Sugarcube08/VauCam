package com.example.procamerax.ui.components.overlays

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RecordingTimerPill(
    isRecording: Boolean,
    isPaused: Boolean,
    durationSeconds: Long,
    modifier: Modifier = Modifier
) {
    if (!isRecording) return

    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recDotAlpha"
    )

    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    val formattedTime = if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
            .border(
                width = 1.dp,
                color = if (isPaused) Color.Yellow else Color.Red.copy(alpha = 0.8f),
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = if (isPaused) Color.Yellow else Color.Red,
                shape = CircleShape,
                modifier = Modifier
                    .size(10.dp)
                    .alpha(if (isPaused) 1f else dotAlpha)
            ) {}
            Text(
                text = if (isPaused) "PAUSED • $formattedTime" else formattedTime,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CountdownTimerOverlay(
    secondsRemaining: Int?,
    modifier: Modifier = Modifier
) {
    val seconds = secondsRemaining ?: return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .border(3.dp, Color.Yellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = seconds.toString(),
                color = Color.Yellow,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ShutterFlashOverlay(
    shutterFlashTrigger: Long,
    isScreenFlashActive: Boolean,
    modifier: Modifier = Modifier
) {
    var showShutterBlink by remember { mutableStateOf(false) }

    LaunchedEffect(shutterFlashTrigger) {
        if (shutterFlashTrigger > 0L) {
            showShutterBlink = true
            delay(85L)
            showShutterBlink = false
        }
    }

    val blinkAlpha by animateFloatAsState(
        targetValue = if (showShutterBlink) 0.75f else 0f,
        animationSpec = tween(durationMillis = 70),
        label = "shutterBlinkAlpha"
    )

    if (isScreenFlashActive) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
        )
    } else if (blinkAlpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = blinkAlpha))
        )
    }
}

@Composable
fun NightSightActivePill(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    Box(
        modifier = modifier
            .background(Color.Yellow.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, Color.Yellow, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NightsStay,
                contentDescription = "Night Mode Active",
                tint = Color.Yellow,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "NIGHT SIGHT ACTIVE",
                color = Color.Yellow,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun HoldStillProgressOverlay(
    progress: Int?,
    modifier: Modifier = Modifier
) {
    val currentProgress = progress ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                progress = (currentProgress.toFloat() / 100f).coerceIn(0f, 1f),
                color = Color.Yellow,
                strokeWidth = 6.dp,
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = "Hold still...",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "$currentProgress%",
                color = Color.Yellow,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun HistogramOverlay(
    bins: List<Float>,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible || bins.isEmpty()) return

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "LUMA HISTOGRAM",
            color = Color.Yellow,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
        Canvas(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(120.dp)
                .height(46.dp)
        ) {
            val fillPath = Path()
            val strokePath = Path()
            val count = bins.size
            val stepX = size.width / (count - 1).coerceAtLeast(1)

            fillPath.moveTo(0f, size.height)
            bins.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value.coerceIn(0f, 1f) * size.height)
                fillPath.lineTo(x, y)
                if (index == 0) {
                    strokePath.moveTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                color = Color.Yellow.copy(alpha = 0.3f)
            )
            drawPath(
                path = strokePath,
                color = Color.Yellow,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

class LuminanceHistogramAnalyzer(
    private val onHistogramComputed: (List<Float>) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val rawCounts = IntArray(64)

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimestamp < 120L) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = now

        try {
            val plane = image.planes.firstOrNull()
            if (plane != null) {
                val buffer = plane.buffer
                buffer.rewind()
                rawCounts.fill(0)

                val limit = buffer.remaining()
                // Sample every 16th byte for minimal CPU footprint
                var i = 0
                while (i < limit) {
                    val luma = buffer.get(i).toInt() and 0xFF
                    val bin = luma shr 2 // 0..255 -> 0..63
                    rawCounts[bin]++
                    i += 16
                }

                val maxCount = (rawCounts.maxOrNull() ?: 1).coerceAtLeast(1)
                val normalized = List(64) { idx ->
                    (rawCounts[idx].toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                }
                onHistogramComputed(normalized)
            }
        } catch (_: Exception) {
            // Ignore transient buffer read exceptions
        } finally {
            image.close()
        }
    }
}
