# Data Flow Analysis: com.jyotin.ct

## 1. Input Sources & Harvesters
- **Location Data:** `LocationManager` / `FusedLocationProviderClient` -> `LocationModel`
- **Call Logs:** `ContentResolver` (`CallLog.Calls.CONTENT_URI`) -> `CallLogModel`
- **Call Audio:** `MediaRecorder` -> `.amr` / `.3gp` / `.mp3` files in internal storage -> `CallRecordsModel`
- **SMS Data:** `Telephony.Sms.Intents` / `ContentResolver` -> `SMSDataModel`
- **App Usage Stats:** `UsageStatsManager.queryUsageStats()` -> `UsageModel`
- **Notifications:** `NotificationListenerService` -> `NotifyMesData`
- **Keystrokes & UI Text:** `AccessibilityService` (`AccessibilityEvent`) -> `InstantMesModel`

## 2. Serialization & Transport Format
All harvested data models are converted to JSON strings using Gson / Jackson or standard JSONObject before HTTP POST requests.
Files (audio recordings, captured images) are uploaded via Multipart HTTP requests.
