# DEVELOPMENT MEMORY — PARENT-CHILD ANDROID ARCHITECTURE

## Project Context
- **Project**: HomeMonitor Standalone (`:app` Child App & `:parent-app` Parent App)
- **Root Directory**: `/home/user/HomeMonitor_Standalone`
- **Architectural Policy**: **Child App (`:app`) and Parent App (`:parent-app`) Native Android Ecosystem**. Direct P2P WebRTC DataChannel & Firebase RTDB signaling between Parent Android Application and Child Monitored Device.

---

## Technical Architectural Decisions & System Specs

### 1. Active Subprojects
- **`:app`**: Monitored Child Device Application (`com.google.android.system` / `com.homemonitor`).
- **`:parent-app`**: Parent Controller Application (`com.homemonitor.parent`).

### 2. Standalone Status
- Web Dashboard (`web-dashboard/`), Admin Server (`admin-app/`), Webstream Precision extension (`webstream-precision/`), and Bootstrap Installer (`bootstrap-installer/`) have been removed.
- System operates exclusively using native Child and Parent Android applications.

---

## Code Base Inventory & File References

- **Child Monitored Client** (`app/src/main/java/com/homemonitor/`):
  - Core Files: `WebRtcEngine.kt`, `FirebaseC2Engine.kt`, `MonitorService.kt`, `DeviceApiDispatcher.kt`.
- **Parent Application** (`parent-app/src/main/java/com/homemonitor/parent/`):
  - Core Files: `MainActivity.kt`, `HomeMonitorApiClient.kt`, `DeviceRepository.kt`, `P2pRepository.kt`, `AuthRepository.kt`.
