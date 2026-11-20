# Class and Method Map: com.jyotin.ct

## Key Package Mappings & Decompiled Signatures

### 1. UI Components (`com.jyotin.ct.activity`)
- `com.jyotin.ct.MainActivity`: Launcher activity, configures navigation drawer and initializes background services (`InitialProcess.startService`).
- `com.jyotin.ct.activity.Login`: `onCreate()`, `doLogin()`, `validateInputs()`, saves JWT/session tokens in `SharedPreferences`.
- `com.jyotin.ct.activity.Signup`: `onCreate()`, `registerAccount()`, posts user profile to registration backend.
- `com.jyotin.ct.activity.Permissions`: `onRequestPermissionsResult()`, requests all runtime permissions.
- `com.jyotin.ct.activity.AutostartScreen`: Checks build manufacturer (`Build.MANUFACTURER`) and launches corresponding OEM autostart activity intent.

### 2. Services (`com.jyotin.ct.services`)
- `com.jyotin.ct.services.AccessService`: `onAccessibilityEvent(AccessibilityEvent event)`, `onInterrupt()`, records typing and UI text changes.
- `com.jyotin.ct.services.NotificationListener`: `onNotificationPosted(StatusBarNotification sbn)`, `onNotificationRemoved()`, parses notification titles, text, app package name.
- `com.jyotin.ct.services.CallRecordingService`: `onStartCommand()`, initializes `MediaRecorder`, handles start/stop audio recording.
- `com.jyotin.ct.services.LocationTrackerService`: `onStartCommand()`, initializes LocationClient / FusedLocationProviderApi.
- `com.jyotin.ct.services.FirebaseMessages`: `onMessageReceived(RemoteMessage remoteMessage)`, handles push command triggers.

### 3. Receivers (`com.jyotin.ct.receivers`)
- `com.jyotin.ct.receivers.PhoneCallsBroadCastReciver`: `onReceive(Context context, Intent intent)`, tracks `TelephonyManager.EXTRA_STATE_RINGING`, `OFFHOOK`, `IDLE`.
- `com.jyotin.ct.receivers.SOSReceiver`: Intercepts `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`.
- `com.jyotin.ct.receivers.DeviceAdmin`: `onEnabled()`, `onDisabled()`, DeviceAdminReceiver implementation.
