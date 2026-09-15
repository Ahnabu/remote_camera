# Security Architecture & Specifications

## 1. Threat Model & Design Boundaries

The system protects against:
- Unauthorized device access to camera streams.
- Replay attacks on session activation requests.
- Man-in-the-middle (MITM) attacks on signaling and media channels.
- Stolen authentication tokens or spoofed device IDs.

---

## 2. Device Identity & Hardware Keystore

- **Key Generation:** Every Android device generates an EC/RSA keypair in `AndroidKeyStore`.
- **Non-Exportable Guarantee:** Private keys are flagged `setIsStrongBoxBacked(true)` / non-exportable where hardware support exists.
- **Backend Storage:** Firebase Firestore stores public keys associated with `deviceId` and `userId`. Private keys are **never** transmitted.

---

## 3. Session Authorization Tokens

- **Activation Credential (Short TTL):** Single-use, valid for 60 seconds, signed by device private key and verified by Cloud Functions.
- **Session Credential:** Issued after activation for ongoing ICE restarts and quality renegotiations. Bound to `sessionId`, `cameraDeviceId`, and `viewerDeviceId`.
- **TURN Credentials:** Ephemeral HMAC-SHA1 credentials generated on-demand with a maximum lifetime of 1 hour.

---

## 4. Media & Signaling Security

- **Media Encryption:** All video and audio tracks are encrypted end-to-end using DTLS-SRTP as mandated by WebRTC.
- **Signaling Transport:** WebSocket connections use TLS (`wss://`) authenticated via short-lived JWT tokens and device signatures.
- **Relay-Only Privacy Mode:** Allows forcing all media through TURN relay, masking peer IP addresses.
