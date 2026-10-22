# VauCam

VauCam is an Android camera application based on Kotlin, Jetpack Compose, CameraX 1.4, and Camera2 interop. It retains the upstream ProCameraX camera experience and adds a local media-backup pipeline. The live `.cube` LUT pipeline is not integrated yet.

## Current capabilities

- Photo and video capture to MediaStore.
- Tap-to-focus, exposure compensation, Camera2 manual controls, and camera switching.
- Photo timer, framing ratios, flash, and front-screen flash behavior.
- Video quality/FPS selection, stabilization, torch, audio toggle, and pause/resume.
- Composition grid, horizon level, histogram, and high-zoom stability overlay. Support varies by device.
- Room-backed capture records, private backup staging, SHA-256 verification, and WorkManager retry/reconciliation.

Camera capabilities depend on device hardware, OS version, and OEM CameraX extension support. Features are not guaranteed uniformly across devices.

## Backup behavior and limitations

Verified copies are written to the app-private `files/protected_media` directory, checked against the source using SHA-256 and byte counts, and recorded as `PROTECTED` in Room. This helps protect against some accidental changes to the public media copy, but it is **not an off-device backup** and does not survive app-data clearing, uninstall, device loss, or physical storage failure. Keep an independent external backup for those risks.

Capture intent registration is attempted before CameraX capture. WorkManager and periodic reconciliation recover pending operations, but Android may defer background work; force-stop can prevent execution until the app is started again.

## Build and test

Requirements:
- Android Studio or an equivalent Android SDK setup
- JDK 21 (Gradle wrapper uses Gradle 8.5)
- Android SDK platform 34

Run:
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Known gaps

- The `.cube` LUT importer, GPU preview processing, graded still-image export, and LUT-graded video encoding pipeline are not implemented yet.
- Backup recovery needs physical-device testing under process death, storage pressure, permission revocation, and incomplete video finalization.
- The project currently targets API 34; an Android 16/API 36 upgrade needs separate compatibility validation.
- Specialized modes such as panorama, macro, portrait, long exposure, tilt-shift, group portrait, and text scanning are not complete implementations in this repository.

## Project layout

- `app/src/main/java/org/vaucam/viewmodel/CameraViewModel.kt`: camera state and capture callbacks.
- `app/src/main/java/org/vaucam/camera/CaptureCoordinator.kt`: capture registration and WorkManager scheduling.
- `app/src/main/java/org/vaucam/data/database/`: Room entities, state enum, DAO, and database.
- `app/src/main/java/org/vaucam/data/repository/BackupRepository.kt`: copy, digest verification, and state transitions.
- `app/src/main/java/org/vaucam/data/storage/`: protected storage and SHA-256 utilities.
- `app/src/main/java/org/vaucam/platform/background/`: backup and reconciliation workers.
