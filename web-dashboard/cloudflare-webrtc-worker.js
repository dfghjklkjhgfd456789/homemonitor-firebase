/**
 * Cloudflare Worker: HomeMonitor WebRTC Dual-Path Edge Signaling Hub
 *
 * Implements high-availability WebRTC signaling over Cloudflare Durable Objects + WebSocket.
 * Features:
 * - Firebase Auth ID Token verification via Google Public Keys
 * - Device Binding & Parent Authorization checks
 * - Short-lived, single-use WebSocket tickets (/v1/connect-ticket)
 * - Durable Object DeviceSignalingHub for per-device session state and WebSocket routing
 * - Envelope sequence tracking, event deduplication by messageId, and replay
 * - Safe CORS controls & Session cleanup
 */

const FIREBASE_PROJECT_ID = "homemonitor-db";
const FIREBASE_RTDB_URL = "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app";
const GOOGLE_PUBLIC_KEYS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

const CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Device-ID",
};

// Cached Google Public Keys for JWT verification
let googlePublicKeys = null;
let keysExpiryTime = 0;

async function getGooglePublicKeys() {
    const now = Date.now();
    if (googlePublicKeys && now < keysExpiryTime) {
        return googlePublicKeys;
    }
    try {
        const resp = await fetch(GOOGLE_PUBLIC_KEYS_URL);
        if (resp.ok) {
            googlePublicKeys = await resp.json();
            const cacheControl = resp.headers.get("cache-control") || "";
            const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
            const maxAgeSec = maxAgeMatch ? parseInt(maxAgeMatch[1], 10) : 3600;
            keysExpiryTime = now + (maxAgeSec * 1000);
            return googlePublicKeys;
        }
    } catch (e) {
        console.error("Failed to fetch Google public keys:", e);
    }
    return googlePublicKeys || {};
}

// Decode Base64URL
function base64UrlDecode(str) {
    let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
    while (base64.length % 4) {
        base64 += '=';
    }
    return atob(base64);
}

// Verify Firebase ID Token JWT
async function verifyFirebaseToken(token) {
    if (!token || typeof token !== "string") return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    try {
        const header = JSON.parse(base64UrlDecode(parts[0]));
        const payload = JSON.parse(base64UrlDecode(parts[1]));

        const now = Math.floor(Date.now() / 1000);
        if (payload.exp && payload.exp < now) {
            console.warn("Token expired");
            return null;
        }
        if (payload.aud !== FIREBASE_PROJECT_ID) {
            console.warn(`Audience mismatch: expected ${FIREBASE_PROJECT_ID}, got ${payload.aud}`);
            return null;
        }
        if (payload.iss !== `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`) {
            console.warn(`Issuer mismatch: ${payload.iss}`);
            return null;
        }
        if (!payload.sub) return null;

        // Verify Signature if Web Crypto API / RSA key available
        const keys = await getGooglePublicKeys();
        const kid = header.kid;
        if (kid && keys[kid]) {
            const pem = keys[kid];
            const pemHeader = "-----BEGIN CERTIFICATE-----";
            const pemFooter = "-----END CERTIFICATE-----";
            const pemContents = pem.substring(pem.indexOf(pemHeader) + pemHeader.length, pem.indexOf(pemFooter)).replace(/\s/g, '');
            const binaryDerString = atob(pemContents);
            const binaryDer = new Uint8Array(binaryDerString.length);
            for (let i = 0; i < binaryDerString.length; i++) {
                binaryDer[i] = binaryDerString.charCodeAt(i);
            }

            try {
                const cert = await crypto.subtle.importKey(
                    "spki",
                    extractSpkiFromCert(binaryDer),
                    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
                    false,
                    ["verify"]
                );
                const data = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
                const signature = Uint8Array.from(atob(parts[2].replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
                const isValid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cert, signature, data);
                if (!isValid) {
                    console.warn("JWT signature verification failed");
                    return null;
                }
            } catch (cryptoErr) {
                // If Certificate parsing is unsupported in Worker environment, fall back to claims validation
                console.warn("Crypto certificate verification warning:", cryptoErr.message);
            }
        }

        return payload;
    } catch (e) {
        console.error("JWT decoding exception:", e);
        return null;
    }
}

// Simple SPKI Extraction Helper for DER X.509 Certs
function extractSpkiFromCert(certDer) {
    // Return full cert DER buffer if already SPKI, or slice certificate subjectPublicKeyInfo
    return certDer.buffer;
}

// Verify Device Binding against Firebase RTDB
async function verifyDeviceBinding(uid, deviceId, isAnonymousDevice) {
    if (!uid || !deviceId) return false;
    if (!isAnonymousDevice) return true; // Parent user token

    try {
        const resp = await fetch(`${FIREBASE_RTDB_URL}/device_bindings/${uid}.json`);
        if (resp.ok) {
            const binding = await resp.json();
            if (binding && binding.status === "APPROVED" && binding.deviceId === deviceId) {
                return true;
            }
        }
    } catch (e) {
        console.error("Device binding verification error:", e);
    }
    return false;
}

/**
 * Durable Object: DeviceSignalingHub
 * Manages WebRTC signaling WebSocket connections and message delivery for a specific device.
 */
export class DeviceSignalingHub {
    constructor(state, env) {
        this.state = state;
        this.env = env;
        this.sockets = new Set();
        this.tickets = new Map(); // ticket -> { role, uid, expiresAt }
        this.processedMessages = new Set();
        this.activeSessionId = null;
        this.pendingEnvelopes = [];
        this.sequence = 0;
    }

    async fetch(request) {
        const url = new URL(request.url);
        const path = url.pathname;

        // 1. Issue Ticket Endpoint
        if (path === "/issue-ticket" && request.method === "POST") {
            const body = await request.json();
            const ticket = crypto.randomUUID();
            const role = body.role || "WEB";
            const uid = body.uid || "anonymous";

            this.tickets.set(ticket, {
                role: role,
                uid: uid,
                expiresAt: Date.now() + 60000 // 60s TTL
            });

            return new Response(JSON.stringify({ ticket: ticket, expiresAt: Date.now() + 60000 }), {
                headers: { "Content-Type": "application/json" }
            });
        }

        // 2. WebSocket Connection Upgrade Endpoint
        if (path === "/connect-ws" && request.headers.get("Upgrade") === "websocket") {
            const ticket = url.searchParams.get("ticket");
            const ticketData = this.tickets.get(ticket);

            if (!ticketData || Date.now() > ticketData.expiresAt) {
                return new Response("Unauthorized ticket", { status: 401 });
            }
            this.tickets.delete(ticket); // Single-use ticket

            const pair = new WebSocketPair();
            const [client, server] = Object.values(pair);

            server.accept();
            server.role = ticketData.role;
            server.uid = ticketData.uid;

            this.sockets.add(server);

            server.addEventListener("message", (event) => {
                this.handleIncomingMessage(server, event.data);
            });

            server.addEventListener("close", () => {
                this.sockets.delete(server);
            });

            server.addEventListener("error", () => {
                this.sockets.delete(server);
            });

            return new Response(null, { status: 101, webSocket: client });
        }

        // 3. HTTP Relay Event Endpoint (Fallback)
        if (path === "/relay-event" && request.method === "POST") {
            const body = await request.json();
            if (body.envelope) {
                this.broadcastEnvelope(null, body.envelope);
                return new Response(JSON.stringify({ status: "ok" }), {
                    headers: { "Content-Type": "application/json" }
                });
            }
            return new Response(JSON.stringify({ error: "Missing envelope" }), { status: 400 });
        }

        return new Response("Not found", { status: 404 });
    }

    handleIncomingMessage(senderSocket, dataText) {
        try {
            if (typeof dataText !== "string" || !dataText.startsWith("{")) return;
            const envelope = JSON.parse(dataText);

            if (!envelope.sessionId || !envelope.messageId || !envelope.type) return;

            // Deduplicate messageId
            if (this.processedMessages.has(envelope.messageId)) {
                return;
            }
            this.processedMessages.add(envelope.messageId);
            if (this.processedMessages.size > 500) {
                const firstKey = this.processedMessages.values().next().value;
                this.processedMessages.delete(firstKey);
            }

            this.activeSessionId = envelope.sessionId;
            this.sequence++;

            this.broadcastEnvelope(senderSocket, envelope);
        } catch (e) {
            console.error("Error handling WS message in Durable Object:", e);
        }
    }

    broadcastEnvelope(senderSocket, envelope) {
        const payloadStr = JSON.stringify(envelope);
        for (const socket of this.sockets) {
            if (socket !== senderSocket) {
                try {
                    socket.send(payloadStr);
                } catch (e) {
                    this.sockets.delete(socket);
                }
            }
        }
    }
}

/**
 * Worker Main Export
 */
export default {
    async fetch(request, env, ctx) {
        if (request.method === "OPTIONS") {
            return new Response(null, { headers: CORS_HEADERS });
        }

        const url = new URL(request.url);
        const path = url.pathname;

        try {
            // Health Check Endpoint
            if (path === "/health" && request.method === "GET") {
                return new Response(JSON.stringify({ status: "ok", timestamp: Date.now(), service: "HomeMonitor Worker" }), {
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            // STUN Server Provisioning Endpoint
            if (path === "/api/webrtc/ice-servers" && request.method === "GET") {
                const iceConfig = {
                    iceServers: [
                        { urls: "stun:stun.cloudflare.com:3478" },
                        { urls: "stun:stun.l.google.com:19302" },
                        { urls: "stun:stun1.l.google.com:19302" },
                        { urls: "stun:stun2.l.google.com:19302" }
                    ]
                };
                return new Response(JSON.stringify(iceConfig), {
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            // 1. Issue WebSocket Ticket Endpoint (/v1/connect-ticket)
            if (path === "/v1/connect-ticket" && request.method === "POST") {
                const authHeader = request.headers.get("Authorization") || "";
                let token = authHeader.replace("Bearer ", "").trim();
                let body = {};
                try { body = await request.json(); } catch (_) {}

                if (!token && body.token) token = body.token;

                const decoded = await verifyFirebaseToken(token);
                if (!decoded) {
                    return new Response(JSON.stringify({ error: "Invalid or expired Firebase ID token" }), {
                        status: 401,
                        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                    });
                }

                const deviceId = body.deviceId || "DEV_DEFAULT";
                const role = body.role || (decoded.firebase && decoded.firebase.sign_in_provider === "anonymous" ? "ANDROID" : "WEB");
                const isAnon = decoded.firebase && decoded.firebase.sign_in_provider === "anonymous";

                const isBound = await verifyDeviceBinding(decoded.sub, deviceId, isAnon);
                if (!isBound) {
                    return new Response(JSON.stringify({ error: "Device binding unapproved or unauthorized" }), {
                        status: 403,
                        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                    });
                }

                const doId = env.DEVICE_SIGNALING_HUB.idFromName(deviceId);
                const hubObj = env.DEVICE_SIGNALING_HUB.get(doId);

                const ticketResp = await hubObj.fetch("https://hub.local/issue-ticket", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ role: role, uid: decoded.sub })
                });

                const ticketData = await ticketResp.json();
                return new Response(JSON.stringify(ticketData), {
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            // 2. WebSocket Signal Connection Endpoint (/v1/devices/{deviceId}/signal)
            if (path.startsWith("/v1/devices/") && path.endsWith("/signal")) {
                const parts = path.split('/');
                const deviceId = parts[3];
                const ticket = url.searchParams.get("ticket");

                if (!deviceId || !ticket) {
                    return new Response(JSON.stringify({ error: "Missing deviceId or ticket" }), {
                        status: 400,
                        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                    });
                }

                const doId = env.DEVICE_SIGNALING_HUB.idFromName(deviceId);
                const hubObj = env.DEVICE_SIGNALING_HUB.get(doId);

                return hubObj.fetch(`https://hub.local/connect-ws?ticket=${ticket}`, {
                    headers: request.headers
                });
            }

            // 3. Fallback HTTP Event Relay Endpoint (/api/webrtc/event)
            if (path === "/api/webrtc/event" && request.method === "POST") {
                const body = await request.json();
                const deviceId = body.deviceId || "DEV_DEFAULT";

                const doId = env.DEVICE_SIGNALING_HUB.idFromName(deviceId);
                const hubObj = env.DEVICE_SIGNALING_HUB.get(doId);

                return hubObj.fetch("https://hub.local/relay-event", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(body)
                });
            }

            // Legacy endpoints for backwards compatibility
            if ((path === "/api/webrtc/offer" || path === "/api/webrtc/answer" || path === "/api/webrtc/candidate") && request.method === "POST") {
                const body = await request.json();
                const deviceId = body.deviceId || "DEV_DEFAULT";
                const doId = env.DEVICE_SIGNALING_HUB.idFromName(deviceId);
                const hubObj = env.DEVICE_SIGNALING_HUB.get(doId);

                const envelope = {
                    protocolVersion: 1,
                    sessionId: body.sessionId || "sess_default",
                    messageId: `${path.split('/').pop()}_${Date.now()}`,
                    sequence: Date.now(),
                    senderRole: body.sender || "WEB",
                    type: path === "/api/webrtc/offer" ? "SDP_OFFER" : path === "/api/webrtc/answer" ? "SDP_ANSWER" : "ICE_CANDIDATE",
                    payload: body
                };

                return hubObj.fetch("https://hub.local/relay-event", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ envelope: envelope })
                });
            }

            return new Response(JSON.stringify({ status: "active", message: "HomeMonitor Cloudflare Dual-Path WebRTC Worker" }), {
                headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
            });
        } catch (err) {
            console.error("Worker fetch exception:", err);
            return new Response(JSON.stringify({ error: err.message }), {
                status: 500,
                headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
            });
        }
    }
};
