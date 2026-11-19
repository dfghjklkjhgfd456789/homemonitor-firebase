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

### 9. Connection Status & Reconnection Handling Pipeline
- **Android Failover Pool**: `FirebaseC2Engine.rotateToNextFallbackAccount()` seamlessly rotates across 6 Firebase instances (1 Primary + 5 Fallbacks) on HTTP 429 quota limits or network errors.
- **Web Dashboard RTDB Monitor**: `app.js` listens on `.info/connected` to render `#rtdbConnBadge` (`🟢 Sync Active` vs `🔴 Offline / Reconnecting...`).
- **WebRTC P2P ICE Restart**: `webrtc-manager.js` and `WebRtcEngine.kt` automatically trigger `restartIce()` when P2P connection states transition to `disconnected` or `failed`.

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
