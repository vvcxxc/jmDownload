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
    private val SEPARATORS = Regex("[,，;；、]+")
    private val JM_CONCAT = Regex("(?i)jm\\s*(\\d+)")
    private val WHITESPACE = Regex("\\s+")
    private val PURE_ID = Regex("\\d{3,}")

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

    /** Splits a part that may contain multiple concatenated JM IDs like "JM428980JM433633". */
    private fun splitConcatenatedJm(text: String): List<String> {
        val matches = JM_CONCAT.findAll(text).toList()
        if (matches.size > 1) {
            return matches.map { "JM${it.groupValues[1]}" }
        }
        return listOf(text)
    }

    /** Treats a whitespace/newline-separated list as multiple IDs only when every token is an ID. */
    private fun splitWhitespaceIds(text: String): List<String>? {
        val raw = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (raw.size <= 1) return null

        val merged = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            if (raw[i].equals("jm", ignoreCase = true) &&
                i + 1 < raw.size &&
                raw[i + 1].matches(PURE_ID)
            ) {
                merged += "JM${raw[i + 1]}"
                i += 2
            } else {
                merged += raw[i]
                i++
            }
        }
        if (merged.size <= 1) return null

        val allIds = merged.all { token ->
            token.matches(PURE_ID) ||
                token.matches(JM_PREFIXED) ||
                URL_PATH.containsMatchIn(token) ||
                URL_QUERY.containsMatchIn(token)
        }
        return if (allIds) merged else null
    }

    /** Splits by comma/semicolon and parses each part.
     *  Also detects concatenated JM IDs like "JM428980JM433633".
     *  If all parts yield valid IDs, returns them as separate entries.
     *  Otherwise falls back to extracting all digits as a single ID. */
    fun parseAll(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()

        val parts = t.split(SEPARATORS).filter { it.isNotEmpty() }
        val allParts = parts.flatMap { splitConcatenatedJm(it) }

        if (allParts.size <= 1) {
            val whitespaceIds = splitWhitespaceIds(t)
            if (whitespaceIds != null) {
                val ids = whitespaceIds.mapNotNull { parse(it) }
                if (ids.size == whitespaceIds.size) return ids.distinct()
            }
            return listOfNotNull(parse(t))
        }

        val ids = allParts.mapNotNull { parse(it) }
        if (ids.size == allParts.size) {
            return ids.distinct()
        }

        // Some parts failed — treat as Chinese text, extract all digits as one ID
        return listOfNotNull(parse(t))
    }
}