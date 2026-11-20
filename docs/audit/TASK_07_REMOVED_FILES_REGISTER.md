# HOMEMONITOR — TASK 07: REMOVED FILES & ROUTINES REGISTER

**Date:** November 20, 2026  
**Auditor & Lead Android Architect:** Senior Android & Security Architect  
**Scope:** Register of Removed Legacy Files, Obsolete Routines, Telegram Purge, Dependency Audits, and Deletion Reasons  
**Target Repository:** `/home/user/HomeMonitor_Standalone`

---

## 1. REMOVED ROUTINES & OBSOLETE CODE REGISTER

| Removed Routine / Component | Original Location / Responsibility | Dependency Check | Deletion Reason |
| :--- | :--- | :--- | :--- |
| `startCommandPollingLoop()` | Firebase RTDB `commands.json` 3s command polling | Replaced by WebRTC DataChannel | Disabling alternative command polling fallbacks per Section 2 direct-only P2P policy. |
| `startActionApiPollingLoop()`| Firebase RTDB `actions.json` 3s REST action polling | Replaced by WebRTC DataChannel | Disabling alternative REST action polling fallbacks per Section 2 direct-only P2P policy. |
| Legacy Telegram Bot Polling | HTTP polling of Telegram `getUpdates` API | Replaced by Firebase RTDB & WebRTC | Legacy C2 channel superseded by Firebase RTDB presence, WebRTC P2P DataChannel, and Discord Admin Portal. |
| Telegram `/` Command Parsers | `/bind_telegram_group`, `/setmycommands`, `/getupdates` | Audited and purged | All Telegram `/` commands and bot menu markups completely removed. |
| Cloudflare Worker Tunnel Code | Cloudflare token provisioning constants & loops | Replaced by WebRTC P2P | Removed per user instruction ("there is no need of cloudflare worker tunnel"). |
| Covert Clipboard Harvester | Background user clipboard harvesting | Audited for privacy compliance | Removed legacy covert surveillance harvesting routines in favor of transparent, parent-authorized APIs. |

---

## 2. PRESERVED COMPONENTS REGISTER

| Component | Responsibility | Retention Justification |
| :--- | :--- | :--- |
| `DeviceStatusManager.kt` | Device hardware specs, battery, RAM, storage stats DTOs | New modular manager component. |
| `LocationReportingManager.kt` | GPS location verification, `LocationTelemetryDto`, geofencing | New modular manager component. |
| `AppUsageReporter.kt` | Installed package querying and screen time summaries | New modular manager component. |
| `FamilySafetyEventManager.kt` | Low battery warnings, connectivity alerts, permission status | New modular manager component. |
| `DiscordC2Engine.kt` | Standalone Discord Bot Gateway & Slash Commands | Extracted as the dedicated hidden admin portal per user instruction. |
| `FirebaseC2Engine.kt` | 15s heartbeat pulse, feature flag sync, license status sync | Retained for presence, status sync, and telemetry log uploads. |
| `WebRtcEngine.kt` | STUN-only WebRTC PeerConnection & DataChannel engine | Retained for direct P2P interactive parent communications. |
| `FirebaseSignalingRepository.kt` | SDP offer/answer exchanges & candidate trickling | Retained for WebRTC P2P session signaling. |
| `WebDashboardApiRouter.kt` | REST API router processing DataChannel & Port 5467 requests | Retained for structured API response handling. |
| `WebStreamSessionManager.kt` | Cryptographic session store, CSRF validation, IP rate limiting | Retained for Port 5467 WebStream single-tunnel lifecycle. |

---

*Task 07 Removed Files Register completed and verified on November 20, 2026.*
