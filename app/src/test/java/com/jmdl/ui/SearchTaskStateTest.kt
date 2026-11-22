package com.jmdl.ui

import com.jmdl.data.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTaskStateTest {
    @Test
    fun queuedAndRunningTasksAreActive() {
        val state = searchTaskStateFor(listOf(TaskStatus.QUEUED, TaskStatus.RUNNING))
        assertTrue(state.hasTask)
        assertTrue(state.hasActive)
        assertFalse(state.hasDone)
        assertFalse(state.hasFailed)
    }

    @Test
    fun pausedAndZippingTasksAreActive() {
        val state = searchTaskStateFor(listOf(TaskStatus.PAUSED, TaskStatus.ZIPPING))
        assertTrue(state.hasActive)
    }

    @Test
    fun doneOnlyTaskIsJoined() {
        val state = searchTaskStateFor(listOf(TaskStatus.DONE))
        assertFalse(state.hasActive)
        assertTrue(state.hasDone)
    }

    @Test
    fun failedOnlyTaskCanRetry() {
        val state = searchTaskStateFor(listOf(TaskStatus.FAILED))
        assertFalse(state.hasActive)
        assertFalse(state.hasDone)
        assertTrue(state.hasFailed)
    }

    @Test
    fun mixedDoneAndActiveChaptersCanCancel() {
        val state = searchTaskStateFor(listOf(TaskStatus.DONE, TaskStatus.RUNNING, TaskStatus.QUEUED))
        assertTrue(state.hasActive)
        assertTrue(state.hasDone)
    }

    @Test
    fun noTasksMeansDownloadable() {
        val state = searchTaskStateFor(emptyList())
        assertFalse(state.hasTask)
        assertFalse(state.hasActive)
        assertFalse(state.hasDone)
        assertFalse(state.hasFailed)
    }
}
