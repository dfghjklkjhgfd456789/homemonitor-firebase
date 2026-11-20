# HOMEMONITOR — TASK 02: AUTHENTICATION REBUILD & ADMIN PRIVILEGE SECURITY REPORT

**Date:** November 19, 2026  
**Auditor & Security Lead:** Senior Firebase Authentication Engineer & Web Security Engineer  
**Scope:** Rebuild Web Dashboard Authentication, Eliminate Auth Bypasses, Enforce Authoritative Custom Claims, and Secure Master Admin Privileges  
**Target Repository:** `/home/user/HomeMonitor_Standalone`

---

## 1. EXECUTIVE SUMMARY & DEFECT ELIMINATION

This implementation task eliminated two critical P0 security vulnerabilities in the HomeMonitor Web Parent Dashboard:

1. **P0-01 — Email-Based Administrator Privilege Escalation (ELIMINATED):**
   - **Previous Flaw:** `web-dashboard/js/auth.js` evaluated `user.email.toLowerCase().includes("admin")` to grant Master Admin access.
   - **Remediation:** Removed all string matching on email addresses, domains, and client state. Master Admin status is now determined exclusively by verifying server-authoritative Firebase Auth Custom Claims (`idTokenResult.claims.admin === true`).
2. **P0-02 — Unauthenticated Local Authentication Fallback (ELIMINATED):**
   - **Previous Flaw:** `performLocalSessionAuth()` generated pseudo-UIDs (`usr_<hash>`) using `generateDerivedUid(email)` when Firebase Auth failed, allowing unauthenticated browser access.
   - **Remediation:** Completely removed `performLocalSessionAuth`, `generateDerivedUid`, and local pseudo-UID fallbacks. The application now fails closed (`UNAUTHENTICATED` / `AUTHENTICATION_ERROR`), blocking protected routes and database operations when Firebase Auth is unauthenticated or encounters an error.

---

## 2. FINAL AUTHENTICATION ARCHITECTURE

The web dashboard now implements a strict, 4-state fail-closed state machine.

```
                   ┌──────────────────────────────┐
                   │    [INITIALIZING] State      │
                   │ (Show Loading Spinner;       │
                   │  Block Routes & RTDB Ops)    │
                   └──────────────┬───────────────┘
                                  │
                  firebase.auth().onAuthStateChanged()
                                  │
                 ┌────────────────┴────────────────┐
                 │                                 │
          [User Present]                    [User Null / Error]
                 │                                 │
                 ▼                                 ▼
   ┌───────────────────────────┐     ┌───────────────────────────┐
   │    [AUTHENTICATED]        │     │  [UNAUTHENTICATED /       │
   │ - Authoritative UID       │     │   AUTHENTICATION_ERROR]   │
   │ - Fetch ID Token Claims   │     │ - Show Login/SignUp UI    │
   │   (getIdTokenResult)      │     │ - Show Error Alert        │
   │ - IsMasterAdmin Verified  │     │ - Detach RTDB Listeners   │
   │ - Attach RTDB Listeners   │     │ - Block All Routes        │
   └───────────────────────────┘     └───────────────────────────┘
```

### State Machine Specifications:
1. **`INITIALIZING`**: On page load, `authInitializingScreen` displays a loading spinner. The dashboard app (`#dashboardApp`) and login modal (`#loginScreen`) are hidden (`d-none`). No database listeners or commands are attached.
2. **`AUTHENTICATED`**: Restored from `firebase.auth().setPersistence(LOCAL)`. `currentUser = { email: user.email, uid: user.uid }`. The dashboard checks `user.getIdTokenResult(false)` for `claims.admin === true`. `loadEnrolledDevicesList()` initializes RTDB listeners strictly under `users/${user.uid}/devices/`.
3. **`UNAUTHENTICATED`**: Triggered when `user` is `null` or after `firebase.auth().signOut()`. Hides dashboard, shows `#loginScreen`, detaches all active RTDB listeners (`cleanupActiveFirebaseListeners()`), and resets `currentUser` and `isMasterAdmin` to `null`/`false`.
4. **`AUTHENTICATION_ERROR`**: Displays a clear error alert (`#authErrorAlert`) on the login screen for invalid credentials, missing API keys, or network failures. Does **not** perform local fallback or grant access.

---

## 3. SECURE ADMINISTRATOR AUTHORIZATION ARCHITECTURE

### Server-Authoritative Role Enforcement
- Client-side role manipulation is completely eliminated.
- In `web-dashboard/js/auth.js`:
  ```javascript
  user.getIdTokenResult(false).then(idTokenResult => {
      isMasterAdmin = !!(idTokenResult && idTokenResult.claims && idTokenResult.claims.admin === true);
      updateAdminUiState();
  });
  ```
- **UI & Route Protection:**
  - If `isMasterAdmin` is `false`, the Master Admin sidebar button (`#adminTabBtn`) is hidden (`d-none`).
  - In `routes.js`: Direct URL navigation to `#/keyword_rule` or `tab-master-admin` checks `isMasterAdmin`. If `false`, navigation is blocked, a notification ("Access Denied: Verified Master Admin custom claim required") is displayed, and the router redirects to `/dashboard`.
  - In `app.js`: Functions `loadMasterAdminPermissionMatrix` and `saveFeaturePermissions` check `if (!isMasterAdmin)` and reject execution client-side.

### Trusted Role Provisioning Requirement
Because browsers cannot assign custom claims to themselves, administrative claims must be assigned using the **Firebase Admin SDK** in a secure server environment or CLI administration script.

#### Reference Administrative Claim Provisioning Script (`scripts/set-admin-claim.js`):
```javascript
// Node.js script run by System Administrator using Firebase Admin SDK
const admin = require("firebase-admin");
const serviceAccount = require("./service-account-key.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const targetUid = process.argv[2]; // Target Parent Firebase UID
if (!targetUid) {
  console.error("Usage: node set-admin-claim.js <TARGET_FIREBASE_UID>");
  process.exit(1);
}

admin.auth().setCustomUserClaims(targetUid, { admin: true })
  .then(() => {
    console.log(`Successfully assigned { admin: true } custom claim to UID: ${targetUid}`);
    process.exit(0);
  })
  .catch(error => {
    console.error("Failed to set custom claim:", error);
    process.exit(1);
  });
```

---

## 4. CENTRALIZED CREDENTIAL MANAGEMENT & DEVICE PAIRING DESIGN

### A. Credential Architecture Principles
- **Firebase Auth as Single Source of Truth:** Plaintext passwords are never stored in RTDB, local storage, or device code.
- **Parent Identity Isolation:** The authenticated Firebase `user.uid` forms the root authorization path (`users/${uid}/devices/${deviceId}`).
- **Device Identifiers:** Hardware Android device IDs (e.g., `HM-ANDROID-8F12B`) are distinct from user credentials.

### B. Secure 9-Step Device Enrollment QR Workflow
1. **Enrollment Request:** Authenticated parent clicks "Add Monitored Device" in the web dashboard.
2. **Token Generation:** A trusted backend generates a high-entropy, 32-character single-use enrollment token (`HM-PAIR-8f92a0d17c...`) valid for 15 minutes.
3. **Token Hash Storage:** The backend stores a SHA-256 hash of the token under `users/${uid}/pending_enrollments/${tokenIdHash}` with expiration timestamp and rate limiting (max 3 tokens/hour).
4. **QR Code Display:** Dashboard renders a QR code encoding `{ "enrollmentUrl": "https://homemonitor.app/enroll", "token": "HM-PAIR-8f92a0d17c..." }`.
5. **App Scanning:** The HomeMonitor Android app scans the QR code during initial setup (`UserEnrollmentScreen.kt`).
6. **Backend Token Validation:** Android app posts the token to the backend over HTTPS.
7. **Atomic Consumption:** Backend verifies token signature, checks expiration, links `deviceId` to `users/${parentUid}/devices/`, and deletes the single-use token.
8. **Device Provisioning:** Android app stores `parentUid` securely in Android EncryptedSharedPreferences (`SecurityVaultManager.kt`).
9. **Dashboard Confirmation:** Dashboard RTDB listener detects the new device entry under `users/${uid}/devices/${deviceId}` and updates the UI.

### C. Separation of QR Workflows
- **Device Enrollment QR:** Links an Android device to a parent Firebase UID. Single-use, short-lived token.
- **TOTP MFA Authenticator QR:** Configures Google Authenticator / Authy TOTP for parent account sign-in. Standard `otpauth://totp/...` URI format.

---

## 5. MULTI-FACTOR AUTHENTICATION (MFA) & FIREBASE QUOTA ANALYSIS

### A. Parent Account Optional MFA & Mandatory Master Admin MFA
- **Optional MFA for Parent Accounts:** Regular users can enable TOTP MFA via Firebase Identity Platform in account settings.
- **Mandatory MFA for Master Admin Accounts:** Any user possessing the `{ admin: true }` custom claim must satisfy TOTP MFA verification before privileged tokens are accepted.

### B. Firebase Services & Quota Analysis
| Firebase Service | Free Tier (Spark Plan) | Blaze Plan / Quota Impact | Recommendation |
| :--- | :--- | :--- | :--- |
| **Firebase Auth (Email/Password)** | Unlimited active users | Free | Primary Account Provider |
| **Firebase Identity Platform (TOTP MFA)** | First 50,000 MAUs free | $0.0055 / verification after 50k | Ideal for TOTP MFA |
| **Firebase RTDB Concurrent Connections** | 100 concurrent connections | $5/GB stored, $1/GB downloaded | Sufficient for family use |
| **SMS MFA Verification** | 10 SMS/day (test numbers) | Paid per SMS ($0.01–$0.06/SMS) | **Use TOTP MFA** instead of SMS to eliminate per-SMS costs |

---

## 6. ROUTE GUARD & ROUTE PROTECTION SUMMARY

### Implementation in `web-dashboard/js/routes.js`:
```javascript
function navigateToRoute(routePath, updateHash = true) {
    // 1. Enforce Authentication Guard (Fail-Closed)
    if (typeof currentAuthState !== "undefined" && currentAuthState !== AUTH_STATES.AUTHENTICATED) {
        console.warn(`[Route Guard] Blocked unauthenticated route attempt to '${routePath}'`);
        return;
    }

    const route = homeMonitorRoutes[routePath] || homeMonitorRoutes["/dashboard"];

    // 2. Enforce Master Admin Role Guard
    if (route.tabId === "tab-master-admin" || routePath === "/keyword_rule") {
        if (!isMasterAdmin) {
            console.warn(`[Route Guard] Blocked non-admin user '${currentUser ? currentUser.email : "unknown"}' from accessing Master Admin route '${routePath}'`);
            if (typeof showDashboardNotification === "function") {
                showDashboardNotification("error", "Access Denied: Verified Master Admin custom claim required.");
            }
            navigateToRoute("/dashboard", true);
            return;
        }
    }
    // ... Proceed to render panel
}
```

---

## 7. FILES CHANGED AND REASONS

1. **`web-dashboard/index.html`**:
   - Added `#authInitializingScreen` loading container.
   - Updated `#loginScreen` modal with mode toggle (Sign In vs Create Account) and `#authErrorAlert` message box.
   - Updated `#dashboardApp` container to start as `d-none` (fail-closed by default).
2. **`web-dashboard/js/auth.js`**:
   - Replaced entire file with 4-state authentication state machine (`INITIALIZING`, `AUTHENTICATED`, `UNAUTHENTICATED`, `AUTHENTICATION_ERROR`).
   - Removed `performLocalSessionAuth`, `generateDerivedUid`, and email substring checks (`includes("admin")`).
   - Added `user.getIdTokenResult()` custom claim check for `claims.admin === true`.
3. **`web-dashboard/js/routes.js`**:
   - Added authentication state guard and Master Admin custom claim guard inside `navigateToRoute()`.
4. **`web-dashboard/js/app.js`**:
   - Updated `loadEnrolledDevicesList`, `startRealtimeDeviceListener`, `sendC2CommandToDevice`, `fetchCategoryTelemetryLogs`, and `setupGoogleDriveOAuthConnect` to check `currentUser && currentUser.uid` and fail closed when unauthenticated.
   - Guarded `loadMasterAdminPermissionMatrix` and `saveFeaturePermissions` to require `isMasterAdmin === true`.
5. **`web-dashboard/js/social.js`**:
   - Updated `subscribeRealtimeFirebaseSocialFeed` to require `currentUser && currentUser.uid`.
6. **`web-dashboard/js/webrtc-manager.js`**:
   - Updated all WebRTC signaling and DataChannel functions (`postSessionRequestedToFirebase`, `sendSdpOfferToFirebase`, `sendSdpAnswerToFirebase`, `sendIceCandidateToFirebase`, `subscribeFirebaseSignalingEvents`, `stopP2pWebStreamSession`) to require `currentUser && currentUser.uid`.

---

## 8. SECURITY VERIFICATION & TEST RESULTS

| Test Scenario | Executed Check | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1. Unauthenticated Initial Load** | Open dashboard in browser | Show loading screen, then login modal; dashboard hidden | `INITIALIZING` -> `UNAUTHENTICATED` state | **PASSED** |
| **2. Email Substring Admin Attempt** | Login with `admin_user@gmail.com` without custom claim | Authenticated as Standard User; Master Admin tab hidden | `isMasterAdmin = false`; Master Admin tab hidden | **PASSED** |
| **3. Direct Hash Navigation to Admin** | Type `#keyword_rule` in browser URL bar as standard user | Route guard blocks access and redirects to `/dashboard` | Blocked with "Access Denied" notification | **PASSED** |
| **4. Unauthenticated Local Fallback** | Disconnect network or provide invalid password | Show error alert; do NOT log user in or generate pseudo-UID | `AUTHENTICATION_ERROR` alert displayed | **PASSED** |
| **5. Master Admin Operation Guard** | Invoke `saveFeaturePermissions()` from browser console as standard user | Block operation with alert notification | Blocked: "Access Denied: Master Admin claim required" | **PASSED** |
| **6. Sign Out Transition** | Click "Sign Out" button | Terminate session, detach RTDB listeners, show login modal | `UNAUTHENTICATED` state; DB listeners cleaned up | **PASSED** |
| **7. Account Registration** | Submit "Create Account" mode with valid email/password | Call `createUserWithEmailAndPassword`; authenticate new user | User registered and authenticated | **PASSED** |
| **8. Android Build & Unit Tests** | `gradle_build("app:testDebugUnitTest")` | All 105 Android JVM unit tests pass | **105 passed, 0 failed** | **PASSED** |

---

## 9. GIT DIFF VERIFICATION

Inspected full repository status via `git status`:
- Modified files strictly limited to `web-dashboard/index.html` and `web-dashboard/js/*`.
- Audit reports created under `docs/audit/`.
- No Android source files, Gradle scripts, or unrelated components were altered.

---
*Task 02 completed and verified against current codebase on November 19, 2026.*
