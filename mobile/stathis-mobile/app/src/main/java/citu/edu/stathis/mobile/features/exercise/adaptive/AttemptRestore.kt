package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Blocks a late process-death restore after the user has chosen Start or Retry
 * in this composition. Configuration change creates a new gate, so a rotation
 * can still restore a cycle that disposal did not abandon.
 */
internal class AttemptRestoreGate {
    private var blocked = false

    fun blockForNewAttempt() {
        blocked = true
    }

    /** A Start click that did not actually begin an attempt may restore again. */
    fun cancelNewAttemptBlock() {
        blocked = false
    }

    fun mayApplyRestore(): Boolean = !blocked

    fun isBlocked(): Boolean = blocked
}

internal data class RestoredAttemptFlags(
    val isExerciseStarted: Boolean,
    val adaptiveSessionLive: Boolean,
    val sessionInProgress: Boolean,
    val sessionCountingActive: Boolean
)

/**
 * True only when restore finished for the same attempt generation the screen
 * observed, and the user has not since chosen Start or Retry.
 */
internal fun shouldApplyRestoredAttempt(
    blocked: Boolean,
    generationAtStart: Int,
    generationNow: Int,
    restored: Boolean
): Boolean = restored && !blocked && generationAtStart == generationNow

/**
 * The overlay timer starts a new coaching session only when it is running and
 * this screen has not already resumed an attempt. A restored attempt sets
 * [sessionInProgress] so the timer enables counting without [startSession].
 */
internal fun overlayTimerStartsNewAttempt(running: Boolean, sessionInProgress: Boolean): Boolean =
    running && !sessionInProgress

/**
 * UI state applied after a matching restore. Counting stays off until the
 * overlay timer sets it. Returns null when the restore must not change the screen.
 */
internal fun applyRestoredAttemptFlags(
    apply: Boolean,
    exerciseCompleted: Boolean
): RestoredAttemptFlags? {
    if (!apply || exerciseCompleted) return null
    return RestoredAttemptFlags(
        isExerciseStarted = true,
        adaptiveSessionLive = true,
        sessionInProgress = true,
        sessionCountingActive = false
    )
}
