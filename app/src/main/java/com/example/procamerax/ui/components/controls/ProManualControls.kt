package com.example.procamerax.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.procamerax.viewmodel.CameraUiState
import com.example.procamerax.viewmodel.PRO_ISO_OPTIONS
import com.example.procamerax.viewmodel.PRO_SHUTTER_OPTIONS
import com.example.procamerax.viewmodel.WhiteBalanceMode
import java.util.Locale

private enum class ProTab {
    WB, ISO, SHUTTER, EV, FOCUS
}

@Composable
fun ProManualControls(
    uiState: CameraUiState,
    onSetWhiteBalance: (WhiteBalanceMode) -> Unit,
    onSetIso: (Int?) -> Unit,
    onSetShutterSpeed: (Long?) -> Unit,
    onSetExposureIndex: (Int) -> Unit,
    onSetFocusDistance: (Float?) -> Unit,
    onResetProControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableStateOf<ProTab?>(ProTab.ISO) }

    val hasCustomValues = uiState.proIso != null ||
        uiState.proShutterSpeedNs != null ||
        uiState.proWhiteBalance != WhiteBalanceMode.AUTO ||
        uiState.proFocusDistance != null ||
        uiState.exposureCompensationIndex != 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Active Parameter Detail Strip
        when (selectedTab) {
            ProTab.WB -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WhiteBalanceMode.entries.forEach { wb ->
                        val isSelected = uiState.proWhiteBalance == wb
                        ProOptionChip(
                            text = wb.label,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetWhiteBalance(wb)
                            }
                        )
                    }
                }
            }
            ProTab.ISO -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRO_ISO_OPTIONS.forEach { isoValue ->
                        val isSelected = uiState.proIso == isoValue
                        ProOptionChip(
                            text = isoValue?.toString() ?: "AUTO",
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetIso(isoValue)
                            }
                        )
                    }
                }
            }
            ProTab.SHUTTER -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRO_SHUTTER_OPTIONS.forEach { option ->
                        val isSelected = uiState.proShutterSpeedNs == option.exposureTimeNs
                        ProOptionChip(
                            text = option.label,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetShutterSpeed(option.exposureTimeNs)
                            }
                        )
                    }
                }
            }
            ProTab.EV -> {
                val minEv = uiState.exposureCompensationRangeMin
                val maxEv = uiState.exposureCompensationRangeMax
                if (maxEv > minEv) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", minEv * uiState.exposureCompensationStep),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Slider(
                            value = uiState.exposureCompensationIndex.toFloat(),
                            onValueChange = { newVal ->
                                val target = newVal.toInt().coerceIn(minEv, maxEv)
                                if (target != uiState.exposureCompensationIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSetExposureIndex(target)
                                }
                            },
                            valueRange = minEv.toFloat()..maxEv.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Yellow,
                                activeTrackColor = Color.Yellow,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format(Locale.US, "+%.1f", maxEv * uiState.exposureCompensationStep),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            ProTab.FOCUS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProOptionChip(
                        text = "AF",
                        isSelected = uiState.proFocusDistance == null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetFocusDistance(null)
                        }
                    )
                    Text(
                        text = "∞",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = uiState.proFocusDistance ?: 0f,
                        onValueChange = { value ->
                            onSetFocusDistance(value.coerceIn(0f, 1f))
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Yellow,
                            activeTrackColor = Color.Yellow,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "MACRO",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
            null -> {}
        }

        // Bottom Parameter Dials Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProParameterDial(
                title = "WB",
                value = uiState.proWhiteBalance.label,
                isActive = selectedTab == ProTab.WB,
                isModified = uiState.proWhiteBalance != WhiteBalanceMode.AUTO,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == ProTab.WB) null else ProTab.WB
                }
            )

            ProParameterDial(
                title = "ISO",
                value = uiState.proIso?.toString() ?: "AUTO",
                isActive = selectedTab == ProTab.ISO,
                isModified = uiState.proIso != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == ProTab.ISO) null else ProTab.ISO
                }
            )

            val shutterLabel = PRO_SHUTTER_OPTIONS.firstOrNull {
                it.exposureTimeNs == uiState.proShutterSpeedNs
            }?.label ?: "AUTO"
            ProParameterDial(
                title = "S",
                value = shutterLabel,
                isActive = selectedTab == ProTab.SHUTTER,
                isModified = uiState.proShutterSpeedNs != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == ProTab.SHUTTER) null else ProTab.SHUTTER
                }
            )

            val evText = String.format(
                Locale.US,
                "%+.1f",
                uiState.exposureCompensationIndex * uiState.exposureCompensationStep
            )
            ProParameterDial(
                title = "EV",
                value = evText,
                isActive = selectedTab == ProTab.EV,
                isModified = uiState.exposureCompensationIndex != 0,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == ProTab.EV) null else ProTab.EV
                }
            )

            val focusText = uiState.proFocusDistance?.let {
                String.format(Locale.US, "MF %.1f", it)
            } ?: "AF"
            ProParameterDial(
                title = "FOCUS",
                value = focusText,
                isActive = selectedTab == ProTab.FOCUS,
                isModified = uiState.proFocusDistance != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == ProTab.FOCUS) null else ProTab.FOCUS
                }
            )

            if (hasCustomValues) {
                Text(
                    text = "RESET",
                    color = Color.Yellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Yellow.copy(alpha = 0.18f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetProControls()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ProParameterDial(
    title: String,
    value: String,
    isActive: Boolean,
    isModified: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(
                color = if (isActive) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isActive || isModified) Color.Yellow else Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = if (isModified) Color.Yellow else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProOptionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (isSelected) Color.Black else Color.White,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .background(
                color = if (isSelected) Color.Yellow else Color.DarkGray.copy(alpha = 0.65f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
