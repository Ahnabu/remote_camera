# Secure Remote Camera Access System

A high-reliability, two-device Android remote camera solution built with **WebRTC**, **Firebase Auth & Firestore**, **Android Keystore hardware cryptography**, **Media captures & storage**, and **Jetpack Compose UI**.

- **Camera Device:** Android Camera Agent running a continuous Foreground Service (dynamic device model via `android.os.Build.MODEL`)
- **Viewer Device:** Android Viewer UI with WebRTC player & 10 FPS continuous photo recording (dynamic device model via `android.os.Build.MODEL`)

---

## 📱 User Guide (How to Use the App)

### 1. Initial Launch & Firebase Setup
1. Install and launch the **Camera Agent app** on your Camera device.
2. Install and launch the **Viewer app** on your Viewer device.
3. Both devices automatically authenticate anonymously with Firebase backend.

### 2. Device Pairing via QR Code
1. On the **Camera Agent Device**:
   - The app displays a unique, cryptographically signed QR Code.
   - Device ID is deterministically generated from `Settings.Secure.ANDROID_ID` (`Camera_Agent_[ANDROID_ID_HASH]`), staying constant across reinstalls.
   - The QR code contains the Camera public key (`EC secp256r1`) and a signed challenge generated inside hardware-backed Android Keystore.
2. On the **Viewer Device**:
   - Tap **📷 Scan Camera QR Code**.
   - Point the camera at the Camera Agent screen to scan the QR code.
   - The Viewer app automatically verifies the digital signature locally before registering the pairing in Firestore.
   - Alternatively, copy/paste the pairing JSON payload manually into the Viewer app and tap **Verify & Pair Device**.

### 3. Live Video Streaming & Controls
1. Once paired, the Camera Agent enters the **READY / STANDBY** foreground service state.
2. On the Viewer app, tap **Connect** next to the paired Camera Agent.
3. Live WebRTC video stream begins with adaptive quality, hardware encoding (VP8/H.264), and ultra-low latency (< 500ms).
4. **Hardware Controls**:
   - **Flashlight ⚡ / 🔦**: Remote hardware flashlight toggle.
   - **Switch Camera 📷**: Switch between Front and Rear camera lenses.
   - **Quality Profile ⚙️**: Select 360p / 720p / 1080p stream resolution.
   - **ICE Restart 🔄**: Re-establish NAT traversal if network changes.

### 4. Continuous 10 FPS Photo Burst Capture & Storage
1. While streaming live video, tap the **`📷` Photo Camera** button on the bottom control bar of the Viewer.
2. The Viewer app samples the incoming WebRTC video stream every 100ms (10 FPS), converts frames to JPEG via high-speed NV21 YUV processing, and writes files to disk.
3. Photos are saved directly into `Pictures/RemoteCamera_[CameraName]/` on the Viewer device via Android `MediaStore`.
4. A top banner displays live recording feedback: `🔴 REC (10 FPS) — Saved: X photos`.
5. Tap **`📷`** again to stop recording.

### 5. Debug Consoles & One-Tap Log Export
- Both the Camera Agent and Viewer apps feature a **Live Debug Console** card on their home screens and overlay UI.
- Tap **"📋 Copy"** to copy the entire formatted log trajectory to the Android clipboard with Toast confirmation.

---

## 🛠️ Developer Guide

### Repository Architecture

```text
d:\scrs\
├── android\
│   ├── settings.gradle.kts           # Multi-module settings (:camera, :viewer)
│   ├── build.gradle.kts              # Root Android Gradle build script (AGP 8.5.1, Kotlin 1.9.24)
│   ├── camera\                       # Camera Agent Module
│   │   ├── google-services.json      # Firebase configuration for com.remotecamera.camera
│   │   └── src/main/java/com/remotecamera/camera/
│   │       ├── auth/AuthManager.kt   # Firebase Auth flow
│   │       ├── camerax/CameraManager.kt # CameraX provider, resolution & torch control
│   │       ├── crypto/CryptoManager.kt # Android KeyStore EC keypair generation & signing
│   │       ├── debug/DebugLogger.kt  # Centralized live debug logging
│   │       ├── pairing/              # QR Code generator & hardware-derived device ID repository
│   │       ├── service/CameraAgentService.kt # Android Foreground Service with active session filtering
│   │       ├── signaling/            # Firestore delta signaling client
│   │       ├── webrtc/WebRTCManager.kt # Native WebRTC PeerConnection & capturer lifecycle release
│   │       └── ui/PairingScreen.kt   # Compose UI for Camera app with live console & QR view
│   └── viewer\                       # Viewer UI Module
│       ├── google-services.json      # Firebase configuration for com.remotecamera.viewer
│       └── src/main/java/com/remotecamera/viewer/
│           ├── auth/AuthManager.kt   # Firebase Auth flow
│           ├── crypto/CryptoManager.kt # Hardware signature verification
│           ├── debug/DebugLogger.kt  # Centralized live debug logging
│           ├── pairing/              # QR Code Scanner & pairing repo
│           ├── signaling/            # Firestore signaling client
│           ├── storage/FrameSaverHelper.kt # High-speed I420 YUV -> JPEG & MediaStore storage helper
│           ├── webrtc/WebRTCManager.kt # Native WebRTC PeerConnection, 10 FPS video sink & reactive track state
│           └── ui/                   # Compose UI, StreamViewerScreen & SurfaceViewRenderer player view
├── backend\
│   ├── firebase.json                 # Firebase configuration (Firestore rules & functions)
│   ├── firestore.rules               # Deployed Cloud Firestore security rules
│   └── signaling\                    # Node.js TypeScript WebSocket Signaling Server (server.ts)
└── .env.example                      # Environment variables reference template
```

### Build Commands

```powershell
# Navigate to Android directory
cd d:\scrs\android

# Build Camera Agent APK
.\gradlew :camera:assembleDebug

# Build Viewer UI APK
.\gradlew :viewer:assembleDebug

# Build Both APKs
.\gradlew assembleDebug

# Deploy Firestore Security Rules (100% Free Spark Plan)
cd d:\scrs\backend
firebase deploy --only firestore
```

### Key Technical Architecture Highlights
- **Operating Model:** Camera Agent runs an explicit Android Foreground Service (`READY` state) with `foregroundServiceType="camera"` to survive OEM background kills.
- **Hardware-Derived Identity:** Persistent device IDs (`Camera_Agent_[ANDROID_ID_HASH]`) backed by `Settings.Secure.ANDROID_ID` prevent pairing staleness.
- **Clean Hardware Resource Management:** Explicit `videoCapturer.stopCapture()` & `surfaceTextureHelper.dispose()` on session close release Camera2 hardware immediately, turning off the notification camera dot and freeing system Face Lock.
- **High-Speed Storage:** `FrameSaverHelper` converts WebRTC `I420Buffer` bytebuffers using `duplicate().rewind()` and row-based memory array copies (~2–4ms/frame), saving 10 FPS photos directly to `Pictures/RemoteCamera_[CameraName]/` via MediaStore.
- **100% Free Signaling:** Firestore snapshot listeners with delta changes (`documentChanges`) provide zero-cost global WebRTC signaling over LTE/5G and Wi-Fi without cross-talk between old sessions.

