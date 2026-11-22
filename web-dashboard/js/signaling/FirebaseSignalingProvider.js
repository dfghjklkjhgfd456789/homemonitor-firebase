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
            if (val.answer && val.answer.sdp) {
                const msgId = val.answer.messageId || `ans_${sessionId}`;
                const envelope = {
                    protocolVersion: val.answer.protocolVersion || 1,
                    sessionId: sessionId,
                    messageId: msgId,
                    sequence: val.answer.sequence || Date.now(),
                    senderRole: val.answer.senderRole || "ANDROID",
                    type: "SDP_ANSWER",
                    payload: val.answer
                };
                if (this.listener && typeof this.listener.onEnvelopeReceived === "function") {
                    this.listener.onEnvelopeReceived(envelope);
                }
            }

            // Handle Candidate Envelopes
            if (val.candidates) {
                Object.keys(val.candidates).forEach(key => {
                    const cand = val.candidates[key];
                    const sender = cand.senderRole || cand.sender;
                    if (sender !== "WEB" && sender !== "DASHBOARD") {
                        const msgId = cand.messageId || `cand_${sessionId}_${key}`;
                        const envelope = {
                            protocolVersion: cand.protocolVersion || 1,
                            sessionId: sessionId,
                            messageId: msgId,
                            sequence: cand.sequence || Date.now(),
                            senderRole: sender || "ANDROID",
                            type: "ICE_CANDIDATE",
                            payload: cand
                        };
                        if (this.listener && typeof this.listener.onEnvelopeReceived === "function") {
                            this.listener.onEnvelopeReceived(envelope);
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
                    payload: {}
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
                await ref.child("offer").set({
                    type: "offer",
                    sdp: envelope.payload.sdp,
                    messageId: envelope.messageId,
                    senderRole: envelope.senderRole,
                    sequence: envelope.sequence,
                    revision: 1
                });
            } else if (envelope.type === "ICE_CANDIDATE") {
                await ref.child("candidates").push({
                    senderRole: envelope.senderRole,
                    sender: envelope.senderRole,
                    candidate: envelope.payload.candidate,
                    sdpMid: envelope.payload.sdpMid,
                    sdpMLineIndex: envelope.payload.sdpMLineIndex,
                    messageId: envelope.messageId,
                    sequence: envelope.sequence
                });
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
