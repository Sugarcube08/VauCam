package org.vaucam.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@RunWith(RobolectricTestRunner::class)
class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        // AndroidViewModel's viewModelScope uses Dispatchers.Main; install a deterministic
        // test dispatcher for local JVM tests where the Android Main looper is unavailable.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CameraViewModel(RuntimeEnvironment.getApplication<Application>())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has expected defaults`() {
        val state = viewModel.uiState.value
        assertEquals(CameraSelector.LENS_FACING_BACK, state.lensFacing)
        assertEquals(CameraMode.PHOTO, state.cameraMode)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, state.photoFlashMode)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, state.backPhotoFlashMode)
        assertEquals(ImageCapture.FLASH_MODE_OFF, state.frontPhotoFlashMode)
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
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.uiState.value.photoFlashMode)
        assertEquals(1f, viewModel.uiState.value.zoomLevel, 0.001f)
        assertEquals(0, viewModel.uiState.value.exposureCompensationIndex)

        viewModel.toggleLens()
        assertEquals(CameraSelector.LENS_FACING_BACK, viewModel.uiState.value.lensFacing)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, viewModel.uiState.value.photoFlashMode)
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

    @Test
    fun `flash settings are remembered per lens and across app restarts`() {
        val prefs = InMemorySharedPreferences()
        val app = object : Application() {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }

        val firstSessionVm = CameraViewModel(app)
        // Back defaults to AUTO, Front defaults to OFF
        assertEquals(ImageCapture.FLASH_MODE_AUTO, firstSessionVm.uiState.value.photoFlashMode)
        firstSessionVm.toggleLens()
        assertEquals(ImageCapture.FLASH_MODE_OFF, firstSessionVm.uiState.value.photoFlashMode)

        // Change front flash from OFF -> AUTO -> ON
        firstSessionVm.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_AUTO, firstSessionVm.uiState.value.photoFlashMode)
        firstSessionVm.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_ON, firstSessionVm.uiState.value.photoFlashMode)

        // Switch back to back camera and change back flash from AUTO -> ON -> OFF
        firstSessionVm.toggleLens()
        assertEquals(ImageCapture.FLASH_MODE_AUTO, firstSessionVm.uiState.value.photoFlashMode)
        firstSessionVm.cyclePhotoFlashMode()
        firstSessionVm.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_OFF, firstSessionVm.uiState.value.photoFlashMode)

        // Simulate app restart with a new ViewModel instance sharing the same preferences
        val restartedVm = CameraViewModel(app)
        assertEquals(ImageCapture.FLASH_MODE_OFF, restartedVm.uiState.value.photoFlashMode)
        restartedVm.toggleLens()
        assertEquals(ImageCapture.FLASH_MODE_ON, restartedVm.uiState.value.photoFlashMode)
    }

    @Test
    fun `front camera auto flash only triggers screen flash in low ambient light`() {
        // Switch to front camera (defaults to FLASH_MODE_OFF)
        viewModel.toggleLens()
        assertEquals(CameraSelector.LENS_FACING_FRONT, viewModel.uiState.value.lensFacing)
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.uiState.value.photoFlashMode)
        assertFalse(viewModel.shouldTriggerFrontScreenFlash())

        // Cycle to FLASH_MODE_AUTO
        viewModel.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_AUTO, viewModel.uiState.value.photoFlashMode)

        // Sufficient ambient light (e.g., 150 lux) -> should NOT flash screen
        viewModel.updateAmbientLux(150f)
        assertFalse(viewModel.shouldTriggerFrontScreenFlash())

        // Low ambient light (e.g., 8 lux < 20 lux threshold) -> SHOULD flash screen
        viewModel.updateAmbientLux(8f)
        assertTrue(viewModel.shouldTriggerFrontScreenFlash())

        // Cycle to FLASH_MODE_ON -> SHOULD flash screen even in bright light
        viewModel.cyclePhotoFlashMode()
        assertEquals(ImageCapture.FLASH_MODE_ON, viewModel.uiState.value.photoFlashMode)
        viewModel.updateAmbientLux(300f)
        assertTrue(viewModel.shouldTriggerFrontScreenFlash())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            map[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                for ((k, v) in pending) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
    }
}
