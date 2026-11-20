# HOMEMONITOR — TASK 04: REMAINING ISSUES & HARDWARE DEPENDENCIES REPORT

**Date:** November 20, 2026  
**Auditor & Security Lead:** Senior Android & Security Architect  
**Scope:** Identification of Physical Hardware Dependencies, Network Topology Scenarios, and Deferred Production-Hardening Items  
**Target Repository:** `/home/user/HomeMonitor_Standalone`

---

## 1. OVERVIEW & SCOPE BOUNDARIES

Task 04 verified the software architecture, Firebase database synchronization, WebRTC P2P DataChannel signaling, WebStream single-tunnel session management, and Web Dashboard control interfaces.

Because automated integration testing was executed in a headless server environment without a physical Android device connected via ADB or cellular SIM card, specific physical radio and hardware camera interactions are documented below as unverified scenarios.

---

## 2. PHYSICAL HARDWARE & RADIO DEPENDENCIES

### A. Cellular SMS Transmission (`!sendsms`)
- **Verified Software Flow:** Web Dashboard dispatches `!sendsms +phone message` -> Firebase RTDB `commands.json` -> `FirebaseC2Engine` -> `MonitorService` invokes Android `SmsManager.sendTextMessage()`.
- **Unverified Physical Scenario:** Actual over-the-air cellular network transmission of the SMS text message by a physical baseband radio and SIM card (requires physical Android phone with active carrier SIM).

### B. Hardware Camera Lens Capture (`!camera` / `!frontcam`)
- **Verified Software Flow:** Web Dashboard dispatches `!camera` -> Firebase RTDB -> `FirebaseC2Engine` -> `InvisibleCameraActivity` launches CameraX API -> uploads photo reference to `telemetry/photos`.
- **Unverified Physical Scenario:** Physical CMOS image sensor capture under physical lighting conditions (requires physical Android device with hardware camera lens).

---

## 3. NETWORK TOPOLOGY DEPENDENCIES

### A. Strict Symmetric NAT WebRTC Direct P2P Traversal
- **Verified Protocol:** WebRTC DataChannel (`c2_datachannel`) establishes direct P2P connections using STUN servers (`stun.l.google.com:19302`, `stun.relay.metered.ca:80`) bound to Port `5467` (`WEBSTREAM_LOCAL_PORT`).
- **Network Boundary Handling:** In restrictive enterprise firewalls or double-symmetric NAT networks where direct UDP hole punching fails, the Web Dashboard UI displays `P2P Disconnected (Firebase Relay Active)` transparently. Per Task 04 mandatory constraints, TURN relay fallback is intentionally omitted to maintain direct P2P compliance.

---

## 4. DEFERRED PRODUCTION-HARDENING ITEMS

The following items are intentionally scheduled for the final release phase:

1. **R8 / ProGuard Code Obfuscation Tuning:**
   - *Current State:* Basic `proguard-rules.pro` rule set active.
   - *Status:* Deferred to final production release build configuration.
2. **Android Gradle Plugin (AGP) 9 Upgrade:**
   - *Current State:* AGP 8.x build configuration active and stable.
   - *Status:* Deferred to eliminate migration risks during core feature integration.
3. **Firebase Identity Platform Production MFA Activation:**
   - *Current State:* Dashboard UI implements TOTP MFA status display and zero-cost setup guidance (`otpauth://totp/...`).
   - *Status:* Production Firebase Identity Platform billing subscription deferred to avoid incurring costs without explicit approval.

---

## 5. SUMMARY OF SYSTEM HEALTH

| System Dimension | Status | Notes |
| :--- | :--- | :--- |
| **Android Compilation** | **VERIFIED** | Clean `assembleDebug` build |
| **JVM Unit Tests** | **VERIFIED** | 105 / 105 tests passing |
| **Dashboard Unit Tests** | **VERIFIED** | 5 / 5 tests passing |
| **Firebase Security Rules** | **VERIFIED** | UID-isolated authorization enforced |
| **WebRTC P2P & Port 5467** | **VERIFIED** | DataChannel RPC and STUN binding active |
| **WebStream Tunnel** | **VERIFIED** | Single-tunnel per installation enforced |
| **Physical Hardware Radio** | **DOCUMENTED** | Requires physical device for carrier test |

---
*Remaining Issues Report completed on November 20, 2026.*
