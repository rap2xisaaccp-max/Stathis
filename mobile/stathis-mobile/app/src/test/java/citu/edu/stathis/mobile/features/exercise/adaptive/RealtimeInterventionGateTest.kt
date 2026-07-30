package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeInterventionGateTest {

    @Test
    fun requiresConfirmTicksBeforeAllowing() {
        val gate = RealtimeInterventionGate(confirmTicks = 3, maxPerMinute = 4)
        assertFalse(gate.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, now = 1_000L, cooldownMs = 8_000L))
        assertFalse(gate.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, now = 1_100L, cooldownMs = 8_000L))
        assertTrue(gate.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, now = 1_200L, cooldownMs = 8_000L))
    }

    @Test
    fun resetsConfirmWhenErrorChanges() {
        val gate = RealtimeInterventionGate(confirmTicks = 3)
        gate.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, 1_000L, 8_000L)
        gate.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.6, 1_100L, 8_000L)
        // Switch error — confirmation restarts
        assertFalse(gate.shouldDeliver(FormErrorCode.KNEES_IN, 0.6, 1_200L, 8_000L))
        assertFalse(gate.shouldDeliver(FormErrorCode.KNEES_IN, 0.6, 1_300L, 8_000L))
        assertTrue(gate.shouldDeliver(FormErrorCode.KNEES_IN, 0.6, 1_400L, 8_000L))
    }

    @Test
    fun enforcesCooldownBetweenDeliveries() {
        val gate = RealtimeInterventionGate(confirmTicks = 1)
        assertTrue(gate.shouldDeliver(FormErrorCode.SAG, 0.6, 0L, 8_000L))
        gate.markDelivered(0L)
        assertFalse(gate.shouldDeliver(FormErrorCode.SAG, 0.6, 4_000L, 8_000L))
        assertTrue(gate.shouldDeliver(FormErrorCode.SAG, 0.6, 8_000L, 8_000L))
    }

    @Test
    fun highSeverityHalvesCooldown() {
        val gate = RealtimeInterventionGate(confirmTicks = 1, highSeverity = 0.75)
        assertTrue(gate.shouldDeliver(FormErrorCode.SAG, 0.9, 0L, 8_000L))
        gate.markDelivered(0L)
        assertTrue(gate.shouldDeliver(FormErrorCode.SAG, 0.9, 4_000L, 8_000L))
    }

    @Test
    fun capsInterventionsPerMinute() {
        val gate = RealtimeInterventionGate(confirmTicks = 1, maxPerMinute = 2)
        assertTrue(gate.shouldDeliver(FormErrorCode.PIKE, 0.6, 0L, 1_000L))
        gate.markDelivered(0L)
        assertTrue(gate.shouldDeliver(FormErrorCode.PIKE, 0.6, 2_000L, 1_000L))
        gate.markDelivered(2_000L)
        assertFalse(gate.shouldDeliver(FormErrorCode.PIKE, 0.6, 4_000L, 1_000L))
    }

    @Test
    fun rejectsBelowMinSeverity() {
        val gate = RealtimeInterventionGate(confirmTicks = 1, minSeverity = 0.25)
        assertFalse(gate.shouldDeliver(FormErrorCode.DEPTH_LOW, 0.1, 0L, 8_000L))
    }
}
