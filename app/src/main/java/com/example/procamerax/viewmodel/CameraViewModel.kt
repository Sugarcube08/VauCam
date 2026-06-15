package com.example.procamerax.viewmodel

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale

enum class CameraMode {
    PHOTO, VIDEO
}

enum class FpsMode(val value: Int?) {
    AUTO(null),
    FPS_30(30),
    FPS_60(60)
}

data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val isRecording: Boolean = false,
    val zoomLevel: Float = 1f, // 0.5x, 1x, 5x
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
    val isTorchEnabled: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    fun setImageCapture(capture: ImageCapture) {
        imageCapture = capture
    }

    fun setVideoCapture(capture: VideoCapture<Recorder>) {
        videoCapture = capture
    }

    fun toggleLens() {
        _uiState.update { current ->
            val newLens = if (current.lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            current.copy(lensFacing = newLens)
        }
    }

    fun setCameraMode(mode: CameraMode) {
        if (uiState.value.isRecording) return
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

    fun toggleAutoNightMode() {
        if (uiState.value.isRecording) return
        _uiState.update { it.copy(isAutoNightModeEnabled = !it.isAutoNightModeEnabled) }
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
            current.copy(photoFlashMode = nextMode)
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchEnabled = !it.isTorchEnabled) }
    }

    fun capturePhoto() {
        val imageCapture = imageCapture ?: return
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
                    _uiState.update { it.copy(photoCaptureProgress = null) }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Log.d(TAG, msg)
                    _uiState.update { it.copy(
                        lastCapturedMediaUri = output.savedUri.toString(),
                        photoCaptureProgress = null
                    ) }
                }

                override fun onCaptureProcessProgressed(progress: Int) {
                    _uiState.update { it.copy(photoCaptureProgress = progress) }
                }
            }
        )
    }

    fun captureVideo() {
        val videoCapture = this.videoCapture ?: return
        _uiState.update { it.copy(isRecording = true) }

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            _uiState.update { it.copy(isRecording = false) }
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

        recording = videoCapture.output
            .prepareRecording(getApplication(), mediaStoreOutputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(getApplication())) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        _uiState.update { it.copy(isRecording = true) }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                            Log.d(TAG, msg)
                            _uiState.update { it.copy(
                                lastCapturedMediaUri = recordEvent.outputResults.outputUri.toString(),
                                isRecording = false
                            ) }
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                            _uiState.update { it.copy(isRecording = false) }
                        }
                    }
                }
            }
    }

    companion object {
        private const val TAG = "CameraViewModel"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}
