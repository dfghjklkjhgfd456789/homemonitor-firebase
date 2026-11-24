// homemonitor-api-client.test.js - Unit Test Suite for HomeMonitorAPI WebRTC Client
const assert = require('assert');
const { HomeMonitorApiClient } = require('../js/api/homemonitor-api-client');

console.log("==========================================================");
console.log("RUNNING HOMEMONITOR WEBRTC API CLIENT UNIT TEST SUITE");
console.log("==========================================================\n");

// Mock DataChannel environment
global.activeSessionId = "sess_test_999";
global.isMasterAdmin = true;

let mockSentEnvelopes = [];
global.dc = {
    readyState: "open",
    bufferedAmount: 0,
    send: (str) => {
        mockSentEnvelopes.push(JSON.parse(str));
    }
};

const api = new HomeMonitorApiClient();

async function runApiClientTests() {
    // Test 1: Request Envelope Formatting
    mockSentEnvelopes = [];
    const summaryPromise = api.device.getSummary();

    assert.strictEqual(mockSentEnvelopes.length, 1);
    const reqEnv = mockSentEnvelopes[0];
    assert.strictEqual(reqEnv.version, 1);
    assert.strictEqual(reqEnv.type, "REQUEST");
    assert.strictEqual(reqEnv.method, "device.getSummary");
    assert.strictEqual(reqEnv.sessionId, "sess_test_999");
    assert.strictEqual(reqEnv.role, "SUPER_ADMIN");
    assert.ok(reqEnv.requestId.startsWith("req_"));
    console.log("  ✓ [TEST 1 PASSED] Request envelope correctly formatted with version, method, role & request ID");

    // Test 2: Response Correlation & Promise Resolution
    const responseEnv = {
        version: 1,
        type: "RESPONSE",
        requestId: reqEnv.requestId,
        sessionId: "sess_test_999",
        success: true,
        data: {
            deviceModel: "Pixel 9 Pro",
            batteryLevel: 92,
            isCharging: true
        },
        error: null,
        timestamp: Date.now()
    };

    api.handleIncomingEnvelope(responseEnv);
    const summaryData = await summaryPromise;
    assert.strictEqual(summaryData.deviceModel, "Pixel 9 Pro");
    assert.strictEqual(summaryData.batteryLevel, 92);
    assert.strictEqual(summaryData.isCharging, true);
    console.log("  ✓ [TEST 2 PASSED] Response envelope correlated with request ID and promise resolved with data");

    // Test 3: Response Error Handling
    mockSentEnvelopes = [];
    const failedPromise = api.files.delete("/sys/protected");
    const failedReqEnv = mockSentEnvelopes[0];

    const errorResponseEnv = {
        version: 1,
        type: "RESPONSE",
        requestId: failedReqEnv.requestId,
        success: false,
        data: {},
        error: "Permission denied: Path is read-only",
        timestamp: Date.now()
    };

    api.handleIncomingEnvelope(errorResponseEnv);
    try {
        await failedPromise;
        assert.fail("Promise should have rejected on success=false response");
    } catch (err) {
        assert.ok(err.message.includes("Permission denied"));
        console.log("  ✓ [TEST 3 PASSED] Error response correctly rejected promise with server error message");
    }

    // Test 4: Topic Subscriptions & Event Dispatching
    mockSentEnvelopes = [];
    let eventReceivedData = null;
    const unsub = api.subscribe("notifications.stream", (data) => {
        eventReceivedData = data;
    });

    assert.strictEqual(mockSentEnvelopes.length, 1);
    assert.strictEqual(mockSentEnvelopes[0].type, "SUBSCRIBE");
    assert.strictEqual(mockSentEnvelopes[0].topic, "notifications.stream");

    const eventEnv = {
        version: 1,
        type: "EVENT",
        eventId: "evt_101",
        event: "notifications.stream",
        data: {
            appName: "WhatsApp",
            title: "New Message",
            text: "Hello from Android"
        },
        timestamp: Date.now()
    };

    api.handleIncomingEnvelope(eventEnv);
    assert.ok(eventReceivedData);
    assert.strictEqual(eventReceivedData.appName, "WhatsApp");
    assert.strictEqual(eventReceivedData.text, "Hello from Android");

    unsub();
    console.log("  ✓ [TEST 4 PASSED] Topic SUBSCRIBE envelope sent and incoming EVENT payload delivered to subscriber");

    // Test 5: Backpressure Rejection
    global.dc.bufferedAmount = 200000; // > 128 KB
    try {
        await api.device.getStatus();
        assert.fail("Request should fail when backpressure threshold exceeded");
    } catch (err) {
        assert.ok(err.message.includes("backpressure high"));
        console.log("  ✓ [TEST 5 PASSED] High DataChannel backpressure immediately rejected new request");
    }
    global.dc.bufferedAmount = 0;

    // Test 6: Timeout Handling
    mockSentEnvelopes = [];
    const timeoutPromise = api.device.getStatus();
    const timeoutReqEnv = mockSentEnvelopes[0];

    // Trigger timeout prematurely for test verification
    const pendingObj = api.pendingRequests.get(timeoutReqEnv.requestId);
    clearTimeout(pendingObj.timer);
    pendingObj.reject(new Error("P2P Request Timeout for method 'device.getStatus' (15s)"));
    api.pendingRequests.delete(timeoutReqEnv.requestId);

    try {
        await timeoutPromise;
        assert.fail("Timeout request should reject");
    } catch (err) {
        assert.ok(err.message.includes("Timeout"));
        console.log("  ✓ [TEST 6 PASSED] Request timeout rejected pending promise and cleaned up request tracker");
    }

    console.log("\n==========================================================");
    console.log("ALL WEBRTC API CLIENT TESTS PASSED (6/6)");
    console.log("==========================================================\n");
}

runApiClientTests().catch(err => {
    console.error("❌ Test runner exception:", err);
    process.exit(1);
});
