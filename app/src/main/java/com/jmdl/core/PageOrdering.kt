package com.jmdl.core

/** Shared helpers for recognising and ordering image files inside downloaded/imported zips. */
object PageOrdering {

    /**
     * True when [name] looks like a real page image. macOS resource forks
     * (`__MACOSX/._001.jpg`) are metadata and must not become pages.
     */
    fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        if (n.startsWith("__macosx/") || n.substringAfterLast('/').startsWith("._")) return false
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
    }

    /** Compares names so "2.jpg" sorts before "10.jpg" (plain string sort does not). */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val rawA = a.substring(startA, i)
                val rawB = b.substring(startB, j)
                val numA = rawA.trimStart('0')
                val numB = rawB.trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
                // Equal value (e.g. "1" vs "01"): keep the order deterministic.
                val rawCmp = rawA.compareTo(rawB)
                if (rawCmp != 0) return rawCmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
