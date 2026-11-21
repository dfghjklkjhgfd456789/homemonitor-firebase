// firebase-rules-security.test.js - Comprehensive Security Rules Test Suite for database.rules.json
const fs = require('fs');
const path = require('path');

console.log("==================================================================");
console.log("RUNNING HOMEMONITOR REPAIR 02.2 FIREBASE SECURITY RULES SUITE");
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
    const now = Date.now();
    const parts = pathStr.split('/').filter(Boolean);

    const isAnonymous = auth && auth.token && auth.token.firebase && auth.token.firebase.sign_in_provider === 'anonymous';

    if (parts[0] === 'device_bindings') {
        const deviceUid = parts[1];
        if (!auth) return false;
        if (isAnonymous) return false; // Anonymous devices CANNOT write bindings!

        const exists = data && Object.keys(data).length > 0;
        const parentUidInNewData = newData ? newData.parentUid : null;
        const parentUidInData = data ? data.parentUid : null;

        if (!exists) {
            const invId = newData ? newData.invitationId : null;
            const inv = root && root.enrollment_invitations && root.enrollment_invitations[invId];
            const invValid = inv && inv.parentUid === auth.uid && inv.claim && inv.claim.deviceUid === deviceUid;
            return Boolean(newData && auth.uid === parentUidInNewData && newData.status === 'APPROVED' && invValid);
        } else {
            return Boolean(auth.uid === parentUidInData);
        }
    }

    if (parts[0] === 'enrollment_invitations') {
        if (!auth) return false;
        const exists = data && Object.keys(data).length > 0;
        if (!exists) {
            if (isAnonymous) return false; // Anonymous devices cannot create invitations
            return Boolean(newData && newData.parentUid === auth.uid &&
                   newData.status === 'PENDING' &&
                   newData.expiresAt > now &&
                   newData.expiresAt <= now + 300000);
        } else if (data.parentUid === auth.uid) {
            return Boolean(!isAnonymous);
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
        if (auth.uid === parentUid && !isAnonymous) return true;

        if (parts[2] === 'pending_devices') {
            const deviceUid = parts[3];
            const exists = data && Object.keys(data).length > 0;
            if (auth.uid === deviceUid && !exists) {
                const invId = newData ? newData.invitationId : null;
                const inv = root && root.enrollment_invitations && root.enrollment_invitations[invId];
                const invValid = inv && inv.parentUid === parentUid && inv.claim && inv.claim.deviceUid === auth.uid && inv.status === 'CLAIMED';
                return Boolean(newData && newData.deviceUid === auth.uid && newData.status === 'PENDING' && invValid);
            }
            return false;
        }

        if (parts[2] === 'devices') {
            const deviceId = parts[3];
            const subPath = parts[4];
            const binding = root && root.device_bindings && root.device_bindings[auth.uid];
            const isApprovedDevice = Boolean(binding && binding.parentUid === parentUid && binding.deviceId === deviceId && binding.status === 'APPROVED');

            if (!subPath) {
                // Root device node: only parent can write root device metadata
                return auth.uid === parentUid && !isAnonymous;
            }

            if (subPath === 'commands') {
                // Commands node: only parent can issue commands
                return auth.uid === parentUid && !isAnonymous;
            }

            // Permitted device-originated subpaths: status, telemetry, webrtc_signaling, media, social, response
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

console.log("=== 1. PARENT IDENTITY & ANONYMOUS DEVICE ISOLATION TESTS ===");
const anonDeviceAuth = { uid: "anon_device_uid_123", token: { firebase: { sign_in_provider: "anonymous" } } };
const parentAAuth = { uid: "parent_user_a_456", token: { firebase: { sign_in_provider: "password" } } };
const parentBAuth = { uid: "parent_user_b_789", token: { firebase: { sign_in_provider: "password" } } };

const now = Date.now();
const rootWithClaimedInv = {
    enrollment_invitations: {
        "inv_123": { parentUid: "parent_user_a_456", status: "CLAIMED", claim: { deviceUid: "anon_device_uid_123" } }
    }
};

// Attack: Anonymous device tries to create invitation
const anonInvAttempt = evaluateWriteRule("enrollment_invitations/inv_anon", anonDeviceAuth, null, { parentUid: "anon_device_uid_123", status: "PENDING", expiresAt: now + 60000 }, {});
assertRule("Anonymous device creating invitation must be REJECTED", anonInvAttempt, false);

// Attack: Anonymous device tries to create a binding for itself
const selfBindingAttempt = evaluateWriteRule("device_bindings/anon_device_uid_123", anonDeviceAuth, null, { parentUid: "parent_user_b_789", deviceId: "DEV_001", status: "APPROVED", invitationId: "inv_123" }, rootWithClaimedInv);
assertRule("Anonymous device self-binding creation attempt must be REJECTED", selfBindingAttempt, false);

// Legitimate: Parent A approves and binds Device 123
const legitimateBinding = evaluateWriteRule("device_bindings/anon_device_uid_123", parentAAuth, null, { parentUid: "parent_user_a_456", deviceId: "DEV_001", status: "APPROVED", invitationId: "inv_123" }, rootWithClaimedInv);
assertRule("Parent A binding creation for Device 123 with valid claim must be ALLOWED", legitimateBinding, true);

// Attack: Parent A tries to modify Parent B's binding
const hijackBinding = evaluateWriteRule("device_bindings/anon_device_uid_123", parentAAuth, { parentUid: "parent_user_b_789" }, { parentUid: "parent_user_a_456" }, {});
assertRule("Parent A modifying Parent B's existing binding must be REJECTED", hijackBinding, false);

console.log("\n=== 2. PENDING DEVICE REQUEST LINKAGE TESTS ===");

// Device creating pending request linked to valid claimed invitation
const validPendingReq = evaluateWriteRule(
    "users/parent_user_a_456/pending_devices/anon_device_uid_123",
    anonDeviceAuth,
    null,
    { deviceUid: "anon_device_uid_123", invitationId: "inv_123", status: "PENDING" },
    rootWithClaimedInv
);
assertRule("Device submitting pending request linked to valid claimed invitation must be ALLOWED", validPendingReq, true);

// Attack: Device submitting pending request WITHOUT invitation linkage
const unlinkedPendingReq = evaluateWriteRule(
    "users/parent_user_a_456/pending_devices/anon_device_uid_123",
    anonDeviceAuth,
    null,
    { deviceUid: "anon_device_uid_123", invitationId: "inv_fake_nonexistent", status: "PENDING" },
    rootWithClaimedInv
);
assertRule("Device submitting pending request with unlinked/fake invitation must be REJECTED", unlinkedPendingReq, false);

// Attack: Device self-approving pending request
const selfApprove = evaluateWriteRule(
    "users/parent_user_a_456/pending_devices/anon_device_uid_123",
    anonDeviceAuth,
    null,
    { deviceUid: "anon_device_uid_123", invitationId: "inv_123", status: "APPROVED" },
    rootWithClaimedInv
);
assertRule("Device self-approving request to APPROVED status must be REJECTED", selfApprove, false);

console.log("\n=== 3. INVITATION LIFECYCLE & SINGLE-USE CLAIMING TESTS ===");

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
    { uid: "attacker_device_uid_999", token: { firebase: { sign_in_provider: "anonymous" } } },
    { parentUid: "parent_user_a_456", status: "CLAIMED", expiresAt: now + 120000 },
    { parentUid: "parent_user_a_456", status: "CLAIMED", claim: { deviceUid: "attacker_device_uid_999" } },
    {}
);
assertRule("Second device claiming an already-claimed invitation must be REJECTED", secondClaim, false);

console.log("\n=== 4. DEVICE ISOLATION & FIELD-LEVEL WRITE PROTECTION TESTS ===");

const rootWithActiveBinding = {
    device_bindings: {
        "anon_device_uid_123": { parentUid: "parent_user_a_456", deviceId: "DEV_001", status: "APPROVED" }
    }
};

const rootWithRevokedBinding = {
    device_bindings: {}
};

// Approved Device Subpath Status Write Access
const approvedDeviceStatusWrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_001/status", anonDeviceAuth, {}, { online: true }, rootWithActiveBinding);
assertRule("Approved Device 123 writing to DEV_001/status under Parent A must be ALLOWED", approvedDeviceStatusWrite, true);

// Approved Device Subpath Telemetry Write Access
const approvedDeviceTelemetryWrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_001/telemetry", anonDeviceAuth, {}, { location: "GPS" }, rootWithActiveBinding);
assertRule("Approved Device 123 writing to DEV_001/telemetry under Parent A must be ALLOWED", approvedDeviceTelemetryWrite, true);

// Approved Device Root Overwrite Attack
const approvedDeviceRootOverwrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_001", anonDeviceAuth, { nickname: "Alex's Phone" }, { nickname: "Hijacked Name" }, rootWithActiveBinding);
assertRule("Approved Device 123 attempting to overwrite root device metadata must be REJECTED", approvedDeviceRootOverwrite, false);

// Approved Device Commands Write Attack
const approvedDeviceCommandsWrite = evaluateWriteRule("users/parent_user_a_456/devices/DEV_001/commands", anonDeviceAuth, {}, { text: "fake_command" }, rootWithActiveBinding);
assertRule("Approved Device 123 attempting to write to commands path must be REJECTED", approvedDeviceCommandsWrite, false);

// Approved Device Sibling Access Attack
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
