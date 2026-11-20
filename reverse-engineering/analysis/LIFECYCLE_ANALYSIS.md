# Lifecycle Analysis: com.jyotin.ct

## 1. Runtime Lifecycles
- **UI Lifecycle:** Standard Activity & Fragment lifecycles. Handled gracefully during screen orientation changes.
- **Service Lifecycles:**
  - `AccessService` runs persistently as long as Accessibility Service is enabled in system settings.
  - `NotificationListener` runs persistently once granted in Notification Access settings.
  - `CallRecordingService` is short-lived, starting on phone call offhook state and stopping on idle.
  - `DeviceSyncJob` / `DataSyncJob` / `WorkManager` execute periodic background tasks subject to Android Doze mode and JobScheduler rules.
