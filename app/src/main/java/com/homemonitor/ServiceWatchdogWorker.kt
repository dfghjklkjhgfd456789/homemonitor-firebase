package com.google.android.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * ServiceWatchdogWorker
 *
 * Background WorkManager Resilient Watchdog Worker (FEAT-04):
 * - Runs periodically every 15 minutes to monitor system service health.
 * - Detects if MonitorService or Cloudflare Tunnel Engine was killed by Doze mode or OS memory pressure.
 * - Auto-restarts MonitorService silently in foreground mode if terminated.
 * - Records execution telemetry in SecurityVaultManager encrypted preferences.
 */
class ServiceWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ServiceWatchdogWorker"
        const val PREF_LAST_CHECK_TIME = "watchdog_last_check_timestamp"
        const val PREF_CHECK_COUNT = "watchdog_check_count"
        const val PREF_RECOVERY_COUNT = "watchdog_recovery_count"
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val prefs = SecurityVaultManager.getEncryptedPrefs(context)
            val now = System.currentTimeMillis()

            val currentCheckCount = prefs.getLong(PREF_CHECK_COUNT, 0L) + 1
            var currentRecoveryCount = prefs.getLong(PREF_RECOVERY_COUNT, 0L)

            val isServiceAlive = MonitorService.activeInstance != null

            Log.i(
                TAG,
                "Watchdog audit #$currentCheckCount: Service alive=$isServiceAlive"
            )

            if (!isServiceAlive) {
                Log.w(TAG, "MonitorService terminated detected! Triggering automatic background restart...")
                restartMonitorService(context)
                currentRecoveryCount++
            }

            prefs.edit()
                .putLong(PREF_LAST_CHECK_TIME, now)
                .putLong(PREF_CHECK_COUNT, currentCheckCount)
                .putLong(PREF_RECOVERY_COUNT, currentRecoveryCount)
                .apply()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ServiceWatchdogWorker: ${e.message}", e)
            Result.retry()
        }
    }

    private fun restartMonitorService(context: Context) {
        try {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "MonitorService background restart intent dispatched successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart MonitorService from watchdog: ${e.message}")
        }
    }
}
