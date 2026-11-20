# Architecture Analysis: com.jyotin.ct

## 1. System Architecture Pattern
The application uses a Service-Driven Background Daemon architecture combined with a standard Android Activity-Fragment UI frontend.

```
+-------------------------------------------------------------+
|                      UI Layer (Activities)                  |
|  Login / Signup -> Purpose -> Permissions -> Autostart -> UI|
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                   Service & Daemon Layer                    |
|  - AccessService (Accessibility Service for keylogging/UI)  |
|  - NotificationListener (Intercepts system notifications)  |
|  - DeviceSyncJob & DataSyncJob (Periodic Telemetry Sync)    |
|  - CallRecordingService & LocationTrackerService            |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                 Data & Networking Layer                     |
|  - SharedPreferences / Internal Storage                     |
|  - OkHttp / HTTP Legacy Client                              |
|  - Remote C2 / Sync Servers                                 |
+-------------------------------------------------------------+
```

## 2. Key Subsystems
1. **Authentication & Onboarding Subsystem:** Managed by `Login.java`, `Signup.java`, `Purpose.java`, `Permissions.java`, and `AutostartScreen.java`.
2. **Telemetry Data Harvester Subsystem:**
   - Call logs (`PhoneCallsBroadCastReciver.java`)
   - Location tracking (`LocationTrackerService.java`, `LocationUpdateWorker.java`)
   - Call Recording (`CallRecordingService.java`)
   - Notification monitoring (`NotificationListener.java`)
   - App Usage (`UsageDataWorker.java`)
   - Input/Screen events (`AccessService.java`)
3. **Data Sync Engine Subsystem:** Managed via `DeviceSyncJob.java`, `DataSyncJob.java`, `DeviceSync.java`, `DataSync.java`, and `DataPushWorker.java`.
