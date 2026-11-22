// FirebaseSignalingProvider.js - Firebase Realtime Database WebRTC Signaling Provider for Browser

class FirebaseSignalingProvider {
    constructor() {
        this.listener = null;
        this.activeRef = null;
    }

    setListener(listener) {
        this.listener = listener;
    }

    startListening(parentUid, deviceId, sessionId) {
        this.stopListening();
        if (!parentUid || !deviceId || !sessionId || !firebase || !firebase.database) return;

        this.activeRef = firebase.database().ref(`users/${parentUid}/devices/${deviceId}/webrtc_signaling/sessions/${sessionId}`);

        this.activeRef.on("value", snapshot => {
            const val = snapshot.val();
            if (!val) return;

            // Handle Answer Envelope
            if (val.answer) {
                const ansObj = val.answer;
                const sdpStr = (ansObj.payload && ansObj.payload.sdp) ? ansObj.payload.sdp : ansObj.sdp;
                if (sdpStr) {
                    const envelope = {
                        protocolVersion: ansObj.protocolVersion || 1,
                        sessionId: ansObj.sessionId || sessionId,
                        messageId: ansObj.messageId || `ans_${sessionId}`,
                        sequence: ansObj.sequence || Date.now(),
                        senderRole: ansObj.senderRole || "ANDROID",
                        type: ansObj.type || "SDP_ANSWER",
                        createdAt: ansObj.createdAt || Date.now(),
                        expiresAt: ansObj.expiresAt || (Date.now() + 60000),
                        payload: ansObj.payload || { type: "answer", sdp: sdpStr }
                    };
                    if (!envelope.payload.sdp) envelope.payload.sdp = sdpStr;
                    if (this.listener && typeof this.listener.onEnvelopeReceived === "function") {
                        this.listener.onEnvelopeReceived(envelope);
                    }
                }
            }

            // Handle Candidate Envelopes
            if (val.candidates) {
                Object.keys(val.candidates).forEach(key => {
                    const cand = val.candidates[key];
                    const sender = cand.senderRole || cand.sender;
                    if (sender !== "WEB" && sender !== "DASHBOARD") {
                        const candStr = (cand.payload && cand.payload.candidate) ? cand.payload.candidate : cand.candidate;
                        if (candStr) {
                            const envelope = {
                                protocolVersion: cand.protocolVersion || 1,
                                sessionId: cand.sessionId || sessionId,
                                messageId: cand.messageId || `cand_${sessionId}_${key}`,
                                sequence: cand.sequence || Date.now(),
                                senderRole: sender || "ANDROID",
                                type: cand.type || "ICE_CANDIDATE",
                                createdAt: cand.createdAt || Date.now(),
                                expiresAt: cand.expiresAt || (Date.now() + 60000),
                                payload: cand.payload || {
                                    candidate: candStr,
                                    sdpMid: cand.sdpMid || "0",
                                    sdpMLineIndex: cand.sdpMLineIndex !== undefined ? cand.sdpMLineIndex : 0
                                }
                            };
                            if (!envelope.payload.candidate) envelope.payload.candidate = candStr;
                            if (this.listener && typeof this.listener.onEnvelopeReceived === "function") {
                                this.listener.onEnvelopeReceived(envelope);
                            }
                        }
                    }
                });
            }

            if (val.status === "TERMINATED" || val.status === "EXPIRED") {
                const envelope = {
                    protocolVersion: 1,
                    sessionId: sessionId,
                    messageId: `close_${sessionId}`,
                    sequence: Date.now(),
                    senderRole: "ANDROID",
                    type: "SESSION_CLOSE",
                    payload: { status: val.status }
                };
                if (this.listener && typeof this.listener.onEnvelopeReceived === "function") {
                    this.listener.onEnvelopeReceived(envelope);
                }
            }
        });
    }

    stopListening() {
        if (this.activeRef) {
            try { this.activeRef.off(); } catch (_) {}
            this.activeRef = null;
        }
    }

    async publishEnvelope(parentUid, deviceId, envelope) {
        if (!parentUid || !deviceId || !envelope || !firebase || !firebase.database) return false;
        try {
            const baseUrl = `users/${parentUid}/devices/${deviceId}/webrtc_signaling/sessions/${envelope.sessionId}`;
            const ref = firebase.database().ref(baseUrl);

            if (envelope.type === "SESSION_REQUEST") {
                await ref.update({
                    status: "REQUESTED",
                    requestedCapabilities: ["SCREEN", "DATA"],
                    timestamp: Date.now(),
                    envelope: envelope
                });
            } else if (envelope.type === "SDP_OFFER") {
                await ref.child("offer").set(envelope);
            } else if (envelope.type === "ICE_CANDIDATE") {
                await ref.child("candidates").push(envelope);
            } else if (envelope.type === "SESSION_CLOSE") {
                await ref.update({
                    status: "TERMINATED",
                    terminatedAt: Date.now()
                });
            }
            return true;
        } catch (e) {
            console.error("[FirebaseSignalingProvider] Publish error:", e);
            return false;
        }
    }
}
