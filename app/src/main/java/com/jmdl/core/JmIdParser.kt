package com.jmdl.core

/**
 * Extracts a JM album id from arbitrary user input — a bare number, "JM123456",
 * "jm 123456", or a pasted URL like ".../photo/412038" or "?id=412038".
 * Mirrors the Python bot's parse behaviour (qqbot_jm.py parse_jm_id + jm_toolkit patterns).
 */
object JmIdParser {
    private val JM_PREFIXED = Regex("(?i)jm\\s*(\\d{3,})")
    private val URL_PATH = Regex("(?i)(?:photos?|albums?)/(\\d{3,})")
    private val URL_QUERY = Regex("(?i)[?&]id=(\\d{3,})")
    private val BARE_NUMBER = Regex("(\\d{3,})")

    /** Returns the album id (digits only) or null if none can be found. */
    fun parse(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        JM_PREFIXED.find(t)?.let { return it.groupValues[1] }
        URL_PATH.find(t)?.let { return it.groupValues[1] }
        URL_QUERY.find(t)?.let { return it.groupValues[1] }
        t.filter { it.isDigit() }.takeIf { it.length >= 3 }?.let { return it }
        return null
    }
}
