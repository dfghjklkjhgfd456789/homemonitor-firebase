package com.homemonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.system.SecurityVaultManager

/**
 * C2TrialManager
 *
 * Manages demo/trial period state, app licensing enforcement,
 * custom admin push notifications, and functionality locking for headless C2 service.
 *
 * Default State: STATUS_ACTIVE (App functions normally until an Admin explicitly
 * triggers a trial expiry / lock command for this specific device).
 */
object C2TrialManager {
    private const val TAG = "C2TrialManager"

    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_EXPIRED = "EXPIRED"
    const val STATUS_DISABLED = "DISABLED"

    private const val PREF_TRIAL_STATUS = "user_trial_status"
    private const val PREF_TRIAL_MESSAGE = "user_trial_message"
    private const val PREF_TRIAL_UPDATED = "user_trial_updated_at"

    const val DEFAULT_EXPIRED_MESSAGE =
        "⚠️ Your trial/demo period has expired. Please contact the administrator to purchase full app access or renew your license."

    const val DEFAULT_DISABLED_MESSAGE =
        "🔒 Application functionality has been disabled by administrator."

    /**
     * Retrieves current trial status string (ACTIVE, EXPIRED, or DISABLED).
     * Defaults to STATUS_ACTIVE so app works perfectly until admin command is triggered.
     */
    fun getTrialStatus(context: Context): String {
        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        return prefs.getString(PREF_TRIAL_STATUS, STATUS_ACTIVE) ?: STATUS_ACTIVE
    }

    /**
     * Checks if trial has expired or app has been disabled by admin.
     */
    fun isTrialExpired(context: Context): Boolean {
        val status = getTrialStatus(context)
        return status == STATUS_EXPIRED || status == STATUS_DISABLED
    }

    /**
     * Retrieves the custom or default trial notification message.
     */
    fun getTrialMessage(context: Context): String {
        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        val msg = prefs.getString(PREF_TRIAL_MESSAGE, null)
        return if (!msg.isNullOrBlank()) {
            msg
        } else {
            if (getTrialStatus(context) == STATUS_DISABLED) DEFAULT_DISABLED_MESSAGE else DEFAULT_EXPIRED_MESSAGE
        }
    }

    /**
     * Sets trial status and optional custom notification message.
     */
    fun setTrialStatus(context: Context, status: String, customMessage: String? = null): String {
        val finalStatus = when (status.uppercase().trim()) {
            "EXPIRED", "EXP", "TRIAL_EXPIRED" -> STATUS_EXPIRED
            "DISABLED", "DISABLE", "LOCK", "LOCKED" -> STATUS_DISABLED
            else -> STATUS_ACTIVE
        }

        val finalMsg = if (!customMessage.isNullOrBlank()) {
            customMessage.trim()
        } else if (finalStatus == STATUS_EXPIRED) {
            DEFAULT_EXPIRED_MESSAGE
        } else if (finalStatus == STATUS_DISABLED) {
            DEFAULT_DISABLED_MESSAGE
        } else {
            "✅ App License Active"
        }

        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        prefs.edit()
            .putString(PREF_TRIAL_STATUS, finalStatus)
            .putString(PREF_TRIAL_MESSAGE, finalMsg)
            .putLong(PREF_TRIAL_UPDATED, System.currentTimeMillis())
            .apply()

        Log.i(TAG, "Trial status updated to '$finalStatus' with message: '$finalMsg'")
        return finalStatus
    }

    /**
     * Resets trial to active state with no restrictions.
     */
    fun resetTrial(context: Context) {
        setTrialStatus(context, STATUS_ACTIVE, "✅ App License Active")
    }

    /**
     * Pushes a system notification / toast alert on the device displaying the admin's notice.
     */
    fun showDeviceNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "app_license_alerts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Application License Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications regarding app license & trial status"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(99102, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error posting trial notification: ${e.message}")
        }

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "$title: $message", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {}
    }
}
