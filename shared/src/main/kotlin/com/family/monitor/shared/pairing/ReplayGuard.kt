package com.family.monitor.shared.pairing

/**
 * Remembers recently seen one-time nonces so a captured start-request cannot be
 * replayed. The TTL must exceed the longest period a MAC can still verify
 * (window * (skew + 1), i.e. ~10 min with defaults); 15 min is the default.
 * Bounded in size so a flood of requests cannot exhaust memory.
 */
class ReplayGuard(
    private val ttlMillis: Long = 15 * 60 * 1000L,
    private val maxEntries: Int = 256,
) {
    private val seen = LinkedHashMap<String, Long>()

    /** Returns true the first time [nonce] is presented, false for any replay. */
    @Synchronized
    fun firstUse(nonce: String, nowMillis: Long): Boolean {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (nowMillis - it.next().value > ttlMillis) it.remove()
        }
        if (seen.containsKey(nonce)) return false
        seen[nonce] = nowMillis
        while (seen.size > maxEntries) {
            val oldest = seen.keys.iterator()
            oldest.next(); oldest.remove()
        }
        return true
    }
}
