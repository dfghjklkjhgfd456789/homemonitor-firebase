# Module Inventory: com.jyotin.ct

## 1. Core Modules & Packages

### `com.jyotin.ct.activity`
- `AutostartScreen`: Checks vendor-specific autostart permission intents (Xiaomi, Vivo, Oppo, Letv, Honor).
- `Login`: Handles user sign-in API requests and stores auth tokens in SharedPreferences.
- `Permissions`: Handles runtime permission requests for location, camera, phone, call log, SMS, storage, and audio.
- `Purpose`: Introductory activity displaying terms of usage and agreement screen.
- `Signup`: Handles user registration API requests.
- `Sync`: Activity managing initial data synchronization UI progress.

### `com.jyotin.ct.services`
- `AccessService`: Keylogger and UI inspector AccessibilityService implementation.
- `AppBlockJob`: JobService for evaluating app blocking rules.
- `AppMessage`: Messaging background worker thread.
- `BackServices`: Boot receiver helper background service.
- `CallRecordingService`: Handles audio recording of active telephone calls.
- `DataSync` / `DataSyncJob`: Scheduled background telemetry upload jobs.
- `DeviceSync` / `DeviceSyncJob`: Device info and status sync jobs.
- `FirebaseMessages`: Handles FCM push commands (e.g. remote wipe, record audio, track location, take photo).
- `FloatingViewService`: Overlay view service for UI blocking or alerts.
- `InitialCtSync` / `InitialProcess`: First-run setup background tasks.
- `LocationTrackerService`: FusedLocationProviderClient location polling daemon.
- `NotificationListener`: Intercepts system notifications from messaging apps (WhatsApp, Telegram, SMS, etc.).
- `Reconsole`: Console output redirection service.

### `com.jyotin.ct.receivers`
- `AppBlockReceiver`: Receiver for screen state and app blocking events.
- `AppNotify`: Receiver for app install/uninstall events.
- `ConnectionNotify`: Connectivity state change receiver.
- `DeviceAdmin`: BIND_DEVICE_ADMIN receiver to prevent uninstall and enable device management.
- `MyReceiver`: USER_PRESENT screen unlock receiver.
- `PhoneCallsBroadCastReciver`: Incoming/outgoing phone call listener.
- `SOSReceiver`: Emergency/SMS command broadcast receiver.

### `com.jyotin.ct.workers`
- `BatteryStatusWorker`: WorkManager worker for battery telemetry.
- `DataPushWorker`: Data upload execution worker.
- `DeviceInfo`: Gathers hardware, network, and OS details.
- `LocationUpdateWorker`: Location background worker.
- `UsageDataWorker`: UsageStatsManager app usage data harvester.

### `com.jyotin.ct.dbModels`
- POJO data transfer models: `APPModel`, `BrowserModel`, `CallLogModel`, `CallRecordsModel`, `ClipboardModel`, `ContactsModel`, `GeoFencesModel`, `GeofenceModel`, `InstantMesModel`, `LocationModel`, `NotifyCallModel`, `NotifyMesData`, `NotifyMessage`, `PicturesModel`, `SettingsModel`, `SettingsResponse`, `SMSDataModel`, `UsageModel`, `YTModel`.

### `com.jyotin.ct.utility`
- `BlockedDialog`: UI dialog shown when a blocked app is launched.
- `CommonMethods`: Encryption, hashing, format helper utilities.
- `MyDialog`: Generic dialog helper.
