package com.family.monitor.shared

import com.family.monitor.shared.pairing.Base32
import com.family.monitor.shared.pairing.PairingCrypto
import com.family.monitor.shared.pairing.PairingPayload
import com.family.monitor.shared.signaling.Role
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingTest {

    private val rng = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) }

    @Test fun base32_round_trips_random_bytes() {
        repeat(100) {
            val n = (it % 40)
            val bytes = ByteArray(n).also { b -> rng.nextBytes(b) }
            assertTrue(bytes.contentEquals(Base32.decode(Base32.encode(bytes))))
        }
    }

    @Test fun base32_decode_ignores_grouping_and_case() {
        val bytes = ByteArray(20).also { rng.nextBytes(it) }
        val grouped = Base32.group(Base32.encode(bytes)).lowercase()
        assertTrue(bytes.contentEquals(Base32.decode(grouped)))
    }

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

    @Test fun generated_payloads_are_unique() {
        val a = PairingPayload.generate(rng)
        val b = PairingPayload.generate(rng)
        assertNotEquals(a.room, b.room)
        assertNotEquals(a.secret, b.secret)
    }

    @Test fun token_verifies_within_window() {
        val p = PairingPayload.generate(rng)
        val now = 1_700_000_000_000L
        val token = PairingCrypto.deriveToken(p.secret, p.room, Role.VIEWER, now)
        assertTrue(PairingCrypto.verifyToken(token, p.secret, p.room, Role.VIEWER, now))
    }

    @Test fun token_tolerates_one_window_skew() {
        val p = PairingPayload.generate(rng)
        val now = 1_700_000_000_000L
        val token = PairingCrypto.deriveToken(p.secret, p.room, Role.CAMERA, now)
        val later = now + PairingCrypto.DEFAULT_WINDOW_MILLIS   // next window
        assertTrue(PairingCrypto.verifyToken(token, p.secret, p.room, Role.CAMERA, later))
    }

    @Test fun token_fails_with_wrong_secret() {
        val p = PairingPayload.generate(rng)
        val other = PairingPayload.generate(rng)
        val now = 1_700_000_000_000L
        val token = PairingCrypto.deriveToken(p.secret, p.room, Role.VIEWER, now)
        assertFalse(PairingCrypto.verifyToken(token, other.secret, p.room, Role.VIEWER, now))
    }

    @Test fun token_is_role_bound() {
        val p = PairingPayload.generate(rng)
        val now = 1_700_000_000_000L
        val cameraToken = PairingCrypto.deriveToken(p.secret, p.room, Role.CAMERA, now)
        assertFalse(PairingCrypto.verifyToken(cameraToken, p.secret, p.room, Role.VIEWER, now))
    }

    @Test fun token_fails_far_outside_window() {
        val p = PairingPayload.generate(rng)
        val now = 1_700_000_000_000L
        val token = PairingCrypto.deriveToken(p.secret, p.room, Role.VIEWER, now)
        val wayLater = now + 10 * PairingCrypto.DEFAULT_WINDOW_MILLIS
        assertFalse(PairingCrypto.verifyToken(token, p.secret, p.room, Role.VIEWER, wayLater))
    }
}
