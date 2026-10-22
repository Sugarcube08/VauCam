package org.vaucam.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalCameraInfo
import androidx.camera.core.ExperimentalImageCaptureOutputFormat
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import org.vaucam.ui.components.controls.BottomBarControls
import org.vaucam.ui.components.controls.ProManualControls
import org.vaucam.ui.components.controls.TopBarControls
import org.vaucam.ui.components.overlays.CountdownTimerOverlay
import org.vaucam.ui.components.overlays.HistogramOverlay
import org.vaucam.ui.components.overlays.HoldStillProgressOverlay
import org.vaucam.ui.components.overlays.LuminanceHistogramAnalyzer
import org.vaucam.ui.components.overlays.NightSightActivePill
import org.vaucam.ui.components.overlays.RecordingTimerPill
import org.vaucam.ui.components.overlays.ShutterFlashOverlay
import org.vaucam.ui.components.overlays.SpaceZoomHud
import org.vaucam.ui.components.viewfinder.FocusExposureOverlay
import org.vaucam.ui.components.viewfinder.GridAndLevelOverlay
import org.vaucam.viewmodel.AspectRatioMode
import org.vaucam.viewmodel.CameraMode
import org.vaucam.viewmodel.CameraUiState
import org.vaucam.viewmodel.CameraViewModel
import org.vaucam.viewmodel.FpsMode
import org.vaucam.viewmodel.WhiteBalanceMode
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(
    ExperimentalCamera2Interop::class,
    ExperimentalCameraInfo::class,
    ExperimentalZeroShutterLag::class,
    ExperimentalImageCaptureOutputFormat::class
)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importLut(uri)
        }
    }

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearCameraReferences()
            analysisExecutor.shutdown()
        }
    }

    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }
    var minFocusDistance by remember { mutableFloatStateOf(10f) }
    var isNightExtensionAvailable by remember { mutableStateOf(false) }
    var isUltraHdrActive by remember { mutableStateOf(false) }

    DisposableEffect(cameraInfo) {
        val liveData = cameraInfo?.zoomState
        val observer = Observer<ZoomState> { state ->
            zoomState = state
        }
        liveData?.observeForever(observer)
        onDispose {
            liveData?.removeObserver(observer)
        }
    }

    val activity = remember(context) { context.findActivity() }

    // Screen Flash Max Brightness Override for Front Camera
    DisposableEffect(uiState.isScreenFlashActive, activity) {
        val window = activity?.window
        if (window != null && uiState.isScreenFlashActive) {
            val params = window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = params
        }
        onDispose {
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

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
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        activity?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.colorMode = if (isHdrActive) {
                    ActivityInfo.COLOR_MODE_HDR
                } else {
                    ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    // Check Night Sight Extension Support when lens changes
    LaunchedEffect(uiState.lensFacing) {
        isNightExtensionAvailable = try {
            val cameraProvider = context.getCameraProvider()
            val extensionsManager = context.getExtensionsManager(cameraProvider)
            val baseSelector = CameraSelector.Builder()
                .requireLensFacing(uiState.lensFacing)
                .build()
            extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT)
        } catch (_: Exception) {
            false
        }
    }

    // Ambient Light Sensor for Front Camera Auto Screen Flash & Auto Night Mode
    // Auto Night Mode activates below 7 lux sustained for 1.5s; deactivates above 15 lux sustained for 1.5s
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val lightSensor = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }
    var pendingNightModeTarget by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(lightSensor) {
        if (lightSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val lux = event?.values?.getOrNull(0) ?: return
                    val currentState = viewModel.uiState.value
                    // Ignore transient brightness spikes caused by the screen flash itself
                    if (currentState.isScreenFlashActive) return

                    viewModel.updateAmbientLux(lux)

                    if (currentState.isAutoNightModeEnabled) {
                        val currentlyActive = currentState.isNightModeActive
                        if (!currentlyActive && lux < 7.0f) {
                            pendingNightModeTarget = true
                        } else if (currentlyActive && lux > 15.0f) {
                            pendingNightModeTarget = false
                        } else if (lux in 7.0f..15.0f) {
                            pendingNightModeTarget = null
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    LaunchedEffect(uiState.isAutoNightModeEnabled) {
        if (!uiState.isAutoNightModeEnabled) {
            pendingNightModeTarget = null
            viewModel.setNightModeActive(false)
        } else {
            val lux = uiState.ambientLux
            if (lux != null) {
                if (!uiState.isNightModeActive && lux < 7.0f) {
                    pendingNightModeTarget = true
                } else if (uiState.isNightModeActive && lux > 15.0f) {
                    pendingNightModeTarget = false
                }
            }
        }
    }

    LaunchedEffect(pendingNightModeTarget, uiState.isAutoNightModeEnabled) {
        val target = pendingNightModeTarget ?: return@LaunchedEffect
        if (!uiState.isAutoNightModeEnabled) return@LaunchedEffect
        delay(1500L)
        if (pendingNightModeTarget == target && uiState.isNightModeActive != target) {
            viewModel.setNightModeActive(target)
        }
    }

    // Orientation Event Listener for UseCase Rotation AND Smooth UI Icon Rotation
    var targetUiRotation by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val surfaceRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCaptureUseCase?.targetRotation = surfaceRotation
                videoCaptureUseCase?.targetRotation = surfaceRotation

                targetUiRotation = when (surfaceRotation) {
                    Surface.ROTATION_90 -> 90f
                    Surface.ROTATION_180 -> 180f
                    Surface.ROTATION_270 -> -90f
                    else -> 0f
                }
            }
        }
        orientationEventListener.enable()
        onDispose {
            orientationEventListener.disable()
        }
    }

    val animatedUiRotation by animateFloatAsState(
        targetValue = targetUiRotation,
        animationSpec = tween(durationMillis = 280),
        label = "uiIconRotation"
    )

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

    // Dynamic Zoom Effect
    LaunchedEffect(uiState.zoomLevel, cameraControl) {
        cameraControl?.setZoomRatio(uiState.zoomLevel)
    }

    // Exposure Compensation Effect
    LaunchedEffect(uiState.exposureCompensationIndex, cameraControl) {
        cameraControl?.setExposureCompensationIndex(uiState.exposureCompensationIndex)
    }

    // Flash Effect
    LaunchedEffect(uiState.photoFlashMode, imageCaptureUseCase, uiState.lensFacing) {
        if (uiState.cameraMode != CameraMode.VIDEO) {
            // Front camera uses screen flash overlay, so hardware flash stays off on front lens
            val effectiveFlash = if (uiState.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                ImageCapture.FLASH_MODE_OFF
            } else {
                uiState.photoFlashMode
            }
            imageCaptureUseCase?.flashMode = effectiveFlash
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

    // Real-time Camera2Interop Pro Controls (WB, ISO, Shutter Speed, Manual Focus) without rebinding!
    LaunchedEffect(
        cameraControl,
        cameraInfo,
        uiState.cameraMode,
        uiState.proWhiteBalance,
        uiState.proIso,
        uiState.proShutterSpeedNs,
        uiState.proFocusDistance,
        uiState.exposureCompensationIndex,
        uiState.exposureCompensationStep,
        minFocusDistance
    ) {
        val control = cameraControl ?: return@LaunchedEffect
        applyProCaptureRequestOptions(
            control = control,
            info = cameraInfo,
            uiState = uiState,
            minFocusDistance = minFocusDistance
        )
    }

    // Bind Camera Use Cases
    val shouldUseNightExtension = uiState.cameraMode == CameraMode.PHOTO &&
        uiState.isNightModeActive &&
        isNightExtensionAvailable

    LaunchedEffect(
        uiState.lensFacing,
        uiState.cameraMode,
        uiState.imageCaptureMode,
        uiState.isVideoStabilizationEnabled,
        uiState.videoQuality,
        uiState.videoFps,
        uiState.isVideoHdrEnabled,
        shouldUseNightExtension,
        uiState.aspectRatioMode.cameraXAspectRatio
    ) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val baseCameraSelector = CameraSelector.Builder()
            .requireLensFacing(uiState.lensFacing)
            .build()

        val cameraSelector = if (shouldUseNightExtension) {
            try {
                val extensionsManager = context.getExtensionsManager(cameraProvider)
                extensionsManager.getExtensionEnabledCameraSelector(
                    baseCameraSelector,
                    ExtensionMode.NIGHT
                )
            } catch (_: Exception) {
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
        } catch (_: Exception) {
            false
        }

        val dynamicRange = if (uiState.cameraMode == CameraMode.VIDEO && uiState.isVideoHdrEnabled && isHlg10Supported) {
            DynamicRange.HLG_10_BIT
        } else {
            DynamicRange.SDR
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    uiState.aspectRatioMode.cameraXAspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        // Enable preview stabilization only in VIDEO mode so 3-stream Pro mode (Preview + ImageCapture + ImageAnalysis)
        // does not hit CameraX's 2-stream preview-stabilization surface combination limit on Pixel devices.
        val isPreviewStabilizationActive =
            uiState.cameraMode == CameraMode.VIDEO && uiState.isVideoStabilizationEnabled

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setPreviewStabilizationEnabled(isPreviewStabilizationActive)
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
            if (uiState.cameraMode == CameraMode.PHOTO || uiState.cameraMode == CameraMode.PRO) {
                val localCameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                val isUltraHdrSupported = ImageCapture.getImageCaptureCapabilities(localCameraInfo)
                    .supportedOutputFormats
                    .contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)

                val effectiveFlash = if (uiState.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    ImageCapture.FLASH_MODE_OFF
                } else {
                    uiState.photoFlashMode
                }

                val builder = ImageCapture.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setCaptureMode(uiState.imageCaptureMode)
                    .setFlashMode(effectiveFlash)

                // Ultra HDR (JPEG_R) in CameraX 1.4 only supports 2-surface combinations (Preview + ImageCapture)
                // and cannot be combined with ImageAnalysis (Pro mode histogram) or CameraX Extensions.
                val shouldEnableUltraHdr = isUltraHdrSupported &&
                    uiState.cameraMode == CameraMode.PHOTO &&
                    !shouldUseNightExtension &&
                    uiState.imageCaptureMode != ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG

                if (shouldEnableUltraHdr) {
                    builder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                    isUltraHdrActive = true
                } else {
                    isUltraHdrActive = false
                }

                val imageCapture = builder.build()
                imageCaptureUseCase = imageCapture
                viewModel.setImageCapture(imageCapture)

                val camera = if (uiState.cameraMode == CameraMode.PRO) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                analysisExecutor,
                                LuminanceHistogramAnalyzer { bins ->
                                    viewModel.updateHistogramBins(bins)
                                }
                            )
                        }
                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                    } catch (_: IllegalArgumentException) {
                        // Fallback if a legacy camera device cannot bind 3 simultaneous streams
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }

                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                camera.cameraControl.setZoomRatio(uiState.zoomLevel)
                camera.cameraControl.setExposureCompensationIndex(uiState.exposureCompensationIndex)

                // Query Exposure & Focus capabilities
                val expState = camera.cameraInfo.exposureState
                viewModel.updateExposureCapabilities(
                    minIndex = expState.exposureCompensationRange.lower,
                    maxIndex = expState.exposureCompensationRange.upper,
                    step = expState.exposureCompensationStep.toFloat()
                )
                val detectedMinFocus = runCatching {
                    Camera2CameraInfo.from(camera.cameraInfo)
                        .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 10f
                }.getOrDefault(10f)
                minFocusDistance = detectedMinFocus

                // Re-apply Camera2Interop Pro options after unbindAll/bindToLifecycle
                applyProCaptureRequestOptions(
                    control = camera.cameraControl,
                    info = camera.cameraInfo,
                    uiState = uiState,
                    minFocusDistance = detectedMinFocus
                )
            } else {
                isUltraHdrActive = false
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            uiState.videoQuality,
                            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                        )
                    )
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
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                camera.cameraControl.setZoomRatio(uiState.zoomLevel)
                camera.cameraControl.setExposureCompensationIndex(uiState.exposureCompensationIndex)

                val expState = camera.cameraInfo.exposureState
                viewModel.updateExposureCapabilities(
                    minIndex = expState.exposureCompensationRange.lower,
                    maxIndex = expState.exposureCompensationRange.upper,
                    step = expState.exposureCompensationStep.toFloat()
                )

                applyProCaptureRequestOptions(
                    control = camera.cameraControl,
                    info = camera.cameraInfo,
                    uiState = uiState,
                    minFocusDistance = minFocusDistance
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Viewfinder Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            var focusPoint by remember { mutableStateOf<Offset?>(null) }

            val aspectRatioModifier = uiState.aspectRatioMode.ratioValue?.let { ratio ->
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clipToBounds()
            } ?: Modifier
                .fillMaxSize()
                .clipToBounds()

            // Aspect-Ratio-Framed Camera Preview Box
            Box(modifier = aspectRatioModifier) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(cameraControl, cameraInfo) {
                            detectTransformGestures { _, _, zoom, _ ->
                                cameraInfo?.zoomState?.value?.let { currentZoomState ->
                                    val currentRatio = currentZoomState.zoomRatio
                                    val minRatio = currentZoomState.minZoomRatio
                                    val maxRatio = currentZoomState.maxZoomRatio
                                    val newRatio = (currentRatio * zoom).coerceIn(minRatio, maxRatio)
                                    viewModel.setZoomLevel(newRatio)
                                }
                            }
                        }
                        .pointerInput(
                            cameraControl,
                            uiState.cameraMode,
                            uiState.proFocusDistance,
                            uiState.proIso,
                            uiState.proShutterSpeedNs
                        ) {
                            detectTapGestures(
                                onTap = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(offset.x, offset.y)
                                    val isProMode = uiState.cameraMode == CameraMode.PRO
                                    val useAf = !isProMode || uiState.proFocusDistance == null
                                    val useAe = !isProMode || (uiState.proIso == null && uiState.proShutterSpeedNs == null)
                                    val flags = (if (useAf) FocusMeteringAction.FLAG_AF else 0) or
                                        (if (useAe) FocusMeteringAction.FLAG_AE else 0)
                                    if (flags != 0) {
                                        val action = FocusMeteringAction.Builder(point, flags).build()
                                        cameraControl?.startFocusAndMetering(action)
                                    }
                                    focusPoint = offset
                                }
                            )
                        }
                )

                // 3x3 Rule-of-Thirds Grid & Gravity Horizon Leveler
                GridAndLevelOverlay(
                    isGridEnabled = uiState.isGridEnabled,
                    isLevelerEnabled = uiState.isLevelerEnabled
                )

                // Tap-to-Focus Ring & Interactive Vertical Exposure Compensation Slider
                FocusExposureOverlay(
                    focusPoint = focusPoint,
                    exposureIndex = uiState.exposureCompensationIndex,
                    minExposureIndex = uiState.exposureCompensationRangeMin,
                    maxExposureIndex = uiState.exposureCompensationRangeMax,
                    exposureStep = uiState.exposureCompensationStep,
                    onExposureIndexChanged = { newIndex ->
                        viewModel.setExposureCompensationIndex(newIndex)
                    }
                )

                // Space Zoom HUD with Real Gyro/Accelerometer Stability Lock
                SpaceZoomHud(zoomLevel = uiState.zoomLevel)

                // Shutter Flash & Front Camera Screen Flash Overlay
                ShutterFlashOverlay(
                    shutterFlashTrigger = uiState.shutterFlashTrigger,
                    isScreenFlashActive = uiState.isScreenFlashActive
                )
            }

            // Top Utility & Mode Bar
            TopBarControls(
                uiState = uiState,
                isUltraHdrActive = isUltraHdrActive,
                uiRotationDegrees = animatedUiRotation,
                onFlashOrTorchClick = {
                    if (uiState.cameraMode == CameraMode.VIDEO) {
                        viewModel.toggleTorch()
                    } else {
                        viewModel.cyclePhotoFlashMode()
                    }
                },
                onCycleTimer = { viewModel.cycleTimerMode() },
                onCycleAspectRatio = { viewModel.cycleAspectRatio() },
                onToggleGrid = { viewModel.toggleGrid() },
                onToggleLeveler = { viewModel.toggleLeveler() },
                onToggleAudio = { viewModel.toggleAudioEnabled() },
                onToggleHistogram = { viewModel.toggleHistogram() },
                onSetImageCaptureMode = { viewModel.setImageCaptureMode(it) },
                onToggleAutoNightMode = { viewModel.toggleAutoNightMode() },
                onSetVideoQuality = { viewModel.setVideoQuality(it) },
                onSetVideoFps = { viewModel.setVideoFps(it) },
                onToggleVideoStabilization = { viewModel.toggleVideoStabilization() },
                onToggleVideoHdr = { viewModel.toggleVideoHdr() },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // LUT import controls. This grades exported still copies; preview/video LUT processing is not yet wired.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 76.dp)
            ) {
                Text(
                    text = if (uiState.activeLutTitle == null) "IMPORT LUT" else "LUT: ${uiState.activeLutTitle}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                        .clickable { lutPicker.launch(arrayOf("*/*")) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (uiState.activeLutTitle != null) {
                    Text(
                        text = "Disable",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .padding(top = 4.dp, start = 8.dp)
                            .clickable { viewModel.clearLut() }
                    )
                }
                uiState.lutError?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Video Recording Timer Pill (MM:SS + Pause status)
            RecordingTimerPill(
                isRecording = uiState.isRecording,
                isPaused = uiState.isRecordingPaused,
                durationSeconds = uiState.recordingDurationSeconds,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            )

            // Live Luminance Histogram Overlay (Pro Mode)
            HistogramOverlay(
                bins = uiState.histogramBins,
                isVisible = uiState.cameraMode == CameraMode.PRO && uiState.isHistogramEnabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 112.dp, end = 16.dp)
            )

            // Countdown Timer Overlay (3s / 10s)
            CountdownTimerOverlay(
                secondsRemaining = uiState.countdownSecondsRemaining
            )

            // Fading Zoom Ratio Pill
            var showZoomIndicator by remember { mutableStateOf(false) }
            LaunchedEffect(uiState.zoomLevel) {
                showZoomIndicator = true
                delay(1500)
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
                        .padding(bottom = 16.dp)
                        .alpha(zoomAlpha)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", uiState.zoomLevel),
                        color = Color.Yellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Night Sight Active Pill
            NightSightActivePill(
                isVisible = uiState.isNightModeActive && uiState.cameraMode == CameraMode.PHOTO,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            )

            // Hold Still Processing Overlay
            HoldStillProgressOverlay(progress = uiState.photoCaptureProgress)
        }

        // Pro Manual Dials Panel (Shown in PRO mode)
        if (uiState.cameraMode == CameraMode.PRO) {
            ProManualControls(
                uiState = uiState,
                onSetWhiteBalance = { viewModel.setProWhiteBalance(it) },
                onSetIso = { viewModel.setProIso(it) },
                onSetShutterSpeed = { viewModel.setProShutterSpeed(it) },
                onSetExposureIndex = { viewModel.setExposureCompensationIndex(it) },
                onSetFocusDistance = { viewModel.setProFocusDistance(it) },
                onResetProControls = { viewModel.resetProControls() }
            )
        }

        // Bottom Controls Bar
        BottomBarControls(
            uiState = uiState,
            zoomOptions = zoomOptions,
            imageLoader = imageLoader,
            uiRotationDegrees = animatedUiRotation,
            onSelectZoom = { zoom ->
                val coercedZoom = cameraInfo?.zoomState?.value?.let { currentZoomState ->
                    zoom.coerceIn(currentZoomState.minZoomRatio, currentZoomState.maxZoomRatio)
                } ?: zoom
                viewModel.setZoomLevel(coercedZoom)
            },
            onOpenGallery = {
                uiState.lastCapturedMediaUri?.let { uriString ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            val uri = Uri.parse(uriString)
                            val mimeType = if (uriString.contains("video")) "video/*" else "image/*"
                            setDataAndType(uri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                }
            },
            onTogglePauseResumeVideo = { viewModel.togglePauseResumeVideo() },
            onShutterClick = { viewModel.onShutterTriggered() },
            onSwitchLens = { viewModel.toggleLens() },
            onSelectMode = { viewModel.setCameraMode(it) }
        )
    }
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

suspend fun Context.getExtensionsManager(cameraProvider: ProcessCameraProvider): ExtensionsManager =
    suspendCoroutine { continuation ->
        val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(this, cameraProvider)
        extensionsManagerFuture.addListener({
            continuation.resume(extensionsManagerFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }

@OptIn(ExperimentalCamera2Interop::class)
private fun applyProCaptureRequestOptions(
    control: CameraControl,
    info: CameraInfo?,
    uiState: CameraUiState,
    minFocusDistance: Float
) {
    try {
        val camera2Control = Camera2CameraControl.from(control)
        val optionsBuilder = CaptureRequestOptions.Builder()

        if (uiState.cameraMode == CameraMode.PRO) {
            if (uiState.proWhiteBalance != WhiteBalanceMode.AUTO) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    uiState.proWhiteBalance.awbMode
                )
            }

            if (uiState.proIso != null || uiState.proShutterSpeedNs != null) {
                val camera2Info = info?.let {
                    try {
                        Camera2CameraInfo.from(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                val isoRange = camera2Info?.getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
                )
                val exposureTimeRange = camera2Info?.getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
                )
                val maxFrameDuration = camera2Info?.getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION
                )

                val evValue = (uiState.exposureCompensationIndex * uiState.exposureCompensationStep).toDouble()
                val evMultiplier = 2.0.pow(evValue)

                val rawIso = uiState.proIso ?: (400 * evMultiplier).roundToInt()
                val rawShutterNs = uiState.proShutterSpeedNs
                    ?: if (uiState.proIso != null) {
                        (16_666_667L * evMultiplier).roundToLong()
                    } else {
                        16_666_667L
                    }

                val clampedIso = if (isoRange != null) {
                    rawIso.coerceIn(isoRange.lower, isoRange.upper)
                } else {
                    rawIso.coerceIn(50, 100_000)
                }

                val clampedShutterNs = if (exposureTimeRange != null) {
                    rawShutterNs.coerceIn(exposureTimeRange.lower, exposureTimeRange.upper)
                } else {
                    rawShutterNs.coerceIn(10_000L, 10_000_000_000L)
                }

                val targetFrameDuration = maxOf(clampedShutterNs, 33_333_333L).let { duration ->
                    if (maxFrameDuration != null && maxFrameDuration > 0L) {
                        duration.coerceAtMost(maxFrameDuration)
                    } else {
                        duration
                    }
                }

                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.FLASH_MODE,
                    CameraMetadata.FLASH_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    clampedIso
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    clampedShutterNs
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_FRAME_DURATION,
                    targetFrameDuration
                )
            }

            if (uiState.proFocusDistance != null && minFocusDistance > 0f) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                val targetFocusDiopters = (uiState.proFocusDistance * minFocusDistance)
                    .coerceIn(0f, minFocusDistance)
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    targetFocusDiopters
                )
            }
        }

        camera2Control.setCaptureRequestOptions(optionsBuilder.build())
    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to apply Pro camera controls", e)
    }
}

