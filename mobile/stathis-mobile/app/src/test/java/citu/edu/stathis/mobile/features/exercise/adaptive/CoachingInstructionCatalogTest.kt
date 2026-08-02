package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingInstructionCatalogTest {

    @Test
    fun fiveExercisesHaveDistinctPrimaryReminders() {
        val messages =
            setOf(
                CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.DEPTH_LOW, InstructionIntensity.REMINDER),
                CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.SAG, InstructionIntensity.REMINDER),
                CoachingInstructionCatalog.messageText("GLUTE_BRIDGE", FormErrorCode.LOW_ROM, InstructionIntensity.REMINDER),
                CoachingInstructionCatalog.messageText("STATIC_LUNGES", FormErrorCode.KNEES_IN, InstructionIntensity.REMINDER),
                CoachingInstructionCatalog.messageText("LYING_LEG_RAISES", FormErrorCode.LEGS_BENT, InstructionIntensity.REMINDER)
            )
        assertEquals(5, messages.size)
        assertTrue(messages.none { it.contains("fix form", ignoreCase = true) })
    }

    @Test
    fun escalationDiffersFromReminder() {
        assertNotEquals(
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.DEPTH_LOW, InstructionIntensity.REMINDER),
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.DEPTH_LOW, InstructionIntensity.ESCALATION)
        )
    }
}
