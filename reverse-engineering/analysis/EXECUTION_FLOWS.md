# Execution Flows: com.jyotin.ct

## 1. Boot Sequence & Service Initialization Flow

```
[System Boot Completed / QUICKBOOT_POWERON]
                |
                v
  PhoneCallsBroadCastReciver / SOSReceiver / BackServices
                |
                v
       InitialProcess Service
                |
                v
  +---------------------------------------+
  |  - Starts DeviceSyncJob / DataSyncJob |
  |  - Schedules WorkManager Workers      |
  |  - Starts LocationTrackerService       |
  |  - Verifies AccessibilityService      |
  +---------------------------------------+
```

## 2. Telemetry Harvester Flow

```
[User Action / Event (Call / SMS / Typing / App Launch)]
                |
                v
 [BroadcastReceiver / NotificationListener / AccessService]
                |
                v
      [Data Extraction & Formatting]
                |
                v
 [Local Storage / SharedPreferences / JSON Model Creation]
                |
                v
     [DeviceSyncJob / DataPushWorker Upload]
                |
                v
     [Remote C2 Telemetry Server HTTP POST]
```
