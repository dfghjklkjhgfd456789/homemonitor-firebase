# HOMEMONITOR — REPAIR 00: COMPLETE FIREBASE PROJECT CONFIGURATION & CONNECTIVITY AUDIT

**Date:** 21 November 2026  
**Repository:** `/home/user/HomeMonitor_Standalone`  
**Audit Mode:** Read-Only Investigation  
**Status:** Completed  
**Final Verdict:** `FIREBASE MISCONFIGURED`

---

## 1. Executive Summary

A comprehensive read-only audit of the Firebase configuration across the Android application (`app/`), Web Dashboard (`web-dashboard/`), local database security rules (`database.rules.json`), and build definitions (`build.gradle.kts`, `app/build.gradle.kts`) was performed.

The audit confirms that while both the Android configuration artifact (`app/google-services.json`) and Web Dashboard configuration (`web-dashboard/js/firebase-config.js`) target the primary Firebase project **`homemonitor-db`**, the overall Firebase identity and signaling pipeline is **critically broken and misconfigured**. 

Primary failure factors:
1. **Unapplied Google Services Plugin & Missing Android SDK:** The `com.google.gms.google-services` plugin is not applied, `app/google-services.json` is unparsed, and Android lacks Firebase Authentication and Realtime Database SDK dependencies.
2. **Identity Mismatch:** Android creates a local pseudo-UID (`usr_<email_hash>`), whereas the Web Dashboard uses genuine Firebase Authentication (`currentUser.uid`).
3. **Unauthenticated Signaling Requests:** Android issues raw OkHttp REST requests without an `Authorization: Bearer <ID_TOKEN>` header or `auth` query parameter. Deployed RTDB rules (`auth != null && auth.uid == $uid`) reject all Android signaling attempts with `401 Unauthorized`.
4. **Placeholder Fallback Account Pool:** The 5 fallback accounts in both Android (`FirebaseC2Engine.kt`) and Web Dashboard (`firebase-config.js`) contain placeholder domain names and fake API keys. Automatic failover decouples Android and Web clients.
5. **Manifest Launch Blocker:** Manifest package default (`com.google.android.system`) resolves `.MainActivity` to a non-existent class (`com.google.android.system.MainActivity`), preventing activity launch.

---

## 2. Detailed Configuration Comparisons

### 2.1 Project ID, App ID, and Database URL Comparison

| Property | Android Artifact (`app/google-services.json`) | Web Dashboard (`web-dashboard/js/firebase-config.js`) | Alignment Status |
|---|---|---|---|
| **Project ID** | `homemonitor-db` | Implied by DB URL (Missing in Web `initializeApp` config object) | **MISCONFIGURED** (Web config lacks explicit `projectId`) |
| **Project Number** | `163747662526` | Not explicitly specified | **ALIGNED** |
| **Primary DB URL** | `https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app` | `https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app` | **VERIFIED MATCH** |
| **Storage Bucket** | `homemonitor-db.firebasestorage.app` | Not specified | **ALIGNED** |
| **API Key** | `AIzaSyByPqeoxKXhYxqy88pcep5FYzfTZEDhK8g` | `AIzaSyByPqeoxKXhYxqy88pcep5FYzfTZEDhK8g` | **VERIFIED MATCH** |
| **App ID / Client** | `1:163747662526:android:6a66ead2b3803586336cef` (`com.google.android.system`) | Web App ID not present in configuration object | **PARTIALLY VERIFIED** |

---

## 3. Findings by Audit Section

### Section 1: Android Firebase Configuration
* **`app/google-services.json`**: Valid JSON file present in `app/`. Configured for package name `com.google.android.system` and database URL `https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app`.
* **Build Configuration (`app/build.gradle.kts`)**:
  * The Google Services Gradle plugin (`com.google.gms.google-services`) is **not applied** in either root `build.gradle.kts` or `app/build.gradle.kts`.
  * No Firebase BoM or SDK dependencies (`com.google.firebase:firebase-auth-ktx`, `com.google.firebase:firebase-database-ktx`) are declared in `libs.versions.toml` or `app/build.gradle.kts`.
* **Namespace vs. Application ID vs. Code Package**:
  * `app/build.gradle.kts` defines `namespace = "com.google.android.system"` and `applicationId = "com.google.android.system"`.
  * `AndroidManifest.xml` specifies `android:name=".MainActivity"`, which resolves to `com.google.android.system.MainActivity`.
  * The actual source file `MainActivity.kt` declares `package com.homemonitor`. Consequently, the Android runtime cannot resolve `com.google.android.system.MainActivity`.

### Section 2: Web Dashboard Firebase Configuration
* **Primary Account**: Points to `https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app` with API key `AIzaSyByPqeoxKXhYxqy88pcep5FYzfTZEDhK8g`.
* **Missing Config Properties**: `firebase.initializeApp()` in `firebase-config.js` passes only `databaseURL` and `apiKey`. Standard Web v8/v9 SDK initializations require `projectId`, `authDomain`, and `storageBucket` for proper Firebase Auth token issuance and CORS domain matching.
* **Account Switcher Risk**: Web UI allows parent users to switch active Firebase account index in browser `localStorage`. If switched, the Web Dashboard points to a different database URL than the Android app, severing WebRTC signaling.

### Section 3: Firebase Authentication Configuration
* **Web Dashboard (`auth.js`)**: Uses official Web Firebase Auth (`firebase.auth()`). Signs in parents using `signInWithEmailAndPassword` or `createUserWithEmailAndPassword`, managing `currentUser.uid`.
* **Android Application (`UserEnrollmentScreen.kt`, `FirebaseC2Engine.kt`)**:
  * Does **not** invoke Firebase Auth SDK.
  * Generates a local pseudo-UID: `val generatedUid = "usr_" + Math.abs(normalizedEmail.hashCode()).toString()`.
  * Falls back to `"default_user"` if no UID is found.
  * Does not store or refresh Firebase ID Tokens.

### Section 4: Firebase Realtime Database Configuration & Security Rules
* **`database.rules.json` Inspection**:
  ```json
  {
    "rules": {
      "users": {
        "$uid": {
          ".read": "auth != null && auth.uid == $uid",
          ".write": "auth != null && auth.uid == $uid",
          "devices": { ... }
        }
      }
    }
  }
  ```
* **Security Rule Conflict**:
  1. The rules strictly require `auth != null && auth.uid == $uid` at `/users/$uid`.
  2. Because Android sends raw OkHttp requests **without an ID token**, Firebase RTDB returns HTTP 401 Unauthorized for all Android reads/writes.
  3. Web Dashboard reads/writes to `/users/{parentAuthUid}/...`, whereas Android reads/writes to `/users/usr_{emailHash}/...`. Even if rules were permissive, the two endpoints address different RTDB subtrees.

### Section 5: API & Application Configuration
* **Google Services Plugin**: Missing from build scripts. `app/google-services.json` is not processed into resource values (`values/values.xml`).
* **Firebase SDK Dependencies**: Missing from `libs.versions.toml` and `app/build.gradle.kts`.

### Section 6: Android Firebase Request Authentication
* **Network Execution (`FirebaseSignalingRepository.kt`)**:
  * `val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions.json"`
  * Requests are issued via raw OkHttp `Request.Builder().url(url).get().build()`.
  * **No** `Authorization: Bearer <ID_TOKEN>` header is attached.
  * **No** `?auth=<ID_TOKEN>` URL parameter is attached.
  * HTTP status codes are not checked; failed requests are silently ignored in polling loops.

### Section 7: Six-Account Fallback Pool Investigation
Both `FirebaseC2Engine.kt` (Android) and `firebase-config.js` (Web) contain a 6-account pool:
1. **Index 0 (Primary):** `https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app` (REAL)
2. **Index 1 (Fallback #1):** `https://homemonitor-fallback-1-rtdb.firebaseio.com` (PLACEHOLDER)
3. **Index 2 (Fallback #2):** `https://homemonitor-fallback-2-rtdb.firebaseio.com` (PLACEHOLDER)
4. **Index 3 (Fallback #3):** `https://homemonitor-fallback-3-rtdb.firebaseio.com` (PLACEHOLDER)
5. **Index 4 (Fallback #4):** `https://homemonitor-fallback-4-rtdb.firebaseio.com` (PLACEHOLDER)
6. **Index 5 (Fallback #5):** `https://homemonitor-fallback-5-rtdb.firebaseio.com` (PLACEHOLDER)

**Impact:** Fallbacks 1–5 are dummy URLs with placeholder API keys (`AIzaSyFallbackKeyOne12345`). If Android experiences an OkHttp 401/404 error on the primary database, `rotateToNextFallbackAccount()` rotates Android to a non-existent database, while the Web Dashboard remains on the primary database.

### Section 8: Live Connectivity Verification
* **Cloud Console / CLI Status:** `NOT VERIFIED` (No active cloud CLI credentials provided in execution environment).
* **Local Source Inspection:** `VERIFIED MISCONFIGURED`.

---

## 4. Root Cause Defect Register

| Finding | Classification | Evidence | Impact | Proposed Resolution |
|---|---|---|---|---|
| **`MainActivity` Package Mismatch** | SOURCE-CODE DEFECT | `AndroidManifest.xml` declares `.MainActivity` under `namespace = "com.google.android.system"`, but file is `package com.homemonitor`. | Application fails on launch with `ClassNotFoundException`. | Update `AndroidManifest.xml` activity declaration to `com.homemonitor.MainActivity`. |
| **Missing Firebase Android SDK & Plugin** | BUILD DEFECT | `app/build.gradle.kts` lacks Google Services plugin and Firebase dependencies. | `google-services.json` is ignored; Firebase Auth SDK unavailable on Android. | Add Google Services plugin & Firebase BoM/Auth/Database dependencies. |
| **Email Hash Pseudo-UID on Android** | ARCHITECTURE DEFECT | `UserEnrollmentScreen.kt` generates `usr_<email_hash>` instead of authenticating with Firebase Auth. | Web (`currentUser.uid`) and Android (`usr_<hash>`) target different RTDB paths. | Implement genuine Firebase Auth on Android; derive/store valid device-parent session identity. |
| **Unauthenticated REST Signaling Requests** | IMPLEMENTATION DEFECT | `FirebaseSignalingRepository.kt` sends OkHttp requests without ID token headers. | All Android RTDB requests rejected with 401 Unauthorized under `database.rules.json`. | Attach Firebase ID token to REST requests or migrate to official Firebase Android RTDB SDK. |
| **Placeholder Fallback Account Pool** | CONFIGURATION DEFECT | `firebase-config.js` & `FirebaseC2Engine.kt` contain 5 dummy fallback URLs and fake API keys. | Unsynced failover rotates Android to invalid databases, permanently severing signaling. | Remove placeholder fallback pool; enforce single canonical Firebase project (`homemonitor-db`). |
| **Incomplete Web Firebase Config Object** | CONFIGURATION DEFECT | `firebase-config.js` specifies only `databaseURL` and `apiKey`. | Web Auth lacks explicit `projectId` and `authDomain`. | Add full Firebase Web configuration parameters (`projectId`, `authDomain`, `storageBucket`). |

---

## 5. Required Source Modification Matrix (For Future Repairs)

To resolve all identified issues across Repair 01 and subsequent repair tasks, the following files will require modification:

1. `app/src/main/AndroidManifest.xml` — Correct `MainActivity` package path.
2. `build.gradle.kts` & `app/build.gradle.kts` — Apply Google Services plugin & Firebase dependencies.
3. `gradle/libs.versions.toml` — Declare Firebase BoM, Auth, and Realtime Database versions.
4. `app/src/main/java/com/homemonitor/UserEnrollmentScreen.kt` — Replace email hashing with Firebase Auth sign-in / pairing token.
5. `app/src/main/java/com/homemonitor/FirebaseC2Engine.kt` — Remove fallback pool rotation; align UID with authenticated identity.
6. `app/src/main/java/com/homemonitor/FirebaseSignalingRepository.kt` — Authenticate all REST / SDK signaling calls with Firebase ID Token.
7. `web-dashboard/js/firebase-config.js` — Supply complete `homemonitor-db` config; disable arbitrary project switching.
8. `database.rules.json` — Validate device signaling authorization rules for authenticated parents and enrolled child devices.

---

## 6. Audit Verdict

```
-------------------------------------------------------------------
FINAL VERDICT: FIREBASE MISCONFIGURED
-------------------------------------------------------------------
- Cloud Console Access: NOT VERIFIED (Local audit complete)
- Android-to-Web Signaling Readiness: BROKEN
- Immediate Next Action: Proceed to Repair 01 (Package Mismatch Fix,
  Firebase SDK Integration, and Android Identity Correction).
-------------------------------------------------------------------
```
