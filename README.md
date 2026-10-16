# ProCameraX 📸

[![Download APK](https://img.shields.github.com/badge/Download-APK-brightgreen.svg?style=for-the-badge&logo=android)](https://github.com/TejasRajan98/ProCameraX/releases)

ProCameraX is a feature-rich, high-performance Android camera application built using **Jetpack Compose**, **CameraX 1.4**, and **Camera2Interop**. Inspired by Google Camera (GCam) and flagship Pro camera suites, it features a responsive dark UI and unlocks advanced hardware capabilities on Android devices.

---

## 🌟 Key Features

### 📸 Photo Mode
- **Debounced Auto Night Mode (Night Sight)**: Continuously monitors ambient light levels using the device's light sensor with hysteresis (`< 7 lux` to activate, `> 15 lux` to deactivate, sustained for 1.5s) to prevent preview rebind stutters while switching to OEM Night Sight extensions (`ExtensionMode.NIGHT`).
- **Hold Still Progress UX**: During Night Mode capture, a glassmorphic circular progress overlay displays processing percentage.
- **Tap-to-Focus + Interactive EV Exposure Slider**: Tap anywhere on the viewfinder to focus and drag vertically on the sun slider to adjust exposure compensation (`EV`) in real time.
- **Front Camera Screen Flash & Shutter Blink**: Automatically boosts screen brightness with a full-white screen flash for front-facing low-light selfies, plus tactile shutter flash feedback.
- **Shutter Countdown Timer**: Built-in `OFF`, `3s`, and `10s` countdown timer with large visual overlay.
- **Aspect Ratio Framing**: Switch between `4:3`, `16:9`, `1:1`, and `FULL` aspect ratios.
- **Ultra HDR Support**: Captures and processes Ultra HDR (`JPEG_ULTRA_HDR`) photos on supported Android 14+ devices.

### 🎛️ Pro Manual Mode
- **Real-Time Camera2Interop Controls**: Adjust **White Balance (WB)**, **ISO** (`100`–`3200`), **Shutter Speed** (`1/4000s`–`1s`), **Exposure Compensation (EV)**, and **Manual Focus Distance** (`Macro` to `∞`) live via `Camera2CameraControl` without rebinding the camera session.
- **Live Luminance Histogram**: Real-time 64-bin Y-plane luma histogram analyzer computed via `ImageAnalysis`.

### 🎥 Video Mode
- **10-bit HDR Video**: Capture High Dynamic Range (HDR) videos using the HLG10 profile (HEVC) on supported Android 13+ devices.
- **True Viewfinder HDR Preview**: Dynamically switches the window color mode to `COLOR_MODE_HDR` for real-time HDR contrast in the preview.
- **Live Recording Timer & Pause/Resume**: Displays an animated `MM:SS` recording timer pill and supports pausing and resuming active video recordings.
- **Microphone Mute Toggle**: Easily toggle audio recording on or off before recording, with automatic permission safeguards.
- **Video FPS & Quality Selector**: Switch between `SD`, `HD`, `FHD`, and `UHD` resolutions and `AUTO`, `30 FPS`, or `60 FPS` capture rates.
- **HDR & 60 FPS Mutual Exclusion**: Prevents stream-binding failures by auto-coercing mutually exclusive capabilities.
- **Video Stabilization & Torch**: Native preview/video stabilization and spotlight control during recording.

### 🔍 Space Zoom & Composition Aids
- **Quick Zoom Toggles & Pinch to Zoom**: Rapidly jump between `0.5x`, `1x`, `2x`, `5x`, `10x`, `30x`, and `100x` magnification with haptic detents.
- **Gyroscope / Accelerometer Space Zoom Lock**: When zooming past `20x`, a targeting reticle with a stability progress arc tracks real device motion via hardware sensors—locking framing (`ZOOM LOCK ACTIVE`) when held steady for 1.5 seconds.
- **3x3 Composition Grid & Horizon Leveler**: Rule-of-thirds overlay and gravity-sensor horizon leveler that turns green with a haptic tick at `0.0°`.
- **Orientation-Responsive UI & Volume Key Shutter**: UI icons smoothly rotate (`0°`, `90°`, `180°`, `270°`) when shooting in landscape, and hardware Volume Up/Down keys act as physical shutter buttons.

---

## 📸 Screenshots

<p align="center">
  <img src="screenshots/photo_mode.png" width="35%" alt="Photo Mode" />
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="screenshots/video_mode.png" width="35%" alt="Video Mode" />
</p>

---

## 🛠️ Build & Installation

### Requirements
- **Android Studio Jellyfish** or higher
- **Android SDK 34** (targetSdk)
- **Minimum SDK 26** (Android 8.0)

### Compile & Run Unit Tests
```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

---

## 📂 Project Structure
- [MainActivity.kt](app/src/main/java/com/example/procamerax/MainActivity.kt): Entry point, runtime permission flow, and hardware volume-key shutter handling.
- [CameraViewModel.kt](app/src/main/java/com/example/procamerax/viewmodel/CameraViewModel.kt): Manages camera state, Pro controls, timers, MediaStore queries, and capture pipelines.
- [CameraScreen.kt](app/src/main/java/com/example/procamerax/ui/screens/CameraScreen.kt): Viewfinder lifecycle orchestration, Camera2Interop live control updates, and sensor listeners.
- `ui/components/viewfinder/`:
  - [FocusExposureOverlay.kt](app/src/main/java/com/example/procamerax/ui/components/viewfinder/FocusExposureOverlay.kt): Animated tap-to-focus ring and vertical EV slider.
  - [GridAndLevelOverlay.kt](app/src/main/java/com/example/procamerax/ui/components/viewfinder/GridAndLevelOverlay.kt): 3x3 grid and gravity-sensor horizon leveler.
- `ui/components/overlays/`:
  - [SpaceZoomHud.kt](app/src/main/java/com/example/procamerax/ui/components/overlays/SpaceZoomHud.kt): Gyroscope/accelerometer stability lock reticle.
  - [CameraOverlays.kt](app/src/main/java/com/example/procamerax/ui/components/overlays/CameraOverlays.kt): Recording timer pill, countdown timer, shutter flash, Night Sight status, and live luminance histogram.
- `ui/components/controls/`:
  - [TopBarControls.kt](app/src/main/java/com/example/procamerax/ui/components/controls/TopBarControls.kt): Orientation-aware utility bar and mode-specific pills.
  - [ProManualControls.kt](app/src/main/java/com/example/procamerax/ui/components/controls/ProManualControls.kt): Interactive WB, ISO, Shutter Speed, EV, and Manual Focus dials.
  - [BottomBarControls.kt](app/src/main/java/com/example/procamerax/ui/components/controls/BottomBarControls.kt): Zoom pills, gallery thumbnail, video pause/resume, morphing shutter button, and mode tabs.
