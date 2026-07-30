package citu.edu.stathis.mobile.features.tasks.presentation

/**
 * Pure helpers for automatic exercise completion when a configured goal is met.
 *
 * Completion requires valid counted reps and/or elapsed goal time — not raw camera motion.
 */
object ExerciseGoalCompletion {
    /**
     * Returns true when the student has met the configured rep goal and/or time goal.
     * Goals at or below zero are ignored (do not auto-complete on unset goals).
     */
    fun shouldAutoComplete(
        actualReps: Int,
        goalReps: Int,
        actualTimeSeconds: Int,
        goalTimeSeconds: Int
    ): Boolean {
        val repsMet = goalReps > 0 && actualReps >= goalReps
        val timeMet = goalTimeSeconds > 0 && actualTimeSeconds >= goalTimeSeconds
        return repsMet || timeMet
    }

    /**
     * True only for a brand-new attempt. If parent already holds session reps
     * (e.g. overlay remounted during face re-verify), counters must not be cleared.
     */
    fun shouldClearCountersOnStart(existingSessionReps: Int): Boolean = existingSessionReps <= 0
}

/**
 * Single-flight guard so one exercise attempt cannot be submitted twice
 * (timer auto-complete + Finish, retries, recomposition).
 */
class ExerciseSubmissionGuard {
    @Volatile
    private var submitted: Boolean = false

    fun tryAcquire(): Boolean {
        synchronized(this) {
            if (submitted) return false
            submitted = true
            return true
        }
    }

    fun reset() {
        synchronized(this) {
            submitted = false
        }
    }

    fun isSubmitted(): Boolean = synchronized(this) { submitted }
}
