# REPAIR 03.3.1 — WEBRTC SIGNALING LEAST-PRIVILEGE SECURITY & EMULATOR NEGATIVE-TEST EXPANSION REPORT

**Project:** HomeMonitor  
**Platform:** Android Application (`:app`) + Web Parent Dashboard (`web-dashboard`)  
**Current Branch:** `master`  
**Current HEAD Commit:** `a005c68115b818d64ed1e965a063104f73b4cbdc` (`a005c68`)  
**Emulator Config Commit:** `a005c68` (*test(emulator): integrate local Firebase Realtime Database & Auth Emulator test harness*)  
**Overall Audit Result:** **PASSED — LEAST-PRIVILEGE RULES & EMULATOR NEGATIVE TESTS VERIFIED**

---

## 1. Git and Environment State

- **Current Branch:** `master`
- **Current HEAD:** `a005c68`
- **Working-Tree Status:** Clean (untracked reverse-engineering workspace directory preserved).
- **Environment Tooling:**
  - **Node.js:** `v24.22.1`
  - **npm:** `11.19.0`
  - **Java JDK:** `OpenJDK 25.0.2`
  - **Firebase CLI:** `v15.37.0` (invoked via `npx firebase-tools`)
  - **Rules Unit Testing Library:** `@firebase/rules-unit-testing@^3.0.4`

---

## 2. Complete Ancestor Rule Audit & Signaling Permission Weaknesses

### 2.1 Ancestor Rule Evaluation
A top-to-bottom audit of `database.rules.json` was conducted:
- `/users/$parentUid`: `.read` and `.write` require `auth.uid == $parentUid`.
- `/users/$parentUid/devices/$deviceId`: `.write` requires `auth.uid == $parentUid`.
- **Firebase Cascading Rule Invariant:** Because `.write` is granted to Parent at `$deviceId` level, Parent retains full descendant write permissions. However, Android devices have **NO ancestor write permission** at `$deviceId` level.

### 2.2 Original Weakness Identified
Previously, under `webrtc_signaling/sessions/$sessionId`, `.write` was defined at the `$sessionId` level for approved bound devices. Because Firebase rules cascade downwards, granting `.write` at `$sessionId` level permitted approved devices to overwrite the SDP offer (`/offer`) created by the parent dashboard.

### 2.3 Corrected Granular Least-Privilege Structure (`database.rules.json`)
Removed broad `.write` at `$sessionId` level and placed child-specific write rules:
```json
"webrtc_signaling": {
  ".read": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))",
  "sessions": {
    "$sessionId": {
      ".read": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))",
      ".indexOn": ["status", "timestamp"],
      "status": {
        ".write": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))"
      },
      "offer": {
        ".write": "auth != null && auth.uid == $parentUid"
      },
      "answer": {
        ".write": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))"
      },
      "candidates": {
        ".write": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))"
      }
    }
  }
}
```

- `/offer`: Restricted strictly to Parent (`auth.uid == $parentUid`). An approved device attempting to overwrite the SDP offer is **REJECTED**.
- `/answer` & `/candidates`: Allowed for Parent or Approved Bound Device.

---

## 3. Expanded Realtime Database Emulator Test Results

Executed via `npm run test:emulator` on the official **Firebase Realtime Database Emulator** (`firebase-database-emulator-v4.11.2.jar` on port `9000`) and **Auth Emulator** (port `9099`):

```text
==================================================================
RUNNING OFFICIAL FIREBASE REALTIME DATABASE EMULATOR RULES SUITE
==================================================================

=== 1. EMULATOR SEEDING (Privileged Admin Context) ===
  • Seeded Approved Device Binding in Database Emulator: OK

=== 2. PARENT SIGNALING TENANT ISOLATION TESTS ===
  ✓ [EMULATOR PASSED] Parent A writing signaling session under Parent A must be ALLOWED
  ✓ [EMULATOR PASSED] Parent B attempting to read Parent A's signaling session must be REJECTED
  ✓ [EMULATOR PASSED] Parent B attempting to modify Parent A's signaling session must be REJECTED

=== 3. BOUND DEVICE SIGNALING AUTHORIZATION & LEAST PRIVILEGE TESTS ===
  ✓ [EMULATOR PASSED] Approved Device writing answer SDP under Parent A must be ALLOWED
  ✓ [EMULATOR PASSED] Approved Device attempting to overwrite offer SDP must be REJECTED
  ✓ [EMULATOR PASSED] Approved Device attempting to write to commands path must be REJECTED
  ✓ [EMULATOR PASSED] Unbound Device attempting to write answer SDP under Parent A must be REJECTED

=== 4. UNAUTHENTICATED & REVOCATION ACCESS TESTS ===
  ✓ [EMULATOR PASSED] Unauthenticated client attempting to read signaling must be REJECTED
  ✓ [EMULATOR PASSED] Revoked Device writing ICE candidate after binding deletion must be REJECTED

==================================================================
REALTIME DATABASE EMULATOR TESTS COMPLETE: 9/9 PASSED
==================================================================
✔ Script exited successfully (code 0)
```

---

## 4. Master Admin Boundaries

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

## 5. Exact Files Changed

1. `database.rules.json`
   - *Reason:* Restructured `webrtc_signaling` rules to enforce least-privilege child writes (`offer` parent-only write vs `answer`/`candidates` device write).
2. `web-dashboard/tests/firebase-signaling-emulator.test.js`
   - *Reason:* Expanded negative authorization test suite to 9 tests evaluating offer overwrite rejection, commands path write rejection, Parent B read/modify rejection, and unauthenticated client rejection.

---

## 6. Complete Test Suite Execution Record

| Test Suite / Command | Execution Environment | Result | Evidence |
|---|---|---|---|
| `npm run check:syntax` | Node.js v24 | **Passed (0 errors)** | All 9 web JS files passed static syntax check |
| `npm run test:functional` | Node.js v24 | **7/7 Passed** | Primary parent routes and modals verified |
| `npm run test:rules` | Node.js v24 | **16/16 Passed** | Local Security Rules AST evaluator suite passed |
| `npm run test:emulator` | **Firebase Realtime Database Emulator** | **9/9 Passed** | Real Firebase Security Rules engine verified |
| `./gradlew :app:testDebugUnitTest` | Gradle / Java 25 | **109/109 Passed** | Android unit test suite passed |

---

## 7. Verification Matrix

| Area | Requirement | Status | Evidence |
|---|---|---|---|
| **Signaling** | Parent Tenant Isolation | **VERIFIED** | Parent B read and write attempts rejected on RTDB emulator |
| **Signaling** | Approved Device Binding Verification | **VERIFIED** | Approved device allowed; unbound device rejected on RTDB emulator |
| **Signaling** | Least-Privilege Offer Protection | **VERIFIED** | Approved device offer overwrite attempt rejected (`permission_denied`) |
| **Signaling** | Revocation Access Rejection | **VERIFIED** | Revoked device candidate write rejected on RTDB emulator |
| **Security** | Removed Client-Side Email Hardcoding | **VERIFIED** | `auth.js` evaluates `claims.admin === true` ONLY |
| **Security** | Server-Side Data Isolation | **VERIFIED** | 16/16 local AST tests & 9/9 RTDB emulator tests pass against `database.rules.json` |
| **Security** | Server-Side Custom Claim Issuance | **BLOCKED** | Requires trusted Firebase Admin SDK server/Cloud Function |
| **RPC Surface** | Family Safety Allowlist Enforcement | **VERIFIED** | Allowlisted transparent actions; disabled covert/shell RPCs |
| **Hardware** | Real Browser-to-Android P2P | **NOT VERIFIED** | Physical test hardware currently offline |
| **Deployment**| Firebase Production Rules | **NOT PERFORMED** | Production rules untouched per instructions |

---

## 8. Remaining Security & Connectivity Risks

1. **Medium Risk — Symmetric NAT P2P Traversal Limits:**
   - Without a TURN server fallback, WebRTC connections across strict enterprise or symmetric NATs require TURN relay servers.
2. **Low Risk — Client-Side UI Attribute Modification:**
   - Modifying `isMasterAdmin` in browser developer tools reveals Super Admin UI panels, though any write attempt to an unauthorized parent node remains rejected by Firebase Security Rules (`auth.uid == $parentUid`).

---

## 9. Deployment Confirmation

`Firebase Production Rules Deployment: NOT PERFORMED`

All tests were executed strictly on `127.0.0.1` against the local emulator using demo project `demo-homemonitor`. Production project `homemonitor-db` and production security rules were not modified or deployed.

---

## 10. Recommended Next Task

- **WebStream Precision Extension Automated E2E Test Suite:** Implement automated Jest/Puppeteer test scripts for the `webstream-precision` Chrome Extension (`webstream-precision/extension`) to verify sub-pixel coordinate transformation and gesture dispatch over WebRTC DataChannel.
