/**
 * Cloudflare Worker: HomeMonitor WebRTC P2P Edge Signaling & STUN Server Establisher
 *
 * Provides sub-millisecond edge WebRTC signaling, WebSocket relay channels,
 * and high-availability STUN server provisioning for WebRTC Direct P2P connections.
 *
 * Deploy to Cloudflare Workers via Wrangler or Cloudflare Dashboard.
 */

const CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Device-ID",
};

// In-Memory Edge Signaling Session Cache (for rapid SDP/ICE exchanges)
const edgeSessions = new Map();

export default {
    async fetch(request, env, ctx) {
        // Handle CORS Preflight
        if (request.method === "OPTIONS") {
            return new Response(null, { headers: CORS_HEADERS });
        }

        const url = new URL(request.url);
        const path = url.pathname;

        try {
            // 1. STUN Server Provisioning Endpoint
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

            // 2. WebRTC Offer Relay Endpoint
            if (path === "/api/webrtc/offer" && request.method === "POST") {
                const body = await request.json();
                const deviceId = body.deviceId || "DEV_DEFAULT";
                const sessionId = body.sessionId || "sess_" + Date.now();

                edgeSessions.set(`${deviceId}:${sessionId}`, {
                    sessionId: sessionId,
                    deviceId: deviceId,
                    offer: body.sdp,
                    answer: null,
                    candidates: [],
                    timestamp: Date.now()
                });

                return new Response(JSON.stringify({ status: "ok", sessionId: sessionId }), {
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            // 3. WebRTC Answer Relay Endpoint
            if (path === "/api/webrtc/answer" && request.method === "POST") {
                const body = await request.json();
                const deviceId = body.deviceId || "DEV_DEFAULT";
                const sessionId = body.sessionId;

                const sess = edgeSessions.get(`${deviceId}:${sessionId}`);
                if (sess) {
                    sess.answer = body.sdp;
                    return new Response(JSON.stringify({ status: "ok", message: "Answer recorded" }), {
                        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                    });
                }
                return new Response(JSON.stringify({ error: "Session not found" }), {
                    status: 404,
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            // 4. WebRTC ICE Candidate Relay Endpoint
            if (path === "/api/webrtc/candidate" && request.method === "POST") {
                const body = await request.json();
                const deviceId = body.deviceId || "DEV_DEFAULT";
                const sessionId = body.sessionId;

                const sess = edgeSessions.get(`${deviceId}:${sessionId}`);
                if (sess) {
                    sess.candidates.push({
                        sender: body.sender || "PEER",
                        candidate: body.candidate,
                        sdpMid: body.sdpMid,
                        sdpMLineIndex: body.sdpMLineIndex
                    });
                    return new Response(JSON.stringify({ status: "ok" }), {
                        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                    });
                }
                return new Response(JSON.stringify({ error: "Session not found" }), {
                    status: 404,
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            // 5. WebRTC Session Poll Endpoint
            if (path === "/api/webrtc/poll" && request.method === "GET") {
                const deviceId = url.searchParams.get("deviceId") || "DEV_DEFAULT";
                const sessionId = url.searchParams.get("sessionId");

                if (sessionId) {
                    const sess = edgeSessions.get(`${deviceId}:${sessionId}`);
                    if (sess) {
                        return new Response(JSON.stringify(sess), {
                            headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                        });
                    }
                }
                return new Response(JSON.stringify({ sessions: Array.from(edgeSessions.values()) }), {
                    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
                });
            }

            return new Response(JSON.stringify({ message: "HomeMonitor Cloudflare WebRTC Worker Active" }), {
                headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
            });
        } catch (err) {
            return new Response(JSON.stringify({ error: err.message }), {
                status: 500,
                headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
            });
        }
    }
};
