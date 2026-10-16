package com.example.procamerax.ui.components.controls

import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.procamerax.viewmodel.CameraMode
import com.example.procamerax.viewmodel.CameraUiState
import com.example.procamerax.viewmodel.FpsMode
import com.example.procamerax.viewmodel.TimerMode

@Composable
fun TopBarControls(
    uiState: CameraUiState,
    isUltraHdrActive: Boolean,
    uiRotationDegrees: Float,
    onFlashOrTorchClick: () -> Unit,
    onCycleTimer: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleLeveler: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleHistogram: () -> Unit,
    onSetImageCaptureMode: (Int) -> Unit,
    onToggleAutoNightMode: () -> Unit,
    onSetVideoQuality: (Quality) -> Unit,
    onSetVideoFps: (FpsMode) -> Unit,
    onToggleVideoStabilization: () -> Unit,
    onToggleVideoHdr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primary Utility Icon Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash / Torch
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFlashOrTorchClick()
                },
                modifier = Modifier.rotate(uiRotationDegrees)
            ) {
                val icon = if (uiState.cameraMode == CameraMode.VIDEO) {
                    if (uiState.isTorchEnabled) Icons.Default.Highlight else Icons.Default.FlashOff
                } else {
                    when (uiState.photoFlashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        else -> Icons.Default.FlashOff
                    }
                }
                val tint = if (uiState.cameraMode == CameraMode.VIDEO) {
                    if (uiState.isTorchEnabled) Color.Yellow else Color.White
                } else {
                    if (uiState.photoFlashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Flash Control",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Timer (Photo/Pro) or Audio Mic (Video)
            if (uiState.cameraMode == CameraMode.VIDEO) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleAudio()
                    },
                    enabled = !uiState.isRecording,
                    modifier = Modifier.rotate(uiRotationDegrees)
                ) {
                    Icon(
                        imageVector = if (uiState.isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Toggle Microphone",
                        tint = if (uiState.isAudioEnabled) Color.White else Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCycleTimer()
                    },
                    modifier = Modifier.rotate(uiRotationDegrees)
                ) {
                    val timerIcon = when (uiState.timerMode) {
                        TimerMode.OFF -> Icons.Default.TimerOff
                        TimerMode.SEC_3 -> Icons.Default.Timer3
                        TimerMode.SEC_10 -> Icons.Default.Timer10
                    }
                    Icon(
                        imageVector = timerIcon,
                        contentDescription = "Shutter Timer",
                        tint = if (uiState.timerMode != TimerMode.OFF) Color.Yellow else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Aspect Ratio Badge
            Box(
                modifier = Modifier
                    .rotate(uiRotationDegrees)
                    .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
                    .clickable(enabled = !uiState.isRecording) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCycleAspectRatio()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.aspectRatioMode.label,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // 3x3 Composition Grid Toggle
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleGrid()
                },
                modifier = Modifier.rotate(uiRotationDegrees)
            ) {
                Icon(
                    imageVector = if (uiState.isGridEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "Toggle Grid",
                    tint = if (uiState.isGridEnabled) Color.Yellow else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Horizon Leveler Toggle
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleLeveler()
                },
                modifier = Modifier.rotate(uiRotationDegrees)
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = "Toggle Horizon Leveler",
                    tint = if (uiState.isLevelerEnabled) Color.Yellow else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Pro Mode Histogram Toggle
            if (uiState.cameraMode == CameraMode.PRO) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleHistogram()
                    },
                    modifier = Modifier.rotate(uiRotationDegrees)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Toggle Histogram",
                        tint = if (uiState.isHistogramEnabled) Color.Yellow else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Mode-Specific Secondary Controls
        if (uiState.cameraMode == CameraMode.PHOTO || uiState.cameraMode == CameraMode.PRO) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .background(Color.DarkGray.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val captureModes = listOf(
                    "ZSL" to ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG,
                    "MAX QUAL" to ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY,
                    "MIN LAT" to ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                captureModes.forEach { (label, mode) ->
                    Text(
                        text = label,
                        color = if (uiState.imageCaptureMode == mode) Color.Yellow else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetImageCaptureMode(mode)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.cameraMode == CameraMode.PHOTO) {
                    Surface(
                        color = if (uiState.isAutoNightModeEnabled) {
                            Color.Yellow.copy(alpha = 0.22f)
                        } else {
                            Color.DarkGray.copy(alpha = 0.55f)
                        },
                        shape = CircleShape,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleAutoNightMode()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = "Auto Night Mode",
                                tint = if (uiState.isAutoNightModeEnabled) Color.Yellow else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (uiState.isAutoNightModeEnabled) "AUTO NIGHT ON" else "AUTO NIGHT OFF",
                                color = if (uiState.isAutoNightModeEnabled) Color.Yellow else Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (isUltraHdrActive) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "ULTRA HDR",
                            color = Color.Yellow,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else if (uiState.cameraMode == CameraMode.VIDEO && !uiState.isRecording) {
            // Video Quality Selector
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .background(Color.DarkGray.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val videoQualities = listOf(
                    "SD" to Quality.SD,
                    "HD" to Quality.HD,
                    "FHD" to Quality.FHD,
                    "UHD" to Quality.UHD
                )
                videoQualities.forEach { (label, quality) ->
                    Text(
                        text = label,
                        color = if (uiState.videoQuality == quality) Color.Yellow else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetVideoQuality(quality)
                        }
                    )
                }
            }

            // FPS Selector
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(Color.DarkGray.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val fpsModes = listOf(
                    "AUTO" to FpsMode.AUTO,
                    "30 FPS" to FpsMode.FPS_30,
                    "60 FPS" to FpsMode.FPS_60
                )
                fpsModes.forEach { (label, fps) ->
                    val is60Fps = fps == FpsMode.FPS_60
                    val isDisabled = is60Fps && uiState.isVideoHdrEnabled
                    Text(
                        text = label,
                        color = if (uiState.videoFps == fps) {
                            Color.Yellow
                        } else if (isDisabled) {
                            Color.White.copy(alpha = 0.3f)
                        } else {
                            Color.White
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(enabled = !isDisabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetVideoFps(fps)
                        }
                    )
                }
            }

            // Stabilization & HDR Toggles
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = if (uiState.isVideoStabilizationEnabled) {
                        Color.Yellow.copy(alpha = 0.22f)
                    } else {
                        Color.DarkGray.copy(alpha = 0.55f)
                    },
                    shape = CircleShape,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleVideoStabilization()
                    }
                ) {
                    Text(
                        text = if (uiState.isVideoStabilizationEnabled) "STABILIZED" else "STABILIZATION OFF",
                        color = if (uiState.isVideoStabilizationEnabled) Color.Yellow else Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Surface(
                    color = if (uiState.isVideoHdrEnabled) {
                        Color.Yellow.copy(alpha = 0.22f)
                    } else {
                        Color.DarkGray.copy(alpha = 0.55f)
                    },
                    shape = CircleShape,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleVideoHdr()
                    }
                ) {
                    Text(
                        text = if (uiState.isVideoHdrEnabled) "HDR 10-BIT" else "HDR OFF",
                        color = if (uiState.isVideoHdrEnabled) Color.Yellow else Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
