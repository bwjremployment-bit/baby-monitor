package com.family.monitor.shared

import com.family.monitor.shared.pairing.ReplayGuard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayGuardTest {
    @Test fun first_use_accepted_replay_rejected() {
        val g = ReplayGuard()
        assertTrue(g.firstUse("n1", 1_000))
        assertFalse(g.firstUse("n1", 2_000))
        assertTrue(g.firstUse("n2", 2_000))
    }

    @Test fun nonce_is_forgotten_after_ttl() {
        val g = ReplayGuard(ttlMillis = 10_000)
        assertTrue(g.firstUse("n1", 0))
        assertFalse(g.firstUse("n1", 9_000))
        assertTrue(g.firstUse("n1", 20_000))   // expired -> would be a new nonce (MAC window also expired by then)
    }

    @Test fun memory_is_bounded_under_flood() {
        val g = ReplayGuard(ttlMillis = 1_000_000, maxEntries = 8)
        repeat(1000) { assertTrue(g.firstUse("n$it", it.toLong())) }
        // Oldest entries were evicted; the newest are still remembered.
        assertFalse(g.firstUse("n999", 1_000))
        assertTrue(g.firstUse("n0", 1_001))
    }
}
