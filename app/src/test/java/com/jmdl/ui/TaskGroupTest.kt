package com.jmdl.ui

import com.jmdl.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskGroupTest {
    @Test
    fun groupsAndSortsChaptersByIndex() {
        val groups = buildTaskGroups(
            listOf(
                Task(
                    id = "chapter-2",
                    jmId = "1229332",
                    albumTitle = "Book",
                    chapterId = "1229335",
                    chapterTitle = "第2章",
                    chapterIndex = 2,
                    chapterCount = 2,
                ),
                Task(
                    id = "chapter-1",
                    jmId = "1229332",
                    albumTitle = "Book",
                    chapterId = "1229332",
                    chapterTitle = "第1章",
                    chapterIndex = 1,
                    chapterCount = 2,
                ),
            )
        )

        assertEquals(1, groups.size)
        assertEquals("Book", groups.first().albumTitle)
        assertEquals(2, groups.first().chapterCount)
        assertTrue(groups.first().isMultiChapter)
        assertEquals(listOf("chapter-1", "chapter-2"), groups.first().tasks.map { it.id })
    }

    @Test
    fun keepsSingleTaskAsSingleGroup() {
        val groups = buildTaskGroups(listOf(Task(id = "single", jmId = "10492", title = "Single")))
        assertEquals(1, groups.size)
        assertEquals(false, groups.first().isMultiChapter)
    }
}
