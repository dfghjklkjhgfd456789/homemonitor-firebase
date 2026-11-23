// CloudflareWorkerRelayClient.js - Standalone Cloudflare Worker WebSocket Communication Client ($0 Cost, Zero KV)

const CLOUDFLARE_WORKER_WS_RELAY_URL = "https://websocket-worker-relay.johnduck99.workers.dev";

class CloudflareWorkerRelayClient {
    constructor(relayUrl = CLOUDFLARE_WORKER_WS_RELAY_URL) {
        this.relayUrl = relayUrl;
        this.ws = null;
        this.isConnected = false;
        this.reconnectTimer = null;
        this.currentDeviceId = "default_device";
    }

    start(deviceId = "default_device") {
        this.stop();
        this.currentDeviceId = deviceId;
        this.connectWebSocket();
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

    connectWebSocket() {
        if (!this.currentDeviceId) return;

        try {
            const wsUrl = `${this.relayUrl}/ws?role=dashboard&deviceId=${this.currentDeviceId}`
                .replace("https://", "wss://")
                .replace("http://", "ws://");

            console.log(`[CloudflareWorkerRelayClient] Connecting to Relay WebSocket: ${wsUrl}`);
            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                console.log("%c[CloudflareWorkerRelayClient] WebSocket Connected 🟢", "color: #10b981; font-weight: bold;");
                this.isConnected = true;
                window.cloudflareWs = this.ws;
                if (typeof updateWebRtcConnectionBadge === "function" && (typeof dc === "undefined" || !dc || dc.readyState !== "open")) {
                    updateWebRtcConnectionBadge("WEBSOCKET_RELAY_CONNECTED");
                }
            };

            this.ws.onmessage = (event) => {
                try {
                    const rawData = event.data;
                    console.log("[CloudflareWorkerRelayClient Inbound]", rawData.substring(0, 120));
                    if (typeof HomeMonitorAPI !== "undefined" && HomeMonitorAPI && typeof HomeMonitorAPI.handleIncomingEnvelope === "function") {
                        HomeMonitorAPI.handleIncomingEnvelope(rawData);
                    }
                } catch (err) {
                    console.error("[CloudflareWorkerRelayClient Message Error]", err);
                }
            };

            this.ws.onclose = () => {
                this.isConnected = false;
                window.cloudflareWs = null;
                console.warn("[CloudflareWorkerRelayClient] WebSocket closed. Scheduling reconnect in 4s...");
                if (typeof updateWebRtcConnectionBadge === "function" && (typeof dc === "undefined" || !dc || dc.readyState !== "open")) {
                    updateWebRtcConnectionBadge("DISCONNECTED");
                }
                this.scheduleReconnect();
            };

            this.ws.onerror = (err) => {
                this.isConnected = false;
                window.cloudflareWs = null;
                console.warn("[CloudflareWorkerRelayClient] WebSocket error. Reconnecting...", err);
                if (typeof updateWebRtcConnectionBadge === "function" && (typeof dc === "undefined" || !dc || dc.readyState !== "open")) {
                    updateWebRtcConnectionBadge("DISCONNECTED");
                }
                this.scheduleReconnect();
            };
        } catch (e) {
            console.error("[CloudflareWorkerRelayClient Exception]", e);
            this.scheduleReconnect();
        }
    }

    scheduleReconnect() {
        if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
        this.reconnectTimer = setTimeout(() => {
            this.connectWebSocket();
        }, 4000);
    }

    sendMessage(payloadStr) {
        if (this.ws && this.ws.readyState === 1) { // 1 = OPEN
            this.ws.send(payloadStr);
            return true;
        }
        return false;
    }
}

// Global Singleton Instance
const cloudflareRelayClient = new CloudflareWorkerRelayClient();

if (typeof window !== "undefined") {
    window.cloudflareRelayClient = cloudflareRelayClient;
}
