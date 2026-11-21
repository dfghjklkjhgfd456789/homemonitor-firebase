# HOMEMONITOR — REPAIR 01: ANDROID FIREBASE AUTHENTICATION & DEVICE IDENTITY REPORT

**Date:** 21 November 2026  
**Repository:** `/home/user/HomeMonitor_Standalone`  
**Authoritative Project:** `homemonitor-db`  
**Execution Status:** Completed  
**Final Status:** `IMPLEMENTED & VERIFIED`

---

## 1. Original Identity Problem
The initial repository assessment revealed a critical identity mismatch:
- **Web Dashboard:** Used authentic Firebase Authentication (`currentUser.uid`) at `/users/{parentAuthUid}/...`.
- **Android Application:** Derived a local pseudo-UID (`usr_<email_hash>`) using `Math.abs(email.hashCode())` or defaulted to `"default_user"`.
- **Unauthenticated REST Requests:** Android issued raw OkHttp REST requests without an `Authorization: Bearer <ID_TOKEN>` header.
- **Security Rule Denial:** Deployed RTDB rules (`auth != null && auth.uid == $uid`) rejected all Android signaling attempts with `401 Unauthorized`.

---

## 2. Files Inspected & Modified

### Inspected Files
- `app/src/main/java/com/homemonitor/UserEnrollmentScreen.kt`
- `app/src/main/java/com/homemonitor/FirebaseC2Engine.kt`
- `app/src/main/java/com/homemonitor/FirebaseSignalingRepository.kt`
- `app/src/main/java/com/google/android/system/SecurityVaultManager.kt`
- `web-dashboard/js/auth.js`
- `database.rules.json`

### Modified Files
- `app/src/main/java/com/homemonitor/UserEnrollmentScreen.kt` — Replaced pseudo email-hash generation with `FirebaseAuth.getInstance().signInWithEmailAndPassword()`.
- `app/src/main/java/com/homemonitor/FirebaseC2Engine.kt` — Added `AuthState` enum, `refreshUserIdentity()` via Firebase Auth SDK, `getAuthToken()`, and `buildAuthenticatedRequest()` with `Authorization: Bearer <ID_TOKEN>`. Removed pseudo-UID generation (`usr_<hash>`) and `"default_user"` fallback.
- `app/src/main/java/com/homemonitor/FirebaseSignalingRepository.kt` — Authenticated all WebRTC signaling REST operations with `buildAuthenticatedRequest()`.
- `app/src/test/java/com/homemonitor/FirebaseAuthIdentityTest.kt` — Added unit tests verifying `AuthState` enum transitions and absence of pseudo-UIDs.

---

## 3. Architecture & Implementation Summary

### 3.1 Authenticated Device & Parent Identity Model
1. **Enrollment Sign-In:** When parent authenticates on `UserEnrollmentScreen.kt`, `FirebaseAuth.getInstance().signInWithEmailAndPassword()` authenticates directly with Firebase Auth.
2. **Session Persistence:** Firebase Auth SDK maintains the active user session locally. `FirebaseAuth.getInstance().currentUser?.uid` returns the genuine Firebase Auth UID (`parentUid`).
3. **Identity Alignment:** Android and Web Dashboard now target the exact same RTDB paths:
   `/users/{parentAuthUid}/devices/{deviceId}/...`
4. **Security Rule Authorization:** Both Web Dashboard and enrolled Android child app satisfy `auth != null && auth.uid == $uid` under `database.rules.json`.

### 3.2 Token Handling & Request Authorization
- **`getAuthToken()`:** Asynchronously retrieves fresh Firebase Auth ID tokens via `currentUser.getIdToken(false)`.
- **`buildAuthenticatedRequest()`:** Attaches `Authorization: Bearer <ID_TOKEN>` header to all OkHttp REST requests issued by `FirebaseC2Engine` and `FirebaseSignalingRepository`.
- **No Plaintext Passwords:** Passwords are never written to disk or SharedPreferences.

### 3.3 Explicit AuthState Tracking
Added `AuthState` enum to track and expose authentication status:
- `INITIALIZING`: App startup before identity check.
- `AUTHENTICATED`: Active Firebase Auth session verified.
- `AUTHENTICATION_REQUIRED`: No Firebase Auth session present.
- `TOKEN_EXPIRED`: ID token expired or refresh failed.
- `AUTHORIZATION_DENIED`: HTTP 401/403 returned by Firebase security rules.
- `NETWORK_ERROR`: Connectivity failure during request.
- `AUTHENTICATION_ERROR`: Invalid credentials supplied during enrollment.

---

## 4. Verification & Test Results

### 4.1 Android Build & Unit Tests
- **Debug Assembly:** `./gradlew :app:assembleDebug` — `Build finished successfully.`
- **Unit Test Suite:** `./gradlew :app:testDebugUnitTest` — `107 passed, 0 skipped, 0 failed` (including new `FirebaseAuthIdentityTest`).

### 4.2 Web Dashboard Tests
- **JS Syntax Check:** `node --check web-dashboard/js/*.js` — `Passed (0 errors).`
- **Dashboard Suite:** `node web-dashboard/tests/dashboard-functional.test.js` — `7/7 passed.`

---

## 5. Remaining Blockers for Next Repair (Repair 02)

With Android identity and authenticated Firebase signaling access fully established, the remaining tasks for WebRTC connectivity are:
1. **Real One-Time Pairing Protocol (Repair 02):** Replace placeholder QR payload (`HM-PAIR-INITIALIZATION-TOKEN`) with a verified one-time QR enrollment token.
2. **WebRTC Offerer/Answerer DataChannel Fixes (Repair 03/04):** Ensure DataChannel is created by offerer only, wait for `setLocalDescription` callbacks, and clean up signaling listeners.

```
-------------------------------------------------------------------
FINAL VERDICT: REPAIR 01 COMPLETE (ANDROID FIREBASE IDENTITY FIXED)
- Next Task: Authorize Repair 02 (Genuine Verified Enrollment & QR Protocol)
-------------------------------------------------------------------
```
