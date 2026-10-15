package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachableErrorPriorityTest {

    @Test
    fun highestSeverityWinsThenStableTieBreak() {
        assertEquals(
            FormErrorCode.SAG,
            CoachableErrorPriority.select(
                "PUSH_UP",
                listOf(FormErrorCode.LOW_ROM, FormErrorCode.PIKE, FormErrorCode.SAG)
            )
        )
        assertEquals(
            FormErrorCode.KNEES_IN,
            CoachableErrorPriority.select(
                "SQUATS",
                listOf(FormErrorCode.CHEST_UP, FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN)
            )
        )
        assertEquals(
            FormErrorCode.KNEES_IN,
            CoachableErrorPriority.select(
                "STATIC_LUNGES",
                listOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN)
            )
        )
        assertEquals(
            FormErrorCode.SAG,
            CoachableErrorPriority.select(
                "GLUTE_BRIDGE",
                listOf(FormErrorCode.LOW_ROM, FormErrorCode.SAG)
            )
        )
        assertEquals(
            FormErrorCode.LEGS_BENT,
            CoachableErrorPriority.select(
                "LYING_LEG_RAISES",
                listOf(FormErrorCode.LOW_ROM, FormErrorCode.LEGS_BENT)
            )
        )
    }
}
