# Secure Remote Camera Access --- Updated Architecture

## 1. Executive Summary

This document defines the recommended architecture for a custom Android
remote-camera system using:

-   **Camera phone:** Realme C55
-   **Viewer phone:** Samsung Galaxy S20
-   **Primary media transport:** WebRTC
-   **Backend/control plane:** Firebase Authentication + Firestore +
    Cloud Functions
-   **Real-time control/signaling:** WebSocket
-   **Wake/recovery mechanism:** Firebase Cloud Messaging (FCM)
-   **NAT traversal:** STUN + TURN (coturn)
-   **Camera stack:** CameraX
-   **Device identity:** Android Keystore-backed asymmetric keys

The central architectural change is to treat the Realme not as a camera
that must be remotely "awakened" on every request, but as a **remotely
controllable camera agent** that can be placed into a user-enabled
**READY/STANDBY** state.

This is important because Android and OEM battery-management policies
can restrict or delay background execution and camera foreground-service
startup. The application should therefore design around those platform
boundaries rather than assume that a remote notification can always
cold-start the camera.

The system should prioritize:

1.  Reliable continuous video similar to a video call.
2.  Adaptive quality rather than fixed bitrate/resolution.
3.  Strong device-to-device authorization.
4.  Secure signaling and short-lived session credentials.
5.  WebRTC P2P when practical, with TURN fallback.
6.  Optional relay-only privacy mode.
7.  Graceful handling of network changes, process death, battery
    constraints, and lifecycle interruptions.

------------------------------------------------------------------------

# 2. Design Goals

## Functional goals

The system should allow an authorized viewer to:

-   Request access to the Realme camera.
-   Receive a continuous live video stream.
-   Select a quality profile.
-   Maintain streaming through normal network fluctuations.
-   Recover from Wi-Fi/mobile-network changes without restarting the
    entire application.
-   Stop the session remotely.
-   See whether the camera is offline, ready, busy, connecting,
    streaming, or unavailable.

## Quality goals

The video system should behave like a video call rather than a sequence
of images.

Required characteristics:

-   Low latency.
-   Continuous playback.
-   Adaptive bitrate.
-   Adaptive resolution.
-   Adaptive frame rate.
-   Packet-loss tolerance.
-   Congestion control.
-   ICE restart.
-   TURN fallback.
-   Hardware video encoding where supported.

## Security goals

The system should:

-   Allow only paired/authorized devices to request the camera.
-   Avoid long-lived secrets inside the APK.
-   Use Android Keystore for persistent device identity.
-   Use short-lived session credentials.
-   Prevent unauthorized session replay.
-   Rate-limit activation attempts.
-   Protect signaling traffic with authenticated transport.
-   Keep audit logs with a defined retention period.
-   Support optional relay-only mode for privacy.

## Non-goals

The system should not attempt to:

-   Bypass Android security restrictions.
-   Guarantee camera startup after Android force-stop.
-   Guarantee indefinite background execution after the OS or OEM
    explicitly terminates the application.
-   Keep the camera permanently active when the user has disabled
    remote-camera mode.

------------------------------------------------------------------------

# 3. Core Architectural Principle

## Fragile model

``` text
Viewer
   |
   | request
   v
Backend
   |
   | FCM wake
   v
Realme
   |
   | maybe wakes
   v
Camera service
   |
   v
Camera
```

This architecture depends heavily on background wake-up and OEM
behavior.

## Recommended model

``` text
Realme
   |
   | User enables Remote Camera
   v
Camera Agent
   |
   | READY / STANDBY
   v
Real-time control channel
   |
   v
Backend
   ^
   |
Viewer
   |
   | request session
   v
WebRTC negotiation
   |
   v
Continuous video
```

The Realme should maintain a clearly visible Android foreground-service
state while remote monitoring is enabled.

The goal is not to make the app immortal. The goal is to make the normal
operating state reliable and make abnormal lifecycle events explicit and
recoverable.

------------------------------------------------------------------------

# 4. High-Level Architecture

``` text
                              INTERNET
                                  |
                +-----------------+-----------------+
                |                                   |
                v                                   v
       +-------------------+                 +---------------+
       |   CONTROL PLANE   |                 |  MEDIA PLANE  |
       |                   |                 |               |
       | Firebase Auth     |                 |    WebRTC     |
       | Firestore         |                 |               |
       | Cloud Functions   |                 | P2P / TURN    |
       | Device registry   |                 | DTLS-SRTP      |
       | Authorization     |                 |               |
       | Rate limits       |                 +-------+-------+
       +---------+---------+                         |
                 |                                   |
                 | WebSocket                          |
                 |                                   |
          +------+--------+                    +-----+------+
          |               |                    |            |
          v               v                    v            v
   +-------------+  +-------------+      +-----------+ +-----------+
   | Realme C55  |  | Galaxy S20  |      | Realme C55| | Galaxy S20|
   | Camera App  |  | Viewer App  |      | Camera    | | Viewer    |
   +------+------+  +------+------+      +-----+-----+ +-----+-----+
          |                |                    |             |
          v                v                    v             |
   CameraX + Agent     Viewer UI          Video Encoder       |
          |                                      |             |
          +--------------------------------------+-------------+
                                                 |
                                                 v
                                           WebRTC media
```

------------------------------------------------------------------------

# 5. Three-Plane Architecture

The system should be explicitly divided into three planes.

## 5.1 Control Plane

Responsible for:

-   Authentication.
-   Device registration.
-   Pairing.
-   Authorization.
-   Device presence.
-   Session state.
-   Rate limiting.
-   Session lifecycle.
-   Audit logging.
-   TURN credential issuance.

Primary components:

-   Firebase Authentication.
-   Firestore.
-   Cloud Functions.

The control plane should not carry the video.

## 5.2 Signaling Plane

Responsible for:

-   SDP offer/answer exchange.
-   ICE candidate exchange.
-   ICE restart negotiation.
-   Session commands.
-   Presence.
-   Connection-state changes.

Recommended primary transport:

-   WebSocket.

Fallback/recovery:

-   FCM.

Firestore may retain durable session metadata but should not be the
primary low-latency signaling transport when a real-time connection is
available.

## 5.3 Media Plane

Responsible only for live video/audio transport.

Primary technology:

-   WebRTC.

Network paths:

``` text
P2P
  |
  +--> preferred when appropriate

TURN
  |
  +--> fallback when P2P fails

Relay-only TURN
  |
  +--> optional privacy mode
```

Media encryption:

-   DTLS-SRTP through WebRTC.

------------------------------------------------------------------------

# 6. Device Architecture

## 6.1 Realme C55 --- Camera Device

``` text
+------------------------------------------------+
| Camera App                                     |
|                                                |
|  UI                                            |
|   |                                            |
|   +--> Remote Camera Controller                |
|          |                                     |
|          +--> Session Manager                  |
|          +--> Authentication                   |
|          +--> WebSocket Client                 |
|          +--> FCM Handler                      |
|          +--> Device Identity                  |
|                                                |
|  Foreground Camera Service                     |
|          |                                     |
|          +--> CameraX                          |
|          +--> Video Encoder                    |
|          +--> WebRTC PeerConnection            |
|          +--> Network Monitor                  |
|                                                |
|  Android Keystore                              |
|          +--> Device private key               |
+------------------------------------------------+
```

The camera phone should expose a clear state such as:

-   Remote Camera OFF.
-   READY.
-   REQUESTED.
-   CONNECTING.
-   STREAMING.
-   RECONNECTING.
-   BUSY.
-   UNAVAILABLE.

## 6.2 Galaxy S20 --- Viewer Device

``` text
+-----------------------------------------------+
| Viewer App                                    |
|                                               |
| UI                                            |
|  |                                            |
|  +--> Device Pairing                          |
|  +--> Session Controller                      |
|  +--> Quality Controller                      |
|  +--> WebSocket Client                        |
|  +--> WebRTC PeerConnection                   |
|  +--> Viewer Authentication                   |
|                                               |
| Android Keystore                              |
|  +--> Viewer private key                      |
+-----------------------------------------------+
```

------------------------------------------------------------------------

# 7. Device Identity and Pairing

Each device generates a persistent asymmetric key pair.

``` text
Realme C55
    |
    v
Android Keystore
    |
    +--> private key (non-exportable)
    |
    +--> public key
```

The same model is used for the viewer.

During pairing:

``` text
Realme public key
        |
        v
     Backend
        ^
        |
S20 public key
```

The backend stores the relationship:

``` text
Pairing
├── cameraDeviceId
├── viewerDeviceId
├── cameraPublicKey
├── viewerPublicKey
├── permissions
├── createdAt
└── revokedAt
```

The backend remains responsible for authorization.

The device keys provide an additional cryptographic device identity that
does not depend solely on backend credentials.

------------------------------------------------------------------------

# 8. Authentication Model

Authentication should have multiple layers.

## Layer 1 --- Account authentication

Firebase Authentication identifies the user.

## Layer 2 --- Device authentication

The application proves possession of its registered Keystore-backed
private key.

## Layer 3 --- Pair authorization

The backend checks:

``` text
viewerDeviceId
       |
       v
Is paired with cameraDeviceId?
       |
       +--> YES: continue
       |
       +--> NO: reject
```

## Layer 4 --- Session authorization

Every session receives scoped, short-lived credentials.

------------------------------------------------------------------------

# 9. Session Credential Model

Avoid using one credential for every purpose.

Use two conceptual credentials.

## Activation credential

Purpose:

-   Authorize opening the camera for a new session.

Properties:

-   Short TTL.
-   Single-use.
-   Bound to camera device.
-   Bound to viewer device.
-   Bound to session ID.
-   Bound to issuance time.
-   Replay-resistant.

## Session credential

Purpose:

-   Authorize operations within an already established session.

Examples:

-   ICE restart.
-   Renegotiation.
-   Quality renegotiation.
-   Session continuation.

The session credential remains valid only for the lifetime of that
session and expires when the session ends.

This prevents a network change from requiring a completely new camera
activation.

------------------------------------------------------------------------

# 10. TURN Credentials

Never embed a permanent TURN secret in the application.

Use:

``` text
Viewer requests session
        |
        v
Cloud Function
        |
        +--> creates short-lived TURN credentials
        |
        v
Realme + S20
```

Credentials should be:

-   Per-session.
-   Short-lived.
-   Scoped.
-   Revocable through session expiration.

Recommended TURN infrastructure:

-   coturn.

------------------------------------------------------------------------

# 11. Real-Time Control

## Primary channel

WebSocket.

Used for:

-   Device presence.
-   Session request.
-   Wake/ready notification.
-   SDP signaling.
-   ICE candidates.
-   Session commands.
-   Stop request.
-   Reconnection state.

## Fallback

FCM.

Used primarily for:

-   Recovery.
-   Reconnecting a device whose real-time channel has disappeared.
-   Notifying the camera agent of pending work when the operating system
    permits it.

FCM should not be treated as a guaranteed real-time transport.

------------------------------------------------------------------------

# 12. Session Lifecycle

Recommended state machine:

``` text
OFFLINE
   |
   v
STANDBY
   |
   v
REQUESTED
   |
   v
AUTHENTICATING
   |
   +---- failure ----> STANDBY
   |
   v
NEGOTIATING
   |
   +---- failure ----> STANDBY
   |
   v
CONNECTING
   |
   v
ACTIVE
   |
   +---- network change ----> RECONNECTING
   |                              |
   |                              +---- success ----> ACTIVE
   |                              |
   |                              +---- failure ----> ENDING
   |
   +---- viewer stop ------------> ENDING
   |
   +---- timeout ----------------> ENDING
   |
   +---- camera unavailable -----> ENDING
   |
   v
ENDING
   |
   v
CLEANUP
   |
   v
STANDBY
```

------------------------------------------------------------------------

# 13. Complete Session Flow

## Step 1 --- Viewer request

S20 selects:

``` text
Request Camera
```

Backend verifies:

-   User authenticated.
-   Viewer device registered.
-   Viewer paired with Realme.
-   Permission valid.
-   Activation cooldown satisfied.
-   Hourly activation limit not exceeded.
-   Camera not already occupied.

## Step 2 --- Camera availability

The backend checks the Realme's reported presence/state.

If:

``` text
OFFLINE
```

return:

``` text
Camera unavailable
```

If:

``` text
BUSY
```

return:

``` text
Camera busy
```

If:

``` text
STANDBY / READY
```

continue.

## Step 3 --- Activation

The backend creates:

-   session ID.
-   short-lived activation credential.
-   short-lived TURN credentials.
-   session-scoped credential.

The camera agent receives the authorized session request.

## Step 4 --- Camera opens

The Realme:

1.  Validates the activation credential.
2.  Confirms the session is authorized.
3.  Checks camera availability.
4.  Opens CameraX.
5.  Configures the selected quality profile.
6.  Creates a WebRTC PeerConnection.
7.  Creates SDP offer.

## Step 5 --- Signaling

``` text
Realme
   |
   | SDP offer
   v
WebSocket
   |
   v
S20
   |
   | SDP answer
   v
WebSocket
   |
   v
Realme
```

ICE candidates are exchanged.

## Step 6 --- Media connection

WebRTC establishes:

``` text
Realme
   |
   +--> P2P
   |
   +--> TURN fallback
   |
   +--> relay-only mode
   |
   v
S20
```

## Step 7 --- Streaming

The camera continuously sends video.

The viewer receives adaptive WebRTC media.

## Step 8 --- Network change

If Wi-Fi changes to mobile data:

``` text
ACTIVE
   |
   v
ICE connectivity change
   |
   v
ICE restart
   |
   v
RECONNECTING
   |
   v
ACTIVE
```

The existing session credential remains valid.

A new activation credential is not required.

## Step 9 --- End

The viewer selects:

``` text
Stop
```

or the session reaches:

-   idle timeout.
-   maximum duration.
-   battery threshold.
-   thermal limit.
-   fatal network failure.
-   camera error.

## Step 10 --- Cleanup

The Realme:

-   stops WebRTC.
-   releases camera.
-   releases encoder resources.
-   clears session credentials.
-   returns to STANDBY.
-   updates backend state.

The session record is retained only for the defined audit period.

------------------------------------------------------------------------

# 14. Video Quality Architecture

Do not treat quality as a fixed resolution switch.

Instead expose profiles.

  Profile        Resolution      FPS    Target Bitrate
  ------------ ------------ -------- -----------------
  Data Saver           480p   15--24   \~0.4--0.7 Mbps
  Balanced             720p   24--30       \~1--2 Mbps
  High                1080p   24--30     \~2.5--4 Mbps
  Maximum             1080p   30--60       \~4--8 Mbps

These are target ranges rather than permanent guarantees.

WebRTC should dynamically adapt to actual conditions.

## Example

User selects:

``` text
HIGH
```

Initial stream:

``` text
1080p / 30fps
```

Network deteriorates:

``` text
1080p
   ↓
720p
   ↓
480p
```

Network improves:

``` text
480p
   ↓
720p
   ↓
1080p
```

The WebRTC session remains alive.

This is the key difference between **uninterrupted streaming** and
**fixed-quality streaming**.

------------------------------------------------------------------------

# 15. Quality Controller

The application should monitor:

-   RTT.
-   Packet loss.
-   Jitter.
-   Available outgoing bitrate.
-   Frames dropped.
-   Encoder load.
-   CPU usage.
-   Temperature.
-   Battery.
-   Network type.

The quality controller should select the best profile that can be
sustained.

Conceptually:

``` text
User Quality Preference
          |
          v
   Quality Controller
          |
   +------+------+------+
   |      |      |      |
Network  CPU   Battery Thermal
   |      |      |      |
   +------+------+------+
          |
          v
 Target resolution / FPS / bitrate
          |
          v
       WebRTC
```

------------------------------------------------------------------------

# 16. Privacy Modes

## Standard mode

Allow P2P when appropriate.

Advantages:

-   Lower latency.
-   Lower TURN bandwidth cost.
-   Potentially better performance.

## Relay-only mode

Force all media through TURN.

Advantages:

-   Prevents direct peer network-address exposure.
-   Gives the system a predictable media path.
-   Useful for higher privacy requirements.

Tradeoff:

-   More latency.
-   TURN bandwidth cost.
-   Greater server dependency.

Make this a user-configurable setting.

------------------------------------------------------------------------

# 17. Abuse Resistance

Authentication alone is insufficient.

A compromised authorized viewer should not be able to repeatedly
activate the camera to drain battery or harass the camera device.

Implement server-side:

## Activation cooldown

Example:

``` text
One successful activation
       |
       v
cooldown period
       |
       v
next activation
```

## Activation rate limit

Example policy:

``` text
Maximum activations/hour/device
```

## Concurrent session limit

Recommended:

``` text
One active camera session per Realme
```

unless multi-viewer support is intentionally added later.

## Maximum session duration

Make this configurable rather than hard-coded.

## Idle timeout

Terminate sessions when no meaningful media/viewer activity exists.

------------------------------------------------------------------------

# 18. Firestore Concurrency Control

Session transitions must be atomic.

Do not use:

``` text
read state
if idle:
    write requested
```

without transactional protection.

Use a transaction / compare-and-set model:

``` text
Expected:
state == STANDBY

Transition:
STANDBY -> REQUESTED
```

If another request wins first:

``` text
STANDBY
   |
   +---- Request A ---> REQUESTED
   |
   +---- Request B ---> rejected
```

This prevents duplicate activation tokens and competing camera sessions.

------------------------------------------------------------------------

# 19. Android Lifecycle Strategy

The application must accept that Android can terminate processes.

The correct response is not to attempt to bypass the OS.

## Expected failure states

-   User force-stops app.
-   Android terminates process.
-   Device reboots.
-   App is updated.
-   OEM battery manager restricts execution.
-   User revokes permissions.
-   Camera becomes unavailable.

The backend should represent the device honestly:

``` text
CAMERA = OFFLINE
```

rather than pretending that remote activation is guaranteed.

When the user opens the Realme application again:

``` text
App opened
    |
    v
Permissions checked
    |
    v
Remote Camera enabled?
    |
    +--> YES
    |
    v
Foreground Camera Service
    |
    v
STANDBY / READY
```

------------------------------------------------------------------------

# 20. Realme / ColorOS Hardening

The Realme C55 is an important risk because OEM battery-management
behavior can be more aggressive than standard Android behavior.

Deployment testing should cover:

-   Battery optimization settings.
-   Background activity restrictions.
-   Auto-start behavior.
-   App standby.
-   Screen-off behavior.
-   Wi-Fi sleep behavior.
-   Mobile-data behavior.
-   Reboot.
-   App update.
-   Process termination.
-   Permission revocation.
-   Low battery.
-   Thermal throttling.

The application should provide a setup checklist explaining which system
settings are recommended.

These settings should be treated as reliability improvements, not
security bypasses.

------------------------------------------------------------------------

# 21. FCM Recovery Flow

FCM should be treated as a recovery mechanism.

``` text
WebSocket disconnected
        |
        v
Backend notices stale presence
        |
        v
FCM notification
        |
        v
Realme receives notification if Android/OEM permits
        |
        v
Application reconnects WebSocket
        |
        v
Presence = READY
```

If FCM does not arrive:

``` text
Viewer
   |
   v
Camera unavailable / offline
```

Do not keep retrying indefinitely.

------------------------------------------------------------------------

# 22. Network Architecture

## STUN

Used for discovering network paths.

## P2P

Preferred when privacy requirements allow it and the network supports
it.

## TURN

Fallback when:

-   NAT traversal fails.
-   Firewall blocks direct traffic.
-   Network topology prevents P2P.

## TURN relay-only

Used when:

-   Privacy is prioritized.
-   Direct peer addressing should be avoided.

------------------------------------------------------------------------

# 23. Backend Data Model

Example Firestore structure:

``` text
users/
  {userId}

devices/
  {deviceId}
    type
    publicKey
    ownerId
    status
    lastSeen
    appVersion
    createdAt

pairings/
  {pairingId}
    cameraDeviceId
    viewerDeviceId
    permissions
    createdAt
    revokedAt

sessions/
  {sessionId}
    cameraDeviceId
    viewerDeviceId
    state
    createdAt
    startedAt
    endedAt
    expiryAt
    qualityProfile
    relayMode

auditLogs/
  {logId}
    sessionId
    cameraDeviceId
    viewerDeviceId
    event
    timestamp
    expiryAt
```

Use Firestore TTL or another automatic deletion mechanism for expired
audit records.

------------------------------------------------------------------------

# 24. Logging and Privacy

Log only what is necessary.

Recommended events:

-   Pairing created.
-   Pairing revoked.
-   Session requested.
-   Session accepted.
-   Session rejected.
-   Camera busy.
-   Session started.
-   Session reconnected.
-   Session ended.
-   Camera error.

Do not log:

-   Video frames.
-   Audio.
-   Unnecessary personal information.
-   Long-lived credentials.
-   TURN secrets.

Define a retention period, for example:

``` text
30–90 days
```

and automatically purge expired logs.

------------------------------------------------------------------------

# 25. Security Threat Model

  -----------------------------------------------------------------------
  Threat                              Mitigation
  ----------------------------------- -----------------------------------
  Unauthorized viewer                 Firebase Auth + device pairing

  Stolen account                      Device-bound authentication +
                                      pairing

  Forged device identity              Keystore-backed key pair

  Replay of activation request        Short-lived single-use credential

  Credential leakage                  Short-lived scoped session
                                      credentials

  Static TURN credential theft        Per-session TURN credentials

  Malicious authorized viewer         Cooldown + rate limits + session
                                      limits

  Signaling tampering                 TLS/WSS + authenticated device
                                      messages

  Direct IP exposure                  Optional TURN relay-only mode

  Duplicate sessions                  Firestore transaction

  Process death                       Explicit offline state + recovery

  Network change                      ICE restart

  TURN abuse                          Short-lived scoped credentials

  Local USB/ADB compromise            Disable USB debugging on deployed
                                      device

  Log accumulation                    TTL/automatic deletion
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 26. What Should NOT Be Over-Engineered

For a two-phone private system, avoid unnecessary distributed-system
complexity.

You do not need:

-   Kubernetes.
-   Multiple microservices.
-   A custom video transport protocol.
-   A custom congestion-control algorithm.
-   A custom cryptographic media protocol.
-   A large message queue.
-   A dedicated database cluster.

A compact architecture is sufficient:

``` text
Firebase Auth
Firestore
Cloud Functions
WebSocket server
coturn
       +
Android Camera App
Android Viewer App
```

WebRTC handles the difficult media problems.

------------------------------------------------------------------------

# 27. Recommended Technology Stack

## Android

-   Kotlin.
-   Jetpack.
-   CameraX.
-   Android Foreground Service.
-   Android Keystore.
-   WebRTC native Android library.
-   Firebase SDK.
-   FCM.

## Backend

-   Firebase Authentication.
-   Firestore.
-   Cloud Functions.
-   WebSocket service.

## Networking

-   WebRTC.
-   STUN.
-   coturn.

## Security

-   TLS/WSS.
-   Android Keystore.
-   Device-bound asymmetric keys.
-   Short-lived session credentials.
-   Short-lived TURN credentials.

------------------------------------------------------------------------

# 28. Build Order

## Phase 1 --- Local camera

Build:

``` text
CameraX
  +
Preview
  +
Video capture
```

Verify:

-   Resolution.
-   FPS.
-   Exposure.
-   Focus.
-   Thermal behavior.

## Phase 2 --- Local WebRTC

Build:

``` text
CameraX
   ↓
WebRTC
   ↓
Viewer
```

Test on the same network.

## Phase 3 --- Internet connectivity

Add:

-   STUN.
-   TURN.
-   ICE.
-   ICE restart.

Test:

-   Wi-Fi → Wi-Fi.
-   Wi-Fi → mobile data.
-   Mobile data → Wi-Fi.
-   Different NAT types.

## Phase 4 --- Quality adaptation

Implement:

-   Quality profiles.
-   Dynamic bitrate.
-   Dynamic resolution.
-   Dynamic FPS.

## Phase 5 --- Pairing

Implement:

-   Device IDs.
-   Keystore keys.
-   QR-code/manual pairing.
-   Authorization.

## Phase 6 --- Backend control

Add:

-   Firebase Auth.
-   Firestore.
-   Cloud Functions.
-   Session state.

## Phase 7 --- WebSocket control

Add:

-   Presence.
-   Commands.
-   Signaling.
-   Session lifecycle.

## Phase 8 --- FCM recovery

Add:

-   Reconnection notification.
-   Device wake/recovery where Android permits.

## Phase 9 --- Security hardening

Add:

-   Rate limiting.
-   Activation cooldown.
-   Credential expiration.
-   Replay protection.
-   Audit logging.
-   TTL deletion.

## Phase 10 --- Realme reliability testing

Test:

-   Screen off.
-   App backgrounded.
-   Reboot.
-   Force-stop.
-   Battery saver.
-   OEM battery manager.
-   Wi-Fi loss.
-   Mobile-data transition.
-   App update.
-   Low battery.
-   Thermal stress.

------------------------------------------------------------------------

# 29. Critical Acceptance Tests

The application should not be considered production-ready until these
tests pass.

## Video

-   720p continuous stream.
-   1080p continuous stream where supported.
-   No unnecessary session restart when quality changes.
-   Automatic bitrate adaptation.
-   Recovery from temporary packet loss.

## Network

-   Wi-Fi → mobile data.
-   Mobile data → Wi-Fi.
-   Temporary internet loss.
-   TURN fallback.
-   ICE restart.

## Security

-   Unpaired viewer rejected.
-   Revoked viewer rejected.
-   Expired activation credential rejected.
-   Replayed activation credential rejected.
-   Duplicate activation request rejected.
-   TURN credential expires.
-   Session credential expires.

## Lifecycle

-   Screen off.
-   App backgrounded.
-   Device reboot.
-   Process termination.
-   Force-stop.
-   Play Store update.
-   Battery optimization enabled.
-   Battery optimization disabled.

## Camera

-   Camera already in use.
-   Camera permission revoked.
-   Camera unavailable.
-   Camera hardware error.
-   Thermal pressure.
-   Low battery.

------------------------------------------------------------------------

# 30. Final Recommended Architecture

``` text
                              INTERNET
                                  |
             +--------------------+--------------------+
             |                                         |
             v                                         v
    +----------------------+                   +------------------+
    |     CONTROL PLANE    |                   |    MEDIA PLANE   |
    |                      |                   |                  |
    | Firebase Auth        |                   | WebRTC           |
    | Firestore             |                   |                  |
    | Cloud Functions       |                   | P2P              |
    | Authorization         |                   | TURN fallback    |
    | Device registry       |                   | Relay-only       |
    | Rate limiting         |                   | DTLS-SRTP        |
    +-----------+----------+                   +--------+---------+
                |                                       |
                | WebSocket                              |
                |                                       |
       +--------+---------+                    +--------+---------+
       |                  |                    |                  |
       v                  v                    v                  v
+-------------+    +-------------+      +-------------+    +-------------+
| Realme C55  |    | Galaxy S20  |      | Realme C55  |    | Galaxy S20  |
|             |    |             |      |             |    |             |
| Camera App  |    | Viewer App  |      | CameraX     |    | WebRTC      |
|             |    |             |      | Encoder     |    | Renderer    |
| Agent       |    | Controller  |      | WebRTC      |    | Quality     |
| Keystore    |    | Keystore    |      |             |    | Controller  |
+------+------+    +------+------+      +------+------+    +-------------+
       |                  |                     |
       |                  |                     |
       +------------------+---------------------+
                          |
                     Live Video
```

## Architectural priority

The final design should optimize in this order:

1.  **Android lifecycle reliability**
2.  **Continuous WebRTC media**
3.  **Network resilience**
4.  **Security and authorization**
5.  **Privacy**
6.  **Battery efficiency**
7.  **Operational simplicity**

The most important design decision is therefore:

> **Keep the Realme camera agent reliably ready when the user has
> explicitly enabled remote-camera mode, instead of depending on FCM to
> cold-start camera access on demand.**

That gives the system the best chance of delivering the original goal:
**a secure, low-latency, uninterrupted remote camera experience that
behaves much more like a video call than a conventional IP-camera
polling system.**
