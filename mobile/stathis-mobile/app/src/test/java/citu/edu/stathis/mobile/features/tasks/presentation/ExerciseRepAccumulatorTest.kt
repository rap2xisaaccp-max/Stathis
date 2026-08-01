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
    fun resetClearsSession() {
        val acc = ExerciseRepAccumulator()
        acc.applyDetectorReps(5)
        acc.reset()
        assertEquals(0, acc.applyDetectorReps(0))
        assertEquals(2, acc.applyDetectorReps(2))
    }
}
