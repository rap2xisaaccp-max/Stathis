package citu.edu.stathis.mobile.features.common.refresh

/**
 * Student-facing freshness policy. There is no STOMP topic for teacher task-start,
 * graded completion, or evidence — only live exercise-progress and vitals.
 *
 * Visible classroom/task lists therefore use bounded resume-gated polling.
 * Progress/attempts refresh on resume, navigation, and [citu.edu.stathis.mobile.features.tasks.presentation.TaskCompletionCache]
 * after a successful save — not via a second global poller.
 */
object StudentDataFreshness {
    const val VISIBLE_LIST_POLL_MS = 15_000L
    const val MIN_REPEAT_FETCH_MS = 1_500L

    const val HAS_TASK_START_PUSH = false
    const val HAS_COMPLETION_PUSH = false
    const val HAS_EVIDENCE_PUSH = false

    fun pollIntervalMsForTeacherStart(): Long? =
        if (HAS_TASK_START_PUSH) null else VISIBLE_LIST_POLL_MS

    fun shouldPollWhileVisible(screenResumed: Boolean, pollIntervalMs: Long?): Boolean =
        screenResumed && pollIntervalMs != null && pollIntervalMs > 0L

    fun shouldSkipDuplicateFetch(
        lastAtElapsedMs: Long,
        nowElapsedMs: Long,
        minGapMs: Long = MIN_REPEAT_FETCH_MS
    ): Boolean = nowElapsedMs - lastAtElapsedMs < minGapMs && lastAtElapsedMs > 0L

    fun taskIdsKey(taskIds: Iterable<String>): String =
        taskIds.sorted().joinToString(",")

    fun shouldRefetchProgress(
        previousTaskIdsKey: String,
        currentTaskIdsKey: String,
        previousCompletionGeneration: Long,
        completionGeneration: Long,
        resumeRequested: Boolean
    ): Boolean {
        if (resumeRequested) return true
        if (completionGeneration != previousCompletionGeneration) return true
        return previousTaskIdsKey != currentTaskIdsKey
    }

    fun studentVisibleStarted(active: Boolean?, started: Boolean?): Boolean =
        (active ?: true) && started == true
}
