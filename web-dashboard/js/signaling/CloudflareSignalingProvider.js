// CloudflareSignalingProvider.js - Secondary WebRTC Signaling Provider via Worker WebSocket & Tickets

const DEFAULT_CLOUDFLARE_WORKER_URL = "https://webrtc.johnduck99.workers.dev";

class CloudflareSignalingProvider {
    constructor(workerUrl = DEFAULT_CLOUDFLARE_WORKER_URL) {
        this.workerUrl = workerUrl;
        this.listener = null;
        this.ws = null;
        this.isConnected = false;
        this.reconnectTimer = null;
        this.currentDeviceId = null;
        this.currentSessionId = null;
    }

    setListener(listener) {
        this.listener = listener;
    }

    async start(deviceId, sessionId) {
        this.stop();
        this.currentDeviceId = deviceId;
        this.currentSessionId = sessionId;

        await this.connectWebSocket();
    }

    stop() {
        if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
        if (this.ws) {
            try { this.ws.close(); } catch (_) {}
            this.ws = null;
        }
        this.isConnected = false;
    }

    async connectWebSocket() {
        if (!this.currentDeviceId) return;

        try {
            let idToken = "";
            if (firebase && firebase.auth && firebase.auth().currentUser) {
                idToken = await firebase.auth().currentUser.getIdToken();
            }

            if (!idToken) {
                console.warn("[CloudflareSignalingProvider] No active Firebase auth token. Will retry connection...");
                this.scheduleReconnect();
                return;
            }

            // 1. Fetch Single-Use WebSocket Ticket from Worker
            const ticketResp = await fetch(`${this.workerUrl}/v1/connect-ticket`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${idToken}`
                },
                body: JSON.stringify({
                    deviceId: this.currentDeviceId,
                    role: "WEB"
                })
            });

            if (!ticketResp.ok) {
                console.warn(`[CloudflareSignalingProvider] Ticket request failed with HTTP ${ticketResp.status}`);
                this.scheduleReconnect();
                return;
            }

            const ticketData = await ticketResp.json();
            const ticket = ticketData.ticket;
            if (!ticket) {
                this.scheduleReconnect();
                return;
            }

            // 2. Connect to WebSocket Signal Endpoint
            const wsUrl = `${this.workerUrl}/v1/devices/${this.currentDeviceId}/signal?ticket=${encodeURIComponent(ticket)}`
                .replace("https://", "wss://")
                .replace("http://", "ws://");

            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                console.log("[CloudflareSignalingProvider] Worker WebSocket Connected 🟢");
                this.isConnected = true;
                if (this.listener && typeof this.listener.onConnected === "function") {
                    this.listener.onConnected();
                }
            };

            this.ws.onmessage = (event) => {
                try {
                    if (typeof event.data === "string" && event.data.startsWith("{")) {
                        const envelope = JSON.parse(event.data);
                        if (this.listener && typeof this.listener.onEnvelopeReceived === "function") {
                            this.listener.onEnvelopeReceived(envelope);
                        }
                    }
                } catch (e) {
                    console.error("[CloudflareSignalingProvider] Message parse error:", e);
                }
            };

            this.ws.onclose = () => {
                console.log("[CloudflareSignalingProvider] Worker WebSocket Closed");
                this.isConnected = false;
                if (this.listener && typeof this.listener.onDisconnected === "function") {
                    this.listener.onDisconnected();
                }
                this.scheduleReconnect();
            };

            this.ws.onerror = (err) => {
                console.error("[CloudflareSignalingProvider] Worker WebSocket Error:", err);
                this.isConnected = false;
                this.scheduleReconnect();
            };

        } catch (e) {
            console.error("[CloudflareSignalingProvider] Connection exception:", e);
            this.scheduleReconnect();
        }
    }

    scheduleReconnect() {
        if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
        this.reconnectTimer = setTimeout(() => {
            if (this.currentDeviceId) {
                this.connectWebSocket();
            }
        }, 5000);
    }

    async publishEnvelope(envelope) {
        if (this.ws && this.isConnected && this.ws.readyState === WebSocket.OPEN) {
            try {
                this.ws.send(JSON.stringify(envelope));
                return true;
            } catch (e) {
                console.warn("[CloudflareSignalingProvider] WS send failed, attempting HTTP fallback:", e);
            }
        }

        // HTTP Fallback Push
        try {
            let idToken = "";
            if (firebase && firebase.auth && firebase.auth().currentUser) {
                idToken = await firebase.auth().currentUser.getIdToken();
            }

            const headers = { "Content-Type": "application/json" };
            if (idToken) headers["Authorization"] = `Bearer ${idToken}`;

            const resp = await fetch(`${this.workerUrl}/api/webrtc/event`, {
                method: "POST",
                headers: headers,
                body: JSON.stringify({
                    deviceId: this.currentDeviceId,
                    envelope: envelope
                })
            });
            return resp.ok;
        } catch (e) {
            console.error("[CloudflareSignalingProvider] HTTP fallback error:", e);
            return false;
        }
    }
}
