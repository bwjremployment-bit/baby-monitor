package com.family.monitor.shared.pairing

/**
 * RFC 4648 Base32 (no padding), using the standard alphabet but decoding
 * case-insensitively and ignoring spaces/hyphens so humans can type grouped
 * codes like "ABCD-EFGH-IJKL".
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { i, c ->
            table[c.code] = i
            table[c.lowercaseChar().code] = i
        }
    }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(ALPHABET[(buffer ushr bits) and 0x1F])
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>(text.length * 5 / 8 + 1)
        for (c in text) {
            if (c == ' ' || c == '-' || c == '\n' || c == '\r' || c == '\t') continue
            val v = if (c.code < 128) DECODE[c.code] else -1
            require(v >= 0) { "Invalid base32 character: '$c'" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    /** Groups a code into dash-separated chunks for readability. */
    fun group(code: String, size: Int = 4): String =
        code.chunked(size).joinToString("-")
}
