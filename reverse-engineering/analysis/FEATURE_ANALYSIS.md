# Feature Analysis: com.jyotin.ct

## 1. Major Features Summary

| Feature ID | Feature Name | Description | Key Component References |
|---|---|---|---|
| F-01 | User Auth & Provisioning | Login, Signup, Terms agreement, and Device ID pairing | `Login.java`, `Signup.java`, `Purpose.java` |
| F-02 | Vendor Autostart Permission | Launcher intent targeting for OEM autostart managers | `AutostartScreen.java` |
| F-03 | Location Tracking Engine | High-accuracy & background location polling | `LocationTrackerService.java`, `LocationUpdateWorker.java` |
| F-04 | Call & SMS Harvester | Intercepts SMS messages, call logs, phone state | `PhoneCallsBroadCastReciver.java`, `SOSReceiver.java` |
| F-05 | Audio Call Recording | Automated call recording of voice conversations | `CallRecordingService.java` |
| F-06 | Notification Interceptor | Captures notification text from messaging apps | `NotificationListener.java` |
| F-07 | Keylogger & Input Tracker | AccessibilityService tracking of user screen and typing | `AccessService.java` |
| F-08 | App Usage & Blocking | Harvests usage stats and enforces app blocking dialogs | `UsageDataWorker.java`, `AppBlockJob.java`, `BlockedDialog.java` |
| F-09 | FCM Remote Control | Remote command execution via FCM push messages | `FirebaseMessages.java` |
| F-10 | Telemetry Sync Engine | Periodic upload of gathered data to remote C2 | `DeviceSyncJob.java`, `DataSyncJob.java`, `DataPushWorker.java` |

## 2. Feature Traces & Implementation Details

### F-05: Call Recording Architecture
- **Trigger:** `PhoneCallsBroadCastReciver` receives `EXTRA_STATE_OFFHOOK` or `ACTION_PHONE_STATE`.
- **Execution:** Starts `CallRecordingService`, initializing `MediaRecorder` with `AudioSource.VOICE_CALL` or `AudioSource.MIC`.
- **Output:** Audio file stored in internal/cache storage, queued for `DataPushWorker` upload.

### F-07: Keylogger & Accessibility Monitoring
- **Trigger:** `AccessService` extends `AccessibilityService` handling `AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED`, `TYPE_VIEW_CLICKED`.
- **Execution:** Extracts node text, window title, and active package name into `InstantMesModel` database payload.
- **Output:** Saves to SharedPreferences / local DB and syncs periodically.
