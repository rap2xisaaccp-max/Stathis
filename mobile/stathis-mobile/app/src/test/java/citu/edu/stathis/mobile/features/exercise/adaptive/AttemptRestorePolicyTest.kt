package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen decisions shared with ExerciseTemplateRenderer. The module has no Compose
 * UI-test dependency, so the renderer calls these functions instead of duplicating them.
 */
class AttemptRestorePolicyTest {
    @Test
    fun matchingRestoreLeavesCountingPausedUntilTheOverlayTimer() {
        val flags = applyRestoredAttemptFlags(apply = true, exerciseCompleted = false)
        assertEquals(true, flags?.isExerciseStarted)
        assertEquals(true, flags?.adaptiveSessionLive)
        assertEquals(true, flags?.sessionInProgress)
        assertEquals(false, flags?.sessionCountingActive)
        assertFalse(overlayTimerStartsNewAttempt(running = false, sessionInProgress = true))
        assertFalse(overlayTimerStartsNewAttempt(running = true, sessionInProgress = true))
        assertTrue(overlayTimerStartsNewAttempt(running = true, sessionInProgress = false))
    }

    @Test
    fun completedOrRejectedRestoreDoesNotChangeTheScreen() {
        assertNull(applyRestoredAttemptFlags(apply = false, exerciseCompleted = false))
        assertNull(applyRestoredAttemptFlags(apply = true, exerciseCompleted = true))
    }

    @Test
    fun startOrRetryWinsTheRaceAgainstALateRestore() {
        val gate = AttemptRestoreGate()
        assertTrue(shouldApplyRestoredAttempt(blocked = false, generationAtStart = 1, generationNow = 1, restored = true))

        gate.blockForNewAttempt()
        assertFalse(
            shouldApplyRestoredAttempt(
                blocked = gate.isBlocked(),
                generationAtStart = 1,
                generationNow = 1,
                restored = true
            )
        )
        assertFalse(
            shouldApplyRestoredAttempt(
                blocked = false,
                generationAtStart = 1,
                generationNow = 2,
                restored = true
            )
        )

        gate.cancelNewAttemptBlock()
        assertTrue(gate.mayApplyRestore())
        assertTrue(
            shouldApplyRestoredAttempt(
                blocked = gate.isBlocked(),
                generationAtStart = 3,
                generationNow = 3,
                restored = true
            )
        )
        assertFalse(
            shouldApplyRestoredAttempt(
                blocked = false,
                generationAtStart = 3,
                generationNow = 3,
                restored = false
            )
        )
    }
}
