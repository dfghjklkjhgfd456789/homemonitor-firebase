package com.homemonitor

import com.google.android.system.ProcessedMessageTracker
import com.google.android.system.SignalingEnvelope
import com.google.android.system.SignalingSessionState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DualPathSignalingTest {

    @Test
    fun testEnvelopeSerializationAndDeserialization() {
        val payload = JSONObject().apply {
            put("sdp", "v=0\r\no=- 123456 2 IN IP4 127.0.0.1")
            put("revision", 1)
        }

        val envelope = SignalingEnvelope(
            protocolVersion = 1,
            sessionId = "sess_test_123",
            messageId = "msg_off_001",
            sequence = 100L,
            senderRole = "WEB",
            type = "SDP_OFFER",
            payload = payload
        )

        val json = envelope.toJsonObject()
        assertEquals(1, json.optInt("protocolVersion"))
        assertEquals("sess_test_123", json.optString("sessionId"))
        assertEquals("msg_off_001", json.optString("messageId"))
        assertEquals(100L, json.optLong("sequence"))
        assertEquals("WEB", json.optString("senderRole"))
        assertEquals("SDP_OFFER", json.optString("type"))

        val restored = SignalingEnvelope.fromJsonObject(json)
        assertNotNull(restored)
        assertEquals("sess_test_123", restored?.sessionId)
        assertEquals("msg_off_001", restored?.messageId)
        assertEquals(100L, restored?.sequence)
        assertEquals("WEB", restored?.senderRole)
        assertEquals("SDP_OFFER", restored?.type)
        assertEquals("v=0\r\no=- 123456 2 IN IP4 127.0.0.1", restored?.payload?.optString("sdp"))
    }

    @Test
    fun testProcessedMessageTrackerDeduplication() {
        val tracker = ProcessedMessageTracker()
        val sessId = "sess_dedup_001"
        val msgId = "cand_msg_999"

        assertFalse(tracker.isAlreadyProcessed(sessId, msgId))
        assertTrue(tracker.isAlreadyProcessed(sessId, msgId)) // Duplicate arrival must return true

        val nextMsgId = "cand_msg_1000"
        assertFalse(tracker.isAlreadyProcessed(sessId, nextMsgId))

        tracker.clear()
        assertFalse(tracker.isAlreadyProcessed(sessId, msgId)) // Cleared tracker accepts first arrival again
    }

    @Test
    fun testSignalingSessionStatesEnum() {
        val states = SignalingSessionState.entries
        assertTrue(states.contains(SignalingSessionState.CREATED))
        assertTrue(states.contains(SignalingSessionState.REQUESTED))
        assertTrue(states.contains(SignalingSessionState.ACCEPTED))
        assertTrue(states.contains(SignalingSessionState.OFFER_CREATED))
        assertTrue(states.contains(SignalingSessionState.ANSWER_PUBLISHED))
        assertTrue(states.contains(SignalingSessionState.PEER_CONNECTED))
        assertTrue(states.contains(SignalingSessionState.DATA_CHANNEL_OPEN))
        assertTrue(states.contains(SignalingSessionState.READY))
        assertTrue(states.contains(SignalingSessionState.ICE_FAILED))
    }
}
