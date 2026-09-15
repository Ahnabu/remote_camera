# Architectural Decision Records (ADRs)

## ADR 001: Foreground Service Operating Model for Camera Agent

### Status
Accepted

### Context
Android OS restrictions (Android 12/13/14+) aggressively restrict background execution, background camera access, and background service startup. Devices running Realme UI / ColorOS enforce additional proprietary battery optimization rules.

### Decision
We will not treat the Realme device as a camera that can be cold-started from a remote background push message. Instead, the Realme operates as an active agent running a visible Android Foreground Service with an ongoing notification when remote access mode is turned ON by the user.

### Consequences
- **Pros:** Highly reliable, immune to OEM background execution kills, camera hardware is safely accessible under Android permissions.
- **Cons:** Requires explicit user interaction on the Realme device to enable remote camera mode before remote viewing is possible.

---

## ADR 002: Three-Plane Architectural Separation

### Status
Accepted

### Context
Combining control, signaling, and streaming into a single service leads to single-point-of-failure vulnerabilities, complex state synchronization, and scaling bottlenecks.

### Decision
Separate the system into three distinct functional planes:
1. **Control Plane:** Firebase Auth, Firestore, Cloud Functions.
2. **Signaling Plane:** WebSocket Server.
3. **Media Plane:** Native WebRTC with Coturn TURN fallback.

### Consequences
- Modular component testing.
- Low backend load during active video streaming.
- Enhanced security by isolating long-term storage from short-lived media channels.

---

## ADR 003: Cryptographic Device Identity via Android Keystore

### Status
Accepted

### Context
Relying solely on user accounts or static device IDs leaves the camera open to unauthorized access if credentials leak or device identifiers are spoofed.

### Decision
Each device generates a non-exportable asymmetric key pair stored in hardware-backed Android Keystore during initial setup. Device identity is verified by challenge-signing during pairing and session authorization.

### Consequences
- Private keys never leave the physical device.
- Backend stores only public keys.
- Pairing cannot be copied or spoofed to an unauthorized device.
