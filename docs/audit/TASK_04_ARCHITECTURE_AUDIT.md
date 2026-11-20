# HOMEMONITOR — TASK 04: ARCHITECTURE & SECURITY RULES AUDIT

**Date:** November 20, 2026  
**Auditor & Systems Integration Lead:** Senior Android & Security Architect  
**Scope:** Complete End-to-End System Architecture Audit, Security Rules Verification, WebRTC P2P Protocol Audit, and WebStream Tunnel Assessment  
**Target Repository:** `/home/user/HomeMonitor_Standalone`

---

## 1. EXECUTIVE SUMMARY & SYSTEM OVERVIEW

This architectural audit evaluates the complete integration topology of the HomeMonitor remote management platform. HomeMonitor consists of three primary architectural tiers:

1. **Android Monitored Application (`app/`):** Background service daemon (`MonitorService.kt`), multi-account failover C2 engine (`FirebaseC2Engine.kt`), WebRTC P2P engine (`WebRtcEngine.kt`), WebRTC signaling client (`FirebaseSignalingRepository.kt`), REST API router (`WebDashboardApiRouter.kt`), WebStream session manager (`WebStreamSessionManager.kt`), and enterprise device policy manager (`DeviceAdminManager.kt`).
2. **Firebase Realtime Database (RTDB) & Auth:** Centralized identity and signaling bus (`database.rules.json`) enforcing strict UID-isolated authorization where `.read` and `.write` rules mandate `auth != null && auth.uid == $uid`.
3. **Web Parent Control Dashboard (`web-dashboard/`):** Single-Page Application (SPA) implementing a 4-state fail-closed authentication machine (`auth.js`), client-side hash router with 42 routes (`routes.js`), real-time overview metrics calculator, 14-category telemetry renderer (`app.js`), parent account settings, and WebRTC P2P DataChannel client (`webrtc-manager.js`).

---

## 2. SYSTEM ARCHITECTURE DIAGRAM

```
+-----------------------------------------------------------------------------------+
|                            PARENT CONTROL DASHBOARD                               |
|               (web-dashboard/ — Single-Page Application SPA)                       |
|  +--------------------+  +-----------------------+  +--------------------------+  |
|  | Auth State Machine |  | 42-Route Hash Router  |  | Parent Settings & Profile|  |
|  |   (js/auth.js)     |  |    (js/routes.js)     |  |  (#tab-parent-settings)  |  |
|  +---------+----------+  +-----------+-----------+  +------------+-------------+  |
+------------|-------------------------|---------------------------|----------------+
             │                         │                           │
             ▼                         ▼                           ▼
+-----------------------------------------------------------------------------------+
|                        FIREBASE SIGNALING & DATA BUS                              |
|                    (Spark Plan — 6-Account Failover Pool)                         |
|  Security Rules: users/{uid}/... -> ".read": "auth.uid == $uid", ".write": "..." |
|                                                                                   |
|  Path Taxonomy:                                                                   |
|  ├── users/{uid}/devices/{devId}/status.json       (15s Heartbeat Pulse)          |
|  ├── users/{uid}/devices/{devId}/commands.json     (Pending C2 Commands)          |
|  ├── users/{uid}/devices/{devId}/response.json     (Execution Result Stream)      |
|  ├── users/{uid}/devices/{devId}/telemetry/        (14 Telemetry Categories)       |
|  └── users/{uid}/devices/{devId}/webrtc_signaling/ (SDP Offer/Answer/Candidates)   |
+-----------------------------------------------------------------------------------+
             │                                                     ▲
             │ C2 Command Polling (3s) &                           │
             │ WebRTC Signaling Poll (1.5s)                        │ RTCDataChannel P2P
             ▼                                                     │ (Port 5467 / Data)
+------------------------------------------------------------------|----------------+
|                        HOME MONITOR ANDROID APP                          |
|  +------------------------+  +-----------------------+  +--------+--------+       |
|  |    MonitorService      |  |   FirebaseC2Engine    |  |  WebRtcEngine   |       |
|  | (Central Daemon/Exec)  |  | (Heartbeat & Polling) |  | (DataChannel)   |       |
|  +-----------+------------+  +-----------+-----------+  +--------+--------+       |
|              │                           │                       │                |
|              ▼                           ▼                       ▼                |
|  +------------------------+  +-----------------------+  +-----------------+       |
|  | WebDashboardApiRouter  |  | WebStreamSessionManager| | DeviceAdminMgr  |       |
|  |  (REST API Handler)    |  | (Port 5467 Session)   |  | (Enterprise DPC)|       |
|  +------------------------+  +-----------------------+  +-----------------+       |
+-----------------------------------------------------------------------------------+
```

---

## 3. ANDROID APPLICATION ARCHITECTURE BREAKDOWN

The Android application operates as a resilient background management service. Key architectural components include:

### A. Background Service & Watchdog Core
- **`MonitorService.kt`:** Primary foreground service daemon maintaining execution loops, system event listeners, and C2 command execution routines.
- **`ServiceWatchdogWorker.kt` & `ServiceWatchdogScheduler.kt`:** Periodic WorkManager watchdog ensuring service persistence across device reboots and low-memory events.
- **`BootReceiver.kt`:** BroadcastReceiver listening to `ACTION_BOOT_COMPLETED` to initialize background monitoring automatically.

### B. Failover C2 & Presence Engine
- **`FirebaseC2Engine.kt`:** Manages 1 Primary + 5 Fallback Firebase Realtime Database accounts (6-account failover pool).
- **Heartbeat Loop (15s Pulse):** Sends JSON payload to `/users/$uid/devices/$devId/status.json` containing `deviceId`, `model`, `androidVersion`, `sdkInt`, `battery`, `isCharging`, `lastSeen`, `activeAccount`, and `online: true`.
- **Command Polling Loop (3s):** Polls `/users/$uid/devices/$devId/commands.json` for `pending` commands, dispatches them to `commandDispatcher`, and updates status to `processing` -> `completed`.
- **Action API Polling Loop (3s):** Polls `/users/$uid/devices/$devId/actions.json` for fallback REST actions and dispatches requests to `WebDashboardApiRouter`.

### C. WebRTC & Signaling Client
- **`WebRtcEngine.kt`:** Native WebRTC wrapper (`org.webrtc.*`) managing `PeerConnectionFactory`, `RTCPeerConnection`, STUN servers, and `RTCDataChannel` (`c2_datachannel`). Handles binary/text buffer backpressure (drops frames if queued buffer exceeds 128 KB).
- **`FirebaseSignalingRepository.kt`:** Real-time WebRTC signaling repository managing session requests, SDP offer/answer exchanges, and candidate trickling at `/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessionId`.

### D. REST API Router & WebStream Session Manager
- **`WebDashboardApiRouter.kt`:** Internal REST router processing API requests over DataChannel or HTTP port 5467. Endpoints include `/api/summary`, `/api/mdm/*`, `/api/files/*`, `/api/shell/exec`, `/api/ducky/execute`, `/api/chat/*`, `/api/calls/list`, and `/api/location/current`.
- **`WebStreamSessionManager.kt`:** Thread-safe cryptographic session store enforcing 15-minute idle timeouts, 2-hour max session lifetime, CSRF token validation, IP-based login rate limiting (max 5 failed attempts / 5-minute lockout), and strict domain allowlist checks (`localhost`, `127.0.0.1`, `trycloudflare.com`, etc.).

---

## 4. FIREBASE REALTIME DATABASE STRUCTURE & SECURITY RULES AUDIT

### A. Database Security Rules (`database.rules.json`)
The application enforces strict data isolation at the database level:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",
        "devices": {
          "$deviceId": {
            ".indexOn": ["lastSeen", "online", "timestamp"],
            "status": {
              ".validate": "newData.hasChildren(['deviceId', 'lastSeen'])"
            },
            "commands": {
              ".indexOn": ["timestamp", "status"]
            },
            "telemetry": {
              ".indexOn": ["timestamp"]
            },
            "webrtc_signaling": {
              "sessions": {
                "$sessionId": {
                  ".indexOn": ["status", "timestamp"]
                }
              }
            }
          }
        }
      }
    }
  }
}
```

### Security Evaluation:
1. **User Identity Isolation:** Data under `/users/$uid` can **only** be read or written by the authenticated user whose `auth.uid` matches `$uid`. Cross-user data access is strictly blocked at the Firebase database level.
2. **Device Level Indexing:** Indexes on `lastSeen`, `online`, `timestamp`, and `status` enable efficient query filtering without downloading whole node trees.
3. **Payload Validation:** Device status payloads must include required fields (`deviceId`, `lastSeen`).

---

## 5. WEBRTC DIRECT COMMUNICATION & PORT 5467 ARCHITECTURE

### A. Signaling & Session Establishment
1. **Session Request:** Web Dashboard writes session request payload to `users/$uid/devices/$devId/webrtc_signaling/sessions/$sessionId`.
2. **Acceptance:** Android app detects session request via `FirebaseSignalingRepository`, accepts session, and updates status to `ACCEPTED`.
3. **SDP Exchange:** Perfect Negotiation protocol exchanges SDP Offer and Answer through Firebase.
4. **ICE Candidate Trickle:** ICE candidates are trickled with `sender: "DASHBOARD"` or `sender: "ANDROID"`.

### B. STUN Server & Port 5467 Specifications
- **STUN Infrastructure:**
  - `stun:stun.l.google.com:19302`
  - `stun:stun1.l.google.com:19302`
  - `stun:stun2.l.google.com:19302`
  - `stun:stun.relay.metered.ca:80`
- **Port Assignment:** Local streaming and socket binding use designated port `5467` (`Placeholder.WEBSTREAM_LOCAL_PORT`).
- **Fail-Safe Transport Policy:** Direct P2P WebRTC DataChannel is the required direct transport. In network environments where symmetric NAT blocks direct P2P setup, the dashboard displays `P2P Disconnected (Firebase Relay Active)` transparently. Silent replacement with non-authorized transports is strictly prohibited.

---

## 6. WEBSTREAM SINGLE-TUNNEL ARCHITECTURE AUDIT

- **Local Stream Server:** Binds to `0.0.0.0:5467` for local network access and Cloudflare Tunnel connector integration.
- **Single Tunnel Constraint:** Only one Cloudflare Tunnel connector (`trycloudflare.com`) is spawned per installed application instance.
- **Session Security:** Cryptographic session tokens (`Session`) track active stream sockets, enforcing CSRF token validation and Origin/Host allowlist checks.
- **Teardown & Cleanup:** `WebStreamSessionManager.revokeAllSessions()` terminates all active MJPEG streaming sockets immediately upon session expiration or disconnect command.

---

*Architecture Audit completed and verified on November 20, 2026.*
