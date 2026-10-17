package com.example.procamerax.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraMetadata
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class CameraMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    PRO("PRO")
}

enum class FpsMode(val value: Int?) {
    AUTO(null),
    FPS_30(30),
    FPS_60(60)
}

enum class AspectRatioMode(val label: String, val ratioValue: Float?, val cameraXAspectRatio: Int) {
    RATIO_4_3("4:3", 3f / 4f, AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", 9f / 16f, AspectRatio.RATIO_16_9),
    RATIO_1_1("1:1", 1f, AspectRatio.RATIO_4_3),
    RATIO_FULL("FULL", null, AspectRatio.RATIO_16_9)
}

enum class TimerMode(val seconds: Int, val label: String) {
    OFF(0, "OFF"),
    SEC_3(3, "3s"),
    SEC_10(10, "10s")
}

enum class WhiteBalanceMode(val label: String, val awbMode: Int) {
    AUTO("AUTO", CameraMetadata.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT("DAY", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("CLOUDY", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    INCANDESCENT("TUNGSTEN", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("FLUOR", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT),
    SHADE("SHADE", CameraMetadata.CONTROL_AWB_MODE_SHADE)
}

data class ProShutterOption(val label: String, val exposureTimeNs: Long?)

val PRO_ISO_OPTIONS = listOf<Int?>(null, 100, 200, 400, 800, 1600, 3200)

val PRO_SHUTTER_OPTIONS = listOf(
    ProShutterOption("AUTO", null),
    ProShutterOption("1/4000", 250_000L),
    ProShutterOption("1/1000", 1_000_000L),
    ProShutterOption("1/250", 4_000_000L),
    ProShutterOption("1/60", 16_666_667L),
    ProShutterOption("1/15", 66_666_667L),
    ProShutterOption("1/4", 250_000_000L),
    ProShutterOption("1s", 1_000_000_000L)
)

data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val isAudioEnabled: Boolean = true,
    val zoomLevel: Float = 1f,
    val lastCapturedMediaUri: String? = null,
    val imageCaptureMode: Int = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY,
    val isVideoStabilizationEnabled: Boolean = true,
    val videoQuality: Quality = Quality.FHD,
    val videoFps: FpsMode = FpsMode.AUTO,
    val isVideoHdrEnabled: Boolean = false,
    val isAutoNightModeEnabled: Boolean = false,
    val isNightModeActive: Boolean = false,
    val photoCaptureProgress: Int? = null,
    val photoFlashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    val backPhotoFlashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    val frontPhotoFlashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val isTorchEnabled: Boolean = false,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.RATIO_4_3,
    val timerMode: TimerMode = TimerMode.OFF,
    val countdownSecondsRemaining: Int? = null,
    val isGridEnabled: Boolean = false,
    val isLevelerEnabled: Boolean = false,
    val isHistogramEnabled: Boolean = true,
    val exposureCompensationIndex: Int = 0,
    val exposureCompensationRangeMin: Int = 0,
    val exposureCompensationRangeMax: Int = 0,
    val exposureCompensationStep: Float = 0.33f,
    val proIso: Int? = null,
    val proShutterSpeedNs: Long? = null,
    val proWhiteBalance: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val proFocusDistance: Float? = null,
    val shutterFlashTrigger: Long = 0L,
    val isScreenFlashActive: Boolean = false,
    val ambientLux: Float? = null,
    val histogramBins: List<Float> = List(64) { 0f }
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences? = runCatching {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private val initialBackFlashMode: Int = loadFlashMode(
        key = KEY_BACK_FLASH_MODE,
        defaultMode = ImageCapture.FLASH_MODE_AUTO
    )
    private val initialFrontFlashMode: Int = loadFlashMode(
        key = KEY_FRONT_FLASH_MODE,
        defaultMode = ImageCapture.FLASH_MODE_OFF
    )

    private val _uiState = MutableStateFlow(
        CameraUiState(
            photoFlashMode = initialBackFlashMode,
            backPhotoFlashMode = initialBackFlashMode,
            frontPhotoFlashMode = initialFrontFlashMode
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var countdownJob: Job? = null

    init {
        loadLatestMediaThumbnail()
    }

    private fun loadFlashMode(key: String, defaultMode: Int): Int {
        val saved = prefs?.getInt(key, defaultMode) ?: defaultMode
        return when (saved) {
            ImageCapture.FLASH_MODE_AUTO,
            ImageCapture.FLASH_MODE_ON,
            ImageCapture.FLASH_MODE_OFF -> saved
            else -> defaultMode
        }
    }

    fun setImageCapture(capture: ImageCapture?) {
        imageCapture = capture
    }

    fun setVideoCapture(capture: VideoCapture<Recorder>?) {
        videoCapture = capture
    }

    fun clearCameraReferences() {
        imageCapture = null
        videoCapture = null
    }

    fun loadLatestMediaThumbnail() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                var latestUri: Uri? = null
                var latestDateAdded = -1L

                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_ADDED
                )
                val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val dateAdded = cursor.getLong(1)
                        latestDateAdded = dateAdded
                        latestUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }
                }

                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val dateAdded = cursor.getLong(1)
                        if (dateAdded > latestDateAdded) {
                            latestUri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        }
                    }
                }

                latestUri?.let { uri ->
                    _uiState.update { current ->
                        if (current.lastCapturedMediaUri == null) {
                            current.copy(lastCapturedMediaUri = uri.toString())
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun toggleLens() {
        if (uiState.value.isRecording) return
        cancelCountdown()
        _uiState.update { current ->
            val newLens = if (current.lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            val newFlashMode = if (newLens == CameraSelector.LENS_FACING_FRONT) {
                current.frontPhotoFlashMode
            } else {
                current.backPhotoFlashMode
            }
            current.copy(
                lensFacing = newLens,
                photoFlashMode = newFlashMode,
                zoomLevel = 1f,
                exposureCompensationIndex = 0
            )
        }
    }

    fun setCameraMode(mode: CameraMode) {
        if (uiState.value.isRecording) return
        cancelCountdown()
        _uiState.update { it.copy(cameraMode = mode) }
    }

    fun setZoomLevel(zoom: Float) {
        _uiState.update { it.copy(zoomLevel = zoom) }
    }

    fun setImageCaptureMode(mode: Int) {
        _uiState.update { it.copy(imageCaptureMode = mode) }
    }

    fun toggleVideoStabilization() {
        _uiState.update { it.copy(isVideoStabilizationEnabled = !it.isVideoStabilizationEnabled) }
    }

    fun setVideoQuality(quality: Quality) {
        if (uiState.value.isRecording) return
        _uiState.update { it.copy(videoQuality = quality) }
    }

    fun setVideoFps(fps: FpsMode) {
        if (uiState.value.isRecording) return
        _uiState.update { current ->
            val isHdrEnabled = if (fps == FpsMode.FPS_60) false else current.isVideoHdrEnabled
            current.copy(videoFps = fps, isVideoHdrEnabled = isHdrEnabled)
        }
    }

    fun toggleVideoHdr() {
        if (uiState.value.isRecording) return
        _uiState.update { current ->
            val nextHdr = !current.isVideoHdrEnabled
            val nextFps = if (nextHdr && current.videoFps == FpsMode.FPS_60) FpsMode.AUTO else current.videoFps
            current.copy(isVideoHdrEnabled = nextHdr, videoFps = nextFps)
        }
    }

    fun toggleAudioEnabled() {
        if (uiState.value.isRecording) return
        _uiState.update { it.copy(isAudioEnabled = !it.isAudioEnabled) }
    }

    fun toggleAutoNightMode() {
        if (uiState.value.isRecording) return
        _uiState.update { current ->
            val nextEnabled = !current.isAutoNightModeEnabled
            current.copy(
                isAutoNightModeEnabled = nextEnabled,
                isNightModeActive = if (!nextEnabled) false else current.isNightModeActive
            )
        }
    }

    fun setNightModeActive(active: Boolean) {
        if (uiState.value.isRecording) return
        _uiState.update { it.copy(isNightModeActive = active) }
    }

    fun cyclePhotoFlashMode() {
        _uiState.update { current ->
            val nextMode = when (current.photoFlashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
            if (current.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                prefs?.edit()?.putInt(KEY_FRONT_FLASH_MODE, nextMode)?.apply()
                current.copy(
                    photoFlashMode = nextMode,
                    frontPhotoFlashMode = nextMode
                )
            } else {
                prefs?.edit()?.putInt(KEY_BACK_FLASH_MODE, nextMode)?.apply()
                current.copy(
                    photoFlashMode = nextMode,
                    backPhotoFlashMode = nextMode
                )
            }
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchEnabled = !it.isTorchEnabled) }
    }

    fun cycleAspectRatio() {
        if (uiState.value.isRecording) return
        _uiState.update { current ->
            val entries = AspectRatioMode.entries
            val nextIndex = (entries.indexOf(current.aspectRatioMode) + 1) % entries.size
            current.copy(aspectRatioMode = entries[nextIndex])
        }
    }

    fun cycleTimerMode() {
        if (uiState.value.isRecording) return
        cancelCountdown()
        _uiState.update { current ->
            val entries = TimerMode.entries
            val nextIndex = (entries.indexOf(current.timerMode) + 1) % entries.size
            current.copy(timerMode = entries[nextIndex])
        }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(isGridEnabled = !it.isGridEnabled) }
    }

    fun toggleLeveler() {
        _uiState.update { it.copy(isLevelerEnabled = !it.isLevelerEnabled) }
    }

    fun toggleHistogram() {
        _uiState.update { it.copy(isHistogramEnabled = !it.isHistogramEnabled) }
    }

    fun updateExposureCapabilities(minIndex: Int, maxIndex: Int, step: Float) {
        _uiState.update {
            it.copy(
                exposureCompensationRangeMin = minIndex,
                exposureCompensationRangeMax = maxIndex,
                exposureCompensationStep = step,
                exposureCompensationIndex = it.exposureCompensationIndex.coerceIn(minIndex, maxIndex)
            )
        }
    }

    fun setExposureCompensationIndex(index: Int) {
        _uiState.update { current ->
            val coerced = index.coerceIn(current.exposureCompensationRangeMin, current.exposureCompensationRangeMax)
            current.copy(exposureCompensationIndex = coerced)
        }
    }

    fun setProIso(iso: Int?) {
        _uiState.update { it.copy(proIso = iso) }
    }

    fun setProShutterSpeed(shutterNs: Long?) {
        _uiState.update { it.copy(proShutterSpeedNs = shutterNs) }
    }

    fun setProWhiteBalance(wb: WhiteBalanceMode) {
        _uiState.update { it.copy(proWhiteBalance = wb) }
    }

    fun setProFocusDistance(distance: Float?) {
        _uiState.update { it.copy(proFocusDistance = distance) }
    }

    fun resetProControls() {
        _uiState.update {
            it.copy(
                proIso = null,
                proShutterSpeedNs = null,
                proWhiteBalance = WhiteBalanceMode.AUTO,
                proFocusDistance = null,
                exposureCompensationIndex = 0
            )
        }
    }

    fun updateHistogramBins(bins: List<Float>) {
        _uiState.update { it.copy(histogramBins = bins) }
    }

    fun updateAmbientLux(lux: Float) {
        _uiState.update { it.copy(ambientLux = lux) }
    }

    internal fun shouldTriggerFrontScreenFlash(state: CameraUiState = uiState.value): Boolean {
        if (state.lensFacing != CameraSelector.LENS_FACING_FRONT) return false
        return when (state.photoFlashMode) {
            ImageCapture.FLASH_MODE_ON -> true
            ImageCapture.FLASH_MODE_AUTO -> {
                val lux = state.ambientLux
                if (lux != null) {
                    lux < FRONT_AUTO_FLASH_LUX_THRESHOLD
                } else {
                    state.isNightModeActive
                }
            }
            else -> false
        }
    }

    fun onShutterTriggered() {
        if (uiState.value.cameraMode == CameraMode.VIDEO) {
            captureVideo()
        } else {
            triggerPhotoWithOptionalTimer()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(countdownSecondsRemaining = null) }
    }

    private fun triggerPhotoWithOptionalTimer() {
        if (countdownJob?.isActive == true) {
            cancelCountdown()
            return
        }
        val seconds = uiState.value.timerMode.seconds
        if (seconds <= 0) {
            capturePhoto()
        } else {
            countdownJob = viewModelScope.launch {
                for (remaining in seconds downTo 1) {
                    _uiState.update { it.copy(countdownSecondsRemaining = remaining) }
                    delay(1000L)
                }
                _uiState.update { it.copy(countdownSecondsRemaining = null) }
                capturePhoto()
            }
        }
    }

    fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        viewModelScope.launch {
            val useScreenFlash = shouldTriggerFrontScreenFlash()

            if (useScreenFlash) {
                _uiState.update { it.copy(isScreenFlashActive = true) }
                delay(180L)
            } else {
                _uiState.update { it.copy(shutterFlashTrigger = System.currentTimeMillis()) }
            }

            val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProCameraX")
            }

            val metadata = ImageCapture.Metadata().apply {
                isReversedHorizontal = uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                getApplication<Application>().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).setMetadata(metadata).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(getApplication()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        _uiState.update {
                            it.copy(
                                photoCaptureProgress = null,
                                isScreenFlashActive = false
                            )
                        }
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Log.d(TAG, msg)
                        _uiState.update {
                            it.copy(
                                lastCapturedMediaUri = output.savedUri.toString(),
                                photoCaptureProgress = null,
                                isScreenFlashActive = false
                            )
                        }
                    }

                    override fun onCaptureProcessProgressed(progress: Int) {
                        _uiState.update { it.copy(photoCaptureProgress = progress) }
                    }
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isRecordingPaused = false,
                    recordingDurationSeconds = 0L
                )
            }
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ProCameraX")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            getApplication<Application>().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        recording = videoCapture.output
            .prepareRecording(getApplication(), mediaStoreOutputOptions)
            .apply {
                if (hasAudioPermission && uiState.value.isAudioEnabled) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(getApplication())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        _uiState.update {
                            it.copy(
                                isRecording = true,
                                isRecordingPaused = false,
                                recordingDurationSeconds = 0L
                            )
                        }
                    }
                    is VideoRecordEvent.Status -> {
                        val seconds = TimeUnit.NANOSECONDS.toSeconds(
                            recordEvent.recordingStats.recordedDurationNanos
                        )
                        _uiState.update { it.copy(recordingDurationSeconds = seconds) }
                    }
                    is VideoRecordEvent.Pause -> {
                        _uiState.update { it.copy(isRecordingPaused = true) }
                    }
                    is VideoRecordEvent.Resume -> {
                        _uiState.update { it.copy(isRecordingPaused = false) }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                            Log.d(TAG, msg)
                            _uiState.update {
                                it.copy(
                                    lastCapturedMediaUri = recordEvent.outputResults.outputUri.toString(),
                                    isRecording = false,
                                    isRecordingPaused = false,
                                    recordingDurationSeconds = 0L
                                )
                            }
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                            _uiState.update {
                                it.copy(
                                    isRecording = false,
                                    isRecordingPaused = false,
                                    recordingDurationSeconds = 0L
                                )
                            }
                        }
                    }
                }
            }
    }

    fun togglePauseResumeVideo() {
        val curRecording = recording ?: return
        if (!uiState.value.isRecording) return
        if (uiState.value.isRecordingPaused) {
            curRecording.resume()
        } else {
            curRecording.pause()
        }
    }

    companion object {
        private const val TAG = "CameraViewModel"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        internal const val PREFS_NAME = "procamerax_preferences"
        internal const val KEY_BACK_FLASH_MODE = "pref_back_photo_flash_mode"
        internal const val KEY_FRONT_FLASH_MODE = "pref_front_photo_flash_mode"
        internal const val FRONT_AUTO_FLASH_LUX_THRESHOLD = 20.0f
    }
}
