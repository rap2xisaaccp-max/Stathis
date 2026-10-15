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
    fun increasingRepsWhileErrorRemainsDoesNotCloseObservation() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, currentReps = 0)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        // 3, 6, 9, 12 additional bad reps — past cooldown — still must not close or re-arm.
        for (reps in listOf(3, 6, 9, 12, 20)) {
            val now = reps * 1_000L
            assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.6, now, 8_000L, reps))
            assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
            assertTrue(life.hasOpenIntervention())
            assertEquals(FormErrorCode.SAG, life.openErrorCode())
            assertEquals(1, life.currentCycleSeq())
        }
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
    fun firstClaimIsReminderThenSuccessfulClearRecurrenceEscalates() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertEquals(InstructionIntensity.REMINDER, life.intensityFor(FormErrorCode.DEPTH_LOW))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.6, 0L, 8_000L, 0))
        assertEquals(InstructionIntensity.REMINDER, life.intensityFor(FormErrorCode.DEPTH_LOW))
        life.markDelivered(FormErrorCode.DEPTH_LOW, 0L, 0)
        assertEquals(InstructionIntensity.ESCALATION, life.intensityFor(FormErrorCode.DEPTH_LOW))
        repeat(3) { i -> life.tryClaimDelivery(null, 0.0, 100L + i, 8_000L, 0) }
        assertEquals(InstructionIntensity.ESCALATION, life.intensityFor(FormErrorCode.DEPTH_LOW))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.6, 9_000L, 8_000L, 0))
        assertEquals(InstructionIntensity.ESCALATION, life.intensityFor(FormErrorCode.DEPTH_LOW))
    }

    @Test
    fun heldErrorDoesNotIncrementIntensity() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        val afterFirst = life.intensityFor(FormErrorCode.SAG)
        assertEquals(InstructionIntensity.ESCALATION, afterFirst)
        var t = 1_000L
        while (t <= 20_000L) {
            assertNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, t, 8_000L, 0))
            assertEquals(afterFirst, life.intensityFor(FormErrorCode.SAG))
            t += 500L
        }
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
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

    @Test
    fun additionalBadRepsCannotRearmKneesIn() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.KNEES_IN, 0L, currentReps = 0)
        var claims = 0
        var t = 200L
        var reps = 0
        while (reps <= 12) {
            if (life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, t, 8_000L, reps) != null) {
                claims++
            }
            t += 1_000L
            reps += 3
        }
        assertEquals(0, claims)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        assertEquals(1, life.currentCycleSeq())
        assertEquals(FormErrorCode.KNEES_IN, life.openErrorCode())
    }

    @Test
    fun threeGenuineCleanTicksCloseCycle() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 0L, 8_000L, 5))
        life.markDelivered(FormErrorCode.KNEES_IN, 0L, currentReps = 5)
        assertNull(life.tryClaimDelivery(null, 0.0, 100L, 8_000L, 5))
        assertNull(life.tryClaimDelivery(null, 0.0, 101L, 8_000L, 6))
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        assertTrue(life.hasOpenIntervention())
        life.tryClaimDelivery(null, 0.0, 102L, 8_000L, 7)
        assertFalse(life.hasOpenIntervention())
        assertTrue(
            life.phase == InterventionPhase.COOLDOWN || life.phase == InterventionPhase.OBSERVING
        )
    }

    @Test
    fun resetAllowsNewClaimWithoutClear() {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3)
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 0L, 8_000L, 0))
        life.markDelivered(FormErrorCode.SAG, 0L, 0)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, life.phase)
        life.reset()
        assertEquals(InterventionPhase.OBSERVING, life.phase)
        assertFalse(life.hasOpenIntervention())
        assertEquals(0, life.currentCycleSeq())
        assertNotNull(life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 50L, 8_000L, 0))
        assertEquals(1, life.currentCycleSeq())
    }

    @Test
    fun genuineClearCooldownThenConfirmTicksAllowsSameOrOtherError() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3)
        assertNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 0L, 8_000L, 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 50L, 8_000L, 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.KNEES_IN, 0.7, 100L, 8_000L, 0))
        life.markDelivered(FormErrorCode.KNEES_IN, 100L, 0)
        repeat(3) { i -> life.tryClaimDelivery(null, 0.0, 200L + i, 8_000L, 0) }
        assertFalse(life.hasOpenIntervention())
        assertNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.7, 1_000L, 8_000L, 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.7, 9_050L, 8_000L, 0))
        assertNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.7, 9_100L, 8_000L, 0))
        assertNotNull(life.tryClaimDelivery(FormErrorCode.DEPTH_LOW, 0.7, 9_150L, 8_000L, 0))
        assertEquals(2, life.currentCycleSeq())
    }
}
