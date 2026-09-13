# Secure Remote Camera Access System

A high-reliability, two-device Android remote camera solution built with **WebRTC**, **Firebase Auth & Firestore**, **Android Keystore hardware cryptography**, and **Jetpack Compose UI**.

- **Camera Device:** Realme C55 (Android Camera Agent running a continuous Foreground Service)
- **Viewer Device:** Samsung Galaxy S20 (Jetpack Compose Viewer UI with WebRTC player)

---

## 📱 User Guide (How to Use the App)

### 1. Initial Launch & Firebase Setup
1. Install and launch the **Camera Agent app** on your Realme C55 device.
2. Install and launch the **Viewer app** on your Samsung Galaxy S20 device.
3. Both devices will automatically authenticate anonymously with Firebase backend.

### 2. Device Pairing via QR Code
1. On the **Realme C55 (Camera Agent)**:
   - The app displays a unique, cryptographically signed QR Code.
   - The QR code contains the Realme public key (`EC secp256r1`) and a signed challenge generated directly inside hardware-backed Android Keystore.
2. On the **Samsung Galaxy S20 (Viewer)**:
   - Tap **📷 Scan Camera QR Code**.
   - Point the camera at the Realme C55 screen to scan the QR code.
   - The Viewer app automatically verifies the digital signature locally before registering the pairing in Firestore.
   - Alternatively, you can copy/paste the pairing JSON payload manually into the Viewer app and tap **Verify & Pair Device**.

### 3. Live Video Streaming
1. Once paired, the Realme device enters the **READY / STANDBY** foreground service state.
2. On the Samsung S20 Viewer app, tap **Connect** next to the paired Realme C55.
3. Live WebRTC video stream begins with adaptive quality, hardware encoding, and low latency (< 500ms).

---

## 🛠️ Developer Guide

### Repository Architecture

```text
d:\scrs\
├── android\
│   ├── settings.gradle.kts           # Multi-module settings (:camera, :viewer)
│   ├── build.gradle.kts              # Root Android Gradle build script (AGP 8.5.1, Kotlin 1.9.24)
│   ├── camera\                       # Realme C55 Camera Agent Module
│   │   ├── google-services.json      # Firebase configuration for com.remotecamera.camera
│   │   └── src/main/java/com/remotecamera/camera/
│   │       ├── auth/AuthManager.kt   # Firebase Auth flow
│   │       ├── crypto/CryptoManager.kt # Android KeyStore EC keypair generation & signing
│   │       ├── pairing/              # QR Code generator & Firestore pairing repo
│   │       └── ui/PairingScreen.kt   # Compose UI for Camera app
│   └── viewer\                       # Samsung Galaxy S20 Viewer UI Module
│       ├── google-services.json      # Firebase configuration for com.remotecamera.viewer
│       └── src/main/java/com/remotecamera/viewer/
│           ├── auth/AuthManager.kt   # Firebase Auth flow
│           ├── crypto/CryptoManager.kt # Hardware signature verification
│           ├── pairing/              # QR Code Scanner & pairing repo
│           └── ui/PairingScreen.kt   # Compose UI for Viewer app
├── backend\
│   ├── firebase.json                 # Firebase configuration (Firestore rules & functions)
│   ├── firestore.rules               # Deployed Cloud Firestore security rules
│   └── functions\                    # TypeScript Cloud Functions workspace
└── .env.example                      # Environment variables reference file
```

### Build Commands

```powershell
# Navigate to Android directory
cd d:\scrs\android

# Build Camera Agent APK
.\gradlew :camera:assembleDebug

# Build Viewer UI APK
.\gradlew :viewer:assembleDebug

# Deploy Firestore Security Rules (Free Spark Plan)
cd d:\scrs\backend
firebase deploy --only firestore
```

### Key Technical Architecture Highlights
- **Operating Model:** Realme C55 runs an explicit Android Foreground Service (`READY` state) to survive OEM/ColorOS background kills.
- **Cryptographic Identity:** Android Keystore non-exportable private keys (`SHA256withECDSA`) prevent unauthorized device spoofing.
- **Transport Layer:** WebRTC P2P media with STUN/TURN fallback and adaptive bitrate.
