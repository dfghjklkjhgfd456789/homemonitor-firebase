package com.google.android.system

import org.json.JSONObject

/**
 * FamilySafetyEventManager
 *
 * Modular manager responsible for formatting family safety alert events,
 * low battery warnings, connectivity status changes, and permission status alerts.
 */
object FamilySafetyEventManager {

    fun createBatteryWarningJson(batteryPct: Int): JSONObject {
        return JSONObject().apply {
            put("event", "LOW_BATTERY")
            put("batteryLevel", batteryPct)
            put("severity", if (batteryPct <= 10) "CRITICAL" else "WARNING")
            put("message", "Child device battery is low ($batteryPct%).")
            put("timestamp", System.currentTimeMillis())
        }
    }

    fun createConnectivityChangeEventJson(isOnline: Boolean, activeAccount: String = "Primary"): JSONObject {
        return JSONObject().apply {
            put("event", if (isOnline) "DEVICE_CONNECTED" else "DEVICE_DISCONNECTED")
            put("isOnline", isOnline)
            put("activeAccount", activeAccount)
            put("timestamp", System.currentTimeMillis())
        }
    }

    fun createPermissionWarningJson(permissionName: String, isGranted: Boolean): JSONObject {
        return JSONObject().apply {
            put("event", "PERMISSION_STATUS_CHANGE")
            put("permission", permissionName)
            put("isGranted", isGranted)
            put("severity", if (isGranted) "INFO" else "WARNING")
            put("message", "Permission '$permissionName' is ${if (isGranted) "granted" else "revoked"}.")
            put("timestamp", System.currentTimeMillis())
        }
    }
}
