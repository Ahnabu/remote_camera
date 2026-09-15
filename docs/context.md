# PROJECT CONTEXT — Secure Remote Camera System

> **Authoritative Source of Project State**
> Last Updated: 2026-09-15
> Current Status: Version 1.3.0 — WebRTC Cross-Network WAN Fix, Coturn Ephemeral Authentication, Candidate Pair Stats Logging, Silent Camera Service & Dynamic Device Models

---

## 1. Project Overview

This project is a high-reliability, two-device Android remote-camera system.
- **Camera Device:** Android Camera Agent running a continuous Foreground Service (dynamically named via `android.os.Build.MODEL`)
- **Viewer Device:** Android Viewer UI (dynamically named via `android.os.Build.MODEL`)
- **Primary Media Transport:** WebRTC (P2P + STUN/TURN WAN NAT Traversal for Cellular 4G/5G <-> Wi-Fi via Coturn/OpenRelay)
- **Control Plane:** Firebase Auth + Cloud Firestore + Client-Side Security Rules
- **Real-Time Control / Signaling:** Firebase Cloud Firestore Realtime Listeners (Option C — 100% Free Spark Plan)
- **Device Identity:** Persistent `cameraDeviceId` & `viewerDeviceId` via `SharedPreferences`, backed by Android Keystore hardware asymmetric keypairs per device (`secp256r1`)
- **Wake / Reconnection Fallback:** Firebase Cloud Messaging (FCM)

---

## 2. Core Architectural Principle

The Camera Agent device operates as a remotely controllable agent running in a user-enabled READY / STANDBY foreground-service state.

Android lifecycle rules, OEM battery optimizations, and background execution boundaries are treated as strict technical constraints.

---

## 3. Technology Stack & Component Architecture

### System Planes
1. **Control Plane:** Firebase Auth, Firestore (Device pairing, authorization, credential issuance, rate limiting).
2. **Signaling Channel:** Firebase Cloud Firestore Realtime Snapshot Listeners (`/signaling/{sessionId}` and candidates sub-collections). Firestore operates over HTTP/2, gRPC, and WebSockets to deliver sub-second SDP offer/answer and ICE candidate exchange.
3. **Media Plane:** Native WebRTC (Direct P2P over WAN/LAN, STUN via Google/Mozilla/Twilio, TURN fallback via Coturn, DTLS-SRTP, adaptive bitrate/resolution).

### Android Stack (Both Apps)
- **Language & Build:** Kotlin, Gradle (Kotlin DSL), Android Studio
- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **Architecture:** ViewModel, Coroutines, Flow / StateFlow, DataStore
- **Camera Capture:** CameraX
- **Foreground Service:** Camera Agent service (Realme)
- **Security:** Android Keystore (asymmetric key generation & challenge signing)
- **WebRTC:** `org.webrtc:google-webrtc` / native Android WebRTC wrapper

---

## 4. Development Environment Status

| Component | Status on Machine | Installed Version / Path |
|---|---|---|
| **Node.js** | Installed | `v23.5.0` |
| **npm** | Installed | `11.1.0` |
| **Firebase CLI** | Installed | `14.9.0` (`firebase-tools`) |
| **Git** | Installed | `2.47.1.windows.1` |
| **Python** | Installed | `3.11.9` |
| **Firestore Security Rules** | Deployed | Released `firestore.rules` to cloud |
| **JDK (Java OpenJDK 17/21)** | Missing / Not in PATH | Required for Android Gradle builds |
| **Android Studio / SDK / ADB** | Missing / Not in PATH | Required for Android development |

---

## 5. Project Roadmap & Milestone Tracker

- [x] **Milestone 0: Inspection & Project Context Setup**
  - Analyze architecture (`remote_camera_architecture.md`) & master prompt (`prompt.md`).
  - Create project context & tracking documents in `docs/`.
  - Inspect user environment and outline package/tool installation commands.
- [x] **Milestone 1: Android & Firebase Repository Scaffold**
  - [x] Configure multi-module Android project structure (`android/settings.gradle.kts`, `android/build.gradle.kts`, `android/camera`, `android/viewer`).
  - [x] Integrate `google-services.json` into both `android/camera/` and `android/viewer/` app modules.
  - [x] Deploy Cloud Firestore security rules and indexes (`firebase deploy --only firestore`).
- [x] **Milestone 2: Keystore & Cryptographic Identity**
  - [x] Implement Android Keystore hardware-backed asymmetric key pair generation (`CryptoManager`).
  - [x] Implement challenge signing (`SHA256withECDSA`) and Base64 public key export for device pairing.
- [x] **Milestone 3: Firebase Auth & Device Pairing Flow**
  - [x] Implement `AuthManager` for anonymous and credential-based Firebase Authentication.
  - [x] Implement `QRCodeGenerator` & signed pairing payload format (`PairingPayload`).
  - [x] Implement `PairingRepository` with local EC signature verification and Firestore pairing storage.
  - [x] Implement Compose UI screens (`CameraPairingScreen`, `ViewerPairingScreen`) with ZXing QR scanning.
- [x] **Milestone 4: Real-Time WebRTC Signaling Service**
  - [x] Option A/B: Built TypeScript Node.js WebSocket signaling server in `backend/signaling/` (`server.ts`).
  - [x] Option C (100% Free Spark Plan): Built `FirestoreSignalingClient.kt` for both Camera and Viewer app modules.
  - [x] Deployed updated Cloud Firestore security rules with `/signaling/{sessionId}` and candidates sub-collections (`firebase deploy --only firestore`).
- [x] **Milestone 5: CameraX & Foreground Service Agent (Realme)**
  - [x] Implement `CameraManager.kt` using CameraX (`ProcessCameraProvider`, resolution setup, surface requests, torch control).
  - [x] Implement `CameraAgentService.kt` running an ongoing Android Foreground Service with `foregroundServiceType="camera"`.
  - [x] Sync device status (`READY`, `BUSY`, `OFFLINE`) to Firestore under `/devices/{cameraDeviceId}`.
  - [x] Wire service lifecycle toggle switch into `CameraPairingScreen.kt` & `MainActivity.kt`.
- [x] **Milestone 6: Native WebRTC Video Transport & Adaptation**
  - [x] Implement `WebRTCManager.kt` in `camera` app module (`PeerConnectionFactory`, hardware video encoding, SDP offer creation).
  - [x] Implement `WebRTCManager.kt` in `viewer` app module (`PeerConnectionFactory`, hardware decoding, SDP answer creation, remote track handling).
  - [x] Implement `VideoPlayerView.kt` Jetpack Compose wrapper for WebRTC `SurfaceViewRenderer` with live status overlay.
- [x] **Milestone 7: Viewer Application UI (Galaxy S20)**
  - [x] Implement `StreamControlModels.kt` (`QualityProfile`, `StreamMetrics`, `RemoteCommand`).
  - [x] Implement `StreamViewerScreen.kt` Jetpack Compose screen with live player, stats overlay, torch toggle, switch camera, and ICE restart.
- [x] **Milestone 8: NAT Traversal (STUN/TURN) & Relay Mode**
  - [x] Implement `TurnServerManager.kt` (`camera` & `viewer` modules) for STUN servers and Coturn HMAC-SHA1 credential generation.
- [x] **Milestone 9: Network Resilience, ICE Restart & FCM Recovery**
  - [x] Implement `NetworkConnectivityObserver.kt` using Android `ConnectivityManager.NetworkCallback` for automatic ICE restarts.
  - [x] Implement `RemoteCameraFcmService.kt` for FCM push notification recovery and token registration.
- [x] **Milestone 10: End-to-End Hardening & Testing**
  - [x] Add unit test suite (`SystemIntegrityTest.kt`).
  - [x] Perform architectural audit and document project completion.

---

## 6. Authoritative Reference Files

- [prompt.md](file:///d:/scrs/docs/prompt.md): Master Prompt & strict technical instructions.
- [remote_camera_architecture.md](file:///d:/scrs/docs/remote_camera_architecture.md): Deep-dive system architecture specification.
- [context.md](file:///d:/scrs/docs/context.md): Living context file for session state tracking.
- [ARCHITECTURE.md](file:///d:/scrs/docs/ARCHITECTURE.md): Executive architecture summary & design boundaries.
- [DEVELOPMENT_PLAN.md](file:///d:/scrs/docs/DEVELOPMENT_PLAN.md): Detailed milestone task specifications.
- [DECISIONS.md](file:///d:/scrs/docs/DECISIONS.md): Architectural Decision Records (ADRs).
- [SECURITY.md](file:///d:/scrs/docs/SECURITY.md): Threat model, keystore identity, token TTL specifications.
- [TESTING.md](file:///d:/scrs/docs/TESTING.md): Physical device testing matrix & automated verification routines.
- [CHANGELOG.md](file:///d:/scrs/docs/CHANGELOG.md): History of changes.
- [README.md](file:///d:/scrs/docs/README.md): Quickstart & repository guide.
