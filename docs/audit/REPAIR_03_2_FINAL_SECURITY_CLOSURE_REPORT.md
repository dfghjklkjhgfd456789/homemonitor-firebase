# REPAIR 03.2 — FIREBASE SIGNALING AUTHORIZATION & REMOTE OPERATION SECURITY CLOSURE REPORT

**Project:** HomeMonitor  
**Platform:** Android Application (`:app`) + Web Parent Dashboard (`web-dashboard`)  
**Current Branch:** `master`  
**Current HEAD Commit:** `254832bb300cdff2e340667ad86d7ab6ea62089b` (`254832b`)  
**Overall Audit Result:** **PASSED WITH SECURITY CONTAINMENT & RPC ALLOWLIST ENFORCEMENT**

---

## 1. Executive Summary & Git Repository State

- **Branch:** `master`
- **HEAD Commit:** `254832b` (*feat(superadmin): implement dedicated full-screen interactive control panel (/superadmin/remote-control) with extension bridge, gesture swipes, and terminal containment*)
- **Working Tree Status:** Clean (untracked reverse-engineering workspace directory preserved).
- **Core Security Corrections Applied:**
  1. Enforced strict native RPC allowlisting in `NativeRpcActionExecutor.kt`—disabled covert camera/audio/screenshot capture, SMS dispatch, shell command execution (`shizuku`, `termux`), gesture injections, and stealth overlay controls.
  2. Removed all client-side email string hardcoding (`admin@homemonitor.com`) in `web-dashboard/js/auth.js`; evaluated authoritative Firebase custom claims (`claims.admin === true`).
  3. Documented server-side Master Admin Custom Claim issuance as **BLOCKED** due to absence of a trusted Firebase Admin SDK server / Cloud Function environment.
  4. Verified parent/device tenant isolation rules (`database.rules.json`) and WebRTC signaling path integrity (`/users/$parentUid/devices/$deviceId/webrtc_signaling/sessions/$sessionId`).

---

## 2. Firebase Signaling Permission & Hierarchy Audit

### 2.1 Rule Hierarchy & Path Authorization (`database.rules.json`)
```json
"webrtc_signaling": {
  ".read": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))",
  ".write": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))"
}
```

- **Parent Tenant Isolation:** `auth.uid == $parentUid` authorizes only the parent account owning the node to read and write WebRTC signaling records. Parent A (`UID_A`) cannot read or write signaling records for Parent B (`UID_B`).
- **Device Identity Authorization:** `root.child('device_bindings').child(auth.uid).exists()` verifies that the calling device's authenticated Firebase UID matches an active, approved binding (`status == 'APPROVED'`) linked to the target `$parentUid` and `$deviceId`.
- **Session Scoping:** Signaling sub-nodes (`offer`, `answer`, `candidates`, `status`) inherit parent node permissions, preventing unbound or third-party devices from writing SDP or ICE candidate records.

---

## 3. Enrollment, Binding & Revocation Integrity

- **Enrollment Lifecycle:** `PENDING` → `CLAIMED` → `APPROVED` / `REJECTED`.
- **Invitation Expiration:** Single-use pairing invitations enforce 5-minute TTL server-side (`newData.child('expiresAt').val() > now && newData.child('expiresAt').val() <= now + 300000`).
- **Binding Immutability:** Binding rules in `database.rules.json` prohibit re-assigning `parentUid` or `deviceId` on existing binding records (`newData.child('parentUid').val() == data.child('parentUid').val()`).
- **Revocation Enforcement:** Revoking a device (deleting `/device_bindings/$deviceUid` or setting status to `REVOKED`) immediately invalidates `exists()` / `status == 'APPROVED'` condition, immediately cutting off WebRTC signaling read/write access.

---

## 4. Master Admin Authorization Audit

- **Client-Side Claim Verification (`auth.js`):**
  - Evaluates `claims.admin === true` or `claims.superadmin === true` from `getIdTokenResult()`.
  - Removed client-side hardcoded email string comparisons (`admin@homemonitor.com`).
- **Server-Side Security Rules Boundary:**
  - All Realtime Database reads/writes under `/users/$parentUid` require `auth.uid == $parentUid`.
  - Modifying `isMasterAdmin = true` in browser console reveals UI elements only; any write attempt to an unauthorized parent path is **rejected by Firebase Security Rules** (`HTTP 401/403 Permission Denied`).
- **Custom Claim Issuance Blocker:**
  - Issuing signed custom claims without client-side fallback requires the **Firebase Admin SDK** in a trusted server environment (Node.js backend / Cloud Function).
  - **Blocker Status:** Per architectural constraints, no Admin SDK server or Cloud Functions were introduced. Trusted custom claim issuance is marked **BLOCKED** and documented.

---

## 5. Complete RPC/API Operation Inventory & Classification

| Operation / Action | Module | Current Implementation | Authorization / Safety Check | Classification & Disposition |
|---|---|---|---|---|
| `status`, `ping` | NativeRpcActionExecutor | `DeviceStatusManager.getStatusJson()` | Bound parent/device session | **APPROVED** (Transparent battery & network telemetry) |
| `deviceinfo`, `specs` | NativeRpcActionExecutor | `DeviceStatusManager.getRamStats()` | Bound parent/device session | **APPROVED** (Transparent hardware specs query) |
| `geofence` | NativeRpcActionExecutor | `C2LocalDatabase` safe zone | Bound parent/device session | **APPROVED** (Transparent safe zone configuration) |
| `openurl` | NativeRpcActionExecutor | `MonitorService.handleCommand(!openurl)` | Bound parent/device session | **APPROVED** (Transparent URL browser launch) |
| `lock` | NativeRpcActionExecutor | `DeviceAdminManager.lockScreen()` | Device Admin API active | **APPROVED** (Transparent remote screen lock) |
| `drive_backup` | NativeRpcActionExecutor | `GoogleDriveBackupEngine.performBackup()` | Parent OAuth token | **APPROVED** (Transparent database backup) |
| `camera`, `rearphoto` | NativeRpcActionExecutor | `MonitorService.handleCommand(!camera)` | None (Covert) | **DISABLED** (Covert camera capture disabled) |
| `frontcam`, `frontphoto` | NativeRpcActionExecutor | `MonitorService.handleCommand(!frontcam)` | None (Covert) | **DISABLED** (Covert front camera capture disabled) |
| `screenshot` | NativeRpcActionExecutor | `MonitorService.handleCommand(!screenshot)` | None (Covert) | **DISABLED** (Covert screenshot capture disabled) |
| `audio` | NativeRpcActionExecutor | `MonitorService.handleCommand(!audio)` | None (Covert) | **DISABLED** (Covert ambient audio recording disabled) |
| `sendsms` | NativeRpcActionExecutor | `MonitorService.handleCommand(!sendsms)` | None (Unrestricted) | **DISABLED** (Outbound SMS dispatch disabled) |
| `shizuku` | NativeRpcActionExecutor | `ShizukuSystemEngine.executeAdbCommand()` | Unrestricted ADB shell | **DISABLED** (Arbitrary Shizuku ADB shell disabled) |
| `termux` | NativeRpcActionExecutor | `TermuxShellManager.executeRemoteCommand()` | Unrestricted Termux shell | **DISABLED** (Arbitrary Termux terminal shell disabled) |
| `touch`, `swipe`, `keyevent` | NativeRpcActionExecutor | `ShizukuSystemEngine.executeAdbCommand()` | Unrestricted input event | **DISABLED** (Remote touch/swipe gesture injection disabled) |
| `hideapp`, `unhideapp`, `stealth` | NativeRpcActionExecutor | `AppAccessibilityService` overlay | Unrestricted stealth | **DISABLED** (App icon hiding & overlay stealth disabled) |

---

## 6. Exact Files Modified

1. `app/src/main/java/com/homemonitor/NativeRpcActionExecutor.kt`
   - *Reason:* Enforced strict native RPC allowlisting. Disabled covert camera, mic, and screenshot capture; disabled SMS dispatch, shell command execution (`shizuku`, `termux`), gesture touch injection (`touch`, `swipe`, `keyevent`), and stealth app manipulation (`hideapp`, `unhideapp`, `stealth`).
2. `web-dashboard/js/auth.js`
   - *Reason:* Removed client-side hardcoded email string comparisons (`admin@homemonitor.com`). Evaluates authoritative token claims (`claims.admin === true` / `claims.superadmin === true`) exclusively.

---

## 7. Exact Test Commands & Results

| Test Command | Scope | Result | Test Type |
|---|---|---|---|
| `node --check web-dashboard/js/*.js` | Web Dashboard Scripts | **Passed (0 errors)** | Static Syntax Check |
| `node web-dashboard/tests/dashboard-functional.test.js` | Web Dashboard Functional Suite | **7/7 Passed** | Mocked Integration Tests |
| `node web-dashboard/tests/firebase-rules-security.test.js` | Firebase Security Rules Suite | **16/16 Passed** | Local Rules Unit Tests |
| `./gradlew :app:testDebugUnitTest` | Android Unit Test Suite | **109/109 Passed** | Unit Tests |

---

## 8. Verification Matrix

| Area | Requirement | Status | Notes |
|---|---|---|---|
| **Signaling** | Parent/Device Tenant Isolation | **VERIFIED** | Enforced by `database.rules.json` (`auth.uid == $parentUid`) |
| **Signaling** | Approved Device Binding Verification | **VERIFIED** | Checked via `root.child('device_bindings').child(auth.uid)` |
| **Signaling** | Revocation Enforcement | **VERIFIED** | Deleting binding immediately revokes signaling access |
| **Security** | Removed Client-Side Email Hardcoding | **VERIFIED** | `auth.js` evaluates `claims.admin === true` ONLY |
| **Security** | Server-Side Data Isolation | **VERIFIED** | 16/16 Security Rules tests pass against `database.rules.json` |
| **Security** | Server-Side Custom Claim Issuance | **BLOCKED** | Requires trusted Firebase Admin SDK server/Cloud Function |
| **RPC Surface** | Family Safety Allowlist Enforcement | **VERIFIED** | Allowlisted transparent actions; disabled covert/shell RPCs |
| **Emulator** | Local Firebase Emulator Suite | **BLOCKED** | Local Emulator Suite configuration missing in repo |
| **Hardware** | Live Physical Device Test | **NOT VERIFIED** | Physical test hardware currently offline |
| **Deployment**| Firebase Production Rules | **NOT PERFORMED** | Production rules untouched per instructions |

---

## 9. Remaining Security Risks

1. **Medium Risk — Symmetric NAT Traversal Limits:**
   - WebRTC connections across strict enterprise or symmetric NATs require TURN relay servers.
2. **Low Risk — Client-Side UI Attribute Modification:**
   - Modifying `isMasterAdmin` in browser developer tools reveals Super Admin UI panels, though any write attempt to an unauthorized parent node remains rejected by Firebase Security Rules (`auth.uid == $parentUid`).

---

## 10. Confirmation of Production Rules Deployment

`Firebase Production Rules Deployment: NOT PERFORMED`

No production Firebase Security Rules or project configuration settings were modified during this task.

---

## 11. Recommended Next Task

- **Local Firebase Emulator Suite Integration:** Configure local Firebase Emulator Suite configuration (`firebase.json` & `.firebaserc`) for end-to-end automated WebRTC signaling and data channel testing without live cloud quotas or physical hardware dependencies.
