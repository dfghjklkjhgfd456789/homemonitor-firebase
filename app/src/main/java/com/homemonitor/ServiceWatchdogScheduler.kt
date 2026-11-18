package com.google.android.system

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ServiceWatchdogScheduler
 *
 * Scheduler & Diagnostics Engine for WorkManager Resilient Watchdog (FEAT-04):
 * - Enqueues 15-minute periodic watchdog task with WorkManager KEEP policy.
 * - Generates real-time health diagnostic status reports for C2 and UI cards.
 */
object ServiceWatchdogScheduler {

    private const val TAG = "ServiceWatchdogScheduler"
    private const val WORK_NAME = "HomeMonitorServiceWatchdogWork"

    data class WatchdogStatusReport(
        val isWatchdogActive: Boolean,
        val lastCheckTimestamp: Long,
        val lastCheckFormatted: String,
        val checkCount: Long,
        val recoveryCount: Long,
        val isMonitorServiceAlive: Boolean,
        val cloudflareState: String
    )

    /**
     * Enqueues or maintains the periodic 15-minute WorkManager watchdog task.
     */
    fun scheduleWatchdog(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val watchdogRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    watchdogRequest
                )

            Log.i(TAG, "Successfully enqueued periodic WorkManager Service Watchdog ($WORK_NAME).")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling WorkManager Service Watchdog: ${e.message}", e)
        }
    }

    /**
     * Obtains real-time diagnostic health report of the watchdog and services.
     */
    fun getWatchdogStatus(context: Context): WatchdogStatusReport {
        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        val lastCheck = prefs.getLong(ServiceWatchdogWorker.PREF_LAST_CHECK_TIME, 0L)
        val checks = prefs.getLong(ServiceWatchdogWorker.PREF_CHECK_COUNT, 0L)
        val recoveries = prefs.getLong(ServiceWatchdogWorker.PREF_RECOVERY_COUNT, 0L)

        val formattedDate = if (lastCheck > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastCheck))
        } else {
            "Not Yet Executed"
        }

        val isServiceAlive = MonitorService.activeInstance != null
        val cfState = "DISABLED"

        return WatchdogStatusReport(
            isWatchdogActive = checks > 0 || lastCheck > 0,
            lastCheckTimestamp = lastCheck,
            lastCheckFormatted = formattedDate,
            checkCount = checks,
            recoveryCount = recoveries,
            isMonitorServiceAlive = isServiceAlive,
            cloudflareState = cfState
        )
    }
}
