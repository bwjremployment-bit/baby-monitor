package com.family.monitor.shared.pairing

import com.family.monitor.shared.signaling.Role
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The out-of-band pairing material: a single 128-bit random [secret] (Base32).
 * It is carried screen->viewer (QR) or typed/pasted (manual code) and is NEVER
 * sent over the network, which is what makes pairing secure.
 *
 * The signalling [room] is *derived* from the secret (one-way, see
 * [PairingCrypto.deriveRoom]) so only the secret needs to be transferred, and
 * the relay (which learns the room id) learns nothing about the secret.
 * Rotating the secret therefore also moves the room: this is how pairing is
 * revoked.
 */
data class PairingPayload(
    val version: Int,
    val secret: String,
) {
    /** Signalling room id, derived one-way from [secret]. */
    val room: String get() = PairingCrypto.deriveRoom(secret)

    /** Compact URI encoded inside the QR code. */
    fun toUri(): String = "$SCHEME://pair?v=$version&s=$secret"

    /**
     * Human-typeable fallback: Base32 of (version || secretBytes), grouped
     * ("ABCD-EFGH-..."). 28 characters; can also be copied/pasted/shared.
     */
    fun toManualCode(): String {
        val secretBytes = Base32.decode(secret)
        val buf = ByteArray(1 + secretBytes.size)
        buf[0] = version.toByte()
        secretBytes.copyInto(buf, 1)
        return Base32.group(Base32.encode(buf))
    }

    companion object {
        const val SCHEME = "jiandie"
        const val VERSION = 1
        const val SECRET_BYTES = 16   // 128-bit pairing secret

        fun generate(random: SecureRandom = SecureRandom()): PairingPayload {
            val s = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
            return PairingPayload(VERSION, Base32.encode(s))
        }

        fun fromUri(uri: String): PairingPayload {
            require(uri.startsWith("$SCHEME://")) { "Not a pairing URI" }
            val q = uri.substringAfter('?', "")
            require(q.isNotEmpty()) { "Not a pairing URI" }
            val params = q.split('&').mapNotNull {
                val i = it.indexOf('='); if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            val v = params["v"]?.toIntOrNull() ?: error("Missing version")
            val secret = params["s"] ?: error("Missing secret")
            return validated(v, secret)
        }

        fun fromManualCode(code: String): PairingPayload {
            val buf = Base32.decode(code)
            require(buf.size == 1 + SECRET_BYTES) { "Malformed pairing code" }
            val version = buf[0].toInt() and 0xFF
            val secret = Base32.encode(buf.copyOfRange(1, buf.size))
            return validated(version, secret)
        }

        /** Accepts either a pairing URI or a manual code. */
        fun parse(input: String): PairingPayload {
            val t = input.trim()
            return if (t.startsWith("$SCHEME://")) fromUri(t) else fromManualCode(t)
        }

        private fun validated(version: Int, secret: String): PairingPayload {
            require(version == VERSION) { "Unsupported pairing version $version" }
            require(Base32.decode(secret).size == SECRET_BYTES) { "Malformed pairing secret" }
            return PairingPayload(version, secret)
        }
    }
}

/**
 * Authentication primitives derived from the pairing secret. All are
 * HMAC-SHA256 under the secret, role-bound, and time-windowed (default 5 min,
 * +/- one window of clock-skew tolerance).
 *
 * TRUST MODEL: the signalling relay is untrusted. It never holds the secret and
 * so can neither mint nor verify anything. Authentication is therefore
 * PEER-TO-PEER:
 *  - the viewer proves it is paired by attaching a [signStart] MAC (over a
 *    one-time nonce) to its start-request, which the CAMERA verifies, and
 *    de-duplicates with a [ReplayGuard], before ever turning on capture;
 *  - the offer and answer each carry an [signSdp] MAC over the SDP, which the
 *    receiving peer verifies. Because the SDP contains the DTLS fingerprint,
 *    this stops a malicious relay from swapping fingerprints (a MITM on the
 *    media) as well as from injecting its own session.
 * Because every start-request carries a one-time nonce that the camera
 * remembers, a relay cannot replay a captured request to switch the camera on.
 * Even a successful wake could not obtain media (that needs a validly signed
 * answer).
 */
object PairingCrypto {
    const val DEFAULT_WINDOW_MILLIS = 5 * 60 * 1000L

    /** One-way, deterministic room id for a secret (128 bits, Base32). */
    fun deriveRoom(secret: String): String {
        val digest = hmac(secret, "room-v1")
        return Base32.encode(digest.copyOf(16))
    }

    /** Fresh random nonce for one start-request (128 bits, base64url). */
    fun newNonce(random: SecureRandom = SecureRandom()): String {
        val b = ByteArray(16).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    /** MAC the viewer attaches to a start-request: binds room + a one-time [nonce] + time window. */
    fun signStart(
        secret: String,
        room: String,
        nonce: String,
        epochMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    ): String = hmacB64(secret, "start|$room|${Role.VIEWER.name}|$nonce|${epochMillis / windowMillis}")

    /**
     * Verifies a start-request MAC. This only proves the sender holds the
     * secret; the caller MUST also reject a nonce it has seen before
     * (see [ReplayGuard]) so a captured request cannot be replayed.
     */
    fun verifyStart(
        auth: String,
        secret: String,
        room: String,
        nonce: String,
        epochMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        skewWindows: Int = 1,
    ): Boolean = anyWindowMatches(auth, epochMillis, windowMillis, skewWindows) { t ->
        signStart(secret, room, nonce, t, windowMillis)
    }

    /** MAC over [sdp] by [role] (the sender); binds the SDP, incl. its DTLS fingerprint. */
    fun signSdp(
        secret: String,
        room: String,
        role: Role,
        sdp: String,
        epochMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    ): String {
        val sdpHash = MessageDigest.getInstance("SHA-256").digest(sdp.toByteArray(Charsets.UTF_8))
        val hex = sdpHash.joinToString("") { "%02x".format(it) }
        return hmacB64(secret, "sdp|$room|${role.name}|${epochMillis / windowMillis}|$hex")
    }

    fun verifySdp(
        auth: String,
        secret: String,
        room: String,
        role: Role,
        sdp: String,
        epochMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        skewWindows: Int = 1,
    ): Boolean = anyWindowMatches(auth, epochMillis, windowMillis, skewWindows) { t ->
        signSdp(secret, room, role, sdp, t, windowMillis)
    }

    private inline fun anyWindowMatches(
        presented: String,
        epochMillis: Long,
        windowMillis: Long,
        skewWindows: Int,
        expectedAt: (Long) -> String,
    ): Boolean {
        val presentedBytes = presented.toByteArray(Charsets.UTF_8)
        var ok = false
        // Evaluate every window (no early exit) to keep timing independent of which matched.
        for (w in -skewWindows..skewWindows) {
            val expected = expectedAt(epochMillis + w * windowMillis).toByteArray(Charsets.UTF_8)
            if (MessageDigest.isEqual(expected, presentedBytes)) ok = true
        }
        return ok
    }

    private fun hmac(secret: String, message: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base32.decode(secret), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
    }

    private fun hmacB64(secret: String, message: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(secret, message))
}
