# Security and Permission Audit: com.jyotin.ct

## 1. Requested Permissions Review
The app requests 34 permissions, including high-risk / dangerous permission groups:
- `READ_CONTACTS`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_SMS`, `RECEIVE_SMS`, `CAMERA`, `SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`, `BIND_NOTIFICATION_LISTENER_SERVICE`, `BIND_DEVICE_ADMIN`.

## 2. Security Findings
1. **Cleartext Network Traffic Allowed:** `android:usesCleartextTraffic="true"` is declared in `AndroidManifest.xml`.
2. **Sensitive Data Exposure:** Unencrypted local caching of call logs, SMS contents, and keystrokes in SharedPreferences / local files.
3. **Broad Receiver Intent Filters:** Receivers listen to `BOOT_COMPLETED`, `SMS_RECEIVED`, `CONNECTIVITY_CHANGE`.
