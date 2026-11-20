# Application Overview: Child Tracker (com.jyotin.ct)

## 1. Metadata Summary
- **Target File:** `App134491.apk` (`original.apk`)
- **Package Name:** `com.jyotin.ct`
- **Application Label:** `@string/app_name` (Child Tracker / CT)
- **Version Name:** 1.1
- **Version Code:** 2
- **Min SDK:** 21 (Android 5.0)
- **Target SDK:** 30 (Android 11)
- **Compile SDK:** 30
- **Primary Technology Stack:** Java / Android SDK / Firebase Cloud Messaging / WorkManager / EasyPermissions
- **Architecture Model:** MVC / Service-driven background daemon pattern

## 2. Executive Summary
`com.jyotin.ct` (Child Tracker) is an Android application designed for child monitoring and device tracking. It features comprehensive background telemetry synchronization including location tracking, call logging, call recording, notification listening, application usage stats monitoring, browser history monitoring, and accessibility service interception.

The application communicates with a remote HTTP/S backend for authentication, device configuration retrieval, and telemetry data upload. Data is stored locally in device storage / SQLite / SharedPreferences prior to cloud sync.
