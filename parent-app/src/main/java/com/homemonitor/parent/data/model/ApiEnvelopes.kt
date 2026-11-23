package com.homemonitor.parent.data.model

import org.json.JSONObject

enum class ParentRole {
    PARENT,
    SUPER_ADMIN
}

data class RequestEnvelope(
    val version: Int = 1,
    val type: String = "REQUEST",
    val requestId: String,
    val sessionId: String,
    val method: String,
    val params: JSONObject = JSONObject(),
    val role: ParentRole = ParentRole.PARENT,
    val timestamp: Long = System.currentTimeMillis(),
    val ttlMs: Long = 15000L,
    val deadline: Long = System.currentTimeMillis() + 15000L
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("type", type)
            put("requestId", requestId)
            put("sessionId", sessionId)
            put("method", method)
            put("params", params)
            put("role", role.name)
            put("timestamp", timestamp)
            put("ttlMs", ttlMs)
            put("deadline", deadline)
        }
    }
}

data class ResponseEnvelope(
    val version: Int = 1,
    val type: String = "RESPONSE",
    val requestId: String,
    val sessionId: String,
    val success: Boolean,
    val data: JSONObject = JSONObject(),
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJsonObject(json: JSONObject): ResponseEnvelope {
            val unwrapData = if (json.has("data")) json.optJSONObject("data") ?: JSONObject() else json
            return ResponseEnvelope(
                version = json.optInt("version", 1),
                type = json.optString("type", "RESPONSE"),
                requestId = json.optString("requestId", ""),
                sessionId = json.optString("sessionId", ""),
                success = json.optBoolean("success", true),
                data = unwrapData,
                error = if (json.has("error") && !json.isNull("error")) json.optString("error") else null,
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
