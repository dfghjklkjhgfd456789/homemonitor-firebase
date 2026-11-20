# Application Structure: com.jyotin.ct

## 1. Directory & Package Layout
- `com.jyotin.ct`
  - `MainActivity.java` - Main entry point activity hosting navigation fragments
  - `FirstFragment.java` - Welcome / Status Fragment
  - `SecondFragment.java` - Setup / Info Fragment
  - `activity/`
    - `AutostartScreen.java` - Vendor autostart permission setup (e.g. Vivo, Xiaomi)
    - `Login.java` - Account authentication screen
    - `Permissions.java` - Runtime permission requests flow
    - `Purpose.java` - Terms and application usage purpose display
    - `Signup.java` - New user account creation
    - `Sync.java` - Manual/initial sync status screen
  - `dbModels/` - POJO data models for JSON payload serialization (CallLogModel, LocationModel, SMSDataModel, SettingsModel, APPModel, etc.)
  - `receivers/` - BroadcastReceivers for Boot, Connectivity, Call States, SMS, and App installation events
  - `services/` - Background Services, Job Services, Accessibility Service, and Notification Listener
  - `utility/` - Dialog helpers, common utility methods
  - `workers/` - androidx.work WorkManager Workers (LocationUpdateWorker, UsageDataWorker, DeviceInfo, BatteryStatusWorker, DataPushWorker)

## 2. Component Inventory
- **Activities:** 8
- **Services:** 13
- **Broadcast Receivers:** 7
- **Content Providers:** 2 (WorkManagerInitializer, FirebaseInitProvider)
