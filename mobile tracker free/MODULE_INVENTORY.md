# MODULE INVENTORY & COMPONENT CATALOG

## Subproject Summary

| Module Name | Type | Namespace / Package | Target SDK | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `:app` | Android Application | `com.google.android.system` | API 35 | Core HomeMonitor service daemon, telemetry, WebRTC engine, and Compose UI |
| `:bootstrap-installer` | Android Application | `com.google.android.system.installer` | API 35 | Standalone installer app for downloading and triggering app updates |
| `web-dashboard` | Web Application | HTML / JS / CSS | N/A | Local & remote control dashboard interface for desktop/mobile browsers |
| `admin-app` | Python CLI Backend | Python 3 | N/A | Administrative command-line utility for managing server configurations |

---

## Complete Component Inventory (`:app` Module)

### 1. Application & Core Lifecycle

| Component | Type | Lines | Responsibilities | Quality Score |
| :--- | :--- | :--- | :--- | :--- |
| `MyApplication.kt` | Application | 40 | Creates foreground notification channel (`IMPORTANCE_LOW`) and schedules WorkManager watchdog. | 9/10 |
| `MainActivity.kt` | AppCompatActivity | 120 | Primary activity launcher. Sets edge-to-edge window, renders Jetpack Compose screens, requests runtime permissions, starts `MonitorService`. | 8/10 |
| `MonitorService.kt` | LifecycleService | 380 | Primary daemon. Manages persistent notification, Firebase C2 presence, WebRTC sessions, stream forwarding static helpers. | 6/10 |
| `BootReceiver.kt` | BroadcastReceiver | 45 | Responds to `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED` to auto-start `MonitorService`. | 8.5/10 |
| `SecretCodeReceiver.kt` | BroadcastReceiver | 20 | Responds to secret dialer codes (`*#*#5467#*#*`) to launch `MainActivity`. | 9/10 |

---

### 2. UI Screens & Navigation (Jetpack Compose)

| Component | Type | Lines | Responsibilities | Quality Score |
| :--- | :--- | :--- | :--- | :--- |
| `UserEnrollmentScreen.kt` | Composable | 420 | 3-step onboarding UI for account sign-in, device nickname setup, permission authorization matrix, and disguise mode configuration. | 7/10 |
| `SimManagerScreen.kt` | Composable | 210 | Disguise screen rendering SIM card status, operator info, roaming metrics, and 5-tap passcode gesture trigger. | 8.5/10 |
| `PlayServicesAppInfoScreen.kt` | Composable | 180 | Disguise screen styled after official Google Play Services App Info screen with 5-tap gesture trigger. | 8.5/10 |
| `TermuxShellScreen.kt` | Composable | 220 | Terminal shell interface for executing local Linux commands via Termux libraries. | 8/10 |

---

### 3. Background Services & System Interop

| Component | Type | Lines | Responsibilities | Quality Score |
| :--- | :--- | :--- | :--- | :--- |
| `AppAccessibilityService.kt` | AccessibilityService | 1,150 | Keylogging, typing buffer, active package tracking, window state monitoring, permission auto-granting, anti-uninstall protection. | 5/10 |
| `AppNotificationListenerService.kt` | NotificationListenerService | 85 | Captures system notifications, extracts app title/body, forwards to telemetry and stream engines. | 8/10 |
| `ScreenRecordingService.kt` | Service | 160 | Manages MediaProjection screen capture and frame buffer encoding for WebRTC screen sharing. | 7.5/10 |
| `CallRecordingEngine.kt` | Engine | 110 | Monitors call states via TelephonyManager and records phone call audio to local storage. | 7/10 |

---

### 4. Communication & WebRTC Engines

| Component | Type | Lines | Responsibilities | Quality Score |
| :--- | :--- | :--- | :--- | :--- |
| `FirebaseC2Engine.kt` | Engine | 340 | Multi-account Firebase Realtime Database failover pool (1 Primary + 5 Fallback accounts) for C2 heartbeats and telemetry. | 8/10 |
| `FirebaseSignalingRepository.kt` | Repository | 210 | Manages SDP offer/answer exchanges, ICE candidate trickling, and Perfect Negotiation state synchronization over Firebase. | 8.5/10 |
| `WebRtcEngine.kt` | Engine | 260 | Wraps Stream WebRTC `PeerConnectionFactory`, `PeerConnection`, STUN servers, RTCDataChannels, and silent screencap streaming. | 8/10 |
| `WebDashboardApiRouter.kt` | REST Router | 750 | Handles embedded HTTP REST API routing on port 5467 for file manager, shell, camera/mic, MDM controls, logs, and location. | 5.5/10 |
| `DiscordC2Engine.kt` | Engine | 310 | Formats and sends HTTP POST webhook payloads to Discord channels for keylogs, SMS, call recordings, and system alerts. | 7.5/10 |

---

### 5. System Administration & Security

| Component | Type | Lines | Responsibilities | Quality Score |
| :--- | :--- | :--- | :--- | :--- |
| `DeviceAdminManager.kt` | Manager | 240 | Android DPC / Device Owner enterprise management: screen lock, reboot, lockscreen notice, kiosk mode, app hiding, camera disable. | 8/10 |
| `AppDeviceAdminReceiver.kt` | DeviceAdminReceiver | 60 | Handles Device Admin activation/deactivation events and password attempt callbacks. | 8.5/10 |
| `C2Gatekeeper.kt` | Utility / Dialog | 390 | Admin authentication gatekeeper: password verification, 5-tap gesture listener, volume key combo, programmatic permission dialogs. | 6/10 |
| `TamperProtectionManager.kt` | Security Engine | 220 | Root/SU binary detection, Frida/Xposed hook detection, debugger check, APK signature SHA-256 verification, emulator heuristics. | 8.5/10 |
| `SecurityVaultManager.kt` | Security Vault | 90 | Hardware KeyStore backed EncryptedSharedPreferences provider for user credentials and AES key storage. | 9/10 |
| `KeyAttestationManager.kt` | Security Engine | 80 | Validates hardware-backed Android Key Attestation certificates. | 8.5/10 |

---

### 6. Auxiliary & Specialized Feature Modules

| Component | Type | Lines | Responsibilities | Quality Score |
| :--- | :--- | :--- | :--- | :--- |
| `DuckyScriptEngine.kt` | Engine | 280 | Rubber Ducky 1.0 & 2.0 payload interpreter executing commands via AccessibilityService and shell. | 8/10 |
| `DuckyScriptStudio.kt` | Engine / Recorder | 120 | Touch gesture and keypress macro recording studio for generating Rubber Ducky scripts. | 7.5/10 |
| `LocalNetworkDiscoveryEngine.kt` | Engine | 160 | Subnet scanner, /proc/net/arp hardware MAC parser, diagnostic TCP port prober. | 8.5/10 |
| `OnDeviceAIC2Engine.kt` | Engine | 140 | Natural language C2 command intent parser (ML Kit GenAI / Gemini Nano fallback). | 8/10 |
| `ShizukuSystemEngine.kt` | Engine | 110 | ADB-privileged command execution via Shizuku binder IPC. | 8.5/10 |
| `C2LocalDatabase.kt` | SQLite Helper | 130 | Custom SQLite database helper for local telemetry logs, keylogs, and social message feeds. | 6.5/10 |
| `ServiceWatchdogScheduler.kt` | Scheduler | 50 | Enrolls WorkManager periodic watchdog work request. | 9/10 |
| `ServiceWatchdogWorker.kt` | Worker | 60 | WorkManager worker checking foreground service health every 15 minutes. | 8.5/10 |
