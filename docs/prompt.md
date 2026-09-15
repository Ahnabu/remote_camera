# MASTER PROMPT — Secure Remote Camera System

You are the lead software architect and senior Android/WebRTC engineer for this project.

I want you to build a production-quality custom Android application that turns one Android smartphone into a securely remotely accessible camera and another Android smartphone into the viewer.

Do NOT attempt to build the entire application in one pass.

Your first responsibility is to understand the complete system, establish a persistent project context, break the project into small independently testable milestones, and then implement those milestones sequentially.

The application must prioritize:

1. Reliable continuous video similar to a video call.
2. Adaptive video quality.
3. Secure device pairing and authorization.
4. Reliable operation within Android's actual lifecycle/security restrictions.
5. WebRTC-based low-latency media.
6. Network-change recovery.
7. TURN fallback.
8. Clear failure states instead of pretending unavailable functionality works.
9. Maintainable, modular code.
10. A persistent project context that survives future coding sessions.

---

# 1. PRODUCT DEFINITION

The project is a two-device Android remote-camera system.

## Camera device

**Realme C55**

It acts as the camera device.

Responsibilities:

* Capture video from the phone camera.
* Run the remote-camera agent.
* Maintain a user-visible READY/STANDBY state when remote camera mode is enabled.
* Accept authorized viewer requests.
* Establish WebRTC sessions.
* Stream live video.
* Adapt video quality according to network/device conditions.
* Handle network changes.
* Handle camera availability errors.
* Handle Android lifecycle interruptions gracefully.

## Viewer device

**Samsung Galaxy S20**

It acts as the viewer.

Responsibilities:

* Authenticate the user/device.
* Pair with the camera device.
* Request camera sessions.
* Display live video.
* Select preferred video-quality profiles.
* Display connection state.
* Display camera availability state.
* Stop sessions.
* Recover from temporary network problems.

---

# 2. IMPORTANT ARCHITECTURAL PRINCIPLE

Do NOT design the Realme as a camera that is guaranteed to be remotely cold-started at arbitrary times.

Instead:

> The Realme is a remotely controllable camera agent that enters a user-enabled READY/STANDBY state.

Android and OEM-specific lifecycle restrictions must be treated as architectural constraints.

The system must NOT attempt to bypass:

* Android background execution restrictions.
* Foreground-service restrictions.
* Camera permission restrictions.
* Android process termination.
* User force-stop behavior.
* OEM battery-management policies.

If Android makes a behavior impossible or unreliable, document it and design a graceful fallback.

Never fake reliability.

---

# 3. REQUIRED TECHNOLOGY STACK

Use the following technology stack unless you identify a concrete technical incompatibility and explain it before changing it.

## Android

* Kotlin
* Android Studio
* Gradle
* Kotlin DSL where appropriate
* Jetpack
* Jetpack Compose for UI
* Material 3
* Coroutines
* Flow / StateFlow
* ViewModel
* Navigation Compose
* DataStore for lightweight local settings
* Android Keystore for device identity
* CameraX for camera capture
* Android Foreground Service for the camera agent
* Firebase Android SDK
* Firebase Cloud Messaging
* Native WebRTC Android library

Minimum Android version should be selected deliberately after checking WebRTC, CameraX, Firebase, and foreground-service requirements.

Do not arbitrarily choose a minimum SDK without documenting the reason.

Target the current stable Android SDK available in the development environment.

---

# 4. BACKEND STACK

Use:

* Firebase Authentication
* Cloud Firestore
* Firebase Cloud Functions
* Firebase Cloud Messaging
* WebSocket-based real-time control/signaling service
* coturn for TURN

The backend should remain lightweight.

Do NOT introduce unnecessary:

* Kubernetes
* microservices
* message queues
* distributed databases
* custom media servers
* custom video protocols

unless there is a concrete requirement.

---

# 5. SYSTEM ARCHITECTURE

Use three explicit planes.

## CONTROL PLANE

Responsible for:

* Authentication
* Device registration
* Pairing
* Authorization
* Device presence
* Session state
* Rate limiting
* Session credentials
* TURN credential issuance
* Audit logging

Components:

```text
Firebase Authentication
Firestore
Cloud Functions
```

---

## SIGNALING / CONTROL CHANNEL

Primary real-time transport:

```text
WebSocket
```

Used for:

* Presence
* Session requests
* Session commands
* SDP offer/answer
* ICE candidate exchange
* ICE restart
* Reconnection
* Session state changes

FCM is NOT the primary real-time transport.

FCM should primarily be used for:

* Recovery
* Reconnection assistance
* Notification/wake attempts where Android permits them

Do not assume FCM delivery is guaranteed or real-time.

---

## MEDIA PLANE

Use:

```text
WebRTC
```

Media path:

```text
Realme
   |
   +---- P2P
   |
   +---- TURN fallback
   |
   +---- TURN relay-only mode
   |
   v
Galaxy S20
```

WebRTC should provide:

* Low latency
* Congestion control
* Adaptive bitrate
* Packet-loss handling
* ICE
* ICE restart
* DTLS-SRTP
* NAT traversal
* TURN fallback

Do NOT build a custom video transport.

---

# 6. DEVICE IDENTITY

Each device must generate its own asymmetric key pair.

Use:

```text
Android Keystore
```

The private key must be non-exportable whenever the platform allows it.

Conceptually:

```text
Realme
  |
  +-- Keystore
       |
       +-- Private key
       +-- Public key

S20
  |
  +-- Keystore
       |
       +-- Private key
       +-- Public key
```

The backend stores the public keys.

The backend must never receive or store private keys.

Use device-bound cryptographic identity as an additional authentication layer.

Do not invent a custom cryptographic protocol.

Use established primitives and libraries.

---

# 7. PAIRING

The system needs an explicit secure pairing process.

Preferred UX:

```text
Realme:
Generate pairing code / QR

        ↓

S20:
Scan QR / enter pairing code

        ↓

Backend:
Verify pairing authorization

        ↓

Both devices:
Store pairing information

        ↓

Paired
```

Pairing should establish:

* Camera device ID
* Viewer device ID
* Public keys
* Authorization relationship
* Pairing timestamp
* Revocation state

Pairing must be revocable.

---

# 8. AUTHORIZATION

A viewer must NOT be able to access a camera merely because they know its device ID.

Authorization must check:

```text
Authenticated account
        +
Registered device
        +
Valid pairing
        +
Permission
        +
Session authorization
        +
Rate limits
```

Every camera session must be authorized.

---

# 9. SESSION CREDENTIAL MODEL

Use separate conceptual credentials.

## Activation credential

Purpose:

Authorize starting a new camera session.

Properties:

* Short TTL
* Single-use
* Bound to session ID
* Bound to camera device
* Bound to viewer device
* Replay resistant

## Session credential

Purpose:

Authorize operations during an already-established session.

It must support:

* ICE restart
* Renegotiation
* Quality renegotiation
* Session continuation

Do NOT reuse an activation token indefinitely.

Do NOT require a new camera activation token simply because ICE restarted.

---

# 10. TURN SECURITY

Never embed permanent TURN credentials in the APK.

Use short-lived per-session TURN credentials.

Flow:

```text
Session request
      |
      v
Cloud Function
      |
      +---- create short-lived TURN credentials
      |
      v
Realme + S20
```

Use coturn.

The implementation must document:

* Credential generation
* TTL
* Secret storage
* Server configuration
* Development configuration
* Production configuration

Never commit TURN secrets to Git.

Create appropriate `.env` / secret-management strategy.

---

# 11. CAMERA SERVICE

The Realme camera side should have a dedicated foreground-service architecture.

Conceptually:

```text
Camera App
    |
    v
Remote Camera Controller
    |
    v
Camera Foreground Service
    |
    +---- CameraX
    |
    +---- Video Encoder
    |
    +---- WebRTC
    |
    +---- WebSocket
    |
    +---- Session Manager
    |
    +---- Network Monitor
```

When the user enables remote camera mode:

```text
Remote Camera = ON
        |
        v
Foreground Service
        |
        v
READY / STANDBY
```

The UI must clearly explain that remote camera mode requires the appropriate Android foreground-service state.

---

# 12. CAMERA STATE MACHINE

Implement an explicit state machine.

Use states conceptually similar to:

```text
OFFLINE
STANDBY
REQUESTED
AUTHENTICATING
NEGOTIATING
CONNECTING
ACTIVE
RECONNECTING
ENDING
CLEANUP
BUSY
UNAVAILABLE
ERROR
```

Do not represent the entire application with scattered booleans such as:

```text
isConnected
isStreaming
isCameraOpen
isRequesting
```

Use a coherent state model.

Document valid transitions.

Prevent invalid transitions.

---

# 13. SESSION FLOW

Implement this flow:

```text
1. Viewer requests camera.

2. Backend verifies:
   - authentication
   - pairing
   - authorization
   - rate limits
   - camera state
   - concurrent-session limit

3. Backend creates:
   - session ID
   - activation credential
   - session credential
   - short-lived TURN credentials

4. Camera agent receives session request.

5. Camera validates the activation credential.

6. Camera checks CameraX availability.

7. Camera opens.

8. Camera creates WebRTC PeerConnection.

9. Camera creates SDP offer.

10. Offer travels through WebSocket signaling.

11. Viewer creates SDP answer.

12. ICE candidates are exchanged.

13. WebRTC connection established.

14. Video begins.

15. Network conditions are continuously monitored.

16. WebRTC adapts bitrate/resolution/framerate.

17. Network change triggers ICE restart.

18. Existing session credential remains valid.

19. Viewer stops session or timeout occurs.

20. Camera closes WebRTC.

21. Camera releases CameraX.

22. Session becomes ended.

23. Camera returns to STANDBY.
```

---

# 14. VIDEO QUALITY

The application must provide user-selectable quality profiles.

Do NOT expose only fixed resolution switches.

Use profiles such as:

| Profile    | Resolution |   FPS | Approx. Target Bitrate |
| ---------- | ---------: | ----: | ---------------------: |
| Data Saver |       480p | 15–24 |           0.4–0.7 Mbps |
| Balanced   |       720p | 24–30 |               1–2 Mbps |
| High       |      1080p | 24–30 |             2.5–4 Mbps |
| Maximum    |      1080p | 30–60 |               4–8 Mbps |

These are target values, not immutable guarantees.

WebRTC must dynamically adapt.

Example:

```text
HIGH selected

1080p
  ↓
Network deteriorates
  ↓
720p
  ↓
480p

Network improves
  ↓
720p
  ↓
1080p
```

The session should remain alive during adaptation.

The goal is:

> uninterrupted viewing, not uninterrupted resolution.

---

# 15. QUALITY CONTROLLER

Implement a dedicated quality-control component.

Monitor:

* RTT
* Packet loss
* Jitter
* Available bitrate
* Frames dropped
* Encoder load
* CPU usage
* Battery
* Thermal state
* Network type

The architecture should be:

```text
User Preference
      |
      v
Quality Controller
      |
      +---- Network
      +---- CPU
      +---- Battery
      +---- Thermal
      |
      v
Target bitrate / resolution / FPS
      |
      v
WebRTC
```

Avoid constant aggressive quality switching.

Use hysteresis / stability thresholds where appropriate.

---

# 16. PRIVACY MODES

Support two modes.

## Standard

Prefer P2P when appropriate.

Advantages:

* Lower latency
* Lower TURN cost

## Relay-only

Force media through TURN.

Advantages:

* Better peer-address privacy
* Predictable media path

Tradeoffs:

* Higher latency
* Higher server bandwidth cost

Make this configurable.

---

# 17. NETWORK RESILIENCE

The system must survive normal network changes.

Test:

```text
Wi-Fi → mobile data
mobile data → Wi-Fi
temporary packet loss
temporary internet interruption
NAT changes
TURN fallback
```

Use WebRTC ICE restart.

Do not terminate the entire application just because the current network path changes.

Expected state:

```text
ACTIVE
  |
  v
RECONNECTING
  |
  v
ACTIVE
```

Only end the session after a defined recovery timeout.

---

# 18. RATE LIMITING AND ABUSE PROTECTION

Implement backend-controlled:

* Activation cooldown
* Activation-per-hour limit
* Concurrent session limit
* Session maximum duration
* Idle timeout

Recommended initial policy:

```text
One active session per camera device.
```

Make limits configurable.

Do not hard-code arbitrary policies throughout the codebase.

---

# 19. FIRESTORE TRANSACTIONS

Session transitions must be atomic.

Do not implement:

```text
read
if idle
write requested
```

without concurrency protection.

Use transactions / compare-and-set.

Example:

```text
Expected:
STANDBY

Transition:
STANDBY → REQUESTED
```

If another request wins:

```text
Request A → success
Request B → rejected
```

No duplicate activation credentials.

No competing camera sessions.

---

# 20. BACKEND DATA MODEL

Use a clean Firestore model similar to:

```text
users/
  {userId}

devices/
  {deviceId}
    ownerId
    type
    publicKey
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

Adjust this model if implementation details require it, but document every architectural change.

---

# 21. LOGGING

Log security and lifecycle events.

Examples:

```text
PAIRING_CREATED
PAIRING_REVOKED
SESSION_REQUESTED
SESSION_ACCEPTED
SESSION_REJECTED
CAMERA_BUSY
CAMERA_UNAVAILABLE
SESSION_STARTED
ICE_RESTART
SESSION_RECONNECTED
SESSION_ENDED
CAMERA_ERROR
```

Never log:

* Video frames
* Audio
* Private keys
* Permanent credentials
* TURN secrets
* Sensitive unnecessary user data

Implement automatic retention/deletion.

Use Firestore TTL or an equivalent mechanism.

---

# 22. ANDROID LIFECYCLE

Explicitly handle:

* App backgrounding
* Screen off
* Process termination
* Device reboot
* Force-stop
* App update
* Battery optimization
* OEM battery management
* Permission revocation
* Low battery
* Thermal throttling

Never claim that force-stop can be remotely bypassed.

If the OS makes the camera unavailable:

```text
Backend:
Camera = OFFLINE
```

Viewer should show:

```text
Camera unavailable
Open the camera app on the Realme to resume remote-camera mode.
```

Do not pretend that a background notification guarantees recovery.

---

# 23. REALME / COLOROS

Realme C55 is a target device.

Treat ColorOS battery management as a real engineering concern.

Create a dedicated reliability test matrix covering:

* Screen off
* App background
* Wi-Fi connected
* Mobile data
* Battery saver
* Battery optimization
* Auto-start settings
* Reboot
* Process kill
* App update
* Long-duration streaming
* Low battery
* Thermal load

The app should have a setup/help screen explaining recommended reliability settings.

---

# 24. USER EXPERIENCE

The viewer app should have a simple flow:

```text
Home
 |
 +-- Camera status
 |
 +-- Request Camera
 |
 +-- Live View
       |
       +-- Quality
       +-- Connection state
       +-- Relay mode
       +-- Stop
```

The camera app should have:

```text
Home
 |
 +-- Remote Camera ON/OFF
 |
 +-- Current status
 |
 +-- Paired devices
 |
 +-- Pair new viewer
 |
 +-- Permissions
 |
 +-- Reliability setup
 |
 +-- Security
```

The camera UI must clearly show when the camera is being remotely accessed.

Do not create hidden surveillance behavior.

---

# 25. SECURITY UX

The camera phone should clearly communicate:

* Remote camera mode enabled.
* Current paired viewer.
* Active session.
* Session start time.
* Ability to stop/revoke access.

Pairing should require deliberate user action.

Do not create secret or stealth camera functionality.

---

# 26. PROJECT STRUCTURE

Start with a clean repository.

Prefer a modular architecture.

A reasonable initial structure:

```text
project-root/
│
├── README.md
├── PROJECT_CONTEXT.md
├── ARCHITECTURE.md
├── DEVELOPMENT_PLAN.md
├── DECISIONS.md
├── SECURITY.md
├── TESTING.md
├── CHANGELOG.md
│
├── android/
│   ├── app/
│   ├── core/
│   ├── camera/
│   ├── webrtc/
│   ├── signaling/
│   ├── authentication/
│   ├── pairing/
│   ├── session/
│   ├── quality/
│   ├── security/
│   └── data/
│
├── backend/
│   ├── functions/
│   └── websocket/
│
├── turn/
│   └── configuration/
│
└── docs/
    ├── diagrams/
    ├── testing/
    └── decisions/
```

You may modify this structure if a better structure is justified.

---

# 27. CRITICAL FILE: PROJECT_CONTEXT.md

Create:

```text
PROJECT_CONTEXT.md
```

This file is mandatory.

It is the project's persistent memory.

Future sessions must be able to read this file and understand:

* What the project does.
* Product requirements.
* Devices.
* Architecture.
* Technology stack.
* Current implementation status.
* Completed milestones.
* Current milestone.
* Next milestone.
* Known bugs.
* Known limitations.
* Architecture decisions.
* Security decisions.
* Database schema.
* API contracts.
* WebSocket protocol.
* WebRTC implementation status.
* Android lifecycle findings.
* Realme/ColorOS findings.
* Testing results.
* Commands used to build/test/run.
* Important configuration.
* Unresolved questions.
* Deferred work.

At the beginning of EVERY future work session:

1. Read `PROJECT_CONTEXT.md`.
2. Read relevant architecture/decision files.
3. Inspect the current code.
4. Determine the current milestone.
5. Continue from the documented state.

At the end of EVERY meaningful implementation session:

1. Update `PROJECT_CONTEXT.md`.
2. Update `CHANGELOG.md`.
3. Update `DECISIONS.md` if an architectural decision changed.
4. Update tests/results.
5. Record the next exact task.

Never rely on chat history as the project's only memory.

---

# 28. CONTEXT UPDATE FORMAT

Maintain this structure inside `PROJECT_CONTEXT.md`:

```markdown
# Project Context

## Project
Name:
Purpose:

## Devices
Camera:
Viewer:

## Current Status
Overall:
Current milestone:
Current task:

## Architecture
Summary:

## Technology Stack
Android:
Backend:
WebRTC:
TURN:
Database:

## Implemented
- ...

## In Progress
- ...

## Next
- ...

## Known Issues
- ...

## Known Platform Limitations
- ...

## Security Model
- ...

## Pairing Model
- ...

## Session Model
- ...

## WebRTC Model
- ...

## Quality Profiles
- ...

## Backend Schema
- ...

## WebSocket Protocol
- ...

## Testing
Passed:
Failed:
Not yet tested:

## Decisions
- ...

## Deferred
- ...

## Important Commands
- ...

## Last Updated
Date:
Milestone:
```

Keep this accurate.

If something is unknown, write:

```text
UNKNOWN — needs verification
```

Do not invent information.

---

# 29. DECISIONS.md

Every meaningful architectural decision should be documented.

Use:

```markdown
# Architecture Decision Records

## ADR-001 — WebRTC for Media

Status:
Accepted

Decision:
Use WebRTC for live media.

Reason:
...

Alternatives:
...

Consequences:
...
```

Use sequential ADR numbers.

---

# 30. DEVELOPMENT PLAN

Before writing significant application code, create:

```text
DEVELOPMENT_PLAN.md
```

Break the entire project into small milestones.

Do NOT create vague phases like:

```text
Phase 1: Build backend
Phase 2: Build frontend
Phase 3: Finish app
```

Instead make milestones small and testable.

Example:

```text
M0 — Repository + documentation
M1 — Android project bootstrap
M2 — CameraX local capture
M3 — Local camera preview
M4 — Basic WebRTC sender
M5 — Basic WebRTC viewer
M6 — Same-network streaming
M7 — STUN
M8 — TURN
M9 — Internet streaming
M10 — Quality profiles
M11 — Adaptive quality
M12 — Device identity
M13 — Pairing
M14 — Firebase authentication
M15 — Backend authorization
M16 — WebSocket control
M17 — Session state machine
M18 — Camera foreground service
M19 — Remote session establishment
M20 — ICE restart
M21 — FCM recovery
M22 — Rate limiting
M23 — Security hardening
M24 — Lifecycle testing
M25 — Realme/ColorOS optimization
M26 — UI polish
M27 — Production hardening
```

You should refine this list after inspecting the actual development environment.

Each milestone must have:

```text
Goal
Prerequisites
Files/components
Implementation tasks
Tests
Acceptance criteria
Known risks
```

---

# 31. VERY IMPORTANT: WORK IN SMALL INCREMENTS

Do not implement multiple major milestones simultaneously.

For each milestone:

```text
1. Explain the objective.
2. Inspect current code.
3. Identify exact files to change.
4. Implement the smallest useful increment.
5. Compile.
6. Run automated tests.
7. Fix failures.
8. Update documentation.
9. Update PROJECT_CONTEXT.md.
10. Report what was completed.
11. Identify the next milestone.
```

Then stop.

Do not silently jump ahead.

---

# 32. DO NOT CREATE FAKE IMPLEMENTATIONS

This is extremely important.

Never create code that merely looks complete.

Do not use:

```text
TODO
NotImplemented
fake WebRTC connection
mock camera stream
dummy authentication
fake TURN server
hardcoded production credentials
placeholder security validation
```

unless the current milestone explicitly requires a stub and the stub is clearly documented.

If a real implementation depends on infrastructure not yet available:

1. Build the smallest legitimate abstraction.
2. Clearly mark the dependency.
3. Document what remains.
4. Do not claim the feature works.

---

# 33. TEST-DRIVEN DEVELOPMENT

For every component where practical:

```text
Implementation
    ↓
Unit test
    ↓
Integration test
    ↓
Device test
```

Prioritize tests for:

* Session state transitions.
* Authorization.
* Pairing.
* Credential expiration.
* Replay protection.
* Rate limiting.
* Firestore transactions.
* WebSocket messages.
* WebRTC connection states.
* Quality controller.
* Network recovery.

---

# 34. BUILD QUALITY GATE

After every meaningful milestone run the appropriate:

```text
./gradlew build
./gradlew test
./gradlew lint
```

Use the actual Gradle tasks available in the project.

For backend:

* Build.
* Unit tests.
* Emulator/local Firebase tests where appropriate.
* Type checking/linting.

Do not move forward with known compilation errors unless explicitly documented as a temporary environment limitation.

---

# 35. WEBRTC IMPLEMENTATION REQUIREMENTS

The WebRTC layer should have clean abstractions.

Conceptually:

```text
WebRtcManager
├── PeerConnectionFactory
├── PeerConnection
├── VideoSource
├── VideoTrack
├── ICE handling
├── SDP handling
├── Connection state
├── ICE restart
├── Bitrate control
└── Cleanup
```

Do not scatter WebRTC calls across UI classes.

The UI must not directly manage PeerConnection internals.

---

# 36. CAMERA IMPLEMENTATION REQUIREMENTS

CameraX should be isolated behind a camera abstraction.

Conceptually:

```text
CameraController
├── open()
├── close()
├── startCapture()
├── stopCapture()
├── switchCamera()
├── setQualityProfile()
├── getCapabilities()
└── observeState()
```

The WebRTC layer should consume frames through a clean interface.

Avoid tightly coupling CameraX implementation to UI.

---

# 37. ERROR HANDLING

Every major operation must have explicit failure handling.

Examples:

```text
CameraPermissionDenied
CameraBusy
CameraUnavailable
DeviceOffline
ViewerUnauthorized
PairingRevoked
ActivationExpired
SessionExpired
TURNUnavailable
WebSocketDisconnected
ICEFailed
CameraServiceStopped
ThermalLimit
LowBattery
```

The viewer should receive useful human-readable states.

Do not expose raw stack traces to users.

---

# 38. OBSERVABILITY

Implement structured logging.

Each session should have:

```text
sessionId
deviceId
event
timestamp
```

Useful debug information:

* WebRTC state.
* ICE state.
* Signaling state.
* Network type.
* Quality profile.
* Actual bitrate.
* Resolution.
* FPS.
* Packet loss.
* RTT.

Do not log secrets.

Provide a developer/debug screen during development if useful.

---

# 39. SECURITY RULES

Never:

* Commit secrets.
* Store private keys in plaintext.
* Put permanent TURN secrets in the APK.
* Trust device IDs without authorization.
* Trust client-provided permissions.
* Allow arbitrary viewer IDs to request sessions.
* Disable TLS certificate validation.
* Invent cryptographic protocols.
* Log authentication tokens.
* Log private keys.

The backend must enforce authorization server-side.

The client must never be considered authoritative for access control.

---

# 40. GIT DISCIPLINE

Use meaningful commits.

Example:

```text
feat(camera): add CameraX capture pipeline
feat(webrtc): add local peer connection
feat(signaling): add websocket session signaling
feat(pairing): add device pairing flow
fix(session): make session transition atomic
test(quality): add bitrate adaptation tests
docs(context): update project context
```

Do not create giant commits containing unrelated work.

---

# 41. ENVIRONMENT AND SECRETS

Create appropriate example configuration files.

For example:

```text
.env.example
```

Never commit actual secrets.

Document:

* Firebase configuration.
* WebSocket endpoint.
* TURN endpoint.
* TURN credential mechanism.
* Development environment.
* Production environment.

If a secret is needed, explain how the developer should provide it.

---

# 42. DOCUMENTATION REQUIREMENT

Maintain:

```text
README.md
PROJECT_CONTEXT.md
ARCHITECTURE.md
DEVELOPMENT_PLAN.md
DECISIONS.md
SECURITY.md
TESTING.md
CHANGELOG.md
```

These documents should evolve with the code.

Documentation is part of the implementation, not an afterthought.

---

# 43. FIRST TASK — DO NOT START BUILDING THE APP YET

Your FIRST response/action should be project initialization and planning.

Do NOT immediately generate the complete application.

Perform these tasks in order:

## Step 1 — Inspect the environment

Determine:

* Current directory.
* Existing files.
* Installed Android SDK.
* Java/JDK version.
* Gradle availability.
* Android Studio-related environment if accessible.
* Node.js availability if needed.
* Firebase tooling.
* Git status.
* Existing project structure.

Do not overwrite an existing project without inspecting it first.

## Step 2 — Create the documentation foundation

Create/update:

```text
PROJECT_CONTEXT.md
ARCHITECTURE.md
DEVELOPMENT_PLAN.md
DECISIONS.md
SECURITY.md
TESTING.md
CHANGELOG.md
README.md
```

## Step 3 — Produce the complete project breakdown

Break the project into small milestones.

For each milestone provide:

* Objective
* Dependencies
* Files
* Implementation tasks
* Tests
* Acceptance criteria
* Risks

## Step 4 — Validate the architecture

Before implementation, identify:

* Technical risks.
* Android restrictions.
* WebRTC risks.
* Realme/ColorOS risks.
* Firebase limitations.
* WebSocket deployment requirements.
* TURN requirements.
* Security risks.

If you discover an architectural problem, STOP and explain it.

Do not silently change the architecture.

## Step 5 — Establish the initial repository structure

Only after the architecture and project breakdown are documented.

## Step 6 — Implement ONLY the first milestone

Do not implement future milestones.

## Step 7 — Verify it

Compile/test/lint what was implemented.

## Step 8 — Update project context

Record:

* What changed.
* What works.
* What does not.
* Test results.
* Current milestone.
* Next exact task.

Then stop and wait for my instruction to continue.

---

# 44. HOW YOU SHOULD COMMUNICATE WITH ME

At the beginning of each milestone, briefly tell me:

```text
Current milestone:
Objective:
What I am going to change:
Acceptance criteria:
```

After implementation:

```text
Completed:
Files changed:
Tests:
Result:
Known issues:
Context updated:
Next milestone:
```

Keep explanations technical and precise.

If you need me to perform a manual action, clearly state:

```text
USER ACTION REQUIRED
```

and explain exactly what I need to do.

Examples:

* Connect Firebase.
* Create Firebase project.
* Add SHA certificate.
* Configure TURN server.
* Install APK.
* Grant Android permission.
* Change Realme battery settings.
* Test on physical device.

Do not pretend you performed actions that require my physical device or external account access.

---

# 45. DECISION-MAKING RULE

When multiple technical approaches are possible:

1. Prefer the simplest architecture that satisfies the requirements.
2. Prefer established libraries/protocols.
3. Prefer Android-supported behavior.
4. Prefer reliability over cleverness.
5. Prefer security by design.
6. Prefer testability.
7. Avoid unnecessary infrastructure.
8. Explain meaningful tradeoffs.

Do not introduce complexity merely because it is technically interesting.

---

# 46. IMPORTANT ARCHITECTURAL PRIORITY

Optimize the project in this order:

```text
1. Android lifecycle reliability
2. Continuous WebRTC media
3. Network resilience
4. Authentication / authorization
5. Privacy
6. Battery efficiency
7. Operational simplicity
```

Do not optimize security complexity at the expense of basic camera reliability.

---

# 47. FINAL PRODUCT EXPERIENCE

The intended normal experience is:

```text
REALME

User enables:
Remote Camera = ON

Status:
READY


S20

User opens app.

Status:
Camera READY

User taps:
REQUEST CAMERA


REALME

Receives authorized request.

Camera opens.


S20

Connecting...


WEBRTC

Negotiates.


S20

LIVE VIDEO

Quality:
HIGH

Connection:
Good


Network deteriorates

1080p
 ↓
720p

Video continues.


Network changes

ICE restart

Video continues.


Network recovers

720p
 ↓
1080p

Video continues.


User taps STOP

Camera closes.

Realme returns to:

READY
```

This is the target behavior.

---

# 48. FINAL INSTRUCTION

Start now.

But remember:

**Do not build the entire application immediately.**

Your first objective is:

```text
INSPECT
   ↓
DOCUMENT
   ↓
BREAK DOWN
   ↓
VALIDATE
   ↓
INITIALIZE
   ↓
IMPLEMENT MILESTONE 1
   ↓
TEST
   ↓
UPDATE PROJECT_CONTEXT.md
   ↓
STOP
```

The project context file is mandatory and must remain the authoritative source of project state.

Whenever implementation differs from the architecture, update the documentation and explain the reason.

Whenever you discover a platform limitation, document it instead of hiding it.

Whenever a decision changes, create/update an ADR.

Whenever a milestone is completed, update the context before proceeding.

Do not skip steps.
Do not fabricate successful tests.
Do not fabricate device behavior.
Do not claim Android guarantees something it does not guarantee.

Build this as a real engineering project, incrementally and verifiably.
