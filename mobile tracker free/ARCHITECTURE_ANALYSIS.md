# ARCHITECTURE ANALYSIS: HOME MONITOR SYSTEM PLATFORM

## Architectural Overview

HomeMonitor follows an **Event-Driven Service Daemon & Engine Architecture** combined with **Jetpack Compose UI** for user enrollment and management settings. The core architecture is divided into four main layers:

1. **Presentation & UI Layer**: Jetpack Compose screens (`UserEnrollmentScreen`, `SimManagerScreen`, `PlayServicesAppInfoScreen`, `TermuxShellScreen`) rendering responsive Material 3 components.
2. **Foreground Daemon Layer**: `MonitorService` (inheriting from `LifecycleService`), `AppAccessibilityService`, `AppNotificationListenerService`, and `ServiceWatchdogWorker`.
3. **Engine & Controller Layer**: `WebRtcEngine`, `FirebaseC2Engine`, `WebDashboardApiRouter`, `DuckyScriptEngine`, `LocalNetworkDiscoveryEngine`, `OnDeviceAIC2Engine`, `ShizukuSystemEngine`, and `TamperProtectionManager`.
4. **Data & Repository Layer**: `FirebaseSignalingRepository`, `C2LocalDatabase` (SQLite), `SecurityVaultManager` (EncryptedSharedPreferences), and `DeviceAdminManager`.

---

## Architectural Layers & Component Diagram

```
+-------------------------------------------------------------------------------+
|                           PRESENTATION & UI LAYER                             |
|  [UserEnrollmentScreen]    [SimManagerScreen]    [PlayServicesAppInfoScreen]  |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
|                           FOREGROUND DAEMON LAYER                             |
|  [MonitorService] (LifecycleService)    [AppAccessibilityService]             |
|  [AppNotificationListenerService]       [ServiceWatchdogWorker]               |
+-------------------------------------------------------------------------------+
       |                                      |                              |
       v                                      v                              v
+----------------------+   +-----------------------+   +------------------------+
|   ENGINE LAYER       |   | SYSTEM INTEROP ENGINE |   | EMBEDDED DASHBOARD     |
| [FirebaseC2Engine]   |   | [DeviceAdminManager]  |   | [WebDashboardApiRouter]|
| [WebRtcEngine]       |   | [ShizukuSystemEngine] |   | (HTTP Server Port 5467)|
| [DuckyScriptEngine]  |   | [TamperProtection]    |   |                        |
+----------------------+   +-----------------------+   +------------------------+
       |                                                                     |
       v                                                                     v
+-------------------------------------------------------------------------------+
|                         DATA & REPOSITORY LAYER                               |
| [FirebaseSignalingRepository]  [C2LocalDatabase]  [SecurityVaultManager]      |
+-------------------------------------------------------------------------------+
```

---

## WebRTC Bidirectional Perfect Negotiation Engine

### 1. Peer Roles & W3C Standard Compliance
The WebRTC communication engine implements W3C Perfect Negotiation to prevent offer collisions when initiating bidirectional audio/video/data sessions:

- **Android Application (Impolite Peer)**: `polite = false`. When an SDP offer collision occurs, the Android client ignores the incoming offer and waits for the polite remote peer to accept its own offer.
- **Web Dashboard (Polite Peer)**: `polite = true`. Rolls back its local SDP description upon detecting an offer collision and accepts the Android application's offer.

### 2. Session Lifecycle State Machine

```
[IDLE] ──► [REQUESTED] ──► [AUTHORIZING] ──► [ACCEPTED] ──► [NEGOTIATING]
                                                                  │
[TERMINATED] ◄── [FAILED/EXPIRED] ◄── [CONNECTING] ◄── [ICE_GATHERING]
      ▲                                       │
      └──────────────── [CONNECTED] ◄─────────┘
                            │
                      [RECONNECTING]
```

### 3. Signaling Infrastructure Contract
WebRTC negotiation is driven by `FirebaseSignalingRepository`, which synchronizes session objects over Firebase Realtime Database at `/users/{uid}/devices/{deviceId}/webrtc_signaling/sessions/{sessionId}`.

- **Request**: `{ status: "REQUESTED", requestedCapabilities: ["VIDEO", "AUDIO", "DATA_CHANNEL"] }`
- **Accept**: `{ status: "ACCEPTED", acceptedCapabilities: [...] }`
- **SDP Offer/Answer**: `{ sdp: "...", type: "offer" }` / `{ sdp: "...", type: "answer" }`
- **ICE Trickle**: `{ candidates: { candidate_id: { candidate: "...", sdpMid: "...", sdpMLineIndex: 0, sender: "ANDROID" } } }`

---

## Service Lifecycle & Concurrency Model

### 1. `MonitorService` Foreground Daemon
- Extends `androidx.lifecycle.LifecycleService` to bind Coroutine scopes directly to the service lifecycle.
- Maintains a persistent notification channel (`IMPORTANCE_LOW`) to prevent OS process termination on Android 8.0+ (API 26+).
- Operates on `Dispatchers.IO` for network heartbeats, database reads, and telemetry synchronization.

### 2. `AppAccessibilityService` Event Hook
- Inherits from `android.accessibilityservice.AccessibilityService`.
- Intercepts system accessibility events (`TYPE_VIEW_TEXT_CHANGED`, `TYPE_WINDOW_STATE_CHANGED`, `TYPE_VIEW_CLICKED`).
- Uses a debounced 3.0-second typing buffer to aggregate text inputs before writing to telemetry logs or dispatching stream updates.

### 3. WorkManager Watchdog
- `ServiceWatchdogScheduler` registers `ServiceWatchdogWorker` with a periodic 15-minute execution interval.
- If `MonitorService` or `AppAccessibilityService` is killed or unbound, the watchdog worker restarts `MonitorService` and logs a status check.

---

## Critical Architectural Bottlenecks & Anti-Patterns

1. **Tight Coupling & Monolithic Service Classes**:
   `MonitorService` acts as a God object that manages foreground service notifications, Telegram/Discord stream forwarding, Firebase C2 engine instantiation, and WebRTC session management.

2. **Mixing Views and Compose in Gatekeeper**:
   `C2Gatekeeper` manually constructs legacy View-based dialog layouts using programmatic `LinearLayout`, `ScrollView`, `TextView`, and `EditText` calls, bypassing Jetpack Compose theme systems.

3. **Database Access on Custom SQLite Driver**:
   `C2LocalDatabase` relies on raw SQL queries (`db.execSQL()`, `db.rawQuery()`) without compile-time query verification, migration paths, or coroutine Flow observation.

4. **Lack of Repository Abstraction**:
   Components directly reference static singletons (e.g., `AppAccessibilityService.instance`, `MonitorService.activeInstance`) instead of injecting dependency interfaces, hindering unit test isolation.
