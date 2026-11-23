/**
 * Cloudflare Worker: HomeMonitor WebRTC Dual-Path Edge Signaling Hub
 *
 * Implements high-availability WebRTC signaling over Cloudflare Durable Objects + WebSocket.
 * Features:
 * - Firebase Auth ID Token signature verification via Google Public JWKS Keys
 * - Device Binding & Parent Authorization checks
 * - Short-lived, single-use WebSocket tickets (/v1/connect-ticket)
 * - Durable Object DeviceSignalingHub for per-device session state, WebSocket routing, and persistent replay
 * - Envelope sequence tracking, event deduplication by messageId, and replay
 * - Safe CORS controls & Session cleanup
 */

const FIREBASE_PROJECT_ID = "homemonitor-db";
const FIREBASE_RTDB_URL = "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app";
const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

const CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Device-ID",
};

// Cached Google Public Keys (JWKS) for JWT verification
let cachedJwksKeys = null;
let keysExpiryTime = 0;

async function getGoogleJwksKeys() {
    const now = Date.now();
    if (cachedJwksKeys && now < keysExpiryTime) {
        return cachedJwksKeys;
    }
    try {
        const resp = await fetch(GOOGLE_JWKS_URL);
        if (resp.ok) {
            const data = await resp.json();
            cachedJwksKeys = data.keys || [];
            const cacheControl = resp.headers.get("cache-control") || "";
            const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
            const maxAgeSec = maxAgeMatch ? parseInt(maxAgeMatch[1], 10) : 3600;
            keysExpiryTime = now + (maxAgeSec * 1000);
            return cachedJwksKeys;
        }
    } catch (e) {
        console.error("Failed to fetch Google JWKS keys:", e);
    }
    return cachedJwksKeys || [];
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

        if (header.alg !== "RS256") {
            console.warn("Unsupported token algorithm:", header.alg);
            return null;
        }

        const now = Math.floor(Date.now() / 1000);
        if (payload.exp && payload.exp < now) {
            console.warn("Token expired");
            return null;
        }
        if (payload.iat && payload.iat > now + 300) {
            console.warn("Token issued in the future");
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
        if (!payload.sub || typeof payload.sub !== "string" || !payload.sub.trim()) {
            console.warn("Missing or empty token subject");
            return null;
        }

        // Verify Signature using Google JWKS
        const keys = await getGoogleJwksKeys();
        const jwkKey = keys.find(k => k.kid === header.kid);
        if (!jwkKey) {
            console.warn(`Key ID ${header.kid} not found in Google JWKS`);
            return null;
        }

        const cryptoKey = await crypto.subtle.importKey(
            "jwk",
            {
                kty: jwkKey.kty,
                alg: jwkKey.alg || "RS256",
                use: jwkKey.use || "sig",
                n: jwkKey.n,
                e: jwkKey.e,
                ext: true
            },
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
            false,
            ["verify"]
        );

        const dataBytes = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
        const signatureBytes = Uint8Array.from(atob(parts[2].replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
        const isValid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, signatureBytes, dataBytes);
        if (!isValid) {
            console.warn("JWT signature verification failed");
            return null;
        }

        return payload;
    } catch (e) {
        console.error("JWT decoding/verification exception:", e);
        return null;
    }
}

// Verify Device Binding & Parent Ownership against Firebase RTDB
async function verifyDeviceBinding(uid, deviceId, isAnonymousDevice) {
    if (!uid || !deviceId) return false;

    try {
        if (isAnonymousDevice) {
            // Anonymous Android Device Token -> Check device_bindings entry
            const resp = await fetch(`${FIREBASE_RTDB_URL}/device_bindings/${uid}.json`);
            if (resp.ok) {
                const binding = await resp.json();
                if (binding && binding.status === "APPROVED" && binding.deviceId === deviceId) {
                    return true;
                }
            }
        } else {
            // Non-anonymous Parent Token -> Check parent device ownership under /users/{uid}/devices/{deviceId}.json
            const resp = await fetch(`${FIREBASE_RTDB_URL}/users/${uid}/devices/${deviceId}.json`);
            if (resp.ok) {
                const devData = await resp.json();
                if (devData !== null && typeof devData === "object") {
                    return true;
                }
            }
        }
    } catch (e) {
        console.error("Device binding verification error:", e);
    }
    return false;
}

/**
 * Durable Object: DeviceSignalingHub
 * Manages WebRTC signaling WebSocket connections, message delivery, and persistent event storage per device.
 */
export class DeviceSignalingHub {
    constructor(state, env) {
        this.state = state;
        this.env = env;
        this.sockets = new Set();
        this.tickets = new Map(); // ticket -> { role, uid, expiresAt }
        this.processedMessages = new Set();
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

            // Replay unacknowledged, non-expired pending events destined for this connected role
            await this.replayPendingEvents(server);

            server.addEventListener("message", async (event) => {
                await this.handleIncomingMessage(server, event.data);
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
                const deliveryStatus = await this.processAndDeliverEnvelope(null, body.envelope);
                return new Response(JSON.stringify({ status: "ok", delivery: deliveryStatus, messageId: body.envelope.messageId }), {
                    headers: { "Content-Type": "application/json" }
                });
            }
            return new Response(JSON.stringify({ error: "Missing envelope" }), { status: 400 });
        }

        return new Response("Not found", { status: 404 });
    }

    async handleIncomingMessage(senderSocket, dataText) {
        try {
            if (typeof dataText !== "string" || !dataText.startsWith("{")) return;
            const envelope = JSON.parse(dataText);

            if (!envelope.sessionId || !envelope.messageId || !envelope.type) return;

            // Handle ACK message from receiver
            if (envelope.type === "MESSAGE_ACK" && envelope.payload && envelope.payload.ackMessageId) {
                const ackId = envelope.payload.ackMessageId;
                if (this.state.storage) {
                    await this.state.storage.delete(`event_${ackId}`);
                }
                return;
            }

            await this.processAndDeliverEnvelope(senderSocket, envelope);
        } catch (e) {
            console.error("Error handling WS message in Durable Object:", e);
        }
    }

    async processAndDeliverEnvelope(senderSocket, envelope) {
        const msgId = envelope.messageId;

        // Load processed set from storage if needed
        let storedProcessed = null;
        if (this.state.storage) {
            storedProcessed = await this.state.storage.get("processed_ids");
        }
        if (storedProcessed && Array.isArray(storedProcessed)) {
            storedProcessed.forEach(id => this.processedMessages.add(id));
        }

        if (this.processedMessages.has(msgId)) {
            return "DELIVERED_AND_ACKNOWLEDGED";
        }

        this.processedMessages.add(msgId);
        if (this.processedMessages.size > 500) {
            const firstKey = this.processedMessages.values().next().value;
            this.processedMessages.delete(firstKey);
        }
        if (this.state.storage) {
            await this.state.storage.put("processed_ids", Array.from(this.processedMessages));
        }

        const targetRole = envelope.senderRole === "ANDROID" ? "WEB" : "ANDROID";
        let deliveredToSocket = false;

        const payloadStr = JSON.stringify(envelope);
        for (const socket of this.sockets) {
            if (socket !== senderSocket && (socket.role === targetRole || socket.role === "DASHBOARD" || targetRole === "WEB")) {
                try {
                    socket.send(payloadStr);
                    deliveredToSocket = true;
                } catch (e) {
                    this.sockets.delete(socket);
                }
            }
        }

        if (deliveredToSocket) {
            return "DELIVERED_AND_ACKNOWLEDGED";
        } else {
            // Persist pending event in Durable Object storage for replay when recipient connects
            if (this.state.storage) {
                await this.state.storage.put(`event_${msgId}`, {
                    envelope: envelope,
                    recipientRole: targetRole,
                    createdAt: Date.now(),
                    acknowledged: false
                });
            }
            return "ACCEPTED_FOR_DELIVERY";
        }
    }

    async replayPendingEvents(targetSocket) {
        if (!this.state.storage) return;

        try {
            const keys = await this.state.storage.list({ prefix: "event_" });
            const now = Date.now();

            for (const [key, record] of keys) {
                if (!record || !record.envelope) continue;
                const env = record.envelope;

                // Expire stale events (> 60s)
                if (env.expiresAt && now > env.expiresAt) {
                    await this.state.storage.delete(key);
                    continue;
                }

                if (record.recipientRole === targetSocket.role || (targetSocket.role === "WEB" && record.recipientRole === "WEB")) {
                    try {
                        targetSocket.send(JSON.stringify(env));
                        // Mark acknowledged and delete from storage upon successful replay
                        await this.state.storage.delete(key);
                    } catch (e) {
                        console.warn("Replay send failed:", e);
                    }
                }
            }
        } catch (e) {
            console.error("Error replaying pending events:", e);
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
                        { urls: "stun:stun.l.google.com:19302" },
                        { urls: "stun:stun1.l.google.com:19302" },
                        { urls: "stun:stun2.l.google.com:19302" },
                        { urls: "stun:stun.relay.metered.ca:80" },
                        {
                            urls: "turn:relay.metered.ca:80?transport=udp",
                            username: "openrelayproject",
                            credential: "openrelayproject"
                        },
                        {
                            urls: "turn:relay.metered.ca:443?transport=tcp",
                            username: "openrelayproject",
                            credential: "openrelayproject"
                        },
                        {
                            urls: "turns:relay.metered.ca:443?transport=tcp",
                            username: "openrelayproject",
                            credential: "openrelayproject"
                        }
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
                const isAnon = decoded.firebase && decoded.firebase.sign_in_provider === "anonymous";
                const derivedRole = isAnon ? "ANDROID" : "WEB";

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
                    body: JSON.stringify({ role: derivedRole, uid: decoded.sub })
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
                const isAnon = decoded.firebase && decoded.firebase.sign_in_provider === "anonymous";
                const derivedRole = isAnon ? "ANDROID" : "WEB";

                const isBound = await verifyDeviceBinding(decoded.sub, deviceId, isAnon);
                if (!isBound) {
                    return new Response(JSON.stringify({ error: "Device binding unapproved or unauthorized" }), {
                        status: 403,
                        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                    });
                }

                if (body.envelope) {
                    // Enforce that envelope senderRole matches authenticated identity
                    if (body.envelope.senderRole && body.envelope.senderRole !== derivedRole && derivedRole === "ANDROID") {
                        return new Response(JSON.stringify({ error: "Sender role mismatch" }), {
                            status: 403,
                            headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                        });
                    }
                    body.envelope.senderRole = derivedRole;
                }

                const doId = env.DEVICE_SIGNALING_HUB.idFromName(deviceId);
                const hubObj = env.DEVICE_SIGNALING_HUB.get(doId);

                return hubObj.fetch("https://hub.local/relay-event", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(body)
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
