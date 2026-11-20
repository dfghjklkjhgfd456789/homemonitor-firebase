# HOMEMONITOR — TASK 08B: FIREBASE RTDB MIGRATION REGISTER

**Date:** November 20, 2026  
**Auditor & Lead WebRTC Architecture Engineer:** Senior Systems & WebRTC Architect  
**Scope:** Register of Retired vs Retained Firebase RTDB Database Paths, Producers/Consumers, and Migration Verification Status  
**Target Repository:** `/home/user/HomeMonitor_Standalone`

---

## 1. FIREBASE RTDB PATH MIGRATION REGISTER

| RTDB Path Taxonomy | Previous Communication Role | New Communication Path | Target Status | Migration Verification |
| :--- | :--- | :--- | :--- | :--- |
| `/users/{uid}/devices/{deviceId}/webrtc_signaling/sessions` | WebRTC session creation & SDP exchange | WebRTC session creation & SDP exchange | **RETAINED (SIGNALING ONLY)** | `VERIFIED IN SOURCE` |
| `/users/{uid}/devices/{deviceId}/status` | 15s status heartbeat (`battery`, `online`) | WebRTC DataChannel RPC (`action="status"`) + Status Pulse | **RETAINED FOR LIGHTWEIGHT STATUS** | `VERIFIED IN SOURCE` |
| `/users/{uid}/devices/{deviceId}/feature_flags` | Master Admin permission matrix flags | WebRTC DataChannel RPC + Master Admin config | **RETAINED FOR ADMIN CONFIG** | `VERIFIED IN SOURCE` |
| `/users/{uid}/devices/{deviceId}/license` | License status & expiration key | WebRTC DataChannel RPC + Master Admin config | **RETAINED FOR ADMIN CONFIG** | `VERIFIED IN SOURCE` |
| `/users/{uid}/google_drive_credentials` | Parent Google Drive OAuth tokens | Zero-touch background push credentials | **RETAINED FOR DRIVE BACKUP** | `VERIFIED IN SOURCE` |
| `/users/{uid}/devices/{deviceId}/commands` | 3s command string polling | WebRTC DataChannel RPC (`RPC_REQUEST`) | **RETIRED FROM ACTIVE POLLING** | `VERIFIED IN SOURCE` |
| `/users/{uid}/devices/{deviceId}/actions` | 3s REST action string polling | WebRTC DataChannel RPC (`RPC_REQUEST`) | **ACTION FALLBACK ONLY** | `VERIFIED IN SOURCE` |
| `/users/{uid}/devices/{deviceId}/telemetry` | Firebase RTDB telemetry storage | Local SQLite (`c2_local_database.db`) + DataChannel | **REMOVED FROM ACTIVE RTDB WRITE** | `VERIFIED IN SOURCE` |

---

## 2. HISTORICAL DATA PRESERVATION STATUS

- **Local SQLite Database (`c2_local_database.db`):** 100% of historical telemetry logs, social chat history, call logs, contacts, and location history are preserved locally on the Android device via `C2LocalDatabase.kt`.
- **Google Drive Cloud Vault Backup (`GoogleDriveBackupEngine.kt`):** Zero-touch background push automatically backs up database snapshots to parent's Google Drive Cloud Vault (`HomeMonitor_Cloud_Vault/`).
- **Data Logs Download Button (`#downloadDataLogsBtn`):** Parent user can export complete telemetry logs and database snapshots directly to their computer via the Web Dashboard.

---

*Task 08B RTDB Migration Register completed and verified on November 20, 2026.*
