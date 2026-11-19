# DEVELOPMENT MEMORY — BIDIRECTIONAL MULTI-MODE WEBRTC ARCHITECTURE

## Project Context
- **Project**: HomeMonitor Android System & Web Control Platform (`com.google.android.system`)
- **Root Directory**: `/home/user/HomeMonitor_Standalone`
- **Architectural Policy**: **Bidirectional Multi-Mode WebRTC Engine with Firebase RTDB Signaling & W3C Perfect Negotiation**. High-performance P2P streaming for Video, Audio, Screen Sharing, and Application RTCDataChannel messaging.

---

## Technical Architectural Decisions & System Specs

### 1. WebRTC Negotiation & Peer Roles (W3C Perfect Negotiation)
- **Polite Peer**: Web Dashboard (`polite = true`). Rolls back local SDP on offer collisions.
- **Impolite Peer**: Android Application (`polite = false`). Ignores colliding offers and waits for polite peer to process its offer.
- **Candidate Queuing**: ICE candidates received prior to `setRemoteDescription` are buffered in a queue and processed immediately after description installation.
- **ICE Restart**: Triggered automatically on connection disconnection via `restartIce()`.

### 2. Session Capabilities Model
- Supported Capabilities: `VIDEO`, `AUDIO`, `SCREEN_SHARE`, `DATA_CHANNEL`, `COMBINED_MEDIA`.
- Dynamic Negotiation: Negotiated capabilities are validated against runtime permissions (Camera, Mic, MediaProjection) and hardware availability on Android before session confirmation.

### 3. Signaling Infrastructure & Firebase Database Schema Contract
- **Root Authorization Path**: `/users/{uid}/devices/{deviceId}`
- **Security Rules**: Authoritative `database.rules.json` enforces authenticated user isolation (`auth != null && auth.uid == $uid`) across all device nodes.
- **Audited Schema Nodes & Data Contracts**:
  - `status`: `{ deviceId, model, androidVersion, sdkInt, lastSeen, battery, activeAccount, online }`
  - `feature_flags`: `{ camera_enabled, mic_enabled, location_enabled, screen_record_enabled, termux_enabled, sms_calls_enabled, files_enabled, mdm_wipe_enabled }`
  - `commands`: Pushed `{ text, status, timestamp }`
  - `response`: Console execution output string
  - `telemetry/{category}`: Log items `{ category, content, timestamp }`
  - `location`: `{ latitude, longitude, altitude, speed, accuracy, provider, timestamp }`
  - `social/{appKey}`: Feed items `{ appKey, sender, message, timestamp }`
  - `webrtc_signaling/sessions/{sessionId}`: WebRTC negotiation sessions `{ status, requestedCapabilities, offer, answer, candidates }`

### 8. Authenticated Dashboard-to-Device Command Delivery Pipeline
- **Dashboard Push**: `sendC2CommandToDevice()` pushes `{ text, status: "pending", timestamp }` to `/users/$uid/devices/$activeDeviceId/commands`.
- **Android Polling**: `FirebaseC2Engine.startCommandPollingLoop()` polls `/users/$uid/devices/$devId/commands.json` every 3 seconds.
- **Status State Machine**: Command status transitions `pending` -> `processing` -> `completed`.
- **Command Execution & Response**: `MonitorService.dispatchCommand()` executes command and returns string output to `/users/$uid/devices/$devId/response.json`.
- **Dashboard Render**: Realtime `.on("value")` response listener appends execution log output to console views.

---

## Session State Machine

```
[IDLE] ──► [REQUESTED] ──► [AUTHORIZING] ──► [ACCEPTED] ──► [NEGOTIATING]
                                                                  │
[TERMINATED] ◄── [FAILED/EXPIRED] ◄── [CONNECTING] ◄── [ICE_GATHERING]
      ▲                                       │
      └──────────────── [CONNECTED] ◄─────────┘
                            │
                      [RECONNECTING]
```

---

## Code Base Inventory & File References

- **Android Client** (`app/src/main/java/com/homemonitor/`):
  - Package: `com.google.android.system`
  - Core Files: `WebRtcEngine.kt`, `FirebaseSignalingRepository.kt`, `WebStreamSessionManager.kt`, `MonitorService.kt`.
- **Web Dashboard** (`web-dashboard/`):
  - Core Files: `js/webrtc-manager.js`, `js/app.js`, `js/routes.js`, `js/firebase-config.js`, `index.html`.
- **Architecture Proposal Artifact**:
  - `architecture_proposal.artifact.md`
