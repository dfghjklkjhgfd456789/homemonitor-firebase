# PROJECT OVERVIEW: HOME MONITOR SYSTEM PLATFORM

## Executive Summary

**HomeMonitor** (`com.google.android.system`) is an advanced Android system service, remote device management, and bidirectional peer-to-peer telemetry platform designed for family safety monitoring, authorized asset tracking, and enterprise mobile device management (MDM). 

The platform combines on-device background telemetry collection (location, application usage, active window state, sensors, and intelligence logs) with low-latency WebRTC audio/video/screen streaming and a local HTTP REST API dashboard.

---

## Technical Specifications

| Parameter | Specification |
| :--- | :--- |
| **Application Identifier** | `com.google.android.system` |
| **Installer App Identifier** | `com.google.android.system.installer` |
| **Primary Language** | Kotlin (100% Kotlin source in app and installer modules) |
| **Min SDK Version** | API 26 (Android 8.0 Oreo) |
| **Target SDK Version** | API 35 (Android 15) |
| **Compile SDK Version** | API 35 |
| **Build System** | Gradle KTS with Version Catalogs (`libs.versions.toml`) |
| **UI Framework** | Jetpack Compose (Material 3) + ViewBinding |
| **Concurrency** | Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`) + StateFlow |
| **Real-Time P2P Streaming** | Stream WebRTC Android Engine (`io.getstream:stream-webrtc-android:1.3.10`) |
| **Signaling Infrastructure** | Firebase Realtime Database (6-account failover pool) |
| **Local Dashboard Server** | Embedded HTTP REST API Router on Port `5467` |
| **Security & Encryption** | Android KeyStore Hardware-Backed EncryptedSharedPreferences |
| **Privileged System Interop** | Shizuku Binder API (`dev.rikka.shizuku`), Android DPC / Device Owner |

---

## Project Structure & Repository Layout

```
HomeMonitor_Standalone/
├── app/                              # Main Android System & Monitor application module
│   ├── src/main/java/com/homemonitor/ # Core Kotlin source code & background services
│   ├── src/main/res/                 # Resources, XML configs, strings, drawables
│   └── build.gradle.kts              # Application module build script
├── bootstrap-installer/              # Standalone installer application module
│   ├── src/main/java/com/google/android/system/installer/
│   └── build.gradle.kts
├── admin-app/                        # Python administration backend tool
├── docs/                             # Technical documentation & WebRTC specs
├── web-dashboard/                    # Web-based control dashboard frontend
├── webstream-precision/              # Touch coordinate calibration & streaming tools
├── reverse-engineering/              # Decompiled reference packages & manifests
├── build.gradle.kts                  # Root Gradle build file
├── settings.gradle.kts               # Module inclusion & repository settings
└── DEVELOPMENT_MEMORY.md             # Developer architecture notes & contract specs
```

---

## Core Capabilities & Feature Matrix

### 1. WebRTC Bidirectional Real-Time Streaming Engine
- **W3C Perfect Negotiation**: Polite peer implementation for Web Dashboard, impolite peer implementation for Android application. Handles SDP collisions automatically.
- **Multi-Capability Session**: Supports concurrent `VIDEO`, `AUDIO`, `SCREEN_SHARE`, and `DATA_CHANNEL` streaming.
- **ICE Candidate Buffering**: Queues incoming ICE candidates prior to SDP remote description installation to prevent connection drops.

### 2. Multi-Account Failover Firebase Signaling
- Maintains a primary Firebase Realtime Database endpoint plus 5 fallback accounts.
- Automatic failover on connection timeout, quota exceeded, or HTTP errors.
- Syncs device status (`/status`), feature flags (`/feature_flags`), location (`/location`), and telemetry logs (`/telemetry`).

### 3. User Enrollment & Disguise Management
- **Transparent Enrollment**: Multi-step onboarding UI for account sign-in, device nickname assignment, and system permission authorization.
- **Disguise Interface**: Disguises interface as Google Play Services App Info or SIM Manager when disguise mode is activated by the user.
- **Authentication Gatekeeper**: Passcode-protected administrative controls accessible via custom 5-tap gesture or volume button key sequence.

### 4. Device Management & System Security
- **Device Owner / DPC Integration**: Remote screen locking, rebooting, lockscreen notice configuration, kiosk mode, and hardware feature restrictions.
- **Anti-Tamper & Security Vault**: Root/SU binary detection, Frida/Xposed dynamic instrumentation hook detection, debugger detection, and hardware keystore encryption.
- **Background Resiliency**: WorkManager watchdog worker (`ServiceWatchdogWorker`) and auto-restarting accessibility service for high reliability.

---

## Key Improvement Objectives

1. **Architectural Modularization**: Decompose oversized monolithic classes (`AppAccessibilityService`, `WebDashboardApiRouter`, `C2Gatekeeper`, `MonitorService`, `UserEnrollmentScreen`) into single-responsibility managers and repositories.
2. **Transparent Consent & User Privacy**: Introduce explicit user consent dialogs, persistent monitoring status indicators, and clear opt-in/opt-out toggles for ethical family safety and enterprise management.
3. **Data Management Modernization**: Replace legacy raw SQLite helpers with structured Room database repositories and reactive Kotlin StateFlow pipelines.
4. **Error Handling & Quality Assurance**: Eliminate silent exception swallowing, strengthen input validation, and expand unit test coverage across all core modules.
