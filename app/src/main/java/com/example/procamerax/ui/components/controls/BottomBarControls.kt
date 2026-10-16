package com.example.procamerax.ui.components.controls

import coil.ImageLoader
import coil.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.procamerax.viewmodel.CameraMode
import com.example.procamerax.viewmodel.CameraUiState
import kotlin.math.abs

@Composable
fun BottomBarControls(
    uiState: CameraUiState,
    zoomOptions: List<Float>,
    imageLoader: ImageLoader,
    uiRotationDegrees: Float,
    onSelectZoom: (Float) -> Unit,
    onOpenGallery: () -> Unit,
    onTogglePauseResumeVideo: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchLens: () -> Unit,
    onSelectMode: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var flipSpinTarget by remember { mutableFloatStateOf(0f) }
    val animatedFlipSpin by animateFloatAsState(
        targetValue = flipSpinTarget,
        animationSpec = tween(durationMillis = 320),
        label = "lensFlipSpin"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Zoom Selector Pill
        if (zoomOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .background(Color.DarkGray.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                zoomOptions.forEach { zoom ->
                    val isSelected = abs(uiState.zoomLevel - zoom) < 0.08f
                    val text = if (zoom % 1f == 0f) "${zoom.toInt()}x" else "${zoom}x"
                    Box(
                        modifier = Modifier
                            .rotate(uiRotationDegrees)
                            .background(
                                color = if (isSelected) Color.Yellow.copy(alpha = 0.22f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectZoom(zoom)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            color = if (isSelected) Color.Yellow else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Main Controls Row (Gallery / Pause | Shutter | Flip Lens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.isRecording) {
                // Pause / Resume Video Recording Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .rotate(uiRotationDegrees)
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(alpha = 0.75f))
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTogglePauseResumeVideo()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (uiState.isRecordingPaused) "Resume Video" else "Pause Video",
                        tint = if (uiState.isRecordingPaused) Color.Yellow else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                // Gallery Thumbnail
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .rotate(uiRotationDegrees)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenGallery()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.lastCapturedMediaUri != null) {
                        AsyncImage(
                            model = uiState.lastCapturedMediaUri,
                            imageLoader = imageLoader,
                            contentDescription = "Gallery Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Shutter Button
            ShutterButton(
                isRecording = uiState.isRecording,
                isCountdownActive = uiState.countdownSecondsRemaining != null,
                mode = uiState.cameraMode,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShutterClick()
                }
            )

            // Flip Camera Button
            IconButton(
                onClick = {
                    if (!uiState.isRecording) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        flipSpinTarget += 180f
                        onSwitchLens()
                    }
                },
                enabled = !uiState.isRecording,
                modifier = Modifier
                    .size(52.dp)
                    .rotate(uiRotationDegrees + animatedFlipSpin)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = if (uiState.isRecording) Color.White.copy(alpha = 0.3f) else Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // Mode Selector Row (PHOTO | VIDEO | PRO)
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraMode.entries.forEach { mode ->
                val isSelected = uiState.cameraMode == mode
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color.Yellow.copy(alpha = 0.2f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable(enabled = !uiState.isRecording) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectMode(mode)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (isSelected) {
                            Color.Yellow
                        } else if (uiState.isRecording) {
                            Color.White.copy(alpha = 0.35f)
                        } else {
                            Color.White
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ShutterButton(
    isRecording: Boolean,
    isCountdownActive: Boolean,
    mode: CameraMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val innerRadius by animateFloatAsState(
        targetValue = if (isRecording) 18f else 30f,
        animationSpec = tween(durationMillis = 200),
        label = "shutterInnerRadius"
    )

    val ringColor = if (mode == CameraMode.PRO) Color.Yellow else Color.White

    Box(
        modifier = modifier
            .size(80.dp)
            .border(4.dp, ringColor, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isCountdownActive) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel Timer",
                tint = Color.Yellow,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Canvas(modifier = Modifier.size(60.dp)) {
                val color = if (mode == CameraMode.VIDEO) Color.Red else Color.White
                if (isRecording) {
                    val sizePx = innerRadius.dp.toPx() * 2
                    drawRoundRect(
                        color = color,
                        topLeft = Offset((size.width - sizePx) / 2f, (size.height - sizePx) / 2f),
                        size = Size(sizePx, sizePx),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                } else {
                    drawCircle(color = color, radius = innerRadius.dp.toPx())
                }
            }
        }
    }
}
