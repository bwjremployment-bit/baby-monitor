package com.family.monitor.shared.signaling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The two roles that can join a signalling room. */
@Serializable
enum class Role {
    @SerialName("camera") CAMERA,
    @SerialName("viewer") VIEWER,
}

/** One ICE candidate, transport-agnostic. */
@Serializable
data class IceCandidate(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String,
)

/**
 * Messages exchanged over the signalling WebSocket. Media never flows through
 * here — only connection setup (SDP + ICE), auth, and lifecycle. The JSON wire
 * form carries a "type" discriminator (see [SignalCodec]).
 */
@Serializable
sealed class SignalMessage {
    /**
     * Client -> server: request to enter [room] as [role]. No credential here:
     * the relay is untrusted and cannot verify one. Peers authenticate each
     * other end-to-end (see PairingCrypto): [StartRequest.token] and the
     * [Offer.auth] / [Answer.auth] MACs.
     */
    @Serializable
    @SerialName("join")
    data class Join(val room: String, val role: Role) : SignalMessage()

    /** Server -> client: join accepted; [peers] are the roles already present. */
    @Serializable
    @SerialName("joined")
    data class Joined(val peers: List<Role> = emptyList()) : SignalMessage()

    /** Server -> client: join rejected (malformed room/role, role already taken, etc.). */
    @Serializable
    @SerialName("auth-error")
    data class AuthError(val reason: String) : SignalMessage()

    /** Server -> client: the other peer joined or left. */
    @Serializable
    @SerialName("peer")
    data class PeerEvent(val role: Role, val present: Boolean) : SignalMessage()

    /** Camera -> viewer. [auth] = PairingCrypto.signSdp(...) by the camera over [sdp]. */
    @Serializable
    @SerialName("offer")
    data class Offer(val sdp: String, val auth: String) : SignalMessage()

    /** Viewer -> camera. [auth] = PairingCrypto.signSdp(...) by the viewer over [sdp]. */
    @Serializable
    @SerialName("answer")
    data class Answer(val sdp: String, val auth: String) : SignalMessage()

    @Serializable
    @SerialName("ice")
    data class Ice(val candidate: IceCandidate) : SignalMessage()

    /**
     * Viewer -> camera: ask an armed camera to begin transmitting (Q3 remote
     * start). [token] = PairingCrypto.deriveToken(.., Role.VIEWER, now); the
     * camera verifies it BEFORE turning on camera/microphone.
     */
    @Serializable
    @SerialName("start-request")
    data class StartRequest(val token: String) : SignalMessage()

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String? = null) : SignalMessage()
}

/** Encodes/decodes [SignalMessage] to and from the JSON wire format. */
object SignalCodec {
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true      // tolerate fields a newer peer might add
        encodeDefaults = true
    }

    fun encode(message: SignalMessage): String = json.encodeToString(message)

    fun decode(text: String): SignalMessage = json.decodeFromString(text)
}
