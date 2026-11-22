// signaling-protocol.test.js - WebRTC Signaling Interoperability & Deduplication Protocol Unit Tests
const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log("==========================================================");
console.log("RUNNING WEBRTC SIGNALING INTEROPERABILITY & PROTOCOL TESTS");
console.log("==========================================================\n");

// 1. Test Android-Generated SDP Answer Decoding in Firebase Provider Format
function decodeAndroidAnswer(valSnapshot, sessionId) {
    if (!valSnapshot || !valSnapshot.answer) return null;
    const ansObj = valSnapshot.answer;
    const sdpStr = (ansObj.payload && ansObj.payload.sdp) ? ansObj.payload.sdp : ansObj.sdp;
    if (!sdpStr) return null;

    return {
        protocolVersion: ansObj.protocolVersion || 1,
        sessionId: ansObj.sessionId || sessionId,
        messageId: ansObj.messageId || `ans_${sessionId}`,
        sequence: ansObj.sequence || Date.now(),
        senderRole: ansObj.senderRole || "ANDROID",
        type: ansObj.type || "SDP_ANSWER",
        createdAt: ansObj.createdAt || Date.now(),
        expiresAt: ansObj.expiresAt || (Date.now() + 60000),
        payload: ansObj.payload || { type: "answer", sdp: sdpStr }
    };
}

const androidAnswerSnapshot = {
    answer: {
        protocolVersion: 1,
        sessionId: "sess_p2p_001",
        messageId: "ans_sess_p2p_001_17000000",
        sequence: 5,
        senderRole: "ANDROID",
        type: "SDP_ANSWER",
        createdAt: 1700000000000,
        expiresAt: 1700000060000,
        payload: {
            type: "answer",
            sdp: "v=0\r\no=- 88888 2 IN IP4 127.0.0.1\r\ns=-",
            revision: 1
        }
    }
};

const decodedAns = decodeAndroidAnswer(androidAnswerSnapshot, "sess_p2p_001");
assert.ok(decodedAns, "Decoded answer envelope must not be null");
assert.strictEqual(decodedAns.type, "SDP_ANSWER");
assert.strictEqual(decodedAns.senderRole, "ANDROID");
assert.strictEqual(decodedAns.payload.sdp, "v=0\r\no=- 88888 2 IN IP4 127.0.0.1\r\ns=-");
console.log("  ✓ [TEST 1 PASSED] Android-generated SDP answer decoded correctly from answer.payload.sdp");

// 2. Test Android-Generated ICE Candidate Decoding in Firebase Provider Format
function decodeAndroidCandidates(valSnapshot, sessionId) {
    const envelopes = [];
    if (!valSnapshot || !valSnapshot.candidates) return envelopes;

    Object.keys(valSnapshot.candidates).forEach(key => {
        const cand = valSnapshot.candidates[key];
        const sender = cand.senderRole || cand.sender;
        if (sender !== "WEB" && sender !== "DASHBOARD") {
            const candStr = (cand.payload && cand.payload.candidate) ? cand.payload.candidate : cand.candidate;
            if (candStr) {
                envelopes.push({
                    protocolVersion: cand.protocolVersion || 1,
                    sessionId: cand.sessionId || sessionId,
                    messageId: cand.messageId || `cand_${sessionId}_${key}`,
                    sequence: cand.sequence || Date.now(),
                    senderRole: sender || "ANDROID",
                    type: cand.type || "ICE_CANDIDATE",
                    createdAt: cand.createdAt || Date.now(),
                    expiresAt: cand.expiresAt || (Date.now() + 60000),
                    payload: cand.payload || {
                        candidate: candStr,
                        sdpMid: cand.sdpMid || "0",
                        sdpMLineIndex: cand.sdpMLineIndex !== undefined ? cand.sdpMLineIndex : 0
                    }
                });
            }
        }
    });
    return envelopes;
}

const androidCandidatesSnapshot = {
    candidates: {
        "cand_01": {
            protocolVersion: 1,
            sessionId: "sess_p2p_001",
            messageId: "cand_sess_p2p_001_101",
            sequence: 6,
            senderRole: "ANDROID",
            type: "ICE_CANDIDATE",
            payload: {
                candidate: "candidate:1 1 UDP 2013266431 192.168.1.50 3478 typ host",
                sdpMid: "0",
                sdpMLineIndex: 0
            }
        }
    }
};

const decodedCands = decodeAndroidCandidates(androidCandidatesSnapshot, "sess_p2p_001");
assert.strictEqual(decodedCands.length, 1);
assert.strictEqual(decodedCands[0].type, "ICE_CANDIDATE");
assert.strictEqual(decodedCands[0].senderRole, "ANDROID");
assert.strictEqual(decodedCands[0].payload.candidate, "candidate:1 1 UDP 2013266431 192.168.1.50 3478 typ host");
console.log("  ✓ [TEST 2 PASSED] Android-generated ICE candidate decoded correctly from candidate.payload.candidate");

// 3. Test 9-Step Validation & Deduplication Ordering (Malformed Copy vs Valid Copy)
class MockSignalingCoordinator {
    constructor(activeSessionId) {
        this.activeSessionId = activeSessionId;
        this.processedMessages = new Set();
        this.dispatchedEnvelopes = [];
    }

    handleIncomingEnvelope(envelope) {
        // Step 1: Envelope structure
        if (!envelope || typeof envelope !== "object") return false;
        if (!envelope.sessionId || !envelope.messageId || !envelope.type || !envelope.senderRole) return false;

        // Step 2: Protocol version
        if (envelope.protocolVersion && envelope.protocolVersion < 1) return false;

        // Step 3: Session ID
        if (envelope.sessionId !== this.activeSessionId) return false;

        // Step 4: Sender role
        if (envelope.senderRole === "WEB" || envelope.senderRole === "DASHBOARD") return false;

        // Step 5: Message type
        const knownTypes = ["SDP_ANSWER", "ICE_CANDIDATE", "SESSION_CLOSE", "SESSION_ERROR"];
        if (!knownTypes.includes(envelope.type)) return false;

        // Step 6: Required payload fields
        const payload = envelope.payload || {};
        if (envelope.type === "SDP_ANSWER") {
            const sdp = payload.sdp || envelope.sdp;
            if (!sdp || typeof sdp !== "string" || !sdp.trim()) return false;
        } else if (envelope.type === "ICE_CANDIDATE") {
            const cand = payload.candidate || envelope.candidate;
            if (!cand || typeof cand !== "string" || !cand.trim()) return false;
        }

        // Step 7: Expiry
        if (envelope.expiresAt && Date.now() > envelope.expiresAt) return false;

        // Step 8: Only then register message ID as processed
        if (this.processedMessages.has(envelope.messageId)) {
            return false; // Deduplicated
        }
        this.processedMessages.add(envelope.messageId);

        // Step 9: Dispatch event
        this.dispatchedEnvelopes.push(envelope);
        return true;
    }
}

const mockCoord = new MockSignalingCoordinator("sess_p2p_001");

// Malformed first copy (missing sdp in payload)
const malformedAnswer = {
    protocolVersion: 1,
    sessionId: "sess_p2p_001",
    messageId: "ans_dual_100",
    sequence: 1,
    senderRole: "ANDROID",
    type: "SDP_ANSWER",
    payload: {} // Malformed!
};

const malformedResult = mockCoord.handleIncomingEnvelope(malformedAnswer);
assert.strictEqual(malformedResult, false, "Malformed answer must be rejected at step 6");
assert.strictEqual(mockCoord.processedMessages.has("ans_dual_100"), false, "Malformed copy must NOT be registered in processedMessages");
assert.strictEqual(mockCoord.dispatchedEnvelopes.length, 0);

// Valid second copy with same message ID arriving via Cloudflare Worker fallback
const validAnswer = {
    protocolVersion: 1,
    sessionId: "sess_p2p_001",
    messageId: "ans_dual_100",
    sequence: 1,
    senderRole: "ANDROID",
    type: "SDP_ANSWER",
    payload: {
        sdp: "v=0\r\no=- 77777 2 IN IP4 127.0.0.1"
    }
};

const validResult = mockCoord.handleIncomingEnvelope(validAnswer);
assert.strictEqual(validResult, true, "Valid second copy must pass validation");
assert.strictEqual(mockCoord.processedMessages.has("ans_dual_100"), true, "Valid copy must be registered in processedMessages");
assert.strictEqual(mockCoord.dispatchedEnvelopes.length, 1);

// Duplicate third copy arriving via Firebase
const duplicateResult = mockCoord.handleIncomingEnvelope(validAnswer);
assert.strictEqual(duplicateResult, false, "Duplicate third copy must be rejected at step 8");
assert.strictEqual(mockCoord.dispatchedEnvelopes.length, 1, "Dispatched count must remain 1");

console.log("  ✓ [TEST 3 PASSED] 9-step validation ordering prevents malformed first copy from poisoning valid second copy");

// 4. Test Expired Envelope Rejection
const expiredEnvelope = {
    protocolVersion: 1,
    sessionId: "sess_p2p_001",
    messageId: "cand_exp_001",
    sequence: 10,
    senderRole: "ANDROID",
    type: "ICE_CANDIDATE",
    createdAt: Date.now() - 120000,
    expiresAt: Date.now() - 60000, // Expired 60s ago
    payload: {
        candidate: "candidate:1 1 UDP 2013266431 127.0.0.1 3478 typ host"
    }
};

const expResult = mockCoord.handleIncomingEnvelope(expiredEnvelope);
assert.strictEqual(expResult, false, "Expired envelope must be rejected at step 7");
console.log("  ✓ [TEST 4 PASSED] Expired envelopes rejected prior to messageId registration");

// 5. Verify Cloudflare Worker Persistent Replay Mock Protocol Logic
class MockWorkerDurableObject {
    constructor() {
        this.storage = new Map();
        this.processedIds = new Set();
    }

    async processAndDeliverEnvelope(envelope, recipientConnected) {
        if (this.processedIds.has(envelope.messageId)) {
            return "DELIVERED_AND_ACKNOWLEDGED";
        }
        this.processedIds.add(envelope.messageId);

        if (recipientConnected) {
            return "DELIVERED_AND_ACKNOWLEDGED";
        } else {
            // Persist for replay
            this.storage.set(`event_${envelope.messageId}`, envelope);
            return "ACCEPTED_FOR_DELIVERY";
        }
    }

    async replayEventsOnConnect() {
        const replayed = [];
        for (const [key, env] of this.storage.entries()) {
            if (env.expiresAt && Date.now() > env.expiresAt) {
                this.storage.delete(key);
            } else {
                replayed.push(env);
                this.storage.delete(key); // Acknowledged upon replay
            }
        }
        return replayed;
    }
}

async function testWorkerReplay() {
    const doHub = new MockWorkerDurableObject();
    const env1 = {
        sessionId: "sess_p2p_001",
        messageId: "msg_fallback_001",
        type: "SDP_ANSWER",
        senderRole: "ANDROID",
        expiresAt: Date.now() + 60000,
        payload: { sdp: "v=0" }
    };

    // When recipient is disconnected, HTTP fallback persists message
    const res1 = await doHub.processAndDeliverEnvelope(env1, false);
    assert.strictEqual(res1, "ACCEPTED_FOR_DELIVERY");
    assert.strictEqual(doHub.storage.size, 1);

    // When recipient connects, unacknowledged message is replayed and deleted from storage
    const replayed = await doHub.replayEventsOnConnect();
    assert.strictEqual(replayed.length, 1);
    assert.strictEqual(replayed[0].messageId, "msg_fallback_001");
    assert.strictEqual(doHub.storage.size, 0, "Acknowledged message must be cleared from storage");
}

testWorkerReplay().then(() => {
    console.log("  ✓ [TEST 5 PASSED] HTTP Worker fallback persists unacknowledged messages and replays on reconnect");
    console.log("\n==========================================================");
    console.log("ALL WEBRTC SIGNALING PROTOCOL TESTS PASSED (5/5)");
    console.log("==========================================================\n");
}).catch(err => {
    console.error("❌ Test failed:", err);
    process.exit(1);
});
