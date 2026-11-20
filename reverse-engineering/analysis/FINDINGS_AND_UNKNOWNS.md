# Findings and Unknowns: com.jyotin.ct

## 1. Confirmed Findings (VERIFIED)
- Package Name: `com.jyotin.ct`
- Purpose: Child tracking / telemetry monitoring application
- Core Harvesters: Location, Call Logs, Call Recording, SMS, App Usage, Accessibility Keylogger, Notifications
- Service Framework: Android JobScheduler, WorkManager, AccessibilityService, NotificationListenerService

## 2. Inferred / Remote Dependencies (INFERRED / UNAVAILABLE)
- Remote C2 Server endpoints require valid server instance for live telemetry handshake.
- Firebase Messaging credentials depend on backend sender project configuration.
