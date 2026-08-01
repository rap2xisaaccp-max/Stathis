package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionLifecycleTest {

    @Test
    fun requiresConfirmTicksBeforeAllowing() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3)
        assertFalse(life.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, now = 1_000L, cooldownMs = 8_000L, currentReps = 0))
        assertFalse(life.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, now = 1_100L, cooldownMs = 8_000L, currentReps = 0))
        assertTrue(life.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, now = 1_200L, cooldownMs = 8_000L, currentReps = 0))
    }

    @Test
    fun blocksDuplicateWhileUnresolvedSameError() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertTrue(life.shouldDeliver(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, currentReps = 0)
        assertFalse(life.shouldDeliver(FormErrorCode.SAG, 0.6, 1_000L, 8_000L, 0))
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
    }

    @Test
    fun closesResponseAfterValidReps() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertTrue(life.shouldDeliver(FormErrorCode.SAG, 0.6, 0L, 1_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, currentReps = 0)
        // Still open at +2 reps
        assertFalse(life.shouldDeliver(FormErrorCode.SAG, 0.6, 500L, 1_000L, 2))
        // At +3 reps observation advances and enters cooldown
        life.shouldDeliver(FormErrorCode.SAG, 0.6, 2_000L, 1_000L, 3)
        assertTrue(
            life.phase == InterventionPhase.COOLDOWN || life.phase == InterventionPhase.OBSERVING
        )
        assertFalse(life.hasOpenIntervention())
    }

    @Test
    fun escalatesIntensityAfterRepeatedDeliveries() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 1)
        assertEquals(InstructionIntensity.REMINDER, life.intensityFor(FormErrorCode.DEPTH_LOW))
        life.markDelivered(FormErrorCode.DEPTH_LOW, 0L, 0)
        life.markResponseClosed(successful = false)
        assertEquals(InstructionIntensity.ESCALATION, life.intensityFor(FormErrorCode.DEPTH_LOW))
    }
}
