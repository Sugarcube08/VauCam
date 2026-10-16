package com.example.procamerax.viewmodel

import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        viewModel = CameraViewModel(Application())
    }

    @Test
    fun `initial state has expected defaults`() {
        val state = viewModel.uiState.value
        assertEquals(CameraSelector.LENS_FACING_BACK, state.lensFacing)
        assertEquals(CameraMode.PHOTO, state.cameraMode)
        assertEquals(1f, state.zoomLevel, 0.001f)
        assertEquals(AspectRatioMode.RATIO_4_3, state.aspectRatioMode)
        assertEquals(TimerMode.OFF, state.timerMode)
        assertFalse(state.isRecording)
    }

    @Test
    fun `toggleLens switches between back and front and resets zoom and EV`() {
        viewModel.setZoomLevel(5f)
        viewModel.updateExposureCapabilities(-6, 6, 0.33f)
        viewModel.setExposureCompensationIndex(4)

        viewModel.toggleLens()
        assertEquals(CameraSelector.LENS_FACING_FRONT, viewModel.uiState.value.lensFacing)
        assertEquals(1f, viewModel.uiState.value.zoomLevel, 0.001f)
        assertEquals(0, viewModel.uiState.value.exposureCompensationIndex)

        viewModel.toggleLens()
        assertEquals(CameraSelector.LENS_FACING_BACK, viewModel.uiState.value.lensFacing)
    }

    @Test
    fun `60fps and video HDR are mutually exclusive`() {
        viewModel.toggleVideoHdr()
        assertTrue(viewModel.uiState.value.isVideoHdrEnabled)

        // Selecting 60 FPS disables HDR
        viewModel.setVideoFps(FpsMode.FPS_60)
        assertEquals(FpsMode.FPS_60, viewModel.uiState.value.videoFps)
        assertFalse(viewModel.uiState.value.isVideoHdrEnabled)

        // Enabling HDR resets 60 FPS to AUTO
        viewModel.toggleVideoHdr()
        assertTrue(viewModel.uiState.value.isVideoHdrEnabled)
        assertEquals(FpsMode.AUTO, viewModel.uiState.value.videoFps)
    }

    @Test
    fun `exposure compensation index is clamped to capabilities range`() {
        viewModel.updateExposureCapabilities(minIndex = -4, maxIndex = 4, step = 0.5f)
        viewModel.setExposureCompensationIndex(10)
        assertEquals(4, viewModel.uiState.value.exposureCompensationIndex)

        viewModel.setExposureCompensationIndex(-9)
        assertEquals(-4, viewModel.uiState.value.exposureCompensationIndex)
    }

    @Test
    fun `cycleAspectRatio and cycleTimerMode cycle through all options`() {
        assertEquals(AspectRatioMode.RATIO_4_3, viewModel.uiState.value.aspectRatioMode)
        viewModel.cycleAspectRatio()
        assertEquals(AspectRatioMode.RATIO_16_9, viewModel.uiState.value.aspectRatioMode)
        viewModel.cycleAspectRatio()
        assertEquals(AspectRatioMode.RATIO_1_1, viewModel.uiState.value.aspectRatioMode)
        viewModel.cycleAspectRatio()
        assertEquals(AspectRatioMode.RATIO_FULL, viewModel.uiState.value.aspectRatioMode)
        viewModel.cycleAspectRatio()
        assertEquals(AspectRatioMode.RATIO_4_3, viewModel.uiState.value.aspectRatioMode)

        assertEquals(TimerMode.OFF, viewModel.uiState.value.timerMode)
        viewModel.cycleTimerMode()
        assertEquals(TimerMode.SEC_3, viewModel.uiState.value.timerMode)
        viewModel.cycleTimerMode()
        assertEquals(TimerMode.SEC_10, viewModel.uiState.value.timerMode)
        viewModel.cycleTimerMode()
        assertEquals(TimerMode.OFF, viewModel.uiState.value.timerMode)
    }

    @Test
    fun `pro manual controls update and reset cleanly`() {
        viewModel.setCameraMode(CameraMode.PRO)
        viewModel.setProIso(800)
        viewModel.setProShutterSpeed(4_000_000L)
        viewModel.setProWhiteBalance(WhiteBalanceMode.DAYLIGHT)
        viewModel.setProFocusDistance(0.65f)

        val modified = viewModel.uiState.value
        assertEquals(800, modified.proIso)
        assertEquals(4_000_000L, modified.proShutterSpeedNs)
        assertEquals(WhiteBalanceMode.DAYLIGHT, modified.proWhiteBalance)
        assertEquals(0.65f, modified.proFocusDistance!!, 0.001f)

        viewModel.resetProControls()
        val reset = viewModel.uiState.value
        assertNull(reset.proIso)
        assertNull(reset.proShutterSpeedNs)
        assertEquals(WhiteBalanceMode.AUTO, reset.proWhiteBalance)
        assertNull(reset.proFocusDistance)
        assertEquals(0, reset.exposureCompensationIndex)
    }

    @Test
    fun `cyclePhotoFlashMode cycles AUTO to ON to OFF`() {
        assertEquals(ImageCapture.FLASH_MODE_AUTO, viewModel.uiState.value.photoFlashMode)
        viewModel.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_ON, viewModel.uiState.value.photoFlashMode)
        viewModel.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.uiState.value.photoFlashMode)
        viewModel.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_AUTO, viewModel.uiState.value.photoFlashMode)
    }
}
