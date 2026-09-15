# Secure Remote Camera System

A production-grade, two-device Android remote camera solution using **WebRTC**, **Firebase Auth & Firestore**, **WebSocket signaling**, and **Android Keystore cryptographic identity**.

- **Camera Device:** Realme C55 (Android Camera Agent running a Foreground Service)
- **Viewer Device:** Samsung Galaxy S20 (Jetpack Compose Viewer UI)

---

## Documentation Quick Links

- 📌 [context.md](file:///d:/scrs/context.md) — Current session state and authoritative milestone tracker.
- 🏗️ [ARCHITECTURE.md](file:///d:/scrs/ARCHITECTURE.md) — System architecture, 3-plane separation, and operating model.
- 📖 [remote_camera_architecture.md](file:///d:/scrs/remote_camera_architecture.md) — Complete deep-dive architecture document.
- 📋 [DEVELOPMENT_PLAN.md](file:///d:/scrs/DEVELOPMENT_PLAN.md) — Step-by-step milestone task breakdown.
- 🔐 [SECURITY.md](file:///d:/scrs/SECURITY.md) — Threat model, Android Keystore identity, single-use tokens.
- 🎯 [DECISIONS.md](file:///d:/scrs/DECISIONS.md) — Architectural Decision Records (ADRs).
- 🧪 [TESTING.md](file:///d:/scrs/TESTING.md) — Testing strategy and device verification checklist.
- 📝 [prompt.md](file:///d:/scrs/prompt.md) — Master prompt & system specification guidelines.

---

## Environment Prerequisites & Installation

To build and run this system, the following tools are required:

1. **JDK 17 or 21 (OpenJDK / Eclipse Temurin):** Required for Android Gradle builds.
2. **Android Studio & Android SDK (API 34+):** Includes `adb`, `cmdline-tools`, `build-tools`, `platform-tools`.
3. **Node.js (v18+) & npm:** Required for Firebase Cloud Functions backend & WebSocket server (Already installed: Node `v23.5.0`, npm `11.1.0`).
4. **Firebase CLI:** Required for Cloud Functions & Firestore deployment (Already installed: `v14.9.0`).
