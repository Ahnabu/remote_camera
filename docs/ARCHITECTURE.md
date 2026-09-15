# System Architecture — Secure Remote Camera Access

## 1. System Overview

The system consists of two Android applications and a supporting backend:
1. **Camera App (Realme C55):** Captures video via CameraX, runs a continuous Foreground Service when activated, acts as a WebRTC media sender.
2. **Viewer App (Samsung Galaxy S20):** Connects to the Control Plane, initiates sessions, receives WebRTC media stream, displays live video, and provides controls.
3. **Backend & Signaling:** Firebase Auth + Firestore + Cloud Functions (Control Plane) + WebSocket Server (Signaling Plane) + Coturn TURN Server (NAT Traversal).

---

## 2. Three-Plane Separation

```
+-----------------------------------------------------------------------+
|                             CONTROL PLANE                             |
|  - Firebase Auth                                                      |
|  - Cloud Firestore (Device Registry, Pairing, Session State)          |
|  - Cloud Functions (Credential Issuance, Verification, Rate Limits)  |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                           SIGNALING PLANE                             |
|  - WebSocket Server (Real-time Control Channel)                        |
|  - SDP Offer / Answer Exchange                                        |
|  - ICE Candidate Exchange & ICE Restart signaling                     |
|  - FCM (Wakeup & Reconnection Notification Fallback)                  |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                             MEDIA PLANE                               |
|  - WebRTC (Direct Peer-to-Peer)                                       |
|  - Coturn STUN/TURN Server (Fallback when NAT blocks P2P)              |
|  - DTLS-SRTP Encryption                                               |
|  - Adaptive Bitrate, Resolution & Frame-rate Engine                  |
+-----------------------------------------------------------------------+
```

---

## 3. Core Operating Model & Platform Boundaries

- **Foreground Service Enforcement:** The Realme C55 must run a visible Android Foreground Service with an ongoing system notification while remote mode is enabled.
- **No Background Cold-Start Assumptions:** Android background execution and OEM battery managers (e.g. ColorOS) can restrict background service startup. The app relies on explicit READY/STANDBY state entered by the user on the device.
- **Graceful Error States:** Clear visual states (`OFFLINE`, `READY`, `BUSY`, `CONNECTING`, `STREAMING`, `ERROR`) are maintained and synchronized to Firestore.

---

## 4. Key References

- Full Architecture Specification: [remote_camera_architecture.md](file:///d:/scrs/remote_camera_architecture.md)
- Master System Prompt: [prompt.md](file:///d:/scrs/prompt.md)
- Living Project Context: [context.md](file:///d:/scrs/context.md)
