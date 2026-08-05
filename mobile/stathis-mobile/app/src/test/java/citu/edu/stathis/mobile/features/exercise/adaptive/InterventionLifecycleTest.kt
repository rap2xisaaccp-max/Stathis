package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionLifecycleTest {

    @Test
    fun requiresConfirmTicksBeforeAllowing() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3)
        assertNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.6, now = 1_000L, cooldownMs = 8_000L, currentReps = 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.6, now = 1_100L, cooldownMs = 8_000L, currentReps = 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.6, now = 1_200L, cooldownMs = 8_000L, currentReps = 0))
        assertEquals(InterventionPhase.INTERVENTION_PENDING, life.phase)
    }

    @Test
    fun blocksDuplicateWhileUnresolvedSameError() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, currentReps = 0)
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 1_000L, 8_000L, 0))
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
    }

    @Test
    fun claimBeforeMarkDeliveredBlocksBurst() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        var blocked = 0
        for (i in 1..20) {
            if (life.tryClaimDelivery(FormErrorCode.SAG, 0.6, i * 100L, 8_000L, 0) == null) {
                blocked++
            }
        }
        assertEquals(20, blocked)
        assertEquals(InterventionPhase.INTERVENTION_PENDING, life.phase)
    }

    @Test
    fun closesResponseAfterValidReps() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, currentReps = 0)
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 500L, 8_000L, 2))
        // At +3 reps observation advances into cooldown (8s not elapsed → stay COOLDOWN)
        life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 2_000L, 8_000L, 3)
        assertTrue(
            life.phase == InterventionPhase.COOLDOWN || life.phase == InterventionPhase.OBSERVING
        )
        assertFalse(life.hasOpenIntervention())
    }

    @Test
    fun cooldownBlocksImmediateRepeat() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 1)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        // Close via sustained clear
        repeat(3) { life.tryClaimDelivery(null, 0.0, 100L + it, 8_000L, 0) }
        assertEquals(InterventionPhase.COOLDOWN, life.phase)
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 1_000L, 8_000L, 0))
        // After full cooldown, one new cycle allowed
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 9_000L, 8_000L, 0))
    }

    @Test
    fun differentErrorBlockedWhileUnresolved() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        assertNull(life.tryClaimDelivery(FormErrorCode.CHEST_UP, 0.6, 500L, 8_000L, 0))
    }

    @Test
    fun escalatesIntensityAfterRepeatedDeliveries() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 1)
        assertEquals(InstructionIntensity.REMINDER, life.intensityFor(FormErrorCode.DEPTH_LOW))
        life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.6, 0L, 8_000L, 0)
        life.markDelivered(FormErrorCode.DEPTH_LOW, 0L, 0)
        life.markResponseClosed(successful = false)
        assertEquals(InstructionIntensity.ESCALATION, life.intensityFor(FormErrorCode.DEPTH_LOW))
    }

    @Test
    fun persistentErrorDoesNotRestartAfterResponseWindowAndCooldown() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3)
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 1L, 8_000L, 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 2L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        life.markResponseClosed(successful = false)

        repeat(10) { index ->
            assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 9_000L + index, 8_000L, 0))
        }
        assertEquals(0, life.confirmedTicks())
        assertEquals(1, life.currentCycleSeq())
    }

    @Test
    fun clearFramesRearmSameError() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3)
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 1L, 8_000L, 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 2L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        life.markResponseClosed(successful = false)

        repeat(3) { index ->
            assertNull(life.tryClaimDelivery(null, 0.0, 9_000L + index, 8_000L, 0))
        }
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 10_000L, 8_000L, 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 10_001L, 8_000L, 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 10_002L, 8_000L, 0))
    }

    @Test
    fun differentErrorCanStillBeDeliveredWhilePreviousErrorIsDisarmed() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        life.markResponseClosed(successful = false)

        assertNotNull(life.tryClaimDelivery(FormErrorCode.CHEST_UP, 0.6, 9_000L, 8_000L, 0))
    }

    @Test
    fun meaningfulRepBoundaryRearmsSameError() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        life.markResponseClosed(successful = false)

        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 9_000L, 8_000L, 3))
    }

    @Test
    fun resetRearmsPersistentError() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        life.markResponseClosed(successful = false)
        life.reset()

        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 9_000L, 8_000L, 0))
    }
}
