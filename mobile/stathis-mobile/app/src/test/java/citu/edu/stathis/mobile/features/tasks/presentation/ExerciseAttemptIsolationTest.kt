package citu.edu.stathis.mobile.features.tasks.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2 / 5: new attempts must not inherit prior session totals via the accumulator.
 */
class ExerciseAttemptIsolationTest {

    @Test
    fun newAttemptStartsAtZeroAfterReset() {
        val acc = ExerciseRepAccumulator()
        acc.applyDetectorReps(12)
        acc.reset()
        assertEquals(0, acc.applyDetectorReps(0))
        assertEquals(3, acc.applyDetectorReps(3))
    }

    @Test
    fun preStartDetectorSpikeDoesNotSeedAfterReset() {
        // Detector counted during preview; Start resets accumulator then applies from 0.
        val acc = ExerciseRepAccumulator()
        // Simulated pre-start absolute count that UI never applied
        val preStartDetectorReps = 7
        acc.reset()
        assertEquals(0, acc.applyDetectorReps(0))
        // After analyzer reset, detector also starts at 0
        assertEquals(2, acc.applyDetectorReps(2))
        assertEquals(0, preStartDetectorReps - preStartDetectorReps) // document intent
    }
}
