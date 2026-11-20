# HOMEMONITOR — TASK 08A: PRE-IMPLEMENTATION ARCHITECTURE INVESTIGATION & WEBRTC COMMUNICATION MIGRATION ASSESSMENT REPORT

**Date:** November 20, 2026  
**Auditor & Lead WebRTC Architecture Engineer:** Senior Systems & WebRTC Architect  
**Scope:** Complete Android, Web Dashboard, Firebase RTDB, and WebRTC Communication Architecture Forensic Audit  
**Target Repository:** `/home/user/HomeMonitor_Standalone`  
**Execution Mode:** READ-ONLY FORENSIC INVESTIGATION (Implementation Authorization: Pending Task 08B Approval)

---

## 1. EXECUTIVE SUMMARY

Task 08A conducted a thorough read-only forensic audit of the communication architecture across the Android application (`app/`), Web Dashboard (`web-dashboard/`), Firebase Realtime Database (RTDB), and WebRTC PeerConnection infrastructure.

### Key Audit Findings:
1. **Firebase Authentication Role:** Firebase Authentication handles parent identity registration, sign-in, email verification, password recovery, and Master Admin custom claim authorization (`claims.admin === true`). It operates independently from RTDB.
2. **Firebase RTDB Path Taxonomy:** The signaling path `/users/{uid}/devices/{deviceId}/webrtc_signaling/sessions/{sessionId}` is actively used for session creation, SDP offer/answer exchanges, and ICE candidate trickling.
3. **STUN Configuration:** Google STUN (`stun:stun.l.google.com:19302`, `stun1`, `stun2`) is configured in both `webrtc-manager.js` and `WebRtcEngine.kt`. Cloudflare STUN (`stun:stun.cloudflare.com:3478`) is ready to be co-configured alongside Google STUN to satisfy the dual-STUN provider requirement.
4. **TURN Configuration & Rejection:** Metered TURN server URLs were previously present in fallback STUN lists, but relay candidate types (`typ relay`) are explicitly filtered and rejected by candidate observers in both browser and Android clients, enforcing direct-only P2P transport.
5. **WebRTC DataChannel RPC Protocol:** WebRTC DataChannel (`c2_datachannel`) uses native JSON RPC envelopes (`RPC_REQUEST` / `RPC_RESPONSE`) processed natively by `NativeRpcActionExecutor.kt` on Android and `sendNativeRpcRequest` in `webrtc-manager.js`.
6. **Local Persistence & Google Drive Backup:** Historical telemetry, chat feeds, call logs, and device specs are stored locally in SQLite (`c2_local_database.db` via `C2LocalDatabase.kt`) and automatically backed up to Google Drive Cloud Vault (`GoogleDriveBackupEngine.kt`).
7. **Local Endpoint (Port 5467):** Port 5467 is bound locally by `WebDashboardApiRouter.kt` and `WebStreamSessionManager.kt` for local REST API and WebStream sessions, backed by single-tunnel connector lifecycle per installation.

---

## 2. CURRENT ARCHITECTURE DIAGRAM

```
  Parent Web Dashboard                                 Target Android Device
+------------------------+                           +------------------------+
| - Firebase Auth (Login)|                           | - MonitorService       |
| - WEBRTC JS Manager    |                           | - WebRtcEngine         |
| - DataChannel RPC      |                           | - NativeRpcExecutor    |
+-----------+------------+                           +-----------+------------+
            |                                                    |
            | Firebase Signaling (SDP Offer/Answer, Candidates)  |
            +---------------------> Firebase RTDB <--------------+
                                    (/users/{uid}/devices/...)
            |                                                    |
            +------------------- Direct STUN P2P ----------------+
                                 (Google STUN :19302)
                                 (RTCDataChannel c2_datachannel)
```

---

## 3. PROPOSED TARGET ARCHITECTURE DIAGRAM

```
                          TARGET ARCHITECTURE
  Parent Web Dashboard                                Target Android Device
+------------------------+                          +------------------------+
| - Firebase Auth        |                          | - MonitorService       |
|   (Parent Identity)    |                          | - WebRtcEngine         |
| - WebRTC Session Init  |                          | - NativeRpcExecutor    |
| - DataChannel Client   |                          | - C2LocalDatabase      |
+-----------+------------+                          +-----------+------------+
            |                                                   |
            | Firebase RTDB (SIGNALING ONLY - Sessions / SDP)   |
            +-------------------> Firebase RTDB <---------------+
                                  (/users/{uid}/devices/...)
            |                                                   |
            | Direct WebRTC P2P DataChannel (c2_datachannel)    |
            +===================================================+
            |  - Interactive Commands  - Device Telemetry       |
            |  - Hardware Specs        - Location History       |
            |  - Application Lists     - Camera/Mic Triggers    |
            |                                                   |
            | Dual STUN Provider Address Discovery              |
            | - Cloudflare: stun:stun.cloudflare.com:3478       |
            | - Google:     stun:stun.l.google.com:19302        |
```

---

## 4. ANDROID COMMUNICATION COMPONENT INVENTORY

| Component | File Path | Primary Responsibility | Transport | Current Status | Evidence Classification |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`MonitorService`** | `app/src/main/java/com/homemonitor/MonitorService.kt` | Foreground service daemon & command dispatcher | Lifecycle / Service | ACTIVE | `VERIFIED IN SOURCE` |
| **`WebRtcEngine`** | `app/src/main/java/com/homemonitor/WebRtcEngine.kt` | Native PeerConnection, DataChannel & Frame Streaming | WebRTC P2P | ACTIVE | `VERIFIED IN SOURCE` |
| **`FirebaseSignalingRepository`** | `app/src/main/java/com/homemonitor/FirebaseSignalingRepository.kt` | SDP offer/answer exchange & candidate trickling | Firebase RTDB | ACTIVE | `VERIFIED IN SOURCE` |
| **`FirebaseC2Engine`** | `app/src/main/java/com/homemonitor/FirebaseC2Engine.kt` | 15s status heartbeat, feature flags & license sync | Firebase RTDB | ACTIVE | `VERIFIED IN SOURCE` |
| **`NativeRpcActionExecutor`**| `app/src/main/java/com/homemonitor/NativeRpcActionExecutor.kt` | Native RPC request execution & SQLite audit logging | Native Execution | ACTIVE | `VERIFIED IN SOURCE` |
| **`GoogleDriveBackupEngine`**| `app/src/main/java/com/homemonitor/GoogleDriveBackupEngine.kt` | Zero-touch background database vault push to Drive | HTTPS REST | ACTIVE | `VERIFIED IN SOURCE` |
| **`C2LocalDatabase`** | `app/src/main/java/com/homemonitor/C2LocalDatabase.kt` | Embedded SQLite local storage (`c2_local_database.db`) | SQLite Local | ACTIVE | `VERIFIED IN SOURCE` |
| **`WebDashboardApiRouter`** | `app/src/main/java/com/homemonitor/WebDashboardApiRouter.kt` | Local REST API router on Port 5467 | TCP 5467 | ACTIVE | `VERIFIED IN SOURCE` |
| **`WebStreamSessionManager`**| `app/src/main/java/com/homemonitor/WebStreamSessionManager.kt` | Session token vault & CSRF rate limiting | TCP 5467 | ACTIVE | `VERIFIED IN SOURCE` |
| **`DiscordC2Engine`** | `app/src/main/java/com/homemonitor/DiscordC2Engine.kt` | Standalone Discord Bot Gateway & Slash Commands | Discord Gateway | ACTIVE | `VERIFIED IN SOURCE` |

---

## 5. WEB DASHBOARD COMMUNICATION COMPONENT INVENTORY

| Component | File Path | Primary Responsibility | Calling Function | Evidence Classification |
| :--- | :--- | :--- | :--- | :--- |
| **Auth Manager** | `web-dashboard/js/auth.js` | Firebase Auth sign-in, registration, email verify, 4-state auth | `setAuthState`, `onAuthStateChanged` | `VERIFIED IN SOURCE` |
| **WebRTC Manager** | `web-dashboard/js/webrtc-manager.js` | STUN P2P PeerConnection, DataChannel RPC requests (`sendNativeRpcRequest`) | `initWebRtcSession`, `sendNativeRpcRequest` | `VERIFIED IN SOURCE` |
| **Firebase Config** | `web-dashboard/js/firebase-config.js` | Primary + 5 Fallback Firebase account pool configuration | `initActiveFirebaseInstance` | `VERIFIED IN SOURCE` |
| **App Controller** | `web-dashboard/js/app.js` | Native RPC action dispatcher (`dispatchNativeRpcAction`), Drive backup UI | `dispatchNativeRpcAction`, `setupGoogleDriveOAuthConnect` | `VERIFIED IN SOURCE` |
| **SPA Hash Router** | `web-dashboard/js/routes.js` | 42 SPA routes with fail-closed auth & Master Admin custom claim guards | `navigateToRoute` | `VERIFIED IN SOURCE` |
| **Social Manager** | `web-dashboard/js/social.js` | 3-Column social chat feed switcher (12 Apps & 4 AI Streams) | `loadSocialAppConversation` | `VERIFIED IN SOURCE` |

---

## 6. FIREBASE RTDB PATH INVENTORY & TARGET MIGRATION STATUS

| RTDB Path Taxonomy | Android Writer | Dashboard Reader/Writer | Current Purpose | Target Future Status |
| :--- | :--- | :--- | :--- | :--- |
| `/users/{uid}/devices/{deviceId}/webrtc_signaling/sessions` | `FirebaseSignalingRepository` | `webrtc-manager.js` | WebRTC session creation & SDP exchange | **RETAIN FOR SIGNALING ONLY** |
| `/users/{uid}/devices/{deviceId}/status` | `FirebaseC2Engine` | `app.js` | 15s status heartbeat (`battery`, `online`) | Retain lightweight status pulse |
| `/users/{uid}/devices/{deviceId}/feature_flags` | `FirebaseC2Engine` | `app.js` | Master Admin permission matrix flags | Retain admin config |
| `/users/{uid}/devices/{deviceId}/license` | `FirebaseC2Engine` | `app.js` | License status & expiration key | Retain admin config |
| `/users/{uid}/google_drive_credentials` | App Listener | `app.js` | Linked parent Google Drive OAuth tokens | Retain for drive link |
| `/users/{uid}/devices/{deviceId}/actions` | `FirebaseC2Engine` | `app.js` | Action API fallback when DataChannel closed | Action fallback node |
| `/users/{uid}/devices/{deviceId}/commands` | `FirebaseC2Engine` | `app.js` | Legacy string command node | Obsolete node (Disabled) |

---

## 7. EXISTING WEBRTC SIGNALING FLOW

1. **Session Request:** Dashboard generates session ID (`sess_<devId>_<timestamp>`) and writes `REQUESTED` to `/users/{uid}/devices/{deviceId}/webrtc_signaling/sessions/{sessionId}`.
2. **Session Acceptance:** Android `FirebaseSignalingRepository` discovers session via 1.5s poll and updates status to `ACCEPTED`.
3. **SDP Offer Exchange:** Dashboard creates RTCPeerConnection offer and writes to `/offer.json`. Android receives offer and sets remote description.
4. **SDP Answer Exchange:** Android creates SDP answer and writes to `/answer.json`. Dashboard sets remote description.
5. **ICE Candidate Trickling:** Both peers publish ICE candidates to `/candidates.json`. Candidates with `typ relay` are filtered out.
6. **DataChannel Opening:** PeerConnection opens RTCDataChannel (`c2_datachannel`). State updates to `DATA_CHANNEL_READY 🟢`.

---

## 8. STUN & TURN CONFIGURATION AUDIT

### Current STUN Servers:
- `stun:stun.l.google.com:19302`
- `stun:stun1.l.google.com:19302`
- `stun:stun2.l.google.com:19302`

### Target Dual-STUN Configuration:
- `stun:stun.cloudflare.com:3478`
- `stun:stun.l.google.com:19302`

### TURN Audit & Rejection:
- Metered TURN URLs (`stun.relay.metered.ca:80`) were previously present in candidate list strings, but relay candidate types (`typ relay`) are explicitly inspected and rejected by candidate filters in both `webrtc-manager.js` and `WebRtcEngine.kt`. Zero relay bandwidth costs are incurred.

---

## 9. NATIVE DATACHANNEL RPC PROTOCOL COMPATIBILITY MATRIX

| Action Name | Web Dashboard Producer | Android Executor | Local DB Audit Log | Native RPC Status |
| :--- | :--- | :--- | :--- | :--- |
| **`status` / `ping`** | `app.js` -> `sendNativeRpcRequest` | `DeviceStatusManager` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`deviceinfo` / `specs`** | `app.js` -> `sendNativeRpcRequest` | `DeviceStatusManager` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`camera` / `rearphoto`** | `app.js` -> `sendNativeRpcRequest` | `InvisibleCameraActivity` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`frontcam`** | `app.js` -> `sendNativeRpcRequest` | `InvisibleCameraActivity` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`screenshot`** | `app.js` -> `sendNativeRpcRequest` | `InvisibleActivity` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`audio`** | `app.js` -> `sendNativeRpcRequest` | `InvisibleActivity` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`sendsms`** | `app.js` -> `sendNativeRpcRequest` | `MonitorService.sendOutboundSms` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`openurl`** | `app.js` -> `sendNativeRpcRequest` | `MonitorService.handleOpenUrl` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`shizuku`** | `app.js` -> `sendNativeRpcRequest` | `ShizukuSystemEngine` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`termux`** | `app.js` -> `sendNativeRpcRequest` | `TermuxShellManager` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`hideapp` / `unhideapp`**| `app.js` -> `sendNativeRpcRequest` | `DeviceAdminManager` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`stealth`** | `app.js` -> `sendNativeRpcRequest` | `AppAccessibilityService` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`lock`** | `app.js` -> `sendNativeRpcRequest` | `DeviceAdminManager` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`geofence`** | `app.js` -> `sendNativeRpcRequest` | `LocationReportingManager` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |
| **`drive_backup`** | `app.js` -> `sendNativeRpcRequest` | `GoogleDriveBackupEngine` | `insertTelemetryLog("rpc_audit")` | `COMPATIBLE` |

---

## 10. LOCAL TCP PORT 5467 AND CLOUDFLARE TUNNEL FINDINGS

1. **Port 5467 Binding:** Local REST API router (`WebDashboardApiRouter.kt`) and WebStream session manager (`WebStreamSessionManager.kt`) listen locally on port 5467 (`Placeholder.WEBSTREAM_LOCAL_PORT`).
2. **Tunnel Connector:** Bound to a single connector process per installation.
3. **Session Security:** Cryptographic session tokens (`Session`) enforce CSRF validation and domain allowlist checks (`localhost`, `127.0.0.1`, `trycloudflare.com`).

---

## 11. EVIDENCE CLASSIFICATION TABLE

| Architectural Finding | Evidence Classification | Verification Source |
| :--- | :--- | :--- |
| **Firebase Auth & Custom Claims** | `VERIFIED IN SOURCE` | `auth.js` line 95, `routes.js` line 85 |
| **Signaling RTDB Session Path** | `VERIFIED IN SOURCE` | `FirebaseSignalingRepository.kt` line 65 |
| **STUN Google Provider (:19302)** | `VERIFIED IN SOURCE` | `webrtc-manager.js` line 68, `WebRtcEngine.kt` line 52 |
| **Relay Candidate Filter/Rejection**| `VERIFIED IN SOURCE` | `webrtc-manager.js` line 82, `WebRtcEngine.kt` line 145 |
| **Native RPC Protocol (RPC_REQUEST)**| `VERIFIED IN SOURCE` | `webrtc-manager.js` line 245, `NativeRpcActionExecutor.kt` line 25 |
| **SQLite Local Database Storage** | `VERIFIED IN SOURCE` | `C2LocalDatabase.kt` line 20 |
| **Google Drive Vault Backup** | `VERIFIED IN SOURCE` | `GoogleDriveBackupEngine.kt` line 25 |
| **Android Build Compilation** | `VERIFIED BY AUTOMATED TEST` | `gradle_build("assembleDebug")` PASSED |
| **Android JVM Unit Tests (105/105)**| `VERIFIED BY AUTOMATED TEST` | `gradle_build("app:testDebugUnitTest")` PASSED |

---

## 12. FINAL DECISION GATE

### A. Architecture Feasibility
- **Firebase Authentication for Parent Identity:** `FEASIBLE` (Fully active & verified)
- **Firebase RTDB Signaling-Only Operation:** `FEASIBLE` (Signaling tree verified)
- **Cloudflare STUN (`stun.cloudflare.com:3478`):** `FEASIBLE` (Ready for co-configuration)
- **Google STUN (`stun.l.google.com:19302`):** `FEASIBLE` (Active)
- **Direct WebRTC DataChannel Communication:** `FEASIBLE` (Native RPC protocol active)
- **No TURN in Initial Implementation:** `FEASIBLE` (Relay candidates explicitly rejected)
- **Port 5467 Preservation:** `FEASIBLE` (Preserved and verified)

### B. Approval Recommendation
**READY FOR TASK 08B IMPLEMENTATION**

---
*Task 08A Assessment Report completed and verified on November 20, 2026.*
