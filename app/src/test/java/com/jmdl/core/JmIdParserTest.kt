package com.jmdl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JmIdParserTest {

    @Test
    fun parsesBareId() {
        assertEquals("350234", JmIdParser.parse("350234"))
    }

    @Test
    fun parsesPrefixedIdCaseInsensitively() {
        assertEquals("350234", JmIdParser.parse("JM350234"))
        assertEquals("350234", JmIdParser.parse("jm 350234"))
    }

    @Test
    fun parsesUrlPathAndQuery() {
        assertEquals("412038", JmIdParser.parse("https://example.com/photo/412038"))
        assertEquals("412038", JmIdParser.parse("https://example.com/album?id=412038"))
    }

    @Test
    fun parsesCommaSeparatedIds() {
        assertEquals(
            listOf("350234", "350235", "350236"),
            JmIdParser.parseAll("350234, JM350235; JM 350236"),
        )
    }

    @Test
    fun splitsConcatenatedPrefixedIds() {
        assertEquals(
            listOf("428980", "433633", "435515"),
            JmIdParser.parseAll("JM428980JM433633JM435515"),
        )
    }

    @Test
    fun deduplicatesIds() {
        assertEquals(listOf("123456"), JmIdParser.parseAll("123456, 123456"))
    }

    @Test
    fun textFallbackConcatenatesDigits() {
        assertEquals("1250558", JmIdParser.parse("游玩12次撞残50次重开558次"))
    }

    @Test
    fun rejectsShortOrEmptyInput() {
        assertNull(JmIdParser.parse(""))
        assertNull(JmIdParser.parse("12"))
        assertNull(JmIdParser.parse("hello"))
    }
}
