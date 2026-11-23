package com.homemonitor.parent.data.api

import android.util.Log
import com.homemonitor.parent.data.model.ParentRole
import com.homemonitor.parent.data.model.RequestEnvelope
import com.homemonitor.parent.data.model.ResponseEnvelope
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HomeMonitorApiClient
 *
 * Kotlin implementation of the WebRTC DataChannel API Client for Parent Android.
 * Formats versioned JSON envelopes (`version: 1`), tracks pending requests by `requestId`,
 * checks DataChannel send buffer backpressure, and handles responses and topic events.
 */
class HomeMonitorApiClient {

    companion object {
        private const val TAG = "HomeMonitorApiClient"
        private const val MAX_BUFFERED_AMOUNT_BYTES = 128 * 1024 // 128 KB
    }

    private data class PendingRequest(
        val resolve: (JSONObject) -> Unit,
        val reject: (Throwable) -> Unit,
        val method: String,
        val job: Job
    )

    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private val topicSubscriptions = ConcurrentHashMap<String, MutableSet<(JSONObject) -> Unit>>()

    @Volatile var activeDataChannel: DataChannel? = null
    @Volatile var activeSessionId: String = "sess_parent_android_default"
    @Volatile var currentRole: ParentRole = ParentRole.PARENT

    suspend fun request(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = 15000L
    ): JSONObject = suspendCancellableCoroutine { continuation ->
        val dc = activeDataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            continuation.resumeWithException(IllegalStateException("WebRTC DataChannel is offline. Please connect to target device."))
            return@suspendCancellableCoroutine
        }

        if (dc.bufferedAmount() > MAX_BUFFERED_AMOUNT_BYTES) {
            continuation.resumeWithException(IllegalStateException("DataChannel send buffer full: backpressure high (${dc.bufferedAmount()} bytes buffered)"))
            return@suspendCancellableCoroutine
        }

        val requestId = "req_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val envelope = RequestEnvelope(
            version = 1,
            type = "REQUEST",
            requestId = requestId,
            sessionId = activeSessionId,
            method = method,
            params = params,
            role = currentRole,
            timestamp = System.currentTimeMillis(),
            ttlMs = timeoutMs,
            deadline = System.currentTimeMillis() + timeoutMs
        )

        val timeoutJob = CoroutineScope(Dispatchers.IO).launch {
            delay(timeoutMs)
            if (pendingRequests.containsKey(requestId)) {
                pendingRequests.remove(requestId)
                if (continuation.isActive) {
                    continuation.resumeWithException(java.util.concurrent.TimeoutException("P2P Request Timeout for method '$method' (${timeoutMs / 1000}s)"))
                }
            }
        }

        val pending = PendingRequest(
            resolve = { data ->
                timeoutJob.cancel()
                if (continuation.isActive) continuation.resume(data)
            },
            reject = { throwable ->
                timeoutJob.cancel()
                if (continuation.isActive) continuation.resumeWithException(throwable)
            },
            method = method,
            job = timeoutJob
        )

        pendingRequests[requestId] = pending

        try {
            val jsonStr = envelope.toJsonObject().toString()
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(jsonStr.toByteArray(StandardCharsets.UTF_8)), false)
            val sent = dc.send(buffer)
            if (!sent) {
                timeoutJob.cancel()
                pendingRequests.remove(requestId)
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Failed to transmit DataChannel packet for method '$method'"))
                }
            } else {
                Log.i(TAG, "[HomeMonitorAPI Transmit] $method ($requestId)")
            }
        } catch (e: Exception) {
            timeoutJob.cancel()
            pendingRequests.remove(requestId)
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    }

    fun handleIncomingMessage(text: String) {
        if (text.isBlank() || !text.startsWith("{")) return
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")

            if (type == "RESPONSE" || type == "RPC_RESPONSE" || type == "API_RESPONSE") {
                val requestId = json.optString("requestId", "")
                if (requestId.isNotBlank() && pendingRequests.containsKey(requestId)) {
                    val pending = pendingRequests.remove(requestId) ?: return
                    val success = json.optBoolean("success", true) && !json.has("error")
                    if (success) {
                        val responseData = ResponseEnvelope.fromJsonObject(json).data
                        pending.resolve(responseData)
                    } else {
                        val errMsg = json.optString("error", "Operation '${pending.method}' failed on target device.")
                        pending.reject(RuntimeException(errMsg))
                    }
                }
            } else if (type == "EVENT") {
                val topic = json.optString("event", json.optString("topic", ""))
                val data = json.optJSONObject("data") ?: JSONObject()
                if (topic.isNotBlank() && topicSubscriptions.containsKey(topic)) {
                    topicSubscriptions[topic]?.forEach { callback ->
                        try { callback(data) } catch (e: Exception) { Log.e(TAG, "Callback error for topic '$topic': ${e.message}") }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming DataChannel message: ${e.message}")
        }
    }

    fun subscribe(topic: String, callback: (JSONObject) -> Unit) {
        topicSubscriptions.computeIfAbsent(topic) { ConcurrentHashMap.newKeySet() }.add(callback)
    }

    fun unsubscribe(topic: String) {
        topicSubscriptions.remove(topic)
    }
}
