package com.example.procamerax.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.procamerax.viewmodel.CameraMode
import com.example.procamerax.viewmodel.CameraViewModel
import com.example.procamerax.viewmodel.FpsMode
import androidx.camera.core.DynamicRange
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import android.util.Range
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .build()
    }
    
    // Create PreviewView
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    
    // Hold reference to UseCases for updating rotation
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<androidx.camera.core.CameraInfo?>(null) }
    var zoomState by remember { mutableStateOf<androidx.camera.core.ZoomState?>(null) }

    DisposableEffect(cameraInfo) {
        val liveData = cameraInfo?.zoomState
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { state ->
            zoomState = state
        }
        liveData?.observeForever(observer)
        onDispose {
            liveData?.removeObserver(observer)
        }
    }

    var isUltraHdrActive by remember { mutableStateOf(false) }

    val activity = remember(context) { context.findActivity() }

    // Dynamic HDR Window Color Mode
    LaunchedEffect(uiState.cameraMode, uiState.isVideoHdrEnabled, uiState.lensFacing, activity) {
        val isHdrActive = if (uiState.cameraMode == CameraMode.VIDEO && uiState.isVideoHdrEnabled) {
            try {
                val cameraProvider = context.getCameraProvider()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(uiState.lensFacing)
                    .build()
                val localCameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                localCameraInfo.querySupportedDynamicRanges(
                    setOf(DynamicRange.SDR, DynamicRange.HLG_10_BIT)
                ).contains(DynamicRange.HLG_10_BIT)
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        activity?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                window.colorMode = if (isHdrActive) {
                    android.content.pm.ActivityInfo.COLOR_MODE_HDR
                } else {
                    android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    // Reset window color mode to default when Composable is disposed
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    val zoomOptions = remember(zoomState) {
        val minZoom = zoomState?.minZoomRatio ?: 0.5f
        val maxZoom = zoomState?.maxZoomRatio ?: 10f
        val options = mutableListOf<Float>()

        if (minZoom <= 0.5f) options.add(0.5f)
        if (minZoom <= 1f) options.add(1f)
        if (maxZoom >= 2f) options.add(2f)
        if (maxZoom >= 5f) options.add(5f)
        if (maxZoom >= 10f) options.add(10f)
        if (maxZoom >= 30f) options.add(30f)
        if (maxZoom >= 100f) options.add(100f)

        val commonOptions = listOf(0.5f, 1f, 2f, 5f, 10f, 30f, 100f)
        if (maxZoom > 1f && maxZoom !in commonOptions) {
            options.add(maxZoom)
        }

        options.filter { it in minZoom..maxZoom }.distinct().sorted().toList()
    }

    // Dynamic Light Sensor for Auto Night Mode
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager }
    val lightSensor = remember(sensorManager) { sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) }

    DisposableEffect(uiState.isAutoNightModeEnabled, lightSensor) {
        if (uiState.isAutoNightModeEnabled && lightSensor != null) {
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                    event?.values?.get(0)?.let { lux ->
                        val isDark = lux < 10.0f
                        if (isDark != viewModel.uiState.value.isNightModeActive) {
                            viewModel.setNightModeActive(isDark)
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        } else {
            viewModel.setNightModeActive(false)
            onDispose {}
        }
    }

    // Orientation event listener
    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCaptureUseCase?.targetRotation = rotation
                videoCaptureUseCase?.targetRotation = rotation
            }
        }
        orientationEventListener.enable()
        onDispose {
            orientationEventListener.disable()
        }
    }

    // Dynamic Zoom Effect
    LaunchedEffect(uiState.zoomLevel, cameraControl) {
        cameraControl?.setZoomRatio(uiState.zoomLevel)
    }

    // Flash Effect
    LaunchedEffect(uiState.photoFlashMode, imageCaptureUseCase) {
        if (uiState.cameraMode == CameraMode.PHOTO) {
            imageCaptureUseCase?.flashMode = uiState.photoFlashMode
        }
    }

    // Torch Effect
    LaunchedEffect(uiState.isTorchEnabled, cameraControl, uiState.cameraMode) {
        if (uiState.cameraMode == CameraMode.VIDEO) {
            cameraControl?.enableTorch(uiState.isTorchEnabled)
        } else {
            cameraControl?.enableTorch(false)
        }
    }

    // Set up camera use cases
    LaunchedEffect(uiState.lensFacing, uiState.cameraMode, uiState.imageCaptureMode, uiState.isVideoStabilizationEnabled, uiState.videoQuality, uiState.videoFps, uiState.isVideoHdrEnabled, uiState.isNightModeActive) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val baseCameraSelector = CameraSelector.Builder()
            .requireLensFacing(uiState.lensFacing)
            .build()

        val isNightExtensionSupported = try {
            val extensionsManager = context.getExtensionsManager(cameraProvider)
            extensionsManager.isExtensionAvailable(baseCameraSelector, androidx.camera.extensions.ExtensionMode.NIGHT)
        } catch (e: Exception) {
            false
        }

        val cameraSelector = if (uiState.cameraMode == CameraMode.PHOTO && uiState.isNightModeActive && isNightExtensionSupported) {
            try {
                val extensionsManager = context.getExtensionsManager(cameraProvider)
                extensionsManager.getExtensionEnabledCameraSelector(baseCameraSelector, androidx.camera.extensions.ExtensionMode.NIGHT)
            } catch (e: Exception) {
                baseCameraSelector
            }
        } else {
            baseCameraSelector
        }

        val isHlg10Supported = try {
            val localCameraInfo = cameraProvider.getCameraInfo(cameraSelector)
            localCameraInfo.querySupportedDynamicRanges(
                setOf(DynamicRange.SDR, DynamicRange.HLG_10_BIT)
            ).contains(DynamicRange.HLG_10_BIT)
        } catch (e: Exception) {
            false
        }

        val dynamicRange = if (uiState.cameraMode == CameraMode.VIDEO && uiState.isVideoHdrEnabled && isHlg10Supported) {
            DynamicRange.HLG_10_BIT
        } else {
            DynamicRange.SDR
        }

        val previewBuilder = Preview.Builder()
            .setPreviewStabilizationEnabled(uiState.isVideoStabilizationEnabled)
            .setDynamicRange(dynamicRange)

        if (uiState.cameraMode == CameraMode.VIDEO && uiState.videoFps != FpsMode.AUTO) {
            uiState.videoFps.value?.let { fps ->
                val fpsRange = Range(fps, fps)
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }
        }

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            if (uiState.cameraMode == CameraMode.PHOTO) {
                val localCameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                val isUltraHdrSupported = ImageCapture.getImageCaptureCapabilities(localCameraInfo)
                    .supportedOutputFormats
                    .contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)

                val builder = ImageCapture.Builder()
                    .setCaptureMode(uiState.imageCaptureMode)
                    .setFlashMode(uiState.photoFlashMode)
                
                if (isUltraHdrSupported && uiState.imageCaptureMode != ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG) {
                    builder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                    isUltraHdrActive = true
                } else {
                    isUltraHdrActive = false
                }

                val imageCapture = builder.build()
                imageCaptureUseCase = imageCapture
                viewModel.setImageCapture(imageCapture)
                
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                camera.cameraControl.setZoomRatio(uiState.zoomLevel)
            } else {
                isUltraHdrActive = false
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(uiState.videoQuality, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                    .build()
                val videoCaptureBuilder = VideoCapture.Builder<Recorder>(recorder)
                    .setVideoStabilizationEnabled(uiState.isVideoStabilizationEnabled)
                    .setDynamicRange(dynamicRange)

                if (uiState.videoFps != FpsMode.AUTO) {
                    uiState.videoFps.value?.let { fps ->
                        val fpsRange = Range(fps, fps)
                        Camera2Interop.Extender(videoCaptureBuilder)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
                    }
                }

                val videoCapture = videoCaptureBuilder.build()
                videoCaptureUseCase = videoCapture
                viewModel.setVideoCapture(videoCapture)
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, videoCapture
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                camera.cameraControl.setZoomRatio(uiState.zoomLevel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview taking available top space
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            var focusPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cameraControl, cameraInfo) {
                        detectTransformGestures { _, _, zoom, _ ->
                            cameraInfo?.zoomState?.value?.let { zoomState ->
                                val currentRatio = zoomState.zoomRatio
                                val minRatio = zoomState.minZoomRatio
                                val maxRatio = zoomState.maxZoomRatio
                                val newRatio = (currentRatio * zoom).coerceIn(minRatio, maxRatio)
                                viewModel.setZoomLevel(newRatio)
                            }
                        }
                    }
                    .pointerInput(cameraControl) {
                        detectTapGestures(
                            onTap = { offset ->
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
                                cameraControl?.startFocusAndMetering(action)
                                focusPoint = offset
                            }
                        )
                    }
            )

            // Focus Ring Animation
            focusPoint?.let { offset ->
                var showRing by remember { mutableStateOf(true) }
                LaunchedEffect(offset) {
                    showRing = true
                    kotlinx.coroutines.delay(1000)
                    showRing = false
                }
                
                val ringAlpha by animateFloatAsState(
                    targetValue = if (showRing) 1f else 0f,
                    animationSpec = tween(durationMillis = 500),
                    label = "focusRingAlpha"
                )

                if (ringAlpha > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White,
                            radius = 60f,
                            center = offset,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                            alpha = ringAlpha
                        )
                    }
                }
            }
            
            // Top Right Flash Control
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                IconButton(
                    onClick = {
                        if (uiState.cameraMode == CameraMode.PHOTO) {
                            viewModel.cyclePhotoFlashMode()
                        } else {
                            viewModel.toggleTorch()
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    val icon = if (uiState.cameraMode == CameraMode.PHOTO) {
                        when (uiState.photoFlashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            else -> Icons.Default.FlashOff
                        }
                    } else {
                        if (uiState.isTorchEnabled) Icons.Default.Highlight else Icons.Default.FlashOff
                    }
                    val tint = if (uiState.cameraMode == CameraMode.PHOTO) {
                        if (uiState.photoFlashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White
                    } else {
                        if (uiState.isTorchEnabled) Color.Yellow else Color.White
                    }
                    Icon(imageVector = icon, contentDescription = "Flash Control", tint = tint, modifier = Modifier.size(32.dp))
                }
            }

            // Top UI Elements (Capture Mode Selector & Indicators)
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Capture Mode Selector
                if (uiState.cameraMode == CameraMode.PHOTO) {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                                modifier = Modifier.clickable { viewModel.setImageCaptureMode(mode) }
                            )
                        }
                    }

                    // Auto Night Mode Toggle Pill for Photo Mode
                    Surface(
                        color = if (uiState.isAutoNightModeEnabled) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable { viewModel.toggleAutoNightMode() }
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
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (uiState.isAutoNightModeEnabled) "AUTO NIGHT ON" else "AUTO NIGHT OFF",
                                color = if (uiState.isAutoNightModeEnabled) Color.Yellow else Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                // Ultra HDR Indicator
                if (isUltraHdrActive && uiState.cameraMode == CameraMode.PHOTO) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "ULTRA HDR",
                            color = Color.Yellow,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (uiState.cameraMode == CameraMode.VIDEO) {
                    // Video Quality Selector
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                modifier = Modifier.clickable { viewModel.setVideoQuality(quality) }
                            )
                        }
                    }

                    // FPS Selector
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                modifier = Modifier.clickable(enabled = !isDisabled) { viewModel.setVideoFps(fps) }
                            )
                        }
                    }

                    // Toggles Row (Stabilization & HDR)
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Video Stabilization Toggle
                        Surface(
                            color = if (uiState.isVideoStabilizationEnabled) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier
                                .clickable { viewModel.toggleVideoStabilization() }
                        ) {
                            Text(
                                text = if (uiState.isVideoStabilizationEnabled) "STABILIZED" else "STABILIZATION OFF",
                                color = if (uiState.isVideoStabilizationEnabled) Color.Yellow else Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Video HDR Toggle
                        Surface(
                            color = if (uiState.isVideoHdrEnabled) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier
                                .clickable { viewModel.toggleVideoHdr() }
                        ) {
                            Text(
                                text = if (uiState.isVideoHdrEnabled) "HDR 10-BIT" else "HDR OFF",
                                color = if (uiState.isVideoHdrEnabled) Color.Yellow else Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            
            // Zoom level indicator overlay
            var showZoomIndicator by remember { mutableStateOf(false) }
            LaunchedEffect(uiState.zoomLevel) {
                showZoomIndicator = true
                kotlinx.coroutines.delay(1500)
                showZoomIndicator = false
            }

            val zoomAlpha by animateFloatAsState(
                targetValue = if (showZoomIndicator) 1f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "zoomIndicatorAlpha"
            )

            if (zoomAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .alpha(zoomAlpha)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", uiState.zoomLevel),
                        color = Color.Yellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Night Mode Active Pill
            if (uiState.isNightModeActive && uiState.cameraMode == CameraMode.PHOTO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
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

            // Hold Still Processing Overlay
            uiState.photoCaptureProgress?.let { progress ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            progress = progress.toFloat() / 100f,
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
                            text = "$progress%",
                            color = Color.Yellow,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Space Zoom Targeting HUD (when zoomed in 20x or higher)
            if (uiState.zoomLevel >= 20f) {
                var isZoomLocked by remember { mutableStateOf(false) }
                LaunchedEffect(uiState.zoomLevel) {
                    isZoomLocked = false
                    kotlinx.coroutines.delay(1500)
                    isZoomLocked = true
                }

                val hudColor = if (isZoomLocked) Color.Yellow else Color.White

                // Aiming Reticle in Center
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        // Draw outer target ring
                        drawCircle(
                            color = hudColor,
                            radius = 40f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                        // Draw crosshair lines
                        drawLine(
                            color = hudColor,
                            start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f - 20f),
                            end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f - 5f),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = hudColor,
                            start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + 5f),
                            end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + 20f),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = hudColor,
                            start = androidx.compose.ui.geometry.Offset(size.width / 2f - 20f, size.height / 2f),
                            end = androidx.compose.ui.geometry.Offset(size.width / 2f - 5f, size.height / 2f),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = hudColor,
                            start = androidx.compose.ui.geometry.Offset(size.width / 2f + 5f, size.height / 2f),
                            end = androidx.compose.ui.geometry.Offset(size.width / 2f + 20f, size.height / 2f),
                            strokeWidth = 2f
                        )
                    }
                }

                // HUD Pill Top-Left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 80.dp) // Offset below flash/top controls
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
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
                            text = if (isZoomLocked) "ZOOM LOCK ACTIVE" else "SPACE ZOOM ACTIVE",
                            color = hudColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Bottom Controls Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                zoomOptions.forEach { zoom ->
                    val text = if (zoom % 1f == 0f) "${zoom.toInt()}x" else "${zoom}x"
                    Text(
                        text = text,
                        color = if (kotlin.math.abs(uiState.zoomLevel - zoom) < 0.05f) Color.Yellow else Color.White,
                        modifier = Modifier.clickable {
                            val coercedZoom = cameraInfo?.zoomState?.value?.let { zoomState ->
                                zoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                            } ?: zoom
                            viewModel.setZoomLevel(coercedZoom)
                        }
                    )
                }
            }

            // Main Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable {
                            uiState.lastCapturedMediaUri?.let { uriString ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    val uri = Uri.parse(uriString)
                                    val mimeType = if (uriString.contains("video")) "video/*" else "image/*"
                                    setDataAndType(uri, mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
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

                // Shutter Button
                ShutterButton(
                    isRecording = uiState.isRecording,
                    mode = uiState.cameraMode,
                    onClick = {
                        if (uiState.cameraMode == CameraMode.PHOTO) {
                            viewModel.capturePhoto()
                        } else {
                            viewModel.captureVideo()
                        }
                    }
                )

                // Flip Camera
                IconButton(onClick = { viewModel.toggleLens() }) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Mode Selector
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "PHOTO",
                    color = if (uiState.cameraMode == CameraMode.PHOTO) Color.Yellow else Color.White,
                    modifier = Modifier.clickable { viewModel.setCameraMode(CameraMode.PHOTO) }
                )
                Text(
                    text = "VIDEO",
                    color = if (uiState.cameraMode == CameraMode.VIDEO) Color.Yellow else Color.White,
                    modifier = Modifier.clickable { viewModel.setCameraMode(CameraMode.VIDEO) }
                )
            }
        }
    }
}

@Composable
fun ShutterButton(isRecording: Boolean, mode: CameraMode, onClick: () -> Unit) {
    val innerRadius by animateFloatAsState(targetValue = if (isRecording) 20f else 30f, label = "radius")
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(60.dp)) {
            val color = if (mode == CameraMode.VIDEO) Color.Red else Color.White
            if (isRecording) {
                val sizePx = innerRadius.dp.toPx() * 2
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset((size.width - sizePx) / 2f, (size.height - sizePx) / 2f),
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                )
            } else {
                drawCircle(color = color, radius = innerRadius.dp.toPx())
            }
        }
    }
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}

fun Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

suspend fun Context.getExtensionsManager(cameraProvider: ProcessCameraProvider): androidx.camera.extensions.ExtensionsManager = suspendCoroutine { continuation ->
    val extensionsManagerFuture = androidx.camera.extensions.ExtensionsManager.getInstanceAsync(this, cameraProvider)
    extensionsManagerFuture.addListener({
        continuation.resume(extensionsManagerFuture.get())
    }, ContextCompat.getMainExecutor(this))
}
