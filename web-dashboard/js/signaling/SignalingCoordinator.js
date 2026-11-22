// SignalingCoordinator.js - Browser Dual-Path WebRTC Signaling Coordinator

class SignalingCoordinator {
    constructor() {
        this.firebaseProvider = new FirebaseSignalingProvider();
        this.cloudflareProvider = new CloudflareSignalingProvider();

        this.listener = null;
        this.processedMessages = new Set();
        this.sequence = 0;
        this.activeSessionId = null;
        this.parentUid = null;
        this.deviceId = null;
    }

    setListener(listener) {
        this.listener = listener;
    }

    start(parentUid, deviceId, sessionId) {
        this.parentUid = parentUid;
        this.deviceId = deviceId;
        this.activeSessionId = sessionId;
        this.processedMessages.clear();
        this.sequence = 0;

        const handler = {
            onEnvelopeReceived: (envelope) => this.handleIncomingEnvelope(envelope),
            onConnected: () => {
                console.log("[SignalingCoordinator] Cloudflare hot fallback path connected 🟢");
            },
            onDisconnected: () => {
                console.warn("[SignalingCoordinator] Cloudflare hot fallback path disconnected");
            }
        };

        this.firebaseProvider.setListener(handler);
        this.cloudflareProvider.setListener(handler);

        this.firebaseProvider.startListening(parentUid, deviceId, sessionId);
        this.cloudflareProvider.start(deviceId, sessionId);
    }

    stop() {
        this.firebaseProvider.stopListening();
        this.cloudflareProvider.stop();
        this.processedMessages.clear();
        this.activeSessionId = null;
    }

    handleIncomingEnvelope(envelope) {
        if (!envelope || envelope.senderRole === "WEB" || envelope.senderRole === "DASHBOARD") {
            // Ignore self-published messages
            return;
        }

        if (envelope.sessionId && envelope.sessionId !== this.activeSessionId) {
            return;
        }

        if (envelope.messageId) {
            if (this.processedMessages.has(envelope.messageId)) {
                // Deduplicate second copy arriving via fallback provider
                return;
            }
            this.processedMessages.add(envelope.messageId);
            if (this.processedMessages.size > 500) {
                const firstKey = this.processedMessages.values().next().value;
                this.processedMessages.delete(firstKey);
            }
        }

        console.log(`[SignalingCoordinator] Inbound Envelope (${envelope.type}, ID: ${envelope.messageId})`);

        if (this.listener) {
            if (envelope.type === "SDP_ANSWER" && envelope.payload && envelope.payload.sdp) {
                if (typeof this.listener.onAnswerReceived === "function") {
                    this.listener.onAnswerReceived(envelope.payload.sdp);
                }
            } else if (envelope.type === "ICE_CANDIDATE" && envelope.payload && envelope.payload.candidate) {
                if (typeof this.listener.onIceCandidateReceived === "function") {
                    this.listener.onIceCandidateReceived(envelope.payload);
                }
            } else if (envelope.type === "SESSION_CLOSE") {
                if (typeof this.listener.onSessionClosed === "function") {
                    this.listener.onSessionClosed(envelope.sessionId);
                }
            }
        }
    }

    async postSessionRequest(sessionId) {
        this.sequence++;
        const envelope = {
            protocolVersion: 1,
            sessionId: sessionId,
            messageId: `req_${sessionId}_${Date.now()}`,
            sequence: this.sequence,
            senderRole: "WEB",
            type: "SESSION_REQUEST",
            createdAt: Date.now(),
            expiresAt: Date.now() + 60000,
            payload: { status: "REQUESTED", requestedCapabilities: ["SCREEN", "DATA"] }
        };
        await this.publishDual(envelope);
    }

    async postSdpOffer(sessionId, sdp) {
        this.sequence++;
        const envelope = {
            protocolVersion: 1,
            sessionId: sessionId,
            messageId: `off_${sessionId}_${Date.now()}`,
            sequence: this.sequence,
            senderRole: "WEB",
            type: "SDP_OFFER",
            createdAt: Date.now(),
            expiresAt: Date.now() + 60000,
            payload: { type: "offer", sdp: sdp, revision: 1 }
        };
        await this.publishDual(envelope);
    }

    async postIceCandidate(sessionId, candidate) {
        this.sequence++;
        const envelope = {
            protocolVersion: 1,
            sessionId: sessionId,
            messageId: `cand_${sessionId}_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
            sequence: this.sequence,
            senderRole: "WEB",
            type: "ICE_CANDIDATE",
            createdAt: Date.now(),
            expiresAt: Date.now() + 60000,
            payload: {
                senderRole: "WEB",
                sender: "DASHBOARD",
                candidate: candidate.candidate,
                sdpMid: candidate.sdpMid,
                sdpMLineIndex: candidate.sdpMLineIndex
            }
        };
        await this.publishDual(envelope);
    }

    async postSessionClose(sessionId) {
        this.sequence++;
        const envelope = {
            protocolVersion: 1,
            sessionId: sessionId,
            messageId: `close_${sessionId}_${Date.now()}`,
            sequence: this.sequence,
            senderRole: "WEB",
            type: "SESSION_CLOSE",
            createdAt: Date.now(),
            expiresAt: Date.now() + 60000,
            payload: { status: "TERMINATED" }
        };
        await this.publishDual(envelope);
    }

    async publishDual(envelope) {
        if (!this.parentUid || !this.deviceId) return;
        const fbRes = await this.firebaseProvider.publishEnvelope(this.parentUid, this.deviceId, envelope);
        const cfRes = await this.cloudflareProvider.publishEnvelope(envelope);
        console.log(`[SignalingCoordinator] Dual-Publish (${envelope.type}): Firebase=${fbRes}, Cloudflare=${cfRes}`);
    }
}
