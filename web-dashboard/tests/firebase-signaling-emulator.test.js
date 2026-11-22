// firebase-signaling-emulator.test.js
// Official @firebase/rules-unit-testing Realtime Database Emulator Suite Test Runner

const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');

console.log("==================================================================");
console.log("RUNNING OFFICIAL FIREBASE REALTIME DATABASE EMULATOR RULES SUITE");
console.log("==================================================================\n");

const rulesPath = path.join(__dirname, '../../database.rules.json');
const rulesText = fs.readFileSync(rulesPath, 'utf8');

async function runEmulatorTests() {
    let testEnv;
    try {
        testEnv = await initializeTestEnvironment({
            projectId: 'demo-homemonitor',
            database: {
                host: '127.0.0.1',
                port: 9000,
                rules: rulesText
            }
        });
    } catch (e) {
        console.error("❌ Failed to initialize test environment:", e);
        process.exit(1);
    }

    const parentAContext = testEnv.authenticatedContext("parent_user_a_456");
    const parentBContext = testEnv.authenticatedContext("parent_user_b_789");
    const approvedDeviceContext = testEnv.authenticatedContext("anon_device_uid_123");
    const unboundDeviceContext = testEnv.authenticatedContext("unbound_device_999");
    const unauthenticatedContext = testEnv.unauthenticatedContext();

    console.log("=== 1. EMULATOR SEEDING (Privileged Admin Context) ===");
    await testEnv.withSecurityRulesDisabled(async (adminContext) => {
        const db = adminContext.database();
        await db.ref("device_bindings/anon_device_uid_123").set({
            parentUid: "parent_user_a_456",
            deviceId: "DEV_001",
            status: "APPROVED"
        });
    });
    console.log("  • Seeded Approved Device Binding in Database Emulator: OK");

    console.log("\n=== 2. PARENT SIGNALING TENANT ISOLATION TESTS ===");
    const parentADb = parentAContext.database();
    const parentBDb = parentBContext.database();

    await assertSucceeds(
        parentADb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123/status").set("REQUESTED")
    );
    console.log("  ✓ [EMULATOR PASSED] Parent A writing signaling session under Parent A must be ALLOWED");

    await assertFails(
        parentBDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123").get()
    );
    console.log("  ✓ [EMULATOR PASSED] Parent B attempting to read Parent A's signaling session must be REJECTED");

    await assertFails(
        parentBDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123/status").set("TERMINATED")
    );
    console.log("  ✓ [EMULATOR PASSED] Parent B attempting to modify Parent A's signaling session must be REJECTED");

    console.log("\n=== 3. BOUND DEVICE CANONICAL SIGNALING AUTHORIZATION & LEAST PRIVILEGE TESTS ===");
    const approvedDeviceDb = approvedDeviceContext.database();
    const unboundDeviceDb = unboundDeviceContext.database();

    // Canonical Answer Envelope write by Approved Android Device
    const canonicalAnswerEnvelope = {
        protocolVersion: 1,
        sessionId: "sess_123",
        messageId: "ans_sess_123_101",
        sequence: 5,
        senderRole: "ANDROID",
        type: "SDP_ANSWER",
        createdAt: Date.now(),
        expiresAt: Date.now() + 60000,
        payload: {
            type: "answer",
            sdp: "v=0\r\no=- 12345 2 IN IP4 127.0.0.1",
            revision: 1
        }
    };

    await assertSucceeds(
        approvedDeviceDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123/answer").set(canonicalAnswerEnvelope)
    );
    console.log("  ✓ [EMULATOR PASSED] Approved Device writing canonical answer envelope under Parent A must be ALLOWED");

    // Negative Test: Approved Device attempting to overwrite offer envelope
    const canonicalOfferEnvelope = {
        protocolVersion: 1,
        sessionId: "sess_123",
        messageId: "off_sess_123_100",
        sequence: 1,
        senderRole: "WEB",
        type: "SDP_OFFER",
        createdAt: Date.now(),
        expiresAt: Date.now() + 60000,
        payload: {
            type: "offer",
            sdp: "v=0\r\no=- 88888 2 IN IP4 127.0.0.1",
            revision: 1
        }
    };

    await assertFails(
        approvedDeviceDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123/offer").set(canonicalOfferEnvelope)
    );
    console.log("  ✓ [EMULATOR PASSED] Approved Device attempting to overwrite offer SDP must be REJECTED");

    // Negative Test: Approved Device attempting to write to commands path
    await assertFails(
        approvedDeviceDb.ref("users/parent_user_a_456/devices/DEV_001/commands/cmd_fake").set({
            text: "unauthorized_cmd"
        })
    );
    console.log("  ✓ [EMULATOR PASSED] Approved Device attempting to write to commands path must be REJECTED");

    // Negative Test: Unbound Device attempting to write canonical answer envelope
    await assertFails(
        unboundDeviceDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123/answer").set(canonicalAnswerEnvelope)
    );
    console.log("  ✓ [EMULATOR PASSED] Unbound Device attempting to write answer envelope under Parent A must be REJECTED");

    console.log("\n=== 4. UNAUTHENTICATED & REVOCATION ACCESS TESTS ===");
    // Negative Test: Unauthenticated user read/write
    const unauthDb = unauthenticatedContext.database();
    await assertFails(
        unauthDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123").get()
    );
    console.log("  ✓ [EMULATOR PASSED] Unauthenticated client attempting to read signaling must be REJECTED");

    // Revoke Device
    await testEnv.withSecurityRulesDisabled(async (adminContext) => {
        await adminContext.database().ref("device_bindings/anon_device_uid_123").remove();
    });

    const canonicalCandEnvelope = {
        protocolVersion: 1,
        sessionId: "sess_123",
        messageId: "cand_sess_123_102",
        sequence: 6,
        senderRole: "ANDROID",
        type: "ICE_CANDIDATE",
        createdAt: Date.now(),
        expiresAt: Date.now() + 60000,
        payload: {
            candidate: "candidate:1 1 UDP 2013266431 127.0.0.1 3478 typ host",
            sdpMid: "0",
            sdpMLineIndex: 0
        }
    };

    await assertFails(
        approvedDeviceDb.ref("users/parent_user_a_456/devices/DEV_001/webrtc_signaling/sessions/sess_123/candidates/cand_1").set(canonicalCandEnvelope)
    );
    console.log("  ✓ [EMULATOR PASSED] Revoked Device writing ICE candidate envelope after binding deletion must be REJECTED");

    await testEnv.cleanup();
    console.log("\n==================================================================");
    console.log("REALTIME DATABASE EMULATOR TESTS COMPLETE: 9/9 PASSED");
    console.log("==================================================================\n");
}

runEmulatorTests().catch(err => {
    console.error("❌ Test runner exception:", err);
    process.exit(1);
});
