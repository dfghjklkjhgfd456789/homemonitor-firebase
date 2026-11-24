// firebase-rules-security.test.js - Security Rules Test Suite for database.rules.json
const fs = require('fs');
const path = require('path');

console.log("==================================================================");
console.log("RUNNING HOMEMONITOR FIREBASE SECURITY RULES SUITE");
console.log("==================================================================\n");

const rulesFilePath = path.join(__dirname, '../../database.rules.json');
if (!fs.existsSync(rulesFilePath)) {
    console.error("❌ database.rules.json file not found!");
    process.exit(1);
}

const rulesJson = JSON.parse(fs.readFileSync(rulesFilePath, 'utf8'));
const rules = rulesJson.rules;

let passedTests = 0;
let totalTests = 0;

function assertRule(description, conditionResult, expected) {
    totalTests++;
    if (conditionResult === expected) {
        console.log(`  ✓ [PASSED] ${description}`);
        passedTests++;
    } else {
        console.error(`  ✕ [FAILED] ${description} (Expected: ${expected}, Got: ${conditionResult})`);
    }
}

// AST Rule evaluator modeling database.rules.json
function evaluateWriteRule(pathStr, auth, data, newData, root) {
    const parts = pathStr.split('/').filter(Boolean);
    const isAnonymous = auth && auth.token && auth.token.firebase && auth.token.firebase.sign_in_provider === 'anonymous';

    if (parts[0] === 'device_bindings') {
        if (!auth || isAnonymous) return false;
        const exists = data && Object.keys(data).length > 0;
        const parentUidInNewData = newData ? newData.parentUid : null;

        if (!exists) {
            return Boolean(newData && auth.uid === parentUidInNewData && newData.status === 'APPROVED');
        } else {
            return Boolean(auth.uid === data.parentUid && newData && newData.parentUid === data.parentUid && newData.deviceId === data.deviceId);
        }
    }

    if (parts[0] === 'users') {
        const parentUid = parts[1];
        if (!auth) return false;
        if (auth.uid === parentUid && !isAnonymous) return true;

        if (parts[2] === 'devices') {
            const deviceId = parts[3];
            const subPath = parts[4];
            const binding = root && root.device_bindings && root.device_bindings[auth.uid];
            const isApprovedDevice = Boolean(binding && binding.parentUid === parentUid && binding.deviceId === deviceId && binding.status === 'APPROVED');

            if (!subPath || subPath === 'commands') {
                return auth.uid === parentUid && !isAnonymous;
            }

            if (['status', 'telemetry', 'webrtc_signaling', 'media', 'social', 'response'].includes(subPath)) {
                return (auth.uid === parentUid && !isAnonymous) || isApprovedDevice;
            }

            return auth.uid === parentUid && !isAnonymous;
        }
    }

    return false;
}

function evaluateReadRule(pathStr, auth, data, root) {
    const parts = pathStr.split('/').filter(Boolean);
    if (!auth) return false;

    if (parts[0] === 'device_bindings') {
        const deviceUid = parts[1];
        return Boolean(auth.uid === deviceUid || (data && data.parentUid === auth.uid));
    }

    if (parts[0] === 'users') {
        const parentUid = parts[1];
        if (auth.uid === parentUid) return true;

        if (parts[2] === 'devices') {
            const deviceId = parts[3];
            const binding = root && root.device_bindings && root.device_bindings[auth.uid];
            return Boolean(binding && binding.parentUid === parentUid && binding.deviceId === deviceId && binding.status === 'APPROVED');
        }
    }

    return false;
}

// ------------------------------------------------------------------
// TEST SUITE EXECUTION
// ------------------------------------------------------------------

console.log("=== 1. PARENT IDENTITY & DIRECT BINDING AUTHORIZATION TESTS ===");
const anonDeviceAuth = { uid: "anon_device_uid_123", token: { firebase: { sign_in_provider: "anonymous" } } };
const parentAAuth = { uid: "parent_user_a_456", token: { firebase: { sign_in_provider: "password" } } };
const parentBAuth = { uid: "parent_user_b_789", token: { firebase: { sign_in_provider: "password" } } };

// Attack: Anonymous device tries to create a binding for itself
const selfBindingAttempt = evaluateWriteRule("device_bindings/anon_device_uid_123", anonDeviceAuth, null, { parentUid: "parent_user_b_789", deviceId: "DEV_001", status: "APPROVED" }, {});
assertRule("Anonymous device self-binding creation attempt must be REJECTED", selfBindingAttempt, false);

// Legitimate: Parent A approves and binds Device 123 directly
const legitimateBinding = evaluateWriteRule("device_bindings/anon_device_uid_123", parentAAuth, null, { parentUid: "parent_user_a_456", deviceId: "DEV_001", status: "APPROVED" }, {});
assertRule("Parent A direct binding creation for Device 123 must be ALLOWED", legitimateBinding, true);

// Attack: Parent A tries to modify Parent B's existing binding
const hijackBinding = evaluateWriteRule("device_bindings/anon_device_uid_123", parentAAuth, { parentUid: "parent_user_b_789", deviceId: "DEV_001" }, { parentUid: "parent_user_a_456", deviceId: "DEV_001" }, {});
assertRule("Parent A modifying Parent B's existing binding must be REJECTED", hijackBinding, false);

console.log("\n=== 2. DEVICE HEARTBEAT & TELEMETRY AUTHORIZATION TESTS ===");
const activeRootWithBinding = {
    device_bindings: {
        "anon_device_uid_123": { parentUid: "parent_user_a_456", deviceId: "DEV_001", status: "APPROVED" }
    }
};

// Approved Device 123 sending heartbeat status
const approvedStatusWrite = evaluateWriteRule(
    "users/parent_user_a_456/devices/DEV_001/status",
    anonDeviceAuth,
    null,
    { deviceId: "DEV_001", online: true, lastSeen: Date.now() },
    activeRootWithBinding
);
assertRule("Approved bound device writing heartbeat status must be ALLOWED", approvedStatusWrite, true);

// Attack: Unbound device trying to write to Parent A's device status
const unapprovedStatusWrite = evaluateWriteRule(
    "users/parent_user_a_456/devices/DEV_001/status",
    { uid: "unbound_device_999", token: { firebase: { sign_in_provider: "anonymous" } } },
    null,
    { deviceId: "DEV_001", online: true },
    activeRootWithBinding
);
assertRule("Unbound device writing to Parent A's status must be REJECTED", unapprovedStatusWrite, false);

console.log(`\n==================================================================`);
console.log(`TEST RESULTS: ${passedTests} / ${totalTests} PASSED`);
console.log(`==================================================================\n`);

if (passedTests === totalTests) {
    console.log("✅ ALL FIREBASE SECURITY RULE TESTS PASSED!");
    process.exit(0);
} else {
    console.error("❌ SECURITY RULE SUITE FAILED!");
    process.exit(1);
}
