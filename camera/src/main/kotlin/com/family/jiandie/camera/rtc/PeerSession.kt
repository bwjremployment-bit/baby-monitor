package com.family.jiandie.camera.rtc

import android.os.Handler
import android.os.Looper
import com.family.jiandie.camera.data.Quality
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * One send-only WebRTC session towards a viewer. The camera is always the
 * offerer. Callbacks are delivered on the main thread. Closing a session does
 * not touch the camera: [CameraEngine] owns capture and the tracks.
 */
class PeerSession(
    private val engine: CameraEngine,
    private val iceServers: List<PeerConnection.IceServer>,
    private val quality: Quality,
    private val sendAudio: Boolean,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onOffer(sdp: String)
        fun onLocalIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var pc: PeerConnection? = null
    private var remoteSet = false
    private val pendingRemote = mutableListOf<IceCandidate>()
    private var closed = false

    fun start() {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val connection = engine.factory.createPeerConnection(config, observer)
        if (connection == null) { callbacks.onError("Could not create the connection"); return }
        pc = connection

        val video = engine.videoTrack
        if (video == null) { callbacks.onError("Camera is not running"); return }
        val sendOnly = RtpTransceiver.RtpTransceiverInit(
            RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf("jiandie"),
        )
        capBitrate(connection.addTransceiver(video, sendOnly).sender)
        if (sendAudio) engine.audioTrack?.let { connection.addTransceiver(it, sendOnly) }

        connection.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                connection.setLocalDescription(object : SdpAdapter() {
                    override fun onSetSuccess() { main.post { if (!closed) callbacks.onOffer(description.description) } }
                    override fun onSetFailure(error: String?) { main.post { fail("Local description failed: $error") } }
                }, description)
            }
            override fun onCreateFailure(error: String?) { main.post { fail("Could not create offer: $error") } }
        }, MediaConstraints())
    }

    fun onRemoteAnswer(sdp: String) {
        val connection = pc ?: return
        connection.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                main.post {
                    if (closed) return@post
                    remoteSet = true
                    pendingRemote.forEach { connection.addIceCandidate(it) }
                    pendingRemote.clear()
                }
            }
            override fun onSetFailure(error: String?) { main.post { fail("Remote description failed: $error") } }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun onRemoteIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        val c = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (remoteSet) pc?.addIceCandidate(c) else pendingRemote += c
    }

    /**
     * Describes the path the media actually takes, e.g. "direct · IPv6 · udp"
     * or "via relay (TURN)". This answers empirically whether a relay is needed.
     */
    fun describePath(onResult: (String) -> Unit) {
        val connection = pc ?: return onResult("no connection")
        connection.getStats { report ->
            val stats = report.statsMap
            val pair = stats.values.firstOrNull {
                it.type == "candidate-pair" && it.members["nominated"] == true && it.members["state"] == "succeeded"
            }
            if (pair == null) { main.post { onResult("not connected yet") }; return@getStats }
            fun side(key: String): Pair<String, Boolean> {
                val c = stats[pair.members[key] as? String]
                val type = c?.members?.get("candidateType") as? String ?: "?"
                val addr = (c?.members?.get("address") ?: c?.members?.get("ip")) as? String ?: ""
                return type to addr.contains(':')
            }
            val (local, localV6) = side("localCandidateId")
            val (remote, remoteV6) = side("remoteCandidateId")
            val text = if (local == "relay" || remote == "relay") "via relay (TURN)"
            else "direct · ${if (localV6 && remoteV6) "IPv6" else "IPv4"} · $local/$remote"
            main.post { onResult(text) }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        pendingRemote.clear()
        pc?.let { runCatching { it.close() }; runCatching { it.dispose() } }
        pc = null
    }

    private fun fail(message: String) { if (!closed) callbacks.onError(message) }

    private fun capBitrate(sender: RtpSender) {
        val p = sender.parameters
        for (e in p.encodings) {
            e.maxBitrateBps = quality.maxKbps * 1000
            e.maxFramerate = quality.fps
        }
        sender.setParameters(p)
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            main.post { if (!closed) callbacks.onLocalIce(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp) }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            main.post { if (!closed) callbacks.onConnectionState(newState) }
        }
    }

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
