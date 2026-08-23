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
    fun coachableErrorFlickerDoesNotClearOrRearm() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.8, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.KNEES_IN, 0L, 0)
        val flicker =
            listOf(
                FormErrorCode.PIKE,
                FormErrorCode.SAG,
                FormErrorCode.PIKE,
                FormErrorCode.KNEES_IN,
                FormErrorCode.LOW_ROM,
                FormErrorCode.SAG
            )
        var claims = 0
        var t = 200L
        repeat(40) { i ->
            val code = flicker[i % flicker.size]
            if (life.tryClaimDelivery(code, 0.8, t, 8_000L, currentReps = 0) != null) {
                claims++
            }
            t += 200L
        }
        assertEquals(0, claims)
        assertEquals(1, life.currentCycleSeq())
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        assertEquals(FormErrorCode.KNEES_IN, life.openErrorCode())
    }

    @Test
    fun switchingToAnotherCoachableErrorDoesNotRearmWhileFirstCycleOpen() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.KNEES_IN, 0L, 0)
        repeat(20) { i ->
            assertNull(
                life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.7, 500L + i * 100L, 8_000L, 0)
            )
        }
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        assertEquals(FormErrorCode.KNEES_IN, life.openErrorCode())
        assertEquals(1, life.currentCycleSeq())
    }

    @Test
    fun technicalSignalsDoNotClearOpenCycle() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        repeat(6) { i ->
            assertNull(
                life.tryClaimDelivery(
                    FormErrorCode.LOW_VISIBILITY,
                    0.9,
                    200L + i,
                    8_000L,
                    0
                )
            )
        }
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        assertTrue(life.hasOpenIntervention())
        // Genuine clean ticks still close after technical frames were ignored.
        repeat(3) { i -> life.tryClaimDelivery(null, 0.0, 400L + i, 8_000L, 0) }
        assertEquals(InterventionPhase.COOLDOWN, life.phase)
        assertFalse(life.hasOpenIntervention())
    }

    @Test
    fun genuineClearThenCooldownAllowsSecondClaim() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.KNEES_IN, 0L, 0)
        repeat(3) { i -> life.tryClaimDelivery(null, 0.0, 100L + i, 8_000L, 0) }
        assertEquals(InterventionPhase.COOLDOWN, life.phase)
        assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 1_000L, 8_000L, 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 9_000L, 8_000L, 0))
        assertEquals(2, life.currentCycleSeq())
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
    fun heldErrorDoesNotRearmFromTimeAlone() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        // Same error, same reps, far past response window (10s) and cooldown (8s).
        var claims = 0
        var t = 1_000L
        while (t <= 40_000L) {
            if (life.tryClaimDelivery(FormErrorCode.SAG, 0.7, t, 8_000L, currentReps = 0) != null) {
                claims++
            }
            t += 100L
        }
        assertEquals(0, claims)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        // Legitimate re-arm: sustained clear. Cooldown already elapsed during the 40s hold,
        // so the lifecycle may skip straight to OBSERVING.
        repeat(3) { i ->
            life.tryClaimDelivery(null, 0.0, 41_000L + i, 8_000L, 0)
        }
        assertFalse(life.hasOpenIntervention())
        assertTrue(
            life.phase == InterventionPhase.COOLDOWN ||
                life.phase == InterventionPhase.OBSERVING
        )
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 50_000L, 8_000L, 0))
    }
}
