package com.jmdl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class FilenameSanitizerTest {

    @Test
    fun replacesIllegalCharacters() {
        assertEquals("a b c", FilenameSanitizer.sanitizeTitle("a/b\\c"))
        assertEquals("标题 (1)", FilenameSanitizer.sanitizeTitle("标题 (1)"))
    }

    @Test
    fun zipNameUsesJmPrefixAndExtension() {
        val name = FilenameSanitizer.zipName("350234", "我的标题")
        assertEquals("JM350234-我的标题.zip", name)
    }

    @Test
    fun emptyTitleFallsBackToId() {
        assertEquals("JM350234-JM350234.zip", FilenameSanitizer.zipName("350234", "///"))
    }

    @Test
    fun longCjkTitleStaysWithinByteBudget() {
        val name = FilenameSanitizer.zipName("350234", "很".repeat(300))
        assertTrue(name.toByteArray(StandardCharsets.UTF_8).size <= 200)
        assertTrue(name.endsWith(".zip"))
    }
}
