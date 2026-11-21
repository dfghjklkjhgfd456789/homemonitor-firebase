# HOMEMONITOR — REPAIR 02.1: FIREBASE ENROLLMENT SECURITY RULES HARDENING REPORT

**Date:** 21 November 2026  
**Repository:** `/home/user/HomeMonitor_Standalone`  
**Authoritative Project:** `homemonitor-db`  
**Execution Status:** Completed  
**Final Status:** `IMPLEMENTED & SECURITY VERIFIED`

---

## 1. Vulnerabilities Addressed & Remediated

| Vulnerability | Initial Risk | Hardened Security Rule Enforcement |
|---|---|---|
| **A. Device Self-Binding Authorization** | `HIGH`: `!data.exists()` permitted anonymous devices to write `/device_bindings/$deviceUid` for any `parentUid` | **REMEDIATED**: Anonymous devices are strictly forbidden from writing `/device_bindings/$deviceUid`. Only the parent (`auth.uid == newData.child('parentUid').val()`) can create or modify bindings. |
| **B. Unrestricted Invitation Listing** | `MEDIUM`: `.read: auth != null` allowed any user to list all invitation records | **REMEDIATED**: `.read` under `/enrollment_invitations/$invitationId` requires `data.child('parentUid').val() == auth.uid` or `data.child('claim/deviceUid').val() == auth.uid`. |
| **C. Client-Side Clock Expiration Dependency** | `HIGH`: Expiration checked solely on client device | **REMEDIATED**: Server-side RTDB rule enforces `expiresAt > now` on creation and claim using Firebase's server `now` variable. |
| **D. Re-claimable / Non-Atomic Claims** | `MEDIUM`: `PENDING` status check alone did not prevent repeated claim overwrites | **REMEDIATED**: Enforced atomic transition (`PENDING` -> `CLAIMED`). Subsequent claims on a `CLAIMED` invitation are rejected server-side. |
| **E. Unlinked Pending Device Requests** | `MEDIUM`: Device could write pending request to any parent | **REMEDIATED**: Pending request requires device identity matching (`auth.uid == $deviceUid`) and `PENDING` status. Self-approval is rejected. |
| **F. Revocation Bypass** | `HIGH`: Revoked devices could recreate bindings | **REMEDIATED**: Deleting `/device_bindings/$deviceUid` removes read/write grants under `/users/$parentUid/devices/$deviceId` instantly. Devices cannot recreate bindings. |
| **G. Third-Party QR Payload Transmission** | `PRIVACY`: Tokens sent to `api.qrserver.com` | **REMEDIATED**: Replaced with local client-side SVG rendering using `crypto.getRandomValues()` (128 bits entropy). |

---

## 2. Source Files Inspected & Modified

1. `database.rules.json` — Hardened RTDB security rules enforcing invitation claim atomicity, server expiration, parent-only binding writes, and device isolation.
2. `web-dashboard/index.html` — Updated `qrEnrollmentModal` to render local SVG QR container.
3. `web-dashboard/js/auth.js` — Increased invitation token randomness to 128 bits (`crypto.getRandomValues(16)`) and implemented local client-side QR SVG rendering.
4. `web-dashboard/js/app.js` — Updated `openAddDeviceModalBtn` to render local QR code and attach real-time pending device listener.
5. `web-dashboard/tests/firebase-rules-security.test.js` — Added automated security rules test suite covering 14 negative and positive rule scenarios.

---

## 3. Final Database Schema & Authorization Boundaries

```
homemonitor-db (RTDB Root)
├── enrollment_invitations
│   └── {invitationId}
│       ├── parentUid: "parent_user_uid"
│       ├── status: "PENDING" | "CLAIMED"
│       ├── createdAt: <ServerValue.TIMESTAMP>
│       ├── expiresAt: <now + 300000> (5 mins)
│       └── claim: { deviceUid, deviceName, model, claimedAt }
│
├── device_bindings
│   └── {deviceUid}
│       ├── parentUid: "parent_user_uid"
│       ├── deviceId: "DEV_001"
│       └── status: "APPROVED"
│
└── users
    └── {parentUid}
        ├── pending_devices
        │   └── {deviceUid} = { deviceUid, deviceName, status: "PENDING" | "APPROVED" | "REJECTED" }
        └── devices
            └── {deviceId} = { deviceId, deviceUid, nickname, status: "APPROVED", online: true }
```

### Authorization Boundaries
- **Parent (`parentUid`):** Full read/write over `/users/$parentUid`, `/enrollment_invitations` created by `$parentUid`, and `/device_bindings` where `$parentUid == auth.uid`.
- **Anonymous Device (`deviceUid`):**
  - Read/write ONLY its own invitation claim under `/enrollment_invitations/$invitationId` (if `PENDING` and unexpired).
  - Read/write ONLY its assigned `$deviceId` under `/users/$parentUid/devices/$deviceId` IF AND ONLY IF valid binding `/device_bindings/$deviceUid` exists with `status == "APPROVED"`.
  - CANNOT write to `/device_bindings`, CANNOT approve itself, CANNOT access sibling devices or other parents.

---

## 4. Complete Hardened `database.rules.json`

```json
{
  "rules": {
    "enrollment_invitations": {
      "$invitationId": {
        ".read": "auth != null && (data.child('parentUid').val() == auth.uid || data.child('claim/deviceUid').val() == auth.uid)",
        ".write": "auth != null && ((!data.exists() && newData.child('parentUid').val() == auth.uid && newData.child('status').val() == 'PENDING' && newData.child('expiresAt').val() > now && newData.child('expiresAt').val() <= now + 300000) || (data.exists() && data.child('parentUid').val() == auth.uid) || (data.exists() && data.child('status').val() == 'PENDING' && data.child('expiresAt').val() > now && newData.child('status').val() == 'CLAIMED' && newData.child('claim/deviceUid').val() == auth.uid && newData.child('parentUid').val() == data.child('parentUid').val()))"
      }
    },
    "device_bindings": {
      "$deviceUid": {
        ".read": "auth != null && (auth.uid == $deviceUid || (data.exists() && data.child('parentUid').val() == auth.uid))",
        ".write": "auth != null && ((!data.exists() && newData.child('parentUid').val() == auth.uid && newData.child('status').val() == 'APPROVED') || (data.exists() && data.child('parentUid').val() == auth.uid))"
      }
    },
    "users": {
      "$parentUid": {
        ".read": "auth != null && auth.uid == $parentUid",
        ".write": "auth != null && auth.uid == $parentUid",
        "pending_devices": {
          "$deviceUid": {
            ".read": "auth != null && (auth.uid == $parentUid || auth.uid == $deviceUid)",
            ".write": "auth != null && ((auth.uid == $parentUid) || (auth.uid == $deviceUid && !data.exists() && newData.child('deviceUid').val() == auth.uid && newData.child('status').val() == 'PENDING'))"
          }
        },
        "devices": {
          "$deviceId": {
            ".read": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))",
            ".write": "auth != null && (auth.uid == $parentUid || (root.child('device_bindings').child(auth.uid).exists() && root.child('device_bindings').child(auth.uid).child('parentUid').val() == $parentUid && root.child('device_bindings').child(auth.uid).child('deviceId').val() == $deviceId && root.child('device_bindings').child(auth.uid).child('status').val() == 'APPROVED'))",
            ".indexOn": ["lastSeen", "online", "timestamp"],
            "status": {
              ".validate": "newData.hasChildren(['deviceId'])"
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

---

## 5. Security Rules Verification & Test Results

Executed automated security test suite `web-dashboard/tests/firebase-rules-security.test.js`:

```
==================================================================
RUNNING HOMEMONITOR FIREBASE SECURITY RULES VERIFICATION SUITE
==================================================================

=== 1. VULNERABILITY A: DEVICE SELF-AUTHORIZATION TESTS ===
  ✓ [PASSED] Anonymous device self-binding creation attempt must be REJECTED
  ✓ [PASSED] Parent A binding creation for Device 123 must be ALLOWED
  ✓ [PASSED] Anonymous device updating existing binding must be REJECTED

=== 2. VULNERABILITY B: INVITATION READ/WRITE PROTECTION TESTS ===
  ✓ [PASSED] Anonymous user listing all invitations must be REJECTED
  ✓ [PASSED] Parent reading own invitation must be ALLOWED
  ✓ [PASSED] Unrelated user reading Parent A's invitation must be REJECTED

=== 3. VULNERABILITY C & D: EXPIRATION & SINGLE-USE CLAIMING TESTS ===
  ✓ [PASSED] Device claiming valid unexpired invitation must be ALLOWED
  ✓ [PASSED] Device claiming expired invitation must be REJECTED
  ✓ [PASSED] Second device claiming an already-claimed invitation must be REJECTED

=== 4. VULNERABILITY E: PENDING DEVICE & SELF-APPROVAL PROTECTION ===
  ✓ [PASSED] Device creating pending request with PENDING status must be ALLOWED
  ✓ [PASSED] Device self-approving request to APPROVED status must be REJECTED

=== 5. VULNERABILITY F: DEVICE ACCESS & REVOCATION TESTS ===
  ✓ [PASSED] Approved Device 123 writing to DEV_001 under Parent A must be ALLOWED
  ✓ [PASSED] Approved Device 123 writing to sibling DEV_002 must be REJECTED
  ✓ [PASSED] Revoked Device writing to DEV_001 after binding removal must be REJECTED

==================================================================
SECURITY RULES VERIFICATION COMPLETE: 14/14 TESTS PASSED
==================================================================
```

### 5.1 Android & Web Dashboard Regression Verification
- **Android Assembly Build:** `./gradlew :app:assembleDebug` — `SUCCESS`
- **Android Unit Tests:** `./gradlew :app:testDebugUnitTest` — `109 passed, 0 skipped, 0 failed`
- **Web Dashboard Suite & Syntax:** `node web-dashboard/tests/dashboard-functional.test.js` & `node --check web-dashboard/js/*.js` — `7/7 passed, 0 syntax errors`

---

## 6. Status Classification

| Requirement / Boundary | Classification | Notes |
|---|---|---|
| Parent-Only Binding Creation | `IMPLEMENTED & UNIT-TESTED` | Anonymous device self-binding attempts rejected by security rules |
| Server-Side Expiration Enforcement | `IMPLEMENTED & UNIT-TESTED` | Server-side `now` variable checks `expiresAt > now` |
| Atomic Single-Use Invitation Claims | `IMPLEMENTED & UNIT-TESTED` | Re-claims on `CLAIMED` status rejected |
| Device Isolation & Revocation | `IMPLEMENTED & UNIT-TESTED` | Revoked devices lose read/write grants immediately |
| Local QR SVG Generation | `IMPLEMENTED` | Zero third-party network transmissions (`crypto.getRandomValues(16)`) |
| Security Rule Test Suite | `UNIT-TESTED` | 14/14 negative and positive rule assertion tests passed |
| Android & Web Regression | `BUILD-VERIFIED` | 109/109 Android unit tests passed, 7/7 Web tests passed |
| Live Firebase Rules Deployment | `NOT VERIFIED` | Owner must copy `database.rules.json` to Firebase Console |

```
-------------------------------------------------------------------
FINAL VERDICT: REPAIR 02.1 COMPLETE (SECURITY HARDENED & VERIFIED)
- Next Task: Ready for Repair 03/04 (WebRTC PeerConnection & DataChannel Handshake Repair)
-------------------------------------------------------------------
```
