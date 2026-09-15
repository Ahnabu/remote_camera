# Development Plan — Secure Remote Camera System

## Milestone Breakdown

### Milestone 0: Environment Setup & Project Context (CURRENT)
- [x] Analyze master prompt and architecture specifications.
- [x] Create project tracking markdown files (`context.md`, `ARCHITECTURE.md`, `DEVELOPMENT_PLAN.md`, `DECISIONS.md`, `SECURITY.md`, `TESTING.md`, `CHANGELOG.md`, `README.md`).
- [x] Inspect user local environment (Node.js, npm, Firebase CLI, Git, Python, Java JDK, Android SDK).
- [x] Provide exact installation commands for missing tools/packages.

---

### Milestone 1: Repository Structure & Build Setup
- **Objective:** Create clean Android multi-module project (or twin Android projects) and backend project directory.
- **Tasks:**
  - Create root project layout (`android/`, `backend/`).
  - Configure Gradle build files with Kotlin DSL, Compose, CameraX, WebRTC, and Firebase dependencies.
  - Set up Firebase Cloud Functions project with TypeScript in `backend/`.
- **Acceptance Criteria:** `gradlew assembleDebug` builds without errors; Firebase CLI compiles backend code.

---

### Milestone 2: Android Keystore & Device Cryptographic Identity
- **Objective:** Generate non-exportable hardware-backed key pairs per device.
- **Tasks:**
  - Implement `CryptoManager` using Android Keystore.
  - Generate RSA/EC keypair per device on initial app startup.
  - Expose public key in PEM/JWK format for device registration.
  - Implement challenge signing algorithm for authorization tokens.
- **Acceptance Criteria:** Unit tests verify signature creation & verification; private key non-exportable flag checked.

---

### Milestone 3: Firebase Auth & Device Pairing Protocol
- **Objective:** Securely pair Samsung Galaxy S20 (Viewer) with Realme C55 (Camera).
- **Tasks:**
  - Firebase Authentication setup (Anonymous / Email / Phone).
  - Firestore schema implementation (`users`, `devices`, `pairings`, `sessions`).
  - QR Code generation on Camera app; QR scanning on Viewer app.
  - Cloud Function verification of pairing request & key exchange.
- **Acceptance Criteria:** Viewer can scan Camera QR code; Firestore records valid pairing relationship with active timestamp.

---

### Milestone 4: WebSocket Real-Time Signaling Server
- **Objective:** Establish low-latency control channel for WebRTC negotiation.
- **Tasks:**
  - Build Node.js / TypeScript WebSocket signaling server (or Firebase Realtime / Cloud Function WebSocket handler).
  - Implement message protocol (SDP offer/answer, ICE candidate relay, session status updates).
  - Authenticate WebSocket connections using short-lived Firebase tokens & device signatures.
- **Acceptance Criteria:** Two test clients exchange SDP and ICE candidates via WebSocket server.

---

### Milestone 5: CameraX & Camera Agent Foreground Service (Realme)
- **Objective:** Continuous camera availability and state management on Realme C55.
- **Tasks:**
  - Implement Android Foreground Service with ongoing notification.
  - Integrate CameraX preview & video frame surface pipeline.
  - State machine management (`IDLE`, `READY`, `BUSY`, `STREAMING`).
  - Handle camera lifecycle events, surface detachment, and Android OS interruptions.
- **Acceptance Criteria:** Service runs reliably in foreground on Realme C55 without crash when screen turns off.

---

### Milestone 6: Native WebRTC Video Transport & Adaptive Quality
- **Objective:** Ultra low-latency video streaming from Realme to Galaxy S20.
- **Tasks:**
  - Integrate `google-webrtc` Android SDK into Camera and Viewer apps.
  - Connect CameraX surface input to WebRTC `VideoCapturer` / `VideoSource`.
  - Implement adaptive bitrate and resolution switching based on WebRTC bandwidth estimation (`RTCStatsReport`).
- **Acceptance Criteria:** Continuous video playback on Galaxy S20 with latency < 500ms under local network conditions.

---

### Milestone 7: Viewer Compose UI & Remote Control Surface (Galaxy S20)
- **Objective:** Production Jetpack Compose interface for viewer.
- **Tasks:**
  - Jetpack Compose UI screens (Device List, Pairing Screen, Stream Viewer, Quality Toggles).
  - Real-time connection quality indicators (FPS, Bitrate, Latency, Packet Loss).
  - Remote controls (Stop stream, Toggle quality profile, ICE restart trigger).
- **Acceptance Criteria:** Viewer UI updates dynamically with live video, stats overlay, and control toggles.

---

### Milestone 8: Coturn STUN/TURN Deployment & Credential Issuance
- **Objective:** Ensure connectivity across restrictive NATs / cellular networks.
- **Tasks:**
  - Cloud Function for generating short-lived HMAC TURN credentials (`turnserver` standard REST API format).
  - Integration of STUN/TURN server URLs into WebRTC `RTCConfiguration`.
  - Privacy mode toggle (force relay via TURN).
- **Acceptance Criteria:** Media streams successfully when P2P direct connectivity is blocked by firewalls.

---

### Milestone 9: Network Resilience, ICE Restart & FCM Recovery
- **Objective:** Seamless recovery from Wi-Fi/LTE transitions and dropped connections.
- **Tasks:**
  - Network state observer (`ConnectivityManager.NetworkCallback`).
  - Trigger ICE Restart without tearing down camera session.
  - FCM push fallback to notify Camera agent if WebSocket drops.
- **Acceptance Criteria:** Switching Wi-Fi off on Viewer or Camera recovers stream within 5 seconds without manual app restart.

---

### Milestone 10: System Hardening & Verification
- **Objective:** Final security, performance, and reliability audit.
- **Tasks:**
  - Rate limiting enforcement in Cloud Functions.
  - Revocation testing (unpair device -> immediately terminate active stream).
  - Code cleanup, logging audit, and release build configuration.
- **Acceptance Criteria:** System passes all manual testing scenarios on physical Realme C55 and Samsung S20.
