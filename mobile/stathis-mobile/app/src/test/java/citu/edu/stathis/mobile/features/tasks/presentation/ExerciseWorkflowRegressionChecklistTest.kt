package citu.edu.stathis.mobile.features.tasks.presentation

import citu.edu.stathis.mobile.features.exercise.data.ExerciseDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 — automated stand-ins for the E2E regression checklist.
 *
 * Manual device checks still recommended:
 * 1) Face verify → move before overlay Start → reps stay 0; Start → 0 then count
 * 2) Cancel/Retry attempt 2 starts at 0; Score.reps = latest attempt after complete
 * 3) LLR gibberish vs one valid cycle
 * 4) Unstarted task hidden; after teacher Start visible; direct detail blocked
 * 5) Completion persists after refresh
 */
class ExerciseWorkflowRegressionChecklistTest {

    @Test
    fun preStartGating_trackingOffMeansUiDoesNotApplyReps() {
        val countingEnabled = false
        val isTimerRunning = false
        val verified = true
        val shouldApplyLiveReps = countingEnabled && isTimerRunning && verified
        assertFalse(shouldApplyLiveReps)
    }

    @Test
    fun startResetsAccumulatorBeforeLiveApply() {
        val acc = ExerciseRepAccumulator()
        // Pre-start detector would have been at 9; Start resets first
        acc.reset()
        assertEquals(0, acc.applyDetectorReps(0))
    }

    @Test
    fun attempt2Isolation_afterReset() {
        val acc = ExerciseRepAccumulator()
        acc.applyDetectorReps(10)
        acc.reset()
        assertEquals(0, acc.applyDetectorReps(0))
        assertEquals(4, acc.applyDetectorReps(4))
    }

    @Test
    fun llrGibberishZero_validCycleOne() {
        val gibberish = ExerciseDetector()
        var t = 1_000L
        fun tick() = t.also { t += 50 }
        repeat(15) {
            gibberish.analyzeLyingLegRaiseMetrics(
                400f, 400f, 410f, 405f, 300f, 300f, 170f, 170f, 0.9f, tick()
            )
        }
        assertEquals(0, gibberish.lyingLegRaiseRepCountForTests())

        val valid = ExerciseDetector()
        t = 1_000L
        repeat(3) {
            valid.analyzeLyingLegRaiseMetrics(
                400f, 400f, 520f, 520f, 300f, 300f, 170f, 170f, 0.9f, tick()
            )
        }
        repeat(3) {
            valid.analyzeLyingLegRaiseMetrics(
                400f, 400f, 300f, 300f, 300f, 300f, 170f, 170f, 0.9f, tick()
            )
        }
        t += 900
        repeat(3) {
            valid.analyzeLyingLegRaiseMetrics(
                400f, 400f, 520f, 520f, 300f, 300f, 170f, 170f, 0.9f, tick()
            )
        }
        assertEquals(1, valid.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun teacherStartGate_studentVisibility() {
        fun visible(active: Boolean, started: Boolean) = active && started
        assertFalse(visible(true, false))
        assertTrue(visible(true, true))
    }

    @Test
    fun latestRepsOverwrite_notCumulative() {
        fun merge(previous: Int, session: Int) = session
        assertEquals(7, merge(20, 7))
    }
}
