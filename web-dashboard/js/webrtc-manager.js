// webrtc-manager.js - Browser Direct WebRTC P2P Manager (Dual-Path Signaling & DataChannel Protocol)

let pc = null;
let dc = null;
let activeSessionId = null;
let signalingCoordinator = null;
let p2pPingInterval = null;
let p2pFallbackTimer = null;
let lastCalculatedRtt = 0;
let pendingIceCandidates = [];

function updateWebRtcConnectionBadge(statusType, detailText = "") {
    const connBadge = document.getElementById("connectionModeBadge");
    const statusBadge = document.getElementById("p2pStatusBadge");
    const headerBadge = document.getElementById("p2pHeaderStatusBadge");

    let badgeClass = "badge bg-secondary text-white px-2 py-1 fw-bold";
    let badgeHtml = '<i class="fa-solid fa-circle-xmark me-1"></i>Offline 🔴';

    if (statusType === "CONNECTING") {
        badgeClass = "badge bg-warning text-dark px-2 py-1 fw-bold";
        badgeHtml = '<i class="fa-solid fa-spinner fa-spin me-1"></i>Connecting... 🟡';
    } else if (statusType === "P2P_DIRECT_CONNECTED" || statusType === "DATA_CHANNEL_READY") {
        badgeClass = "badge bg-success text-white px-2 py-1 fw-bold";
        const rttStr = detailText ? ` (${detailText}ms)` : "";
        badgeHtml = `<i class="fa-solid fa-network-wired me-1"></i>Direct P2P Connected 🟢${rttStr}`;
    } else if (statusType === "WEBSOCKET_RELAY_CONNECTED") {
        badgeClass = "badge bg-info text-dark px-2 py-1 fw-bold";
        badgeHtml = `<i class="fa-solid fa-cloud-bolt me-1"></i>WebSocket Relay Connected 🔵`;
    } else if (statusType === "ICE_FAILED" || statusType === "CONNECTION_FAILED" || statusType === "DATA_CHANNEL_FAILED" || statusType === "DEVICE_UNREACHABLE") {
        if (typeof cloudflareRelayClient !== "undefined" && cloudflareRelayClient && cloudflareRelayClient.isConnected) {
            badgeClass = "badge bg-info text-dark px-2 py-1 fw-bold";
            badgeHtml = `<i class="fa-solid fa-cloud-bolt me-1"></i>WebSocket Relay Connected 🔵`;
        } else {
            badgeClass = "badge bg-danger text-white px-2 py-1 fw-bold";
            badgeHtml = '<i class="fa-solid fa-circle-xmark me-1"></i>Offline 🔴';
        }
    } else if (statusType === "DISCONNECTED") {
        if (typeof cloudflareRelayClient !== "undefined" && cloudflareRelayClient && cloudflareRelayClient.isConnected) {
            badgeClass = "badge bg-info text-dark px-2 py-1 fw-bold";
            badgeHtml = `<i class="fa-solid fa-cloud-bolt me-1"></i>WebSocket Relay Connected 🔵`;
        } else {
            badgeClass = "badge bg-secondary text-white px-2 py-1 fw-bold";
            badgeHtml = '<i class="fa-solid fa-circle-xmark me-1"></i>Offline 🔴';
        }
    }

    if (headerBadge) {
        headerBadge.className = badgeClass;
        headerBadge.innerHTML = badgeHtml;
    }
    if (connBadge) {
        connBadge.className = badgeClass;
        connBadge.innerHTML = badgeHtml;
    }
    if (statusBadge) {
        statusBadge.className = badgeClass;
        statusBadge.innerHTML = badgeHtml;
    }
}

function getActiveRealDeviceId() {
    if (typeof activeDeviceId === "undefined" || !activeDeviceId) return "";
    if (typeof discoveredDevices !== "undefined" && discoveredDevices[activeDeviceId]) {
        const devObj = discoveredDevices[activeDeviceId];
        if (devObj.deviceId) return devObj.deviceId;
        if (devObj.status && devObj.status.deviceId) return devObj.status.deviceId;
    }
    return activeDeviceId;
}

function connectP2pWebRtcTunnel() {
    if (pc && (pc.iceConnectionState === "connected" || pc.iceConnectionState === "checking" || pc.connectionState === "connected")) {
        console.log("[WebRTC Manager] WebRTC PeerConnection is already active or checking. Skipping duplicate request.");
        return;
    }
    const devId = getActiveRealDeviceId();
    const sessionId = "sess_" + devId + "_" + Date.now();
    console.log(`[WebRTC Manager] Requesting Direct P2P WebRTC Tunnel for device '${devId}' (Session: ${sessionId})`);
    updateWebRtcConnectionBadge("CONNECTING");
    initWebRtcSession(sessionId);
}

function initWebRtcSession(sessionId) {
    activeSessionId = sessionId;
    pendingIceCandidates = [];
    console.log(`[WebRTC Manager] Initializing Sole Offerer Session: ${sessionId}`);

    const devId = getActiveRealDeviceId();
    const parentUid = (typeof currentUser !== "undefined" && currentUser) ? currentUser.uid : "";

    // Start Standalone Cloudflare Worker WebSocket Relay Client ($0 Cost, Zero KV)
    if (typeof cloudflareRelayClient !== "undefined" && cloudflareRelayClient) {
        cloudflareRelayClient.start(devId);
    }

    // Fast P2P-Preferring Fallback Timer (3.5s timeout)
    if (p2pFallbackTimer) clearTimeout(p2pFallbackTimer);
    p2pFallbackTimer = setTimeout(() => {
        if (!dc || dc.readyState !== "open") {
            console.warn("[WebRTC Manager] Direct P2P connection establishing... Activating WebSocket Relay Fallback 🔵");
            if (typeof cloudflareRelayClient !== "undefined" && cloudflareRelayClient && cloudflareRelayClient.isConnected) {
                updateWebRtcConnectionBadge("WEBSOCKET_RELAY_CONNECTED");
            }
        }
    }, 3500);

    if (pc) {
        try { pc.close(); } catch (_) {}
    }

    // Free Google STUN + OpenRelay TURN ICE Configuration ($0/month, 4G/5G Carrier NAT Traversal)
    const rtcConfig = {
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

    pc = new RTCPeerConnection(rtcConfig);

    // Browser creates DataChannel (c2_datachannel)
    dc = pc.createDataChannel("c2_datachannel", { ordered: true });
    setupDataChannelEvents(dc);

    // Initialize Dual-Path Signaling Coordinator
    if (signalingCoordinator) {
        signalingCoordinator.stop();
    }
    signalingCoordinator = new SignalingCoordinator();
    signalingCoordinator.setListener({
        onAnswerReceived: (sdp) => handleRemoteAnswerSignal(sdp),
        onIceCandidateReceived: (candPayload) => addRemoteCandidateOrBuffer(candPayload),
        onSessionClosed: (sessId) => {
            console.log("[WebRTC Manager] Session closed by remote peer:", sessId);
            stopP2pWebStreamSession();
        }
    });

    signalingCoordinator.start(parentUid, devId, sessionId);

    pc.onicecandidate = ({ candidate }) => {
        if (candidate) {
            signalingCoordinator.postIceCandidate(sessionId, candidate);
        }
    };

    pc.ontrack = (event) => {
        console.log("[WebRTC Manager] Received Remote Media Track", event.streams[0]);
        const videoElement = document.getElementById("webstreamPlayer");
        if (videoElement && event.streams[0]) {
            videoElement.srcObject = event.streams[0];
            videoElement.play().catch(e => console.warn("Video play error:", e));
        }
    };

    pc.oniceconnectionstatechange = () => {
        console.log("[WebRTC Manager] ICE Connection State:", pc.iceConnectionState);
        if (pc.iceConnectionState === "checking" || pc.iceConnectionState === "new") {
            updateWebRtcConnectionBadge("CONNECTING");
        } else if (pc.iceConnectionState === "connected" || pc.iceConnectionState === "completed") {
            verifySelectedIceCandidatePair();
        } else if (pc.iceConnectionState === "disconnected" || pc.iceConnectionState === "failed") {
            console.warn("[WebRTC Manager] Direct P2P PeerConnection failed.");
            updateWebRtcConnectionBadge("ICE_FAILED");
        }
    };

    pc.onnegotiationneeded = async () => {
        try {
            console.log("[WebRTC Manager] Generating SDP Offer...");
            const offer = await pc.createOffer();
            await pc.setLocalDescription(offer);
            await signalingCoordinator.postSessionRequest(sessionId);
            await signalingCoordinator.postSdpOffer(sessionId, pc.localDescription.sdp);
            console.log("[WebRTC Manager] Local Offer set and published via dual providers.");
        } catch (err) {
            console.error("[WebRTC Manager] Offer creation error:", err);
            updateWebRtcConnectionBadge("ICE_FAILED");
        }
    };
}

async function handleRemoteAnswerSignal(answerSdp) {
    if (!pc) return;
    try {
        if (pc.signalingState === "have-local-offer") {
            const answerDesc = new RTCSessionDescription({ type: "answer", sdp: answerSdp });
            await pc.setRemoteDescription(answerDesc);
            console.log("[WebRTC Manager] Installed Remote Answer SDP successfully 🟢");
            await drainPendingIceCandidates();
        }
    } catch (err) {
        console.error("[WebRTC Manager] Error installing remote answer:", err);
    }
}

function addRemoteCandidateOrBuffer(candData) {
    if (!candData || !candData.candidate) return;
    try {
        const rtcCand = new RTCIceCandidate({
            candidate: candData.candidate,
            sdpMid: candData.sdpMid || "0",
            sdpMLineIndex: candData.sdpMLineIndex !== undefined ? candData.sdpMLineIndex : 0
        });

        if (pc && pc.remoteDescription && pc.remoteDescription.type) {
            pc.addIceCandidate(rtcCand).catch(e => console.warn("[WebRTC Candidate Add Error]", e));
        } else {
            pendingIceCandidates.push(rtcCand);
            console.log("[WebRTC Candidate Buffered] Queued remote candidate prior to remoteDescription");
        }
    } catch (e) {
        console.warn("addRemoteCandidateOrBuffer error:", e);
    }
}

async function drainPendingIceCandidates() {
    if (!pc || !pc.remoteDescription || pendingIceCandidates.length === 0) return;
    console.log(`[WebRTC Candidate Drain] Flushing ${pendingIceCandidates.length} buffered ICE candidates...`);
    for (const cand of pendingIceCandidates) {
        try {
            await pc.addIceCandidate(cand);
        } catch (e) {
            console.warn("[WebRTC Buffered Candidate Error]", e);
        }
    }
    pendingIceCandidates = [];
}

function verifySelectedIceCandidatePair() {
    if (!pc || typeof pc.getStats !== "function") {
        updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
        return;
    }

    const currentSessId = activeSessionId;

    pc.getStats(null).then(stats => {
        if (activeSessionId !== currentSessId) return;

        let candidateInfo = "unknown";
        stats.forEach(report => {
            if (report.type === "candidate-pair" && report.state === "succeeded") {
                const localCand = stats.get(report.localCandidateId);
                const remoteCand = stats.get(report.remoteCandidateId);
                const localType = localCand ? localCand.candidateType : "unknown";
                const remoteType = remoteCand ? remoteCand.candidateType : "unknown";
                candidateInfo = `local:${localType}, remote:${remoteType}`;
            }
        });

        console.log(`[WebRTC Manager Diagnostic] Active candidate pair: ${candidateInfo}`);
        if (dc && dc.readyState === "open") {
            updateWebRtcConnectionBadge("DATA_CHANNEL_READY");
        } else {
            updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
        }
    }).catch(_ => {
        if (activeSessionId !== currentSessId) return;
        updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
    });
}

function setupDataChannelEvents(channel) {
    try {
        channel.bufferedAmountLowThreshold = 131072;
    } catch (_) {}

    channel.onopen = () => {
        if (p2pFallbackTimer) {
            clearTimeout(p2pFallbackTimer);
            p2pFallbackTimer = null;
        }
        console.log("[WebRTC Manager] Direct P2P RTCDataChannel OPEN 🟢 (Retaining Direct P2P Channel)");
        verifySelectedIceCandidatePair();
        updateWebRtcConnectionBadge("DATA_CHANNEL_READY");

        if (p2pPingInterval) clearInterval(p2pPingInterval);
        p2pPingInterval = setInterval(() => {
            if (dc && dc.readyState === "open") {
                const pingPayload = {
                    version: "1.0",
                    type: "PING",
                    timestamp: Date.now()
                };
                dc.send(JSON.stringify(pingPayload));
            }
        }, 4000);
    };

    channel.onclose = () => {
        console.log("[WebRTC Manager] Direct P2P RTCDataChannel CLOSED");
        if (p2pPingInterval) clearInterval(p2pPingInterval);
        updateWebRtcConnectionBadge("DISCONNECTED");
    };

    channel.onerror = (err) => {
        console.error("[WebRTC Manager] DataChannel error:", err);
        updateWebRtcConnectionBadge("DATA_CHANNEL_FAILED");
    };

    channel.onmessage = (event) => {
        try {
            const data = typeof event.data === "string" ? JSON.parse(event.data) : JSON.parse(new TextDecoder().decode(event.data));

            // Forward to HomeMonitorAPI client
            if (typeof HomeMonitorAPI !== "undefined" && HomeMonitorAPI && typeof HomeMonitorAPI.handleIncomingEnvelope === "function") {
                HomeMonitorAPI.handleIncomingEnvelope(data);
            }

            if (data.type === "EVENT" && (data.topic === "bootstrap.telemetry" || data.topic === "bootstrap")) {
                if (typeof renderOverviewDashboard === "function") renderOverviewDashboard(data.data || data.payload);
                if (typeof renderGpsLocation === "function" && (data.data?.latitude || data.payload?.latitude)) renderGpsLocation(data.data || data.payload);
            } else if (data.type === "PONG") {
                const sentTs = data.payload?.timestamp || data.timestamp;
                if (sentTs) {
                    lastCalculatedRtt = Math.max(1, Date.now() - sentTs);
                    updateWebRtcConnectionBadge("DATA_CHANNEL_READY", `${lastCalculatedRtt}ms`);
                }
            } else if ((data.type === "RPC_RESPONSE" || data.type === "API_RESPONSE") && (data.requestId || (data.payload && data.payload.requestId))) {
                const reqId = data.requestId || data.payload.requestId;
                if (apiPendingCallbacks.has(reqId)) {
                    const { resolve } = apiPendingCallbacks.get(reqId);
                    apiPendingCallbacks.delete(reqId);
                    resolve(data.data || data.payload?.data || data);
                }
            } else if (data.type === "SCREEN_FRAME" && data.payload && data.payload.frame) {
                renderSilentScreencapFrame(data.payload.frame);
            } else if (data.type === "DB_RESPONSE_SOCIAL" && data.payload && data.payload.messages) {
                if (typeof handleP2pSocialDatabaseResponse === "function") {
                    handleP2pSocialDatabaseResponse(data.payload.appKey, data.payload.messages);
                }
            } else if (data.type === "DB_RESPONSE_TELEMETRY" && data.payload && data.payload.logs) {
                if (typeof handleP2pTelemetryDatabaseResponse === "function") {
                    handleP2pTelemetryDatabaseResponse(data.payload.category, data.payload.logs);
                }
            }
        } catch (e) {
            console.log("[WebRTC Manager] P2P Message Received:", event.data);
        }
    };
}

const apiPendingCallbacks = new Map();

function sendNativeRpcRequest(action, params = {}) {
    return new Promise((resolve, reject) => {
        const requestId = "req_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
        const targetDeviceId = getActiveRealDeviceId();

        apiPendingCallbacks.set(requestId, { resolve, reject, action });

        const reqPayload = {
            version: "1.0",
            type: "RPC_REQUEST",
            requestId: requestId,
            deviceId: targetDeviceId,
            action: action,
            params: params,
            timestamp: Date.now()
        };

        if (dc && dc.readyState === "open") {
            dc.send(JSON.stringify(reqPayload));
            console.log(`[WebRTC Direct P2P RPC] Transmitted native action '${action}' (${requestId})`);
        } else {
            sendFirebaseNativeActionFallback(action, params, requestId)
                .then(res => resolve(res))
                .catch(err => reject(err));
        }

        setTimeout(() => {
            if (apiPendingCallbacks.has(requestId)) {
                apiPendingCallbacks.delete(requestId);
                reject(new Error(`Direct P2P RPC Timeout for action '${action}' (15s)`));
            }
        }, 15000);
    });
}

function sendFirebaseNativeActionFallback(action, params, requestId) {
    return new Promise((resolve, reject) => {
        if (!currentUser || !currentUser.uid) {
            reject(new Error("No active parent user."));
            return;
        }
        const uid = currentUser.uid;
        const devId = getActiveRealDeviceId();
        const actionRef = firebase.database().ref(`users/${uid}/devices/${devId}/actions/${requestId}`);

        const actionPayload = {
            requestId: requestId,
            action: action,
            params: params,
            status: "PENDING",
            timestamp: Date.now()
        };

        actionRef.set(actionPayload).then(() => {
            const responseListener = actionRef.on("value", snapshot => {
                const val = snapshot.val();
                if (val && val.status === "COMPLETED") {
                    actionRef.off("value", responseListener);
                    resolve(val.response || val);
                }
            });
        }).catch(err => reject(err));
    });
}

function sendWebDashboardApiRequest(path, method = "GET", body = null) {
    return new Promise((resolve, reject) => {
        const requestId = "req_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
        const targetDeviceId = getActiveRealDeviceId();

        if (!dc || dc.readyState !== "open") {
            console.warn(`[WebRTC Direct P2P API] Failed: DataChannel is not OPEN for ${method} ${path}`);
            updateWebRtcConnectionBadge("DATA_CHANNEL_FAILED");
            reject(new Error("P2P DataChannel is not connected (DATA_CHANNEL_FAILED). Interactive operations require direct P2P connection."));
            return;
        }

        apiPendingCallbacks.set(requestId, { resolve, reject });

        const reqPayload = {
            version: "1.0",
            type: "API_REQUEST",
            requestId: requestId,
            deviceId: targetDeviceId,
            path: path,
            method: method,
            body: typeof body === "object" && body !== null ? JSON.stringify(body) : (body || ""),
            timestamp: Date.now()
        };

        dc.send(JSON.stringify(reqPayload));
        console.log(`[WebRTC Direct P2P API] Transmitted: ${method} ${path} (${requestId})`);

        setTimeout(() => {
            if (apiPendingCallbacks.has(requestId)) {
                apiPendingCallbacks.delete(requestId);
                reject(new Error("Direct P2P Request Timeout (15s)"));
            }
        }, 15000);
    });
}

function queryP2pLocalDatabase(queryType, keyParam) {
    if (dc && dc.readyState === "open") {
        const targetDeviceId = getActiveRealDeviceId();
        const payload = queryType === "SOCIAL" ?
            { version: "1.0", type: "DB_QUERY_SOCIAL", deviceId: targetDeviceId, appKey: keyParam, timestamp: Date.now() } :
            { version: "1.0", type: "DB_QUERY_TELEMETRY", deviceId: targetDeviceId, category: keyParam, timestamp: Date.now() };
        dc.send(JSON.stringify(payload));
        console.log(`[WebRTC Direct P2P DB Query] Transmitted: ${queryType} (${keyParam})`);
    }
}

function renderSilentScreencapFrame(base64Frame) {
    const canvas = document.getElementById("liveScreenCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const img = new Image();
    img.onload = () => {
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    };
    img.src = base64Frame.startsWith("data:") ? base64Frame : `data:image/jpeg;base64,${base64Frame}`;
}

function startP2pWebStreamSession() {
    connectP2pWebRtcTunnel();
}

function stopP2pWebStreamSession() {
    console.log("[WebRTC Manager] Stopping WebRTC Direct P2P Session:", activeSessionId);
    try {
        if (signalingCoordinator && activeSessionId) {
            signalingCoordinator.postSessionClose(activeSessionId);
            signalingCoordinator.stop();
            signalingCoordinator = null;
        }
        if (dc) { try { dc.close(); } catch (_) {} dc = null; }
        if (pc) { try { pc.close(); } catch (_) {} pc = null; }
    } catch (e) {
        console.warn("[WebRTC Teardown Error]", e);
    } finally {
        activeSessionId = null;
        pendingIceCandidates = [];
        updateWebRtcConnectionBadge("DISCONNECTED");
        const statusBadge = document.getElementById("p2pStatusBadge");
        if (statusBadge) statusBadge.innerText = "P2P Session Idle";
    }
}
