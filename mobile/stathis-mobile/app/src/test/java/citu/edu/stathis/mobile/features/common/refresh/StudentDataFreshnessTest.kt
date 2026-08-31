package citu.edu.stathis.mobile.features.common.refresh

import citu.edu.stathis.mobile.features.tasks.presentation.TaskCompletionCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentDataFreshnessTest {

    @Test
    fun teacherStartUsesBoundedVisiblePollBecauseNoPushExists() {
        assertFalse(StudentDataFreshness.HAS_TASK_START_PUSH)
        val interval = StudentDataFreshness.pollIntervalMsForTeacherStart()
        assertNotNull(interval)
        assertTrue(interval!! >= 10_000L)
        assertTrue(interval <= 30_000L)
        assertTrue(StudentDataFreshness.shouldPollWhileVisible(true, interval))
        assertFalse(StudentDataFreshness.shouldPollWhileVisible(false, interval))
        assertFalse(StudentDataFreshness.shouldPollWhileVisible(true, null))
    }

    @Test
    fun backgroundScreensDoNotPoll() {
        assertFalse(
            StudentDataFreshness.shouldPollWhileVisible(
                screenResumed = false,
                pollIntervalMs = StudentDataFreshness.VISIBLE_LIST_POLL_MS
            )
        )
    }

    @Test
    fun duplicateResumeAndLaunchDoNotStorm() {
        assertTrue(StudentDataFreshness.shouldSkipDuplicateFetch(1000L, 1800L, 1500L))
        assertFalse(StudentDataFreshness.shouldSkipDuplicateFetch(1000L, 3000L, 1500L))
        assertFalse(StudentDataFreshness.shouldSkipDuplicateFetch(0L, 200L, 1500L))
    }

    @Test
    fun taskListIncludesTaskOnlyAfterTeacherStart() {
        assertFalse(StudentDataFreshness.studentVisibleStarted(active = true, started = false))
        assertTrue(StudentDataFreshness.studentVisibleStarted(active = true, started = true))
        val before = StudentDataFreshness.taskIdsKey(emptyList())
        val after = StudentDataFreshness.taskIdsKey(listOf("TASK-STARTED"))
        assertTrue(
            StudentDataFreshness.shouldRefetchProgress(
                previousTaskIdsKey = before,
                currentTaskIdsKey = after,
                previousCompletionGeneration = 0L,
                completionGeneration = 0L,
                resumeRequested = false
            )
        )
    }

    @Test
    fun exerciseCompletionRefetchesProgressWithoutRelaunch() {
        TaskCompletionCache.clear()
        val before = TaskCompletionCache.completionUpdates.value
        TaskCompletionCache.markCompleted("TASK-1")
        val after = TaskCompletionCache.completionUpdates.value
        assertTrue(after != before)
        assertTrue(TaskCompletionCache.isCompleted("TASK-1"))
        assertTrue(
            StudentDataFreshness.shouldRefetchProgress(
                previousTaskIdsKey = "TASK-1",
                currentTaskIdsKey = "TASK-1",
                previousCompletionGeneration = before,
                completionGeneration = after,
                resumeRequested = false
            )
        )
        assertFalse(
            StudentDataFreshness.shouldRefetchProgress(
                previousTaskIdsKey = "TASK-1",
                currentTaskIdsKey = "TASK-1",
                previousCompletionGeneration = after,
                completionGeneration = after,
                resumeRequested = false
            )
        )
        TaskCompletionCache.clear()
    }

    @Test
    fun resumeRefetchesSameTaskIdsWithoutWaitingForPoll() {
        assertTrue(
            StudentDataFreshness.shouldRefetchProgress(
                previousTaskIdsKey = "TASK-1",
                currentTaskIdsKey = "TASK-1",
                previousCompletionGeneration = 1L,
                completionGeneration = 1L,
                resumeRequested = true
            )
        )
    }

    @Test
    fun sameTaskIdsOnPollDoNotForceProgressRefetch() {
        assertFalse(
            StudentDataFreshness.shouldRefetchProgress(
                previousTaskIdsKey = "A,B",
                currentTaskIdsKey = "A,B",
                previousCompletionGeneration = 7L,
                completionGeneration = 7L,
                resumeRequested = false
            )
        )
        assertEquals("A,B", StudentDataFreshness.taskIdsKey(listOf("B", "A")))
    }

    @Test
    fun noCompletionOrEvidencePushSoTeacherStartUsesPoll() {
        assertFalse(StudentDataFreshness.HAS_COMPLETION_PUSH)
        assertFalse(StudentDataFreshness.HAS_EVIDENCE_PUSH)
        assertFalse(StudentDataFreshness.HAS_TASK_START_PUSH)
        assertNotNull(StudentDataFreshness.pollIntervalMsForTeacherStart())
    }
}
