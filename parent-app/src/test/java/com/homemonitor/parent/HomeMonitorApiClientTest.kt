package com.homemonitor.parent

import com.homemonitor.parent.data.api.HomeMonitorApiClient
import com.homemonitor.parent.data.model.ParentRole
import com.homemonitor.parent.data.model.RequestEnvelope
import com.homemonitor.parent.data.model.ResponseEnvelope
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeMonitorApiClientTest {

    @Test
    fun testRequestEnvelopeSerialization() {
        val params = JSONObject().apply {
            put("lens", "back")
        }

        val request = RequestEnvelope(
            version = 1,
            type = "REQUEST",
            requestId = "req_101",
            sessionId = "sess_parent_001",
            method = "camera.takePhoto",
            params = params,
            role = ParentRole.PARENT,
            timestamp = 1700000000000L,
            ttlMs = 15000L,
            deadline = 1700000015000L
        )

        val json = request.toJsonObject()
        assertEquals(1, json.optInt("version"))
        assertEquals("REQUEST", json.optString("type"))
        assertEquals("req_101", json.optString("requestId"))
        assertEquals("sess_parent_001", json.optString("sessionId"))
        assertEquals("camera.takePhoto", json.optString("method"))
        assertEquals("PARENT", json.optString("role"))
        assertEquals("back", json.optJSONObject("params")?.optString("lens"))
    }

    @Test
    fun testResponseEnvelopeDeserialization() {
        val rawResponse = JSONObject().apply {
            put("version", 1)
            put("type", "RESPONSE")
            put("requestId", "req_101")
            put("sessionId", "sess_parent_001")
            put("success", true)
            put("data", JSONObject().apply {
                put("photoUrl", "https://storage.local/photo1.jpg")
            })
            put("timestamp", 1700000000500L)
        }

        val response = ResponseEnvelope.fromJsonObject(rawResponse)
        assertEquals(1, response.version)
        assertEquals("RESPONSE", response.type)
        assertEquals("req_101", response.requestId)
        assertEquals("sess_parent_001", response.sessionId)
        assertTrue(response.success)
        assertNull(response.error)
        assertEquals("https://storage.local/photo1.jpg", response.data.optString("photoUrl"))
    }

    @Test
    fun testErrorResponseEnvelopeDeserialization() {
        val errorResponse = JSONObject().apply {
            put("version", 1)
            put("type", "RESPONSE")
            put("requestId", "req_102")
            put("sessionId", "sess_parent_001")
            put("success", false)
            put("error", "Authorization Failed: Method requires SUPER_ADMIN role permissions.")
            put("timestamp", 1700000000500L)
        }

        val response = ResponseEnvelope.fromJsonObject(errorResponse)
        assertEquals("req_102", response.requestId)
        assertFalse(response.success)
        assertEquals("Authorization Failed: Method requires SUPER_ADMIN role permissions.", response.error)
    }
}
