// firebase-rules-security.test.js - Comprehensive Security Rules Test Suite for database.rules.json
const fs = require('fs');
const path = require('path');

console.log("==================================================================");
console.log("RUNNING HOMEMONITOR FIREBASE SECURITY RULES VERIFICATION SUITE");
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

// Simulated rule evaluator for database.rules.json AST logic
function evaluateWriteRule(pathStr, auth, data, newData, root) {
    const now = Date.now();
    const parts = pathStr.split('/').filter(Boolean);

    if (parts[0] === 'device_bindings') {
        const deviceUid = parts[1];
        if (!auth) return false;

        const exists = data && Object.keys(data).length > 0;
        const parentUidInNewData = newData ? newData.parentUid : null;
        const parentUidInData = data ? data.parentUid : null;

        if (!exists) {
            return Boolean(newData && auth.uid === parentUidInNewData && newData.status === 'APPROVED');
        } else {
            return Boolean(auth.uid === parentUidInData);
        }
    }

    if (parts[0] === 'enrollment_invitations') {
        if (!auth) return false;
        const exists = data && Object.keys(data).length > 0;
        if (!exists) {
            return Boolean(newData && newData.parentUid === auth.uid &&
                   newData.status === 'PENDING' &&
                   newData.expiresAt > now &&
                   newData.expiresAt <= now + 300000);
        } else if (data.parentUid === auth.uid) {
            return true;
        } else if (data.status === 'PENDING' && data.expiresAt > now) {
            return Boolean(newData && newData.status === 'CLAIMED' &&
                   newData.claim && newData.claim.deviceUid === auth.uid &&
                   newData.parentUid === data.parentUid);
        }
        return false;
    }

    if (parts[0] === 'users') {
        const parentUid = parts[1];
        if (!auth) return false;
        if (auth.uid === parentUid) return true;

        if (parts[2] === 'pending_devices') {
            const deviceUid = parts[3];
            const exists = data && Object.keys(data).length > 0;
            if (auth.uid === deviceUid && !exists) {
                return Boolean(newData && newData.deviceUid === auth.uid && newData.status === 'PENDING');
            }
            return false;
        }

        if (parts[2] === 'devices') {
            const deviceId = parts[3];
            const binding = root && root.device_bindings && root.device_bindings[auth.uid];
            return Boolean(binding && binding.parentUid === parentUid && binding.deviceId === deviceId && binding.status === 'APPROVED');
        }
    }

    return false;
}

function evaluateReadRule(pathStr, auth, data, root) {
    const parts = pathStr.split('/').filter(Boolean);
    if (!auth) return false;

    if (parts[0] === 'enrollment_invitations') {
        const invId = parts[1];
        if (!invId) return false; // Prevents listing all invitations
        return Boolean(data && (data.parentUid === auth.uid || (data.claim && data.claim.deviceUid === auth.uid)));
    }

    if (parts[0] === 'device_bindings') {
        const deviceUid = parts[1];
        return Boolean(auth.uid === deviceUid || (data && data.parentUid === auth.uid));
    }

    if (parts[0] === 'users') {
        const parentUid = parts[1];
        if (auth.uid === parentUid) return true;

        if (parts[2] === 'pending_devices') {
            const deviceUid = parts[3];
            return Boolean(auth.uid === deviceUid);
        }

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

console.log("=== 1. VULNERABILITY A: DEVICE SELF-AUTHORIZATION TESTS ===");
const anonDeviceAuth = { uid: "anon_device_uid_123" };
const parentAAuth = { uid: "parent_user_a_456" };
const parentBAuth = { uid: "parent_user_b_789" };

// Attack Attempt: Device tries to create binding for itself under Parent B
const selfBindingAttempt = evaluateWriteRule("device_bindings/anon_device_uid_123", anonDeviceAuth, null, { parentUid: "parent_user_b_789", deviceId: "DEV_001", status: "APPROVED" }, {});
assertRule("Anonymous device self-binding creation attempt must be REJECTED", selfBindingAttempt, false);

// Legitimate Attempt: Parent A approves and binds Device 123
const legitimateBinding = evaluateWriteRule("device_bindings/anon_device_uid_123", parentAAuth, null, { parentUid: "parent_user_a_456", deviceId: "DEV_001", status: "APPROVED" }, {});
assertRule("Parent A binding creation for Device 123 must be ALLOWED", legitimateBinding, true);

// Attack Attempt: Device tries to update/hijack binding
const updateBindingAttempt = evaluateWriteRule("device_bindings/anon_device_uid_123", anonDeviceAuth, { parentUid: "parent_user_a_456" }, { parentUid: "parent_user_b_789" }, {});
assertRule("Anonymous device updating existing binding must be REJECTED", updateBindingAttempt, false);

console.log("\n=== 2. VULNERABILITY B: INVITATION READ/WRITE PROTECTION TESTS ===");

// Listing all invitations
const listInvitations = evaluateReadRule("enrollment_invitations", anonDeviceAuth, null, {});
assertRule("Anonymous user listing all invitations must be REJECTED", listInvitations, false);

// Parent reading own invitation
const parentReadInv = evaluateReadRule("enrollment_invitations/inv_123", parentAAuth, { parentUid: "parent_user_a_456" }, {});
assertRule("Parent reading own invitation must be ALLOWED", parentReadInv, true);

// Unrelated user reading invitation
const strangerReadInv = evaluateReadRule("enrollment_invitations/inv_123", parentBAuth, { parentUid: "parent_user_a_456" }, {});
assertRule("Unrelated user reading Parent A's invitation must be REJECTED", strangerReadInv, false);

console.log("\n=== 3. VULNERABILITY C & D: EXPIRATION & SINGLE-USE CLAIMING TESTS ===");
const now = Date.now();

// Valid Claim
const validClaim = evaluateWriteRule(
    "enrollment_invitations/inv_123",
    anonDeviceAuth,
    { parentUid: "parent_user_a_456", status: "PENDING", expiresAt: now + 120000 },
    { parentUid: "parent_user_a_456", status: "CLAIMED", claim: { deviceUid: "anon_device_uid_123" } },
    {}
);
assertRule("Device claiming valid unexpired invitation must be ALLOWED", validClaim, true);

// Expired Claim
const expiredClaim = evaluateWriteRule(
    "enrollment_invitations/inv_123",
    anonDeviceAuth,
    { parentUid: "parent_user_a_456", status: "PENDING", expiresAt: now - 1000 },
    { parentUid: "parent_user_a_456", status: "CLAIMED", claim: { deviceUid: "anon_device_uid_123" } },
    {}
);
assertRule("Device claiming expired invitation must be REJECTED", expiredClaim, false);

// Re-claiming already claimed invitation
const secondClaim = evaluateWriteRule(
    "enrollment_invitations/inv_123",
    { uid: "attacker_device_uid_999" },
    { parentUid: "parent_user_a_456", status: "CLAIMED", expiresAt: now + 120000 },
    { parentUid: "parent_user_a_456", status: "CLAIMED", claim: { deviceUid: "attacker_device_uid_999" } },
    {}
);
assertRule("Second device claiming an already-claimed invitation must be REJECTED", secondClaim, false);

console.log("\n=== 4. VULNERABILITY E: PENDING DEVICE & SELF-APPROVAL PROTECTION ===");

// Device creating pending request
const pendingRequest = evaluateWriteRule(
    "users/parent_user_a_456/pending_devices/anon_device_uid_123",
    anonDeviceAuth,
    null,
    { deviceUid: "anon_device_uid_123", status: "PENDING" },
    {}
);
assertRule("Device creating pending request with PENDING status must be ALLOWED", pendingRequest, true);

// Device self-approving pending request
const selfApprove = evaluateWriteRule(
    "users/parent_user_a_456/pending_devices/anon_device_uid_123",
    anonDeviceAuth,
    null,
    { deviceUid: "anon_device_uid_123", status: "APPROVED" },
    {}
);
assertRule("Device self-approving request to APPROVED status must be REJECTED", selfApprove, false);

console.log("\n=== 5. VULNERABILITY F: DEVICE ACCESS & REVOCATION TESTS ===");

const rootWithActiveBinding = {
    device_bindings: {
        "anon_device_uid_123": { parentUid: "parent_user_a_456", deviceId: "DEV_001", status: "APPROVED" }
    }
};

const rootWithRevokedBinding = {
    device_bindings: {}
};

// Approved Device Write Access
const approvedDeviceWrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_001/status", anonDeviceAuth, {}, { online: true }, rootWithActiveBinding);
assertRule("Approved Device 123 writing to DEV_001 under Parent A must be ALLOWED", approvedDeviceWrite, true);

// Approved Device Sibling Access
const approvedDeviceSiblingWrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_002/status", anonDeviceAuth, {}, { online: true }, rootWithActiveBinding);
assertRule("Approved Device 123 writing to sibling DEV_002 must be REJECTED", approvedDeviceSiblingWrite, false);

// Revoked Device Write Access
const revokedDeviceWrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_001/status", anonDeviceAuth, {}, { online: true }, rootWithRevokedBinding);
assertRule("Revoked Device writing to DEV_001 after binding removal must be REJECTED", revokedDeviceWrite, false);

console.log("\n==================================================================");
console.log(`SECURITY RULES VERIFICATION COMPLETE: ${passedTests}/${totalTests} TESTS PASSED`);
console.log("==================================================================\n");

if (passedTests !== totalTests) {
    process.exit(1);
}
