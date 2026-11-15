package com.jmdl.core

/**
 * Builds a filesystem-safe zip name "JM{id}-{title}.zip".
 * Mirrors the Python bot's sanitize_zip_name: keep letters/digits (incl. CJK) plus a
 * small punctuation set, replace everything else with spaces, collapse, and cap the
 * total length to a UTF-8 byte budget without splitting a multi-byte character.
 */
object FilenameSanitizer {

    private val ALLOWED_PUNCT = setOf(' ', '-', '_', '[', ']', '(', ')', '.')
    private const val MAX_BYTES = 200
    private val WHITESPACE = Regex("\\s+")

    fun sanitizeTitle(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            if (Character.isLetterOrDigit(c) || c in ALLOWED_PUNCT) sb.append(c) else sb.append(' ')
        }
        return sb.toString().replace(WHITESPACE, " ").trim()
    }

    fun zipName(id: String, title: String): String {
        val safeTitle = sanitizeTitle(title).ifEmpty { "JM$id" }
        return buildZipName(id, safeTitle)
    }

    fun chapterZipName(id: String, albumTitle: String?, chapterTitle: String): String {
        val safeAlbum = albumTitle?.let(::sanitizeTitle).orEmpty()
        val safeChapter = sanitizeTitle(chapterTitle).ifEmpty { "第1章" }
        val body = listOf(safeAlbum, safeChapter)
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .ifEmpty { "JM$id" }
        return buildZipName(id, body)
    }

    private fun buildZipName(id: String, body: String): String {
        val prefix = "JM$id-"
        val ext = ".zip"
        val budget = MAX_BYTES -
            prefix.toByteArray(Charsets.UTF_8).size -
            ext.toByteArray(Charsets.UTF_8).size
        val safeBody = truncateUtf8(body, budget).ifEmpty { "JM$id" }
        return "$prefix$safeBody$ext"
    }

    private fun truncateUtf8(s: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        val bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return s
        var end = maxBytes
        // back off if we'd cut in the middle of a multi-byte sequence
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return String(bytes, 0, end, Charsets.UTF_8).trim()
    }
}
