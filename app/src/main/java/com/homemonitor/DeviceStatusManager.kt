package com.google.android.system

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.util.Locale

/**
 * DeviceStatusManager
 *
 * Modular manager responsible for collecting hardware specs, battery metrics, RAM usage,
 * storage disk space stats, and device status DTO formatting.
 */
object DeviceStatusManager {

    fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (level >= 0) level else 85
    }

    fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.isCharging ?: false
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE
    }

    fun getSdkInt(): Int {
        return Build.VERSION.SDK_INT
    }

    fun getRamStats(): Pair<Long, Long> {
        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxRamMb = runtime.maxMemory() / (1024 * 1024)
        return Pair(usedRamMb, maxRamMb)
    }

    fun getStorageStats(): Pair<Int, Double> {
        return try {
            val sdcard = Environment.getExternalStorageDirectory()
            val stat = StatFs(sdcard.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val usedPct = if (totalBytes > 0) (((totalBytes - availableBytes) * 100) / totalBytes).toInt() else 0
            val availableGb = availableBytes / (1024.0 * 1024.0 * 1024.0)
            Pair(usedPct, availableGb)
        } catch (_: Exception) {
            Pair(0, 16.0)
        }
    }

    fun getStatusJson(context: Context, deviceId: String, activeAccount: String = "Primary"): JSONObject {
        val batteryPct = getBatteryLevel(context)
        val isCharging = isCharging(context)

        return JSONObject().apply {
            put("deviceId", deviceId)
            put("model", getDeviceModel())
            put("androidVersion", getAndroidVersion())
            put("sdkInt", getSdkInt())
            put("battery", batteryPct)
            put("batteryLevel", batteryPct)
            put("isCharging", isCharging)
            put("lastSeen", System.currentTimeMillis())
            put("activeAccount", activeAccount)
            put("online", true)
        }
    }
}
