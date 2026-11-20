package com.homemonitor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Utility manager for handling manufacturer-specific OEM autostart intents
 * and battery optimization exemptions to ensure background persistence.
 */
object OemAutostartManager {
    private const val TAG = "OemAutostartManager"

    data class OemComponent(val packageName: String, val className: String)

    private val OEM_AUTOSTART_INTENTS = listOf(
        // Xiaomi / MIUI
        OemComponent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // Vivo
        OemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        OemComponent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        OemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity"),
        // Oppo / ColorOS
        OemComponent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        OemComponent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        OemComponent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        // Huawei / Honor
        OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        // Asus
        OemComponent("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
        OemComponent("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        // Samsung
        OemComponent("com.samsung.android.looper", "com.samsung.android.looper.auto.AutoStartActivity"),
        OemComponent("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
    )

    fun getManufacturer(): String = Build.MANUFACTURER.lowercase()

    /**
     * Checks if a manufacturer-specific autostart settings activity is available on the device.
     */
    fun isAutostartAvailable(context: Context): Boolean {
        return getAutostartIntent(context) != null
    }

    /**
     * Resolves the Intent for launching the OEM autostart management activity if present.
     */
    fun getAutostartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (component in OEM_AUTOSTART_INTENTS) {
            try {
                val intent = Intent().apply {
                    setComponent(ComponentName(component.packageName, component.className))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                    Log.d(TAG, "Found valid OEM autostart intent: ${component.packageName}/${component.className}")
                    return intent
                }
            } catch (e: Exception) {
                // Component not supported on this specific OS version
            }
        }
        return null
    }

    /**
     * Launches the OEM autostart activity if available. Returns true if launched successfully.
     */
    fun launchAutostartSettings(context: Context): Boolean {
        val intent = getAutostartIntent(context) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch OEM autostart intent", e)
            false
        }
    }

    /**
     * Returns true if the application is currently exempted from battery optimizations.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Returns an Intent to request ignoring battery optimizations for this app.
     */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return if (!isBatteryOptimizationIgnored(context)) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Launches the battery optimization exemption dialog.
     */
    fun launchBatteryOptimizationRequest(context: Context): Boolean {
        return try {
            context.startActivity(getBatteryOptimizationIntent(context))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch battery optimization request", e)
            false
        }
    }
}
