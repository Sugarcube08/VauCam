package org.vaucam.ui.components.controls

import org.vaucam.ui.theme.VauCamBrand
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbShade
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.vaucam.viewmodel.CameraUiState
import org.vaucam.viewmodel.PRO_ISO_OPTIONS
import org.vaucam.viewmodel.PRO_SHUTTER_OPTIONS
import org.vaucam.viewmodel.WhiteBalanceMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class RealmeProTab {
    ISO, SHUTTER, EV, FOCUS, WB
}

/**
 * Realme Camera-style Expert/PRO manual controls with:
 * 1. Signature Realme Yellow highlights and frosted dark container
 * 2. Interactive graduated slider rulers for ISO, Shutter, EV, Focus, and White Balance
 * 3. Prominent "AUTO" toggle buttons on the left of each ruler
 * 4. Distinct manual-override indicators (amber dots & yellow value readouts)
 * 5. One-tap Reset button (↺) to restore all parameters to default
 */
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
    var selectedTab by remember { mutableStateOf<RealmeProTab?>(RealmeProTab.ISO) }

    val hasCustomValues = uiState.proIso != null ||
        uiState.proShutterSpeedNs != null ||
        uiState.proWhiteBalance != WhiteBalanceMode.AUTO ||
        uiState.proFocusDistance != null ||
        uiState.exposureCompensationIndex != 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(VauCamBrand.Glass, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Active Parameter Adjustment Ruler Panel
        AnimatedVisibility(
            visible = selectedTab != null,
            enter = expandVertically(tween(220)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(160))
        ) {
            when (selectedTab) {
                RealmeProTab.ISO -> {
                    RealmeIsoRuler(
                        currentIso = uiState.proIso,
                        onSetIso = onSetIso
                    )
                }
                RealmeProTab.SHUTTER -> {
                    RealmeShutterRuler(
                        currentShutterSpeedNs = uiState.proShutterSpeedNs,
                        onSetShutterSpeed = onSetShutterSpeed
                    )
                }
                RealmeProTab.EV -> {
                    RealmeEvRuler(
                        currentIndex = uiState.exposureCompensationIndex,
                        minIndex = uiState.exposureCompensationRangeMin,
                        maxIndex = uiState.exposureCompensationRangeMax,
                        step = uiState.exposureCompensationStep,
                        onSetIndex = onSetExposureIndex
                    )
                }
                RealmeProTab.FOCUS -> {
                    RealmeFocusRuler(
                        currentFocus = uiState.proFocusDistance,
                        onSetFocusDistance = onSetFocusDistance
                    )
                }
                RealmeProTab.WB -> {
                    RealmeWbRuler(
                        currentWb = uiState.proWhiteBalance,
                        onSetWb = onSetWhiteBalance
                    )
                }
                null -> {}
            }
        }

        if (selectedTab != null) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom Parameter Dials Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO
            RealmeParameterItem(
                label = "ISO",
                value = uiState.proIso?.toString() ?: "AUTO",
                isSelected = selectedTab == RealmeProTab.ISO,
                isModified = uiState.proIso != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == RealmeProTab.ISO) null else RealmeProTab.ISO
                }
            )

            // Shutter Speed (S)
            val shutterLabel = PRO_SHUTTER_OPTIONS.firstOrNull {
                it.exposureTimeNs == uiState.proShutterSpeedNs
            }?.label ?: "AUTO"
            RealmeParameterItem(
                label = "S",
                value = shutterLabel,
                isSelected = selectedTab == RealmeProTab.SHUTTER,
                isModified = uiState.proShutterSpeedNs != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == RealmeProTab.SHUTTER) null else RealmeProTab.SHUTTER
                }
            )

            // EV
            val evVal = uiState.exposureCompensationIndex * uiState.exposureCompensationStep
            val evText = if (uiState.exposureCompensationIndex == 0) "0.0" else String.format(Locale.US, "%+.1f", evVal)
            RealmeParameterItem(
                label = "EV",
                value = evText,
                isSelected = selectedTab == RealmeProTab.EV,
                isModified = uiState.exposureCompensationIndex != 0,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == RealmeProTab.EV) null else RealmeProTab.EV
                }
            )

            // Focus (AF / MF)
            val focusText = uiState.proFocusDistance?.let {
                String.format(Locale.US, "%.2f", it)
            } ?: "AUTO"
            RealmeParameterItem(
                label = if (uiState.proFocusDistance != null) "MF" else "AF",
                value = focusText,
                isSelected = selectedTab == RealmeProTab.FOCUS,
                isModified = uiState.proFocusDistance != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == RealmeProTab.FOCUS) null else RealmeProTab.FOCUS
                }
            )

            // White Balance (WB)
            RealmeParameterItem(
                label = "WB",
                value = uiState.proWhiteBalance.label,
                isSelected = selectedTab == RealmeProTab.WB,
                isModified = uiState.proWhiteBalance != WhiteBalanceMode.AUTO,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = if (selectedTab == RealmeProTab.WB) null else RealmeProTab.WB
                }
            )

            // Reset Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (hasCustomValues) VauCamBrand.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)
                    )
                    .clickable(enabled = hasCustomValues) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onResetProControls()
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Pro Parameters",
                    tint = if (hasCustomValues) VauCamBrand.Accent else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Individual Realme parameter button with:
 * - Parameter title (e.g. ISO)
 * - Active value (e.g. 400 or AUTO)
 * - Yellow amber dot indicator when custom value is set
 * - Frosted pill highlight when tab is selected
 */
@Composable
private fun RealmeParameterItem(
    label: String,
    value: String,
    isSelected: Boolean,
    isModified: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Small amber indicator dot for manual overrides
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    if (isModified) VauCamBrand.Accent else Color.Transparent,
                    CircleShape
                )
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            color = if (isModified || isSelected) VauCamBrand.Accent else Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            color = if (isModified) VauCamBrand.Accent else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isModified || isSelected) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

/**
 * Realme ISO Horizontal Graduated Ruler
 */
@Composable
private fun RealmeIsoRuler(
    currentIso: Int?,
    onSetIso: (Int?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isoValues = listOf(100, 200, 400, 800, 1600, 3200)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current Readout
        Text(
            text = if (currentIso == null) "ISO AUTO" else "ISO $currentIso",
            color = VauCamBrand.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Auto Button on the Left
            RealmeAutoPill(
                isAuto = currentIso == null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetIso(null)
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Horizontal Values Strip
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                isoValues.forEach { iso ->
                    val isSelected = currentIso == iso
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) VauCamBrand.Accent else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetIso(iso)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$iso",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Realme Shutter Speed Horizontal Graduated Ruler
 */
@Composable
private fun RealmeShutterRuler(
    currentShutterSpeedNs: Long?,
    onSetShutterSpeed: (Long?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val options = PRO_SHUTTER_OPTIONS.filter { it.exposureTimeNs != null }

    val activeOption = options.firstOrNull { it.exposureTimeNs == currentShutterSpeedNs }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current Readout
        Text(
            text = if (activeOption == null) "SHUTTER AUTO" else "S ${activeOption.label}s",
            color = VauCamBrand.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RealmeAutoPill(
                isAuto = currentShutterSpeedNs == null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetShutterSpeed(null)
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { opt ->
                    val isSelected = currentShutterSpeedNs == opt.exposureTimeNs
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) VauCamBrand.Accent else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetShutterSpeed(opt.exposureTimeNs)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = opt.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Realme EV Interactive Graduated Slider Dial
 */
@Composable
private fun RealmeEvRuler(
    currentIndex: Int,
    minIndex: Int,
    maxIndex: Int,
    step: Float,
    onSetIndex: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentEv = currentIndex * step

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (currentIndex == 0) "EV 0.0" else String.format(Locale.US, "EV %+.1f", currentEv),
            color = VauCamBrand.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RealmeAutoPill(
                isAuto = currentIndex == 0,
                label = "0.0",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetIndex(0)
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Graduated Ruler Track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .pointerInput(minIndex, maxIndex, currentIndex) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val sensitivity = 16f
                            val deltaIndices = (-dragAmount.x / sensitivity).roundToInt()
                            if (deltaIndices != 0) {
                                val target = (currentIndex + deltaIndices).coerceIn(minIndex, maxIndex)
                                if (target != currentIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSetIndex(target)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    val w = size.width
                    val h = size.height
                    val centerX = w / 2f

                    val stepPx = (w / 14f).coerceAtLeast(8f)

                    for (i in minIndex..maxIndex) {
                        val tickX = centerX + (i - currentIndex) * stepPx
                        if (tickX in 0f..w) {
                            val isZero = i == 0
                            val isMain = i % 3 == 0 || isZero
                            val tickH = if (isZero) 24.dp.toPx() else if (isMain) 16.dp.toPx() else 8.dp.toPx()
                            val color = if (isZero) VauCamBrand.Accent else Color.White.copy(alpha = 0.5f)

                            drawLine(
                                color = color,
                                start = Offset(tickX, (h - tickH) / 2f),
                                end = Offset(tickX, (h + tickH) / 2f),
                                strokeWidth = if (isZero) 2.5.dp.toPx() else 1.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Center fixed Realme Yellow indicator line
                    drawLine(
                        color = VauCamBrand.Accent,
                        start = Offset(centerX, 2.dp.toPx()),
                        end = Offset(centerX, h - 2.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Realme Focus (AF/MF) Graduated Ruler
 */
@Composable
private fun RealmeFocusRuler(
    currentFocus: Float?,
    onSetFocusDistance: (Float?) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (currentFocus == null) "FOCUS AF" else String.format(Locale.US, "MF %.2f", currentFocus),
            color = VauCamBrand.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AF Button
            RealmeAutoPill(
                isAuto = currentFocus == null,
                label = "AF",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetFocusDistance(null)
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "🌷",
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.width(6.dp))

            // MF Slider Track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .pointerInput(currentFocus) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cur = currentFocus ?: 0.5f
                            val delta = dragAmount.x / 240f
                            val next = (cur + delta).coerceIn(0f, 1f)
                            val rounded = (next * 100f).roundToInt() / 100f
                            if (rounded != currentFocus) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetFocusDistance(rounded)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    val w = size.width
                    val h = size.height
                    val prog = currentFocus ?: 0f
                    val needleX = w * prog

                    // Track line
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Active track line
                    if (currentFocus != null) {
                        drawLine(
                            color = VauCamBrand.Accent,
                            start = Offset(0f, h / 2f),
                            end = Offset(needleX, h / 2f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Thumb dot
                        drawCircle(
                            color = VauCamBrand.Accent,
                            radius = 7.dp.toPx(),
                            center = Offset(needleX, h / 2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "∞",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Realme White Balance Presets & Kelvin Dial
 */
@Composable
private fun RealmeWbRuler(
    currentWb: WhiteBalanceMode,
    onSetWb: (WhiteBalanceMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val presets = listOf(
        Triple(WhiteBalanceMode.INCANDESCENT, "Tungsten", Icons.Default.WbIncandescent),
        Triple(WhiteBalanceMode.FLUORESCENT, "Fluor", Icons.Default.WbTwilight),
        Triple(WhiteBalanceMode.DAYLIGHT, "Daylight", Icons.Default.WbSunny),
        Triple(WhiteBalanceMode.CLOUDY, "Cloudy", Icons.Default.WbCloudy),
        Triple(WhiteBalanceMode.SHADE, "Shade", Icons.Default.WbShade)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (currentWb == WhiteBalanceMode.AUTO) "WB AUTO" else "WB ${currentWb.label}",
            color = VauCamBrand.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RealmeAutoPill(
                isAuto = currentWb == WhiteBalanceMode.AUTO,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetWb(WhiteBalanceMode.AUTO)
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { (wb, label, icon) ->
                    val isSelected = currentWb == wb
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) VauCamBrand.Accent else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetWb(wb)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Realme "AUTO" toggle chip
 */
@Composable
private fun RealmeAutoPill(
    isAuto: Boolean,
    label: String = "AUTO",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isAuto) VauCamBrand.Accent else Color.White.copy(alpha = 0.15f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isAuto) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
