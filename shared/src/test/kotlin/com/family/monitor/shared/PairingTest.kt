package com.family.monitor.shared

import com.family.monitor.shared.pairing.Base32
import com.family.monitor.shared.pairing.PairingCrypto
import com.family.monitor.shared.pairing.PairingPayload
import com.family.monitor.shared.signaling.Role
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingTest {

    private val rng = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) }
    private val now = 1_700_000_000_000L
    private val window = PairingCrypto.DEFAULT_WINDOW_MILLIS

    // ---- Base32 ----
    @Test fun base32_round_trips_random_bytes() {
        repeat(100) {
            val bytes = ByteArray(it % 40).also { b -> rng.nextBytes(b) }
            assertTrue(bytes.contentEquals(Base32.decode(Base32.encode(bytes))))
        }
    }

    @Test fun base32_decode_ignores_grouping_and_case() {
        val bytes = ByteArray(20).also { rng.nextBytes(it) }
        val grouped = Base32.group(Base32.encode(bytes)).lowercase()
        assertTrue(bytes.contentEquals(Base32.decode(grouped)))
    }

    // ---- Payload ----
    @Test fun payload_round_trips_via_uri() {
        val p = PairingPayload.generate(rng)
        assertEquals(p, PairingPayload.fromUri(p.toUri()))
        assertEquals(p, PairingPayload.parse(p.toUri()))
    }

    @Test fun payload_round_trips_via_manual_code() {
        val p = PairingPayload.generate(rng)
        assertEquals(p, PairingPayload.fromManualCode(p.toManualCode()))
        assertEquals(p, PairingPayload.parse(p.toManualCode()))
    }

    @Test fun manual_code_is_short_enough_to_type() {
        val code = PairingPayload.generate(rng).toManualCode()
        assertTrue(code.replace("-", "").length <= 28, "manual code too long: $code")
    }

    @Test fun malformed_input_is_rejected() {
        assertFailsWith<IllegalArgumentException> { PairingPayload.parse("jiandie://pair?v=9&s=AAAA") }
        assertFailsWith<IllegalArgumentException> { PairingPayload.parse("ABCD-EFGH") }
        assertFailsWith<IllegalArgumentException> { PairingPayload.parse("https://evil/pair?v=1&s=AAAA") }
        assertFailsWith<IllegalArgumentException> { PairingPayload.parse("!!!!") }
    }

    @Test fun generated_secrets_are_unique() {
        assertNotEquals(PairingPayload.generate(rng).secret, PairingPayload.generate(rng).secret)
    }

    // ---- Room derivation ----
    @Test fun room_is_deterministic_and_secret_specific() {
        val a = PairingPayload.generate(rng)
        val b = PairingPayload.generate(rng)
        assertEquals(a.room, PairingPayload.fromUri(a.toUri()).room)
        assertNotEquals(a.room, b.room)
        assertTrue(a.room.length >= 8)
    }

    @Test fun room_does_not_reveal_secret() {
        val a = PairingPayload.generate(rng)
        assertFalse(a.room.contains(a.secret))
        assertFalse(a.toUri().contains(a.room))
    }

    // ---- Tokens (viewer -> camera start-request) ----
    @Test fun token_verifies_within_window() {
        val p = PairingPayload.generate(rng)
        val t = PairingCrypto.deriveToken(p.secret, p.room, Role.VIEWER, now)
        assertTrue(PairingCrypto.verifyToken(t, p.secret, p.room, Role.VIEWER, now))
    }

    @Test fun token_tolerates_one_window_skew_but_not_ten() {
        val p = PairingPayload.generate(rng)
        val t = PairingCrypto.deriveToken(p.secret, p.room, Role.CAMERA, now)
        assertTrue(PairingCrypto.verifyToken(t, p.secret, p.room, Role.CAMERA, now + window))
        assertFalse(PairingCrypto.verifyToken(t, p.secret, p.room, Role.CAMERA, now + 10 * window))
    }

    @Test fun token_fails_with_wrong_secret_wrong_role_or_wrong_room() {
        val p = PairingPayload.generate(rng)
        val other = PairingPayload.generate(rng)
        val t = PairingCrypto.deriveToken(p.secret, p.room, Role.VIEWER, now)
        assertFalse(PairingCrypto.verifyToken(t, other.secret, p.room, Role.VIEWER, now))
        assertFalse(PairingCrypto.verifyToken(t, p.secret, p.room, Role.CAMERA, now))
        assertFalse(PairingCrypto.verifyToken(t, p.secret, other.room, Role.VIEWER, now))
    }

    @Test fun empty_or_garbage_token_is_rejected() {
        val p = PairingPayload.generate(rng)
        assertFalse(PairingCrypto.verifyToken("", p.secret, p.room, Role.VIEWER, now))
        assertFalse(PairingCrypto.verifyToken("not-a-token", p.secret, p.room, Role.VIEWER, now))
    }

    // ---- SDP signing (offer/answer; defeats a malicious relay swapping the DTLS fingerprint) ----
    private val sdp = "v=0\r\na=fingerprint:sha-256 AA:BB:CC\r\nm=video 9 UDP/TLS/RTP/SAVPF 96"

    @Test fun signed_sdp_verifies() {
        val p = PairingPayload.generate(rng)
        val auth = PairingCrypto.signSdp(p.secret, p.room, Role.CAMERA, sdp, now)
        assertTrue(PairingCrypto.verifySdp(auth, p.secret, p.room, Role.CAMERA, sdp, now))
    }

    @Test fun tampered_sdp_is_rejected_even_one_character() {
        val p = PairingPayload.generate(rng)
        val auth = PairingCrypto.signSdp(p.secret, p.room, Role.CAMERA, sdp, now)
        val swapped = sdp.replace("AA:BB:CC", "AA:BB:CD")   // relay swaps the DTLS fingerprint
        assertFalse(PairingCrypto.verifySdp(auth, p.secret, p.room, Role.CAMERA, swapped, now))
    }

    @Test fun sdp_signature_is_bound_to_sender_role_secret_and_time() {
        val p = PairingPayload.generate(rng)
        val other = PairingPayload.generate(rng)
        val auth = PairingCrypto.signSdp(p.secret, p.room, Role.CAMERA, sdp, now)
        // A relay cannot reflect the camera's offer signature back as a viewer "answer".
        assertFalse(PairingCrypto.verifySdp(auth, p.secret, p.room, Role.VIEWER, sdp, now))
        assertFalse(PairingCrypto.verifySdp(auth, other.secret, p.room, Role.CAMERA, sdp, now))
        assertFalse(PairingCrypto.verifySdp(auth, p.secret, p.room, Role.CAMERA, sdp, now + 10 * window))
    }
}
