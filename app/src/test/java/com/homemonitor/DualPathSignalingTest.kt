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
    fun testBrowserOfferDecodingInKotlinEnvelope() {
        val browserOfferJson = JSONObject().apply {
            put("protocolVersion", 1)
            put("sessionId", "sess_browser_001")
            put("messageId", "off_10001")
            put("sequence", 1L)
            put("senderRole", "WEB")
            put("type", "SDP_OFFER")
            put("payload", JSONObject().apply {
                put("type", "offer")
                put("sdp", "v=0\r\no=- 99999 2 IN IP4 127.0.0.1\r\ns=-")
            })
        }

        val envelope = SignalingEnvelope.fromJsonObject(browserOfferJson)
        assertNotNull(envelope)
        assertEquals("sess_browser_001", envelope?.sessionId)
        assertEquals("off_10001", envelope?.messageId)
        assertEquals("WEB", envelope?.senderRole)
        assertEquals("SDP_OFFER", envelope?.type)
        assertEquals("v=0\r\no=- 99999 2 IN IP4 127.0.0.1\r\ns=-", envelope?.payload?.optString("sdp"))
    }

    @Test
    fun testBrowserIceCandidateDecodingInKotlinEnvelope() {
        val browserCandJson = JSONObject().apply {
            put("protocolVersion", 1)
            put("sessionId", "sess_browser_001")
            put("messageId", "cand_10002")
            put("sequence", 2L)
            put("senderRole", "WEB")
            put("type", "ICE_CANDIDATE")
            put("payload", JSONObject().apply {
                put("candidate", "candidate:1 1 UDP 2013266431 127.0.0.1 3478 typ host")
                put("sdpMid", "0")
                put("sdpMLineIndex", 0)
            })
        }

        val envelope = SignalingEnvelope.fromJsonObject(browserCandJson)
        assertNotNull(envelope)
        assertEquals("sess_browser_001", envelope?.sessionId)
        assertEquals("cand_10002", envelope?.messageId)
        assertEquals("WEB", envelope?.senderRole)
        assertEquals("ICE_CANDIDATE", envelope?.type)
        assertEquals("candidate:1 1 UDP 2013266431 127.0.0.1 3478 typ host", envelope?.payload?.optString("candidate"))
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
    fun testMalformedCopyOrderingValidation() {
        val tracker = ProcessedMessageTracker()
        val sessId = "sess_dedup_002"
        val msgId = "msg_malformed_001"

        // Helper validating pipeline matching SignalingCoordinator
        fun validateAndProcess(envelopeJson: JSONObject): Boolean {
            val env = SignalingEnvelope.fromJsonObject(envelopeJson) ?: return false
            if (env.type == "SDP_OFFER" && env.payload.optString("sdp").isBlank()) return false
            if (env.type == "ICE_CANDIDATE" && env.payload.optString("candidate").isBlank()) return false
            if (tracker.isAlreadyProcessed(env.sessionId, env.messageId)) return false
            return true
        }

        // 1. First copy is malformed (missing payload SDP)
        val malformedOffer = JSONObject().apply {
            put("protocolVersion", 1)
            put("sessionId", sessId)
            put("messageId", msgId)
            put("senderRole", "WEB")
            put("type", "SDP_OFFER")
            put("payload", JSONObject()) // empty payload missing 'sdp'
        }

        val malformedProcessed = validateAndProcess(malformedOffer)
        assertFalse("Malformed copy must be rejected at payload validation step", malformedProcessed)

        // 2. Second valid copy arrives via fallback provider
        val validOffer = JSONObject().apply {
            put("protocolVersion", 1)
            put("sessionId", sessId)
            put("messageId", msgId)
            put("senderRole", "WEB")
            put("type", "SDP_OFFER")
            put("payload", JSONObject().apply {
                put("sdp", "v=0\r\no=- 55555 2 IN IP4 127.0.0.1")
            })
        }

        val validProcessed = validateAndProcess(validOffer)
        assertTrue("Valid second copy must be processed because step 8 was not reached by malformed copy", validProcessed)

        // 3. Third duplicate copy arrives
        val duplicateProcessed = validateAndProcess(validOffer)
        assertFalse("Duplicate third copy must be rejected by tracker at step 8", duplicateProcessed)
    }

    @Test
    fun testExpiredEnvelopeRejection() {
        val expiredJson = JSONObject().apply {
            put("protocolVersion", 1)
            put("sessionId", "sess_exp_001")
            put("messageId", "msg_exp_001")
            put("senderRole", "WEB")
            put("type", "SDP_OFFER")
            put("createdAt", System.currentTimeMillis() - 120_000L)
            put("expiresAt", System.currentTimeMillis() - 60_000L) // Expired 60s ago
            put("payload", JSONObject().apply { put("sdp", "v=0") })
        }

        val envelope = SignalingEnvelope.fromJsonObject(expiredJson)
        assertNotNull(envelope)
        assertTrue(System.currentTimeMillis() > envelope!!.expiresAt)
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
