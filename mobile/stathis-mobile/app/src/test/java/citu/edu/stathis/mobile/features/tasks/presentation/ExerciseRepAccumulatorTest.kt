package citu.edu.stathis.mobile.features.tasks.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseRepAccumulatorTest {

    @Test
    fun accumulatesNormally() {
        val acc = ExerciseRepAccumulator()
        assertEquals(3, acc.applyDetectorReps(3))
        assertEquals(7, acc.applyDetectorReps(7))
    }

    @Test
    fun survivesDetectorResetAfterReverify() {
        val acc = ExerciseRepAccumulator()
        acc.applyDetectorReps(6)
        // Camera/detector reset mid-session → absolute count restarts at 0
        assertEquals(6, acc.applyDetectorReps(0))
        assertEquals(8, acc.applyDetectorReps(2))
        assertEquals(6, acc.anchorForTests())
    }

    @Test
    fun validAndAttemptedStaySeparateAcrossADetectorReset() {
        val acc = ExerciseRepAccumulator()
        val first = acc.applyCounts(2, 4)
        assertEquals(2, first.valid)
        assertEquals(4, first.attempted)
        val dropped = acc.applyCounts(0, 0)
        assertEquals(2, dropped.valid)
        assertEquals(4, dropped.attempted)
        val next = acc.applyCounts(1, 1)
        assertEquals(3, next.valid)
        assertEquals(5, next.attempted)
    }

    @Test
    fun resetClearsSession() {
        val acc = ExerciseRepAccumulator()
        acc.applyDetectorReps(5)
        acc.reset()
        assertEquals(0, acc.applyDetectorReps(0))
        assertEquals(2, acc.applyDetectorReps(2))
    }
}
