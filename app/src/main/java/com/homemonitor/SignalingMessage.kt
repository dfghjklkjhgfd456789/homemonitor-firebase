package com.google.android.system

import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * SignalingMessage & Envelope Specification
 *
 * Defines the canonical signaling envelope shared across Firebase RTDB and Cloudflare Worker.
 */
data class SignalingEnvelope(
    val protocolVersion: Int = 1,
    val sessionId: String,
    val messageId: String,
    val sequence: Long,
    val senderRole: String, // "WEB" or "ANDROID"
    val type: String,       // SESSION_REQUEST, SESSION_ACCEPTED, SDP_OFFER, SDP_ANSWER, ICE_CANDIDATE, ICE_COMPLETE, MESSAGE_ACK, SESSION_STATE, SESSION_CLOSE, SESSION_ERROR
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 60_000L,
    val payload: JSONObject = JSONObject()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("protocolVersion", protocolVersion)
            put("sessionId", sessionId)
            put("messageId", messageId)
            put("sequence", sequence)
            put("senderRole", senderRole)
            put("type", type)
            put("createdAt", createdAt)
            put("expiresAt", expiresAt)
            put("payload", payload)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): SignalingEnvelope? {
            return try {
                val protocolVersion = json.optInt("protocolVersion", 1)
                val sessionId = json.optString("sessionId", "")
                val messageId = json.optString("messageId", "")
                val sequence = json.optLong("sequence", 0L)
                val senderRole = json.optString("senderRole", "")
                val type = json.optString("type", "")
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())
                val expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + 60_000L)
                val payload = json.optJSONObject("payload") ?: JSONObject()

                if (sessionId.isBlank() || messageId.isBlank() || senderRole.isBlank() || type.isBlank()) {
                    null
                } else {
                    SignalingEnvelope(
                        protocolVersion = protocolVersion,
                        sessionId = sessionId,
                        messageId = messageId,
                        sequence = sequence,
                        senderRole = senderRole,
                        type = type,
                        createdAt = createdAt,
                        expiresAt = expiresAt,
                        payload = payload
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * ProcessedMessageTracker
 * Deduplicates incoming messages by messageId per WebRTC session.
 */
class ProcessedMessageTracker {
    private val processedIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile private var currentSessionId: String? = null

    @Synchronized
    fun isAlreadyProcessed(sessionId: String, messageId: String): Boolean {
        if (currentSessionId != sessionId) {
            currentSessionId = sessionId
            processedIds.clear()
        }
        if (processedIds.contains(messageId)) {
            return true
        }
        processedIds.add(messageId)
        // Cap set size to prevent memory leaks
        if (processedIds.size > 500) {
            val iterator = processedIds.iterator()
            repeat(100) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        return false
    }

    fun clear() {
        processedIds.clear()
        currentSessionId = null
    }
}
