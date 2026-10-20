package com.jmdl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageOrderingTest {

    @Test
    fun sortsNumericNamesNaturally() {
        val names = listOf("10.jpg", "2.jpg", "1.jpg", "01.jpg")
        assertEquals(
            listOf("01.jpg", "1.jpg", "2.jpg", "10.jpg"),
            names.sortedWith { a, b -> PageOrdering.naturalCompare(a, b) },
        )
    }

    @Test
    fun comparesNestedPathsNaturally() {
        val names = listOf("chapter10/001.jpg", "chapter2/010.jpg", "chapter2/002.jpg")
        assertEquals(
            listOf("chapter2/002.jpg", "chapter2/010.jpg", "chapter10/001.jpg"),
            names.sortedWith { a, b -> PageOrdering.naturalCompare(a, b) },
        )
    }

    @Test
    fun recognisesImageExtensionsCaseInsensitively() {
        assertTrue(PageOrdering.isImageName("001.JPG"))
        assertTrue(PageOrdering.isImageName("a/b/002.webp"))
        assertTrue(PageOrdering.isImageName("cover.PNG"))
        assertFalse(PageOrdering.isImageName("readme.txt"))
    }

    @Test
    fun ignoresMacResourceForks() {
        assertFalse(PageOrdering.isImageName("__MACOSX/._001.jpg"))
        assertFalse(PageOrdering.isImageName("folder/._002.png"))
    }
}
