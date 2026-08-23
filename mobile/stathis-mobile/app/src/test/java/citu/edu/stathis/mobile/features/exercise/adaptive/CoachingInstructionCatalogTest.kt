package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun allowedCodesHaveReviewedCopyAndNotPikeFallbackOnSquats() {
        val matrix =
            mapOf(
                "SQUATS" to listOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
                "PUSH_UP" to listOf(FormErrorCode.PIKE, FormErrorCode.SAG, FormErrorCode.LOW_ROM),
                "STATIC_LUNGES" to listOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
                "GLUTE_BRIDGE" to listOf(FormErrorCode.LOW_ROM, FormErrorCode.SAG),
                "LYING_LEG_RAISES" to listOf(FormErrorCode.LEGS_BENT, FormErrorCode.LOW_ROM, FormErrorCode.SAG)
            )
        matrix.forEach { (exercise, codes) ->
            codes.forEach { code ->
                assertTrue(
                    CoachingInstructionCatalog.hasReviewedInstruction(exercise, code)
                )
                val reminder =
                    CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.REMINDER)
                assertFalse(reminder.contains("Slow down and check your alignment", ignoreCase = true))
                assertFalse(reminder.contains("Adjust your form", ignoreCase = true))
            }
        }
        val squatPike =
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.PIKE, InstructionIntensity.REMINDER)
        val pushPike =
            CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.PIKE, InstructionIntensity.REMINDER)
        assertNotEquals(pushPike, squatPike)
        assertFalse(CoachingInstructionCatalog.hasReviewedInstruction("SQUATS", FormErrorCode.PIKE))
    }

    @Test
    fun escalationDiffersFromReminder() {
        assertNotEquals(
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.DEPTH_LOW, InstructionIntensity.REMINDER),
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.DEPTH_LOW, InstructionIntensity.ESCALATION)
        )
    }
}
