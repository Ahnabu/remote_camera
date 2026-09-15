# Testing Strategy & Device Matrix

## 1. Physical Device Test Matrix

| Role | Device | OS / Version | Primary Focus |
|---|---|---|---|
| **Camera Agent** | Realme C55 | Android (ColorOS / Realme UI) | CameraX capture, Foreground Service persistence, adaptive encoding, battery optimization compliance |
| **Viewer UI** | Samsung Galaxy S20 | Android (One UI) | Jetpack Compose UI, WebRTC video decoding, status indicators, quality controls |

---

## 2. Automated Test Verification

### Android Unit Tests (`./gradlew test`)
- `CryptoManagerTest`: Key pair generation, signature creation, and verification.
- `StateMachineTest`: Camera Agent lifecycle state transitions (`IDLE` -> `READY` -> `BUSY` -> `STREAMING`).
- `SignalingProtocolTest`: Serialization/deserialization of SDP & ICE WebSocket messages.

### Cloud Functions Tests (`npm test` in `backend/`)
- Firestore security rule verification.
- Pairing validation Cloud Function tests.
- Short-lived TURN token generation tests.

---

## 3. Manual Scenario Test Checklist

- [ ] **QR Code Pairing:** Scan camera QR code from viewer; verify pairing saved in Firestore.
- [ ] **Foreground Service Lifecycle:** Turn screen off on Realme C55; verify stream remains active.
- [ ] **Network Switch (Wi-Fi -> LTE):** Disconnect Wi-Fi during active stream; verify ICE restart recovers video.
- [ ] **Adaptive Bitrate:** Simulate bandwidth throttling; verify resolution scales down without stream crash.
- [ ] **Session Revocation:** Revoke pairing from Viewer UI; verify Camera immediately closes stream and returns to READY state.
