# Changelog

All notable changes to the Secure Remote Camera System will be documented in this file.

## [1.3.0] - 2026-09-15

### Added
- **Selected Candidate Pair Diagnostics:** Instrumented `WebRTCManager.kt` on both Camera and Viewer modules to parse candidate types (`host`, `srflx`, `relay`) and log Selected Candidate Pair statistics (`localType`, `remoteType`, `protocol`, `bytesSent`/`bytesReceived`).
- **Coturn HMAC-SHA1 Credential Generation:** Added dynamic Coturn REST API HMAC-SHA1 username/credential calculation and transport URI parameters (`?transport=udp`, `?transport=tcp`) in `TurnServerManager.kt`.
- **Force Relay Diagnostic Mode:** Added `forceRelayMode` parameter support in `WebRTCManager.kt` to enforce `IceTransportsType.RELAY` for verifying TURN relay performance.

### Fixed
- **WebRTC Cross-Network Traversal:** Fixed NAT traversal failure across Wi-Fi ↔ Cellular (4G/5G), 4G ↔ 4G, and different Wi-Fi networks caused by deprecated public TURN fallback credentials.
- **Silent Foreground Notification:** Made `CameraAgentService` notification silent/minimal (`IMPORTANCE_MIN`, `VISIBILITY_SECRET`) and removed interactive "Stop Remote Mode" notification action buttons on the camera device.
- **Dynamic Device Identity:** Removed generic `Realme_C55` and `Samsung_s20` branding strings across the codebase and documentation in favor of dynamic `android.os.Build.MODEL`.

## [1.2.0] - 2026-09-14

### Added
- Created `docs/DIAGNOSIS_AND_FIXES.md` detailing WebRTC black screen root cause, empirical log evidence, and hardware lock release mechanisms.
- Added OpenRelay TURN servers (`turn:openrelay.metered.ca:80`, `:443`, `turns:openrelay.metered.ca:443`) and port `443` STUN fallback to `TurnServerManager.kt` in both `:camera` and `:viewer` modules for cross-network Cellular (4G/5G) <-> Wi-Fi WAN NAT traversal.

### Fixed
- **WebRTC Camera Sensor Lock:** Resolved zero-frame black screen issue caused by Camera2 lock conflicts on rapid session reconnects by implementing delayed capturer retry, multi-enumerator (`Camera2` -> `Camera1`) fallback, and `640x480` resolution fallback.
- **WebRTC Candidate Draining:** Added candidate buffer queue draining post remote offer setup to prevent ICE connection timeouts.
- **10 FPS Continuous Photo Burst:** Fixed `IndexOutOfBoundsException` and buffer underflow in `FrameSaverHelper.kt` when converting WebRTC YUV I420 byte buffers with non-standard row strides to Bitmap JPEGs.
- **Foreground Service Notification:** Configured `CameraAgentService` notification channel to `IMPORTANCE_MIN`, `PRIORITY_MIN`, and `VISIBILITY_SECRET` to keep the background service silent without distracting notifications on the camera device.
- **Dynamic Device Identity:** Removed hardcoded device model strings (e.g., `Realme C55`, `Samsung S20`) across `:camera` and `:viewer` modules (`PairingScreen`, `PairingRepository`, `MainActivity`, `FcmService`, `SystemIntegrityTest`), adopting dynamic model detection (`android.os.Build.MODEL`).

## [1.1.0] - 2026-09-13

### Added
- Completed **Milestone 10 (End-to-End Hardening & System Verification)**.
- Added unit test suites (`SystemIntegrityTest.kt`) for payload serialization and cryptographic verification.
- Verified architecture compliance and completed full 10-milestone system implementation.

## [1.0.0] - 2026-09-13

### Added
- Implemented `TurnServerManager.kt` for Camera Agent and Viewer app modules (`android/camera/.../webrtc/TurnServerManager.kt` and `android/viewer/.../webrtc/TurnServerManager.kt`) supporting Google STUN servers and Coturn HMAC-SHA1 credential generation.
- Implemented `NetworkConnectivityObserver.kt` (`android/camera/.../network/NetworkConnectivityObserver.kt` and `android/viewer/.../network/NetworkConnectivityObserver.kt`) monitoring network switches and triggering ICE restarts.
- Created `RemoteCameraFcmService.kt` for Camera Agent and Viewer apps to manage FCM tokens and push notification pings.
- Declared FCM services in `android/camera/src/main/AndroidManifest.xml` and `android/viewer/src/main/AndroidManifest.xml`.

## [0.9.0] - 2026-09-13

### Added
- Created `StreamControlModels.kt` (`android/viewer/.../model/StreamControlModels.kt`) defining `QualityProfile` (360p/720p/1080p), real-time `StreamMetrics`, and `RemoteCommand`.
- Created `StreamViewerScreen.kt` (`android/viewer/.../ui/StreamViewerScreen.kt`) Jetpack Compose UI for Samsung S20 Viewer app with stream controls, performance stats overlay, torch toggle, camera switcher, and ICE restart controls.

## [0.8.0] - 2026-09-13

### Added
- Implemented WebRTC PeerConnection engine for Camera Agent (`android/camera/.../webrtc/WebRTCManager.kt`) supporting hardware video encoding (VP8/H.264) and SDP offer creation.
- Implemented WebRTC PeerConnection engine for Viewer UI (`android/viewer/.../webrtc/WebRTCManager.kt`) supporting hardware decoding, SDP answer creation, and remote track callbacks.
- Built `VideoPlayerView.kt` (`android/viewer/.../ui/VideoPlayerView.kt`) Jetpack Compose wrapper for WebRTC `SurfaceViewRenderer` with connection status overlay.

## [0.7.0] - 2026-09-13

### Added
- Created `CameraManager.kt` (`android/camera/.../camerax/CameraManager.kt`) handling CameraX lifecycle binding, target resolutions, and torch controls.
- Created `CameraAgentService.kt` (`android/camera/.../service/CameraAgentService.kt`) extending `LifecycleService` and running a persistent Android Foreground Service (`foregroundServiceType="camera"`).
- Added real-time device presence synchronization (`READY`, `BUSY`, `OFFLINE`) to Firestore collection `/devices/{cameraDeviceId}`.
- Added service lifecycle toggle switch to `CameraPairingScreen.kt` and wired service start/stop in `MainActivity.kt`.
- Declared `CameraAgentService` in `android/camera/src/main/AndroidManifest.xml`.

## [0.6.0] - 2026-09-13

### Added
- Implemented **Option C (100% Free Firebase Signaling)** using `FirestoreSignalingClient.kt` in both `camera` and `viewer` modules (`android/camera/.../signaling/FirestoreSignalingClient.kt` and `android/viewer/.../signaling/FirestoreSignalingClient.kt`).
- Updated `backend/firestore.rules` with security permissions for `/signaling/{sessionId}` and candidates sub-collections.
- Successfully deployed updated Firestore Security Rules live to Cloud Firestore (`firebase deploy --only firestore`).

## [0.5.0] - 2026-09-13

### Added
- Created WebSocket real-time signaling server workspace in [`backend/signaling/`](file:///d:/scrs/backend/signaling).
- Implemented TypeScript WebSocket server (`server.ts`) handling client registration, SDP offer/answer relay, and ICE candidate forwarding.
- Built Android `SignalingClient.kt` using OkHttp WebSocket for both Camera Agent and Viewer app modules.
- Created root [`README.md`](file:///d:/scrs/README.md) with user guide and developer documentation.
- Created [`.gitignore`](file:///d:/scrs/.gitignore) ignoring build artifacts, logs, node_modules, and `docs/`.
- Created [`.env.example`](file:///d:/scrs/.env.example) template collecting all Firebase, WebSocket, and WebRTC STUN/TURN configuration parameters.

## [0.4.0] - 2026-09-13

### Added
- Implemented `AuthManager` for both app modules to handle Firebase Authentication.
- Created `PairingModel.kt` data classes (`PairingPayload`, `DevicePairingRecord`).
- Created `QRCodeGenerator.kt` for rendering signed pairing payloads into Bitmaps in Camera app.
- Implemented `PairingRepository.kt` for Camera Agent and Viewer apps.
- Added cryptographic signature verification of Camera QR payload using `CryptoManager.verifySignature()`.
- Implemented Jetpack Compose pairing screens (`CameraPairingScreen.kt`, `ViewerPairingScreen.kt`) with ZXing QR Scanner integration in `MainActivity.kt`.

## [0.3.0] - 2026-09-13

### Added
- Implemented `CryptoManager` for both Camera Agent and Viewer app modules (`android/camera/.../CryptoManager.kt` and `android/viewer/.../CryptoManager.kt`).
- Generated hardware-backed EC (`secp256r1`) key pairs in `AndroidKeyStore`.
- Added Base64 public key export (X.509 format) and challenge signing with `SHA256withECDSA`.
- Moved all root configuration JSON files (`google-services.camera.json`, `google-services.viewer.json`) to [`android/config_backup/`](file:///d:/scrs/android/config_backup).

## [0.2.0] - 2026-09-13

### Added
- Created Android multi-module project structure in `android/` with `:camera` and `:viewer` modules.
- Added root `android/settings.gradle.kts`, `android/build.gradle.kts`, and `android/gradle.properties`.
- Added module-level `build.gradle.kts` files for both Camera Agent and Viewer UI with Firebase BoM v33.2.0, CameraX v1.3.4, WebRTC v125.6422.06, Jetpack Compose, and Navigation.
- Integrated `google-services.json` into `android/camera/` and `android/viewer/`.
- Added initial `AndroidManifest.xml` files with permissions for camera, audio, internet, and foreground services.

## [0.1.0] - 2026-09-12

### Added
- Created foundational project context and authoritative tracking documentation (`context.md`, `ARCHITECTURE.md`, `DEVELOPMENT_PLAN.md`, `DECISIONS.md`, `SECURITY.md`, `TESTING.md`, `README.md`).
- Conducted local machine environment inspection (verified Node.js v23.5.0, npm 11.1.0, Firebase CLI 14.9.0, Git 2.47.1, Python 3.11.9).
- Formulated environment setup and package installation roadmap for JDK 17/21 and Android SDK command-line tools.
