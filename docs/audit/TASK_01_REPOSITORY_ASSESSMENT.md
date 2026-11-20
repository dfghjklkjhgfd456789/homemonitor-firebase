# HOMEMONITOR — TASK 01: REPOSITORY ASSESSMENT & ENGINEERING AUDIT REPORT

**Date:** November 19, 2026  
**Auditor:** Principal Software Architect & Senior Engineering Auditor  
**Scope:** Read-Only Forensic Repository Audit & Target Baseline Assessment  
**Target Repository:** `/home/user/HomeMonitor_Standalone`

---

## 1. EXECUTIVE SUMMARY

This forensic engineering assessment provides a comprehensive, code-verified audit of the **HomeMonitor** multi-platform suite. The system consists of an Android background monitoring and remote-management application, a web-based parent control dashboard, a Linux desktop helper/server application, a browser precision control extension, and a secondary Android bootstrap installer APK.

### Key Audit Findings & Baseline Status:
1. **Android Application Core Architecture:** The Android application is functionally operational with **105 passing unit tests** across 23 test classes and successful debug compilation (`assembleDebug`). However, it suffers from extreme architectural debt. The primary background service (`app/src/main/java/com/homemonitor/MonitorService.kt`) is a **9,496-line monolith** (516 KB) handling over 15 distinct domain responsibilities. Furthermore, there is a systemic **package declaration mismatch**: source files reside in directory path `app/src/main/java/com/homemonitor/`, yet declare `package com.google.android.system` across 30+ files.
2. **Web Parent Dashboard:** Implements a feature-rich Single-Page Application (SPA) Hash Router mapping 50+ path aliases. However, it contains **critical security vulnerabilities**: client-side Master Admin authorization is granted solely by checking if an email string includes `"admin"`, and a local authentication fallback bypasses Firebase Auth while generating unauthenticated pseudo-UIDs (`usr_<hash>`).
3. **Linux Desktop Application:** Currently lacks a compiled native application (e.g., Tauri, Electron, or GTK). It relies entirely on a lightweight Node.js (`server.js`) or Python 3 (`server.py`) HTTP server hosting the static web dashboard on port `5467` with local SQLite storage (`c2_admin_database.db`).
4. **Firebase & Backend Synchronization:** Database rules (`database.rules.json`) correctly enforce `auth != null && auth.uid == $uid`. However, authentication bypasses on the client side conflict directly with these rules, resulting in failed real-time synchronization when non-authenticated fallback sessions are active.
5. **Product & Privacy Alignment:** Legacy features contain covert monitoring mechanisms (stealth launcher hiding, secret code receiver, unnotified keylogging). In accordance with legal family-safety software requirements, these must be restructured into transparent, consent-aware parental controls.

---

## 2. COMPLETE REPOSITORY INVENTORY

Below is the verified inventory of all top-level components, directories, frameworks, build systems, and configuration files within the repository.

### Directory Structure & Inventory Overview
| Component Path | Primary Framework / Language | Role & Responsibility | Key Configuration / Artifacts |
| :--- | :--- | :--- | :--- |
| `/app` | Kotlin, Android SDK, Jetpack Compose | Core Android Child Application | `build.gradle.kts`, `AndroidManifest.xml`, `proguard-rules.pro` |
| `/bootstrap-installer` | Kotlin, Android SDK | Secondary APK for downloading and installing main app | `build.gradle.kts`, `AndroidManifest.xml` |
| `/admin-app` | Node.js (`server.js`), Python 3 (`server.py`) | Linux Desktop local server hosting & SQLite storage | `start-admin-app.sh`, `c2_admin_database.db`, `README.md` |
| `/web-dashboard` | HTML5, CSS3, JavaScript (ES6), Bootstrap 5 | Parent Web Control Dashboard | `index.html`, `js/app.js`, `js/auth.js`, `js/webrtc-manager.js` |
| `/webstream-precision` | JavaScript (ES6), Manifest V3 | Chromium & Firefox Precision Input Browser Extension | `package.json`, `scripts/build.mjs`, `manifest.json` |
| `/gradle` & Root | Gradle 8.7, Kotlin DSL | Root multi-project build configuration | `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `libs.versions.toml` |
| `/database.rules.json` | JSON | Firebase Realtime Database Security Rules | Security rules for `users/$uid/devices/$deviceId` |
| `DEVELOPMENT_MEMORY.md` | Markdown | Legacy architectural notes & WebRTC specs | Architectural specifications |

### Build Systems & Toolchain Status
- **Android Build System:** Gradle 8.7.3 with Android Gradle Plugin (AGP) `8.7.3`, Kotlin `2.0.21`, Java 17.
- **Unit Test Runners:** JUnit 4.13.2 for Android JVM unit tests (`app:testDebugUnitTest` executed: 105 passed, 0 failed).
- **Web Dashboard / Scripting:** Native browser ES6 JavaScript. No bundler required for raw execution.
- **Linux Admin Backend:** Python 3 (Installed at `/usr/bin/python3`) and Node.js (`server.js`).
- **Browser Extension:** Manifest V3 JavaScript core with build script `scripts/build.mjs`.

---

## 3. CURRENT ARCHITECTURE DIAGRAM

```
+---------------------------------------------------------------------------------------+
|                                    FIREBASE RTDB                                      |
|            Path Contract: /users/{uid}/devices/{deviceId}/                            |
|  - status               - commands             - telemetry/{category}                 |
|  - feature_flags        - response             - webrtc_signaling/sessions/{id}       |
+---------------------------------------------------------------------------------------+
            ▲                                      ▲                               ▲
            │ (Signaling & Telemetry)              │ (Command Dispatch & Sync)     │ (REST/Signaling)
            ▼                                      ▼                               ▼
+-----------------------+              +-----------------------+      +-----------------------+
|  ANDROID APPLICATION  |              |     WEB DASHBOARD     |      |  LINUX DESKTOP APP    |
| (com.google.android.  |              | (web-dashboard/       |      | (admin-app/           |
|      system)          |              |  index.html & JS)     |      |  server.py / .js)     |
+-----------------------+              +-----------------------+      +-----------------------+
  │                                      │                              │
  ├── MonitorService.kt (9.5k lines)     ├── js/auth.js (Role Bypass)   ├── Local SQLite DB
  ├── AppAccessibilityService.kt         ├── js/routes.js (Hash Router) └── Serves Web Dashboard
  ├── WebRtcEngine.kt                    ├── js/webrtc-manager.js           on Port 5467
  └── DeviceAdminManager.kt              └── js/app.js (C2 Controller)
            │                                      │
            └─────────────────────────►────────────┘
               WebRTC Direct P2P Tunnel (DataChannel & Media)
```

---

## 4. ANDROID APPLICATION AUDIT

### 4.1 Application Entry Points & Core Components
- **Main Activity:** `com.google.android.system.MainActivity` (`app/src/main/java/com/homemonitor/MainActivity.kt`). Initializes Compose UI, requests permissions, and starts `MonitorService`.
- **Secondary App (Bootstrap Installer):** `com.google.android.system.installer.MainActivity` (`bootstrap-installer/src/main/java/.../MainActivity.kt`). Independent APK downloader and installer wrapper.
- **Foreground Service:** `MonitorService.kt` (`app/src/main/java/com/homemonitor/MonitorService.kt`). Foreground service declared with types `camera|location|microphone|mediaProjection`.
- **Accessibility Service:** `AppAccessibilityService.kt`. Declared with `BIND_ACCESSIBILITY_SERVICE`. Listens to all window state and text change events.
- **Notification Listener Service:** `AppNotificationListenerService.kt`. Binds to `BIND_NOTIFICATION_LISTENER_SERVICE`.
- **Device Administrator:** `AppDeviceAdminReceiver.kt`. Binds to `BIND_DEVICE_ADMIN`.

### 4.2 Significant Findings & Architectural Defects
1. **Oversized Monolithic God Class (`MonitorService.kt`):**
   - **Path:** `app/src/main/java/com/homemonitor/MonitorService.kt` (9,496 lines, 516 KB, 388 outline items).
   - **Violation:** Violates the Single Responsibility Principle (SRP). Combines foreground service lifecycle, Telegram bot HTTP polling loop, Discord webhook streaming, Firebase RTDB listeners, ambient audio recording, call recording, front/back camera capture, screen recording, local WebStream HTTP/WebSocket server (port 5467), motion sensor detection, keylogger telemetry ingestion, SMS/Call log processing, Termux command execution, and device admin wipe/lock actions into a single class.
2. **Package Name Mismatch Across Directory Tree:**
   - **Path:** `app/src/main/java/com/homemonitor/*`
   - **Defect:** All Kotlin source files reside in filesystem directory `com/homemonitor/`, but over 30 files declare `package com.google.android.system` (e.g., `MonitorService.kt`, `MainActivity.kt`, `WebRtcEngine.kt`), while 6 files declare `package com.homemonitor` (e.g., `C2TrialManager.kt`, `UserEnrollmentScreen.kt`, `WebDashboardApiRouter.kt`). This namespace fragmentation creates import ambiguities and breaks clean module boundaries.
3. **Hardcoded Placeholder Credentials & Fallback Logic:**
   - **Path:** `app/src/main/java/com/homemonitor/Placeholder.kt` & `MonitorService.kt:114-120`
   - **Defect:** `DISCORD_BOT_TOKEN`, `DISCORD_GUILD_ID`, `DISCORD_APP_ID`, `C2_MASTER_PASSCODE`, `SERVER_URL`, `BOT_TOKEN`, and `CHAT_ID` contain empty string placeholders (`""`). When these strings are empty, background routines enter silent retry loops or fail silently without fallback error handling.
4. **Duplicate Database Interfaces:**
   - **Path:** `app/src/main/java/com/homemonitor/C2LocalDatabase.kt` vs `FirebaseC2Engine.kt`
   - **Defect:** Telemetry and social messages are saved to both a local SQLite database (`C2LocalDatabase`) and pushed to Firebase RTDB (`FirebaseC2Engine.kt`), but there is no synchronization or reconciliation logic between local storage and cloud database state.
5. **Screen Recording & Camera Service Concurrency Risk:**
   - **Path:** `app/src/main/java/com/homemonitor/ScreenRecordingService.kt` & `InvisibleCameraActivity.kt`
   - **Defect:** Simultaneous requests for media projection (screen capture) and CameraX capture can cause hardware resource locks on lower-end Android devices due to lack of a centralized hardware lock coordinator.

---

## 5. WEB DASHBOARD AUDIT

### 5.1 Architecture & Navigation Inventory
The Web Dashboard (`web-dashboard/index.html`) is a Single-Page Application (SPA) driven by `js/routes.js` and `js/app.js`.

#### Complete Inventory of Navigation Target Tabs & Verification Status:
| Navigation Alias / Route | Tab ID (`tab-panel`) | Functionality Status | Verified Behavioral Evidence |
| :--- | :--- | :--- | :--- |
| `/` or `/dashboard` | `tab-dashboard-overview` | **VERIFIED** | Displays active device metadata, battery level, GPS widget, and activity stream. |
| `/deviceinfo` | `tab-device-info` | **VERIFIED** | Displays model, Android OS version, SDK level, battery state, and IP details. |
| `/surroundings_recording` | `tab-ambient-mic` | **PARTIAL** | UI triggers `/audio {sec}` command; relies on device microphone permission. |
| `/remote_camera` | `tab-camera-studio` | **PARTIAL** | UI triggers `!camera` front/back photo command; updates telemetry gallery. |
| `/call_recording` | `tab-call-recs` | **PARTIAL** | Fetches call recording telemetry logs; depends on target phone call state listener. |
| `/live_screen_streaming` | `tab-webstream` | **VERIFIED** | Initiates WebRTC P2P session (`!webstream`) and renders live canvas/video stream. |
| `/block_app` | `tab-mdm-security` | **VERIFIED** | Triggers `!hidepkg` and `!suspendpkg` commands via Device Admin Manager. |
| `/block_website` | `tab-browser-history` | **PARTIAL** | Displays browser history; website blocking rules are UI-only input fields. |
| `/key_logger_app` | `tab-keylogger` | **VERIFIED** | Renders real-time keystroke telemetry feed from `telemetry/keylogger`. |
| `/termux` | `tab-termux-shell` | **VERIFIED** | Dispatches `!termux {cmd}` to Android Termux subsystem and renders CLI output. |
| `/sms_chat` | `tab-sms-messages` | **VERIFIED** | Renders SMS inbox telemetry and sends outbound SMS via `/sendsms` form. |
| `/whatsapp_chat` (and 12 Social Apps) | `tab-social-chat` | **VERIFIED** | 3-Column social chat UI populated from `social/{appKey}` database nodes. |
| `/location` & `/geofencing_area` | `tab-gps-locations` / `tab-geofencing` | **PARTIAL** | Displays GPS position; geofence creation dispatches command `!geofence add`. |
| `/file_explorer` / `/files` | `tab-file-explorer` | **PARTIAL** | Fetches file metadata telemetry; full file download requires active P2P DataChannel. |
| `/file_dropper` | `tab-file-dropper` | **VERIFIED** | Dispatches `/download {url} {path}` payload command to Android device. |
| `/master_admin` | `tab-master-admin` | **BROKEN** | Security vulnerability: unlocked via client-side email string check. |

### 5.2 Critical Web Dashboard Defect Analysis
1. **Client-Side Authorization Bypass (Master Admin):**
   - **Path:** `web-dashboard/js/auth.js:12`
   - **Code snippet:** `isMasterAdmin = user.email ? user.email.toLowerCase().includes("admin") : false;`
   - **Defect:** Master Admin privileges (which grant access to feature flags, license state, and device wiping) are granted to any user whose email contains `"admin"` (e.g., `user_admin@gmail.com`).
2. **Local Session Authentication Fallback & Pseudo-UID Risk:**
   - **Path:** `web-dashboard/js/auth.js:46-62`
   - **Defect:** If Firebase Auth returns an error (e.g., invalid API key or offline network), the dashboard executes `performLocalSessionAuth()` and generates a pseudo-UID (`generateDerivedUid(email)` -> `usr_12345`). Unauthenticated requests made with this pseudo-UID fail against Firebase RTDB security rules.

---

## 6. LINUX DESKTOP APPLICATION AUDIT

### 6.1 Architectural Reality Assessment
- **Current State:** There is **NO native compiled desktop application** (Tauri, Electron, or GTK/Libadwaita) in `admin-app/`.
- **Actual Implementation:** `admin-app/` contains two backend server options:
  1. `server.js`: Node.js HTTP server using `sqlite3` driver.
  2. `server.py`: Python 3 standard library server (`http.server` + `sqlite3`).
  3. `start-admin-app.sh`: Launcher script selecting Node.js or Python 3.
- **Functionality:** Serves the static web dashboard (`web-dashboard/`) on port `5467` and exposes local REST API endpoints (`/api/admin/status`, `/api/admin/telemetry`) backed by a local SQLite database (`c2_admin_database.db`).

### 6.2 Framework Technology Evaluation for Target Desktop Application
| Framework | Binary Size | Memory Overhead | Web Dashboard UI Reuse | Native System Integration | Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tauri (v2)** | ~15 MB | ~30–50 MB RAM | **100% Direct Reuse** | Native Rust SQLite, system tray, desktop notifications | **RECOMMENDED TARGET** |
| **Electron** | ~120 MB | ~180–300 MB RAM | **100% Direct Reuse** | High resource overhead, extra security attack surface | Not Recommended |
| **GTK4 / Libadwaita** | ~10 MB | ~20–40 MB RAM | 0% (Requires total UI rewrite) | Native GNOME styling, but requires rewriting UI in C/Rust | Not Recommended |

---

## 7. FIREBASE AND BACKEND AUDIT

### 7.1 Security Rules & Authoritative Schema Contract
The repository contains `database.rules.json` at root:
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
            "status": { ".validate": "newData.hasChildren(['deviceId', 'lastSeen'])" },
            "commands": { ".indexOn": ["timestamp", "status"] },
            "telemetry": { ".indexOn": ["timestamp"] },
            "webrtc_signaling": { "sessions": { "$sessionId": { ".indexOn": ["status", "timestamp"] } } }
          }
        }
      }
    }
  }
}
```

### 7.2 Database Defect Analysis
1. **Rule Enforcement vs Client Local Session Fallback:** Security rules strictly require `auth != null && auth.uid == $uid`. When the Web Dashboard uses `performLocalSessionAuth()` (which generates `usr_<hash>` without a valid Firebase Auth ID Token), all read/write operations to Firebase RTDB are blocked by Firebase servers.
2. **Client-Controlled Feature Flags & License Status:** In `web-dashboard/js/app.js:284`, license status (`ACTIVE`, `TRIAL`, `EXPIRED`) and feature flags are updated directly by the client browser writing to `users/$uid/devices/$deviceId/license`. Security rules do not validate if the caller holds a verified server-side Master Admin role.

---

## 8. FEATURE COMPLETENESS MATRIX

| Feature Area | Android Status | Web Dashboard Status | Linux Desktop Status | Backend / Firebase Status | Missing Dependencies / Evidence |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Authentication** | PARTIAL | BROKEN | PARTIAL | VERIFIED | Web dashboard uses unsafe email substring check & local auth fallback. |
| **Device Enrollment** | VERIFIED | VERIFIED | VERIFIED | VERIFIED | Auto-generates hardware device ID; discovers devices under `users/$uid/devices`. |
| **Device Status & Battery** | VERIFIED | VERIFIED | VERIFIED | VERIFIED | Heartbeat updates `status` node with battery, OS version, and `lastSeen`. |
| **WebRTC P2P Screen Stream** | VERIFIED | VERIFIED | PARTIAL | VERIFIED | W3C Perfect Negotiation & DataChannel verified (`WebRtcEngine.kt` & `webrtc-manager.js`). |
| **Termux Shell Execution** | VERIFIED | VERIFIED | PARTIAL | VERIFIED | Executes via `RUN_COMMAND` intent; streams output to Web Dashboard. |
| **Ambient Mic Recording** | PARTIAL | PARTIAL | PARTIAL | PARTIAL | `MonitorService.kt` captures audio; requires foreground service mic permission. |
| **Camera Capture** | PARTIAL | PARTIAL | PARTIAL | PARTIAL | Front/back photo capture via `InvisibleCameraActivity.kt`; requires camera permission. |
| **GPS Location & Geofence** | VERIFIED | VERIFIED | PARTIAL | VERIFIED | Location updates `telemetry/gps_locations`; geofence distance calculated in Android. |
| **Keylogger & Clipboard** | VERIFIED | VERIFIED | VERIFIED | VERIFIED | `AppAccessibilityService.kt` streams keylogs to `telemetry/keylogger`. |
| **SMS & Call Telemetry** | VERIFIED | VERIFIED | VERIFIED | VERIFIED | SMS and Call Log telemetry streamed to database and dashboard UI. |
| **MDM App Hide & Suspend** | VERIFIED | VERIFIED | PARTIAL | VERIFIED | `DeviceAdminManager.kt` hides/suspends target packages when provisioned as Device Owner. |
| **Social App Monitoring** | VERIFIED | VERIFIED | VERIFIED | VERIFIED | 3-Column social chat UI populated from `social/{appKey}` nodes. |
| **Google Drive Cloud Sync** | PARTIAL | PARTIAL | MISSING | UNVERIFIED | Web dashboard UI contains Google OAuth button; requires valid Client ID. |

---

## 9. BUILD AND TEST BASELINE

### 9.1 Checks Executed & Verified Results
| Check Category | Command / Execution Method | Status | Result / Details |
| :--- | :--- | :--- | :--- |
| **Android Unit Tests** | `gradle_build("app:testDebugUnitTest --continue")` | **PASSED** | **105 passed, 0 skipped, 0 failed** across 23 test classes. |
| **Android Assembly** | `gradle_build("assembleDebug")` | **PASSED** | Compiled `:app` and `:bootstrap-installer` debug APKs successfully. |
| **Python Server Syntax** | `python3 -m py_compile admin-app/server.py` | **PASSED** | Syntax validated with 0 errors. |
| **Git Working Tree** | `git status` | **VERIFIED** | Clean working tree (no modified source files). |
| **Node.js / npm Checks** | `npm test` in `webstream-precision` | **BLOCKED** | Environment constraint: `npm` binary not installed in system PATH. |
| **Direct Shell `./gradlew`**| Shell execution `./gradlew test` | **BLOCKED** | Environment constraint: Shell `/usr/bin/java` binary exec format mismatch. |

---

## 10. SECURITY AND PRIVACY ASSESSMENT

1. **Authentication & Authorization Weaknesses:**
   - Client-side Master Admin check in `web-dashboard/js/auth.js` (`user.email.includes("admin")`) permits unauthorized privilege escalation.
   - Local authentication fallback creates unauthenticated sessions using string-hashed pseudo-UIDs (`usr_<hash>`).
2. **Covert vs Transparent Product Alignment:**
   - Legacy capabilities (`SecretCodeReceiver.kt`, launcher hiding via `setApplicationHidden`, `InvisibleActivity`, unnotified accessibility keylogging) conflict with transparent, parent-authorized family safety product standards.
   - **Remediation:** Transform covert monitoring behaviors into transparent, consent-aware parental safety controls with persistent notification indicators.
3. **Insecure Credential Storage:**
   - Standardize empty string placeholders (`Placeholder.kt`) into secure runtime configuration properties loaded from `local.properties` or encrypted environment variables.

---

## 11. REUSABLE / REFACTOR / REPLACE / REMOVE RECOMMENDATIONS

- **REUSE (Keep & Maintain):**
  - WebRTC P2P Engine (`WebRtcEngine.kt` & `webrtc-manager.js`) — compliant with W3C Perfect Negotiation.
  - Device Admin Manager (`DeviceAdminManager.kt`) — enterprise management and app restriction capabilities.
  - Web Dashboard UI Layout (`web-dashboard/index.html` & `css/style.css`) — comprehensive control panels.
  - SQLite Local Database (`admin-app/server.js` & `server.py` schema).
- **REFACTOR (Restructure):**
  - **`MonitorService.kt`:** Deconstruct 9,496-line monolith into single-responsibility feature managers (`LocationManager`, `MediaCaptureManager`, `MdmManager`, `TelemetryManager`).
  - **Package Structure:** Standardize all Android Kotlin files to package `com.homemonitor`.
  - **`web-dashboard/js/auth.js`:** Replace email string matching with authoritative Firebase Auth tokens and custom claims.
- **REPLACE (Upgrade):**
  - **`admin-app/`:** Replace raw Node/Python scripts with a native **Tauri v2** Linux desktop application wrapper.
- **REMOVE (Obsolete / Insecure):**
  - Hardcoded pseudo-UID fallback auth in `web-dashboard/js/auth.js`.
  - Stealth covert features (secret codes, unnotified keyloggers) that violate family safety compliance.

---

## 12. PROPOSED TARGET ARCHITECTURE

```
+---------------------------------------------------------------------------------------+
|                                    TARGET SYSTEM                                      |
+---------------------------------------------------------------------------------------+

 ┌──────────────────────────────────────┐       ┌──────────────────────────────────────┐
 │     ANDROID CHILD APPLICATION        │       │       PARENT CONTROL CLIENTS         │
 │           (module: :app)             │       │                                      │
 │                                      │       │  ┌────────────────────────────────┐  │
 │  ┌────────────────────────────────┐  │       │  │    WEB PARENT DASHBOARD        │  │
 │  │      Jetpack Compose UI        │  │       │  │ (TypeScript / Modular ES6 SPA)│  │
 │  └───────────────┬────────────────┘  │       │  └───────────────┬────────────────┘  │
 │                  │                   │       │                  │                   │
 │  ┌───────────────▼────────────────┐  │       │  ┌───────────────▼────────────────┐  │
 │  │   Modularized Domain Managers  │  │       │  │   TAURI V2 LINUX DESKTOP APP   │  │
 │  │ - LocationManager              │  │       │  │ (Embedded Web UI + Rust SQLite│  │
 │  │ - MediaCaptureManager          │  │       │  │  & System Tray Integration)    │  │
 │  │ - MdmManager                   │  │       │  └───────────────┬────────────────┘  │
 │  │ - TelemetrySyncManager         │  │       │                  │                   │
 │  └───────────────┬────────────────┘  │       └──────────────────┼───────────────────┘
 │                  │                   │                          │
 │  ┌───────────────▼────────────────┐  │                          │
 │  │ WebRTC P2P Engine & DataChan   │◄─┼──────────────────────────┘
 │  └───────────────┬────────────────┘  │     Direct WebRTC Tunnel / P2P DataChannel
 └──────────────────┼───────────────────┘
                    │
                    ▼
+---------------------------------------------------------------------------------------+
|                              AUTHORITATIVE FIREBASE RTDB                              |
|                    Path: /users/{authenticated_uid}/devices/{deviceId}/               |
+---------------------------------------------------------------------------------------+
```

---

## 13. PRIORITIZED DEFECT REGISTER

| Issue ID | Priority | Affected Component | Exact File Path | Verified Problem Summary | Impact | Recommended Resolution |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **P0-01** | **P0** | Web Dashboard | `web-dashboard/js/auth.js:12` | Admin role granted via `email.includes("admin")`. | Critical auth bypass | Use Firebase Auth Custom Claims or database RBAC roles. |
| **P0-02** | **P0** | Web Dashboard | `web-dashboard/js/auth.js:46` | Offline local auth fallback generates unauthenticated `usr_<hash>` UID. | RTDB sync rejection | Enforce mandatory Firebase Auth token verification. |
| **P0-03** | **P0** | Android App | `app/src/main/java/com/homemonitor/MonitorService.kt` | 9,496-line monolithic service handling 15+ responsibilities. | High debt & instability | Refactor into modular domain managers. |
| **P0-04** | **P0** | Android App | `app/src/main/java/com/homemonitor/*` | Package declared as `package com.google.android.system` in `com/homemonitor/` path. | Namespace & build risk | Align package declarations to `com.homemonitor`. |
| **P1-01** | **P1** | Android App | `app/src/main/AndroidManifest.xml` & `SecretCodeReceiver.kt` | Stealth launcher hiding and secret codes present in codebase. | Policy compliance risk | Convert to transparent parental controls with user disclosure. |
| **P1-02** | **P1** | Linux Desktop | `admin-app/` | Missing compiled native desktop wrapper (only Node/Python scripts). | Usability & packaging | Build a Tauri v2 wrapper bundling web dashboard. |
| **P1-03** | **P1** | Firebase | `web-dashboard/js/app.js:284` & `database.rules.json` | License state and flags updated directly by client without role check. | Security rule bypass | Restrict license updates to verified admin roles. |
| **P2-01** | **P2** | Web Dashboard | `web-dashboard/js/firebase-config.js` | Hardcoded dummy Firebase account keys in account pool array. | Configuration debt | Load Firebase configuration dynamically. |
| **P3-01** | **P3** | Extensions | `webstream-precision/package.json` | Missing `npm` binary in CLI environment for running extension unit tests. | Developer workflow | Document environment setup and containerized testing. |

---

## 14. DEPENDENCY-AWARE IMPLEMENTATION ROADMAP

```
  PHASE 1: SECURITY & CORE CLEANUP (P0)
  ├── Task 1.1: Fix Web Dashboard Authentication & Privilege Escalation (P0-01, P0-02)
  ├── Task 1.2: Align Android Package Declarations & Namespaces (P0-04)
  └── Task 1.3: Deconstruct MonitorService.kt Monolith into Domain Managers (P0-03)
            │
            ▼
  PHASE 2: COMPLIANCE, FIREBASE & DESKTOP WRAPPER (P1)
  ├── Task 2.1: Enforce Authoritative Firebase RTDB Security Rules & RBAC (P1-03)
  ├── Task 2.2: Refactor Covert Monitoring into Transparent Parental Controls (P1-01)
  └── Task 2.3: Build Tauri v2 Linux Desktop Application Wrapper (P1-02)
            │
            ▼
  PHASE 3: POLISH, EXTENSION & E2E TESTING (P2 & P3)
  ├── Task 3.1: Clean Up Configuration Pools & Dynamic Key Loading (P2-01)
  └── Task 3.2: Execute Full System Integration & E2E Verification
```

---

## 15. RECOMMENDED NEXT TASK

### Immediate Next Engineering Action:
**TASK 02: WEB DASHBOARD AUTHENTICATION & PRIVILEGE ESCALATION FIX (P0-01 & P0-02)**

- **Objective:** Eliminate the critical security flaw in `web-dashboard/js/auth.js` where Master Admin role is granted via email string matching (`email.includes("admin")`), and replace the unauthenticated local fallback (`performLocalSessionAuth`) with strict, authoritative Firebase Authentication.
- **Verification Plan:** Verify that login requires valid Firebase Auth credentials, non-admin accounts cannot access the Master Admin tab, and all database interactions pass Firebase Security Rules with a verified `auth.uid`.

---
*Report compiled and verified against codebase evidence on November 19, 2026.*
