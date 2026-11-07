package com.family.monitor.shared.pairing

import com.family.monitor.shared.signaling.Role
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The out-of-band pairing material. [room] and [secret] are Base32 strings of
 * random bytes. The secret is carried screen->camera (QR) or typed (manual
 * code) and is NEVER sent over the network — that is what makes pairing secure.
 *
 * Both devices derive the same short-lived signalling [token] from the secret
 * (see [PairingCrypto]).
 */
data class PairingPayload(
    val version: Int,
    val room: String,
    val secret: String,
) {
    /** Compact URI encoded inside the QR code. */
    fun toUri(): String = "$SCHEME://pair?v=$version&r=$room&s=$secret"

    /**
     * Human-typeable fallback code: Base32 of (version || roomBytes ||
     * secretBytes), grouped for readability. Longer than a PIN by design —
     * it carries the full pairing secret so no server ever sees it.
     */
    fun toManualCode(): String {
        val roomBytes = Base32.decode(room)
        val secretBytes = Base32.decode(secret)
        val buf = ByteArray(1 + roomBytes.size + secretBytes.size)
        buf[0] = version.toByte()
        roomBytes.copyInto(buf, 1)
        secretBytes.copyInto(buf, 1 + roomBytes.size)
        return Base32.group(Base32.encode(buf))
    }

    companion object {
        const val SCHEME = "jiandie"
        const val VERSION = 1
        private const val ROOM_BYTES = 16     // 128-bit unguessable room id
        private const val SECRET_BYTES = 32   // 256-bit pairing secret

        fun generate(random: SecureRandom = SecureRandom()): PairingPayload {
            val r = ByteArray(ROOM_BYTES).also { random.nextBytes(it) }
            val s = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
            return PairingPayload(VERSION, Base32.encode(r), Base32.encode(s))
        }

        fun fromUri(uri: String): PairingPayload {
            val q = uri.substringAfter('?', "")
            require(q.isNotEmpty()) { "Not a pairing URI: $uri" }
            val params = q.split('&').mapNotNull {
                val i = it.indexOf('='); if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            val v = params["v"]?.toIntOrNull() ?: error("Missing version")
            val room = params["r"] ?: error("Missing room")
            val secret = params["s"] ?: error("Missing secret")
            return PairingPayload(v, room, secret)
        }

        fun fromManualCode(code: String): PairingPayload {
            val buf = Base32.decode(code)
            require(buf.size == 1 + ROOM_BYTES + SECRET_BYTES) { "Malformed pairing code" }
            val version = buf[0].toInt() and 0xFF
            val room = Base32.encode(buf.copyOfRange(1, 1 + ROOM_BYTES))
            val secret = Base32.encode(buf.copyOfRange(1 + ROOM_BYTES, buf.size))
            return PairingPayload(version, room, secret)
        }

        /** Accepts either a pairing URI or a manual code. */
        fun parse(input: String): PairingPayload {
            val t = input.trim()
            return if (t.contains("://") || t.contains("?")) fromUri(t) else fromManualCode(t)
        }
    }
}

/**
 * Derives and verifies short-lived signalling tokens from the pairing secret.
 *
 * A token is HMAC-SHA256(secret, "room|role|timeWindow"), base64url, truncated.
 * Time-windowing (default 5 min) bounds replay; [verifyToken] tolerates clock
 * skew of +/- one window. NOTE (trust model, see SECURITY_DESIGN.md): the
 * signalling server must be able to verify tokens, so in the HMAC scheme it is
 * given a room-scoped verification capability. Upgrading to a signed-nonce
 * (server holds only a public key, cannot mint tokens) is a Stage 5 option.
 */
object PairingCrypto {
    const val DEFAULT_WINDOW_MILLIS = 5 * 60 * 1000L
    private const val TOKEN_CHARS = 32

    fun deriveToken(
        secret: String,
        room: String,
        role: Role,
        epochMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    ): String {
        val window = epochMillis / windowMillis
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base32.decode(secret), "HmacSHA256"))
        val digest = mac.doFinal("$room|${role.name}|$window".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(TOKEN_CHARS)
    }

    fun verifyToken(
        token: String,
        secret: String,
        room: String,
        role: Role,
        epochMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        skewWindows: Int = 1,
    ): Boolean {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        for (w in -skewWindows..skewWindows) {
            val candidate = deriveToken(secret, room, role, epochMillis + w * windowMillis, windowMillis)
            if (MessageDigest.isEqual(candidate.toByteArray(Charsets.UTF_8), tokenBytes)) return true
        }
        return false
    }
}
