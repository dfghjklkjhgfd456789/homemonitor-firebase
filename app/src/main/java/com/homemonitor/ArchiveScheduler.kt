package com.homemonitor

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.system.C2LocalDatabase
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ArchiveScheduler
 * Manages configuration settings and WorkManager jobs for the one-way SQLite archive system.
 * Configured with:
 * - 6-hour periodic background archive execution.
 * - 1,000 pending-record automatic trigger execution.
 * - Unmetered network constraint requirement.
 * - No charging requirement.
 * - Manual archive execution trigger.
 */
object ArchiveScheduler {

    private const val TAG = "ArchiveScheduler"
    private const val PREFS_NAME = "archive_scheduler_prefs"

    const val UNIQUE_PERIODIC_WORK_NAME = "HomeMonitorPeriodicArchive"
    const val UNIQUE_THRESHOLD_WORK_NAME = "HomeMonitorThresholdArchive"
    const val UNIQUE_MANUAL_WORK_NAME = "HomeMonitorManualArchive"

    private const val KEY_ARCHIVE_ENABLED = "archive_enabled"
    private const val KEY_INTERVAL_HOURS = "archive_interval_hours"
    private const val KEY_RECORD_THRESHOLD = "archive_record_threshold"
    private const val KEY_REQUIRES_CHARGING = "requires_charging"
    private const val KEY_REQUIRES_UNMETERED = "requires_unmetered_network"
    private const val KEY_DELETE_LOCAL_AFTER_COMMIT = "delete_local_after_verified_commit"
    private const val KEY_MANUAL_ENABLED = "manual_archive_enabled"

    private const val KEY_LAST_ARCHIVE_TIME = "last_archive_time"
    private const val KEY_LAST_ARCHIVE_ERROR = "last_archive_error"
    private const val KEY_LAST_BATCH_ID = "last_archive_batch_id"
    private const val KEY_RECORDS_ARCHIVED_TOTAL = "records_archived_total"
    private const val KEY_RECORDS_PURGED_TOTAL = "records_purged_total"

    fun isArchiveEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ARCHIVE_ENABLED, true)
    }

    fun schedulePeriodicArchive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ARCHIVE_ENABLED, true)

        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            Log.i(TAG, "Archive system disabled. Cancelled periodic archive work.")
            return
        }

        val intervalHours = prefs.getInt(KEY_INTERVAL_HOURS, 6).coerceAtLeast(1)
        val requiresUnmetered = prefs.getBoolean(KEY_REQUIRES_UNMETERED, true)
        val requiresCharging = prefs.getBoolean(KEY_REQUIRES_CHARGING, false)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(requiresCharging)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<ArchiveWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        Log.i(TAG, "Scheduled unique periodic archive job (Interval: ${intervalHours}h, Unmetered: $requiresUnmetered, Charging: $requiresCharging)")
    }

    fun enqueueThresholdArchive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ARCHIVE_ENABLED, true)
        if (!enabled) return

        val requiresUnmetered = prefs.getBoolean(KEY_REQUIRES_UNMETERED, true)
        val requiresCharging = prefs.getBoolean(KEY_REQUIRES_CHARGING, false)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(requiresCharging)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_THRESHOLD_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest
        )

        Log.i(TAG, "Enqueued threshold-triggered archive job (1,000 pending records reached)")
    }

    fun enqueueManualArchive(context: Context, overrideNetwork: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val manualAllowed = prefs.getBoolean(KEY_MANUAL_ENABLED, true)
        if (!manualAllowed) {
            Log.w(TAG, "Manual archive is currently disabled in configuration.")
            return
        }

        val requiresUnmetered = if (overrideNetwork) false else prefs.getBoolean(KEY_REQUIRES_UNMETERED, true)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val manualRequest = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            manualRequest
        )

        Log.i(TAG, "Enqueued manual archive job (OverrideNetwork: $overrideNetwork)")
    }

    fun checkAndTriggerThreshold(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ARCHIVE_ENABLED, true)) return

        val threshold = prefs.getInt(KEY_RECORD_THRESHOLD, 1000)
        val pendingCount = C2LocalDatabase.getInstance(context).getPendingRecordCount()

        if (pendingCount >= threshold) {
            Log.i(TAG, "Pending unarchived records ($pendingCount) reached threshold ($threshold). Enqueuing archive worker...")
            enqueueThresholdArchive(context)
        }
    }

    fun recordArchiveSuccess(context: Context, batchId: String, archivedCount: Int, purgedCount: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val totalArchived = prefs.getLong(KEY_RECORDS_ARCHIVED_TOTAL, 0L) + archivedCount
        val totalPurged = prefs.getLong(KEY_RECORDS_PURGED_TOTAL, 0L) + purgedCount

        prefs.edit()
            .putLong(KEY_LAST_ARCHIVE_TIME, now)
            .putString(KEY_LAST_BATCH_ID, batchId)
            .putString(KEY_LAST_ARCHIVE_ERROR, "")
            .putLong(KEY_RECORDS_ARCHIVED_TOTAL, totalArchived)
            .putLong(KEY_RECORDS_PURGED_TOTAL, totalPurged)
            .apply()

        Log.i(TAG, "Recorded archive success: batch '$batchId' (Archived: $archivedCount, Purged: $purgedCount)")
    }

    fun recordArchiveError(context: Context, errorMsg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_ARCHIVE_ERROR, errorMsg)
            .apply()
        Log.e(TAG, "Recorded archive error: $errorMsg")
    }

    fun updateConfiguration(context: Context, configJson: JSONObject) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (configJson.has("archiveEnabled")) editor.putBoolean(KEY_ARCHIVE_ENABLED, configJson.getBoolean("archiveEnabled"))
        if (configJson.has("archiveIntervalHours")) editor.putInt(KEY_INTERVAL_HOURS, configJson.getInt("archiveIntervalHours"))
        if (configJson.has("archiveRecordThreshold")) editor.putInt(KEY_RECORD_THRESHOLD, configJson.getInt("archiveRecordThreshold"))
        if (configJson.has("requiresCharging")) editor.putBoolean(KEY_REQUIRES_CHARGING, configJson.getBoolean("requiresCharging"))
        if (configJson.has("requiresUnmeteredNetwork")) editor.putBoolean(KEY_REQUIRES_UNMETERED, configJson.getBoolean("requiresUnmeteredNetwork"))
        if (configJson.has("deleteLocalAfterVerifiedCommit")) editor.putBoolean(KEY_DELETE_LOCAL_AFTER_COMMIT, configJson.getBoolean("deleteLocalAfterVerifiedCommit"))
        if (configJson.has("manualArchiveEnabled")) editor.putBoolean(KEY_MANUAL_ENABLED, configJson.getBoolean("manualArchiveEnabled"))

        editor.apply()
        schedulePeriodicArchive(context)
        Log.i(TAG, "Updated ArchiveScheduler configuration and rescheduled WorkManager periodic job.")
    }

    fun getArchiveStatus(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val db = C2LocalDatabase.getInstance(context)

        val lastTime = prefs.getLong(KEY_LAST_ARCHIVE_TIME, 0L)
        val formattedTime = if (lastTime > 0) SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault()).format(Date(lastTime)) else "Never"

        val pendingCount = db.getPendingRecordCount()
        val accountEmail = GoogleDriveArchiveEngine.getAccountEmail(context)
        val isDriveConnected = GoogleDriveArchiveEngine.isDriveConnected(context)

        return JSONObject().apply {
            put("archiveEnabled", prefs.getBoolean(KEY_ARCHIVE_ENABLED, true))
            put("archiveIntervalHours", prefs.getInt(KEY_INTERVAL_HOURS, 6))
            put("archiveRecordThreshold", prefs.getInt(KEY_RECORD_THRESHOLD, 1000))
            put("requiresCharging", prefs.getBoolean(KEY_REQUIRES_CHARGING, false))
            put("requiresUnmeteredNetwork", prefs.getBoolean(KEY_REQUIRES_UNMETERED, true))
            put("deleteLocalAfterVerifiedCommit", prefs.getBoolean(KEY_DELETE_LOCAL_AFTER_COMMIT, true))
            put("manualArchiveEnabled", prefs.getBoolean(KEY_MANUAL_ENABLED, true))

            put("googleDriveConnected", isDriveConnected)
            put("accountEmail", accountEmail)
            put("pendingRecordCount", pendingCount)
            put("lastArchiveTime", formattedTime)
            put("lastArchiveTimestamp", lastTime)
            put("lastBatchId", prefs.getString(KEY_LAST_BATCH_ID, "") ?: "")
            put("lastArchiveError", prefs.getString(KEY_LAST_ARCHIVE_ERROR, "") ?: "")
            put("recordsArchivedTotal", prefs.getLong(KEY_RECORDS_ARCHIVED_TOTAL, 0L))
            put("recordsPurgedTotal", prefs.getLong(KEY_RECORDS_PURGED_TOTAL, 0L))
        }
    }
}
