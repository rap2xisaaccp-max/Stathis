package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingTtsSpeechGateTest {

    @Test
    fun persistentTechnicalMessageSpeaksOnceUntilCooldown() {
        val gate = readyGate()
        val first = gate.requestTechnical("Move back so your full body is visible.", 1_000L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, first.action)
        gate.markSpoken(CoachingTtsLane.TECHNICAL, 1_000L, first.message)

        repeat(5) { i ->
            val again =
                gate.requestTechnical("Move back so your full body is visible.", 1_100L + i * 100L)
            assertEquals(CoachingTtsAction.SKIP_SAME_TECHNICAL, again.action)
        }

        val afterCooldown =
            gate.requestTechnical("Move back so your full body is visible.", 1_000L + 8_000L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, afterCooldown.action)
    }

    @Test
    fun changedTechnicalMessageCanSpeakImmediately() {
        val gate = readyGate()
        gate.markSpoken(
            CoachingTtsLane.TECHNICAL,
            1_000L,
            gate.requestTechnical("Move to the center of the camera frame.", 1_000L).message
        )
        val changed =
            gate.requestTechnical("Step back so your full body is visible.", 1_200L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, changed.action)
        assertEquals("Step back so your full body is visible.", changed.message)
    }

    @Test
    fun technicalDoesNotConsumePhysicalDebounce() {
        val gate = readyGate()
        gate.markSpoken(
            CoachingTtsLane.TECHNICAL,
            1_000L,
            gate.requestTechnical("Move to the center of the camera frame.", 1_000L).message
        )
        val physical =
            gate.requestPhysical("Keep your knees aligned with your toes.", 1_200L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, physical.action)
    }

    @Test
    fun technicalWaitsForPhysicalUtteranceWindow() {
        val gate = readyGate()
        gate.markSpoken(
            CoachingTtsLane.PHYSICAL,
            1_000L,
            gate.requestPhysical("Keep your knees aligned with your toes.", 1_000L).message
        )
        val queued =
            gate.requestTechnical("Move to the center of the camera frame.", 1_200L)
        assertEquals(CoachingTtsAction.QUEUE_PENDING, queued.action)
        assertEquals("Move to the center of the camera frame.", gate.pendingTechnical)

        val stillQueued =
            gate.requestTechnical("Move to the center of the camera frame.", 2_000L)
        assertEquals(CoachingTtsAction.QUEUE_PENDING, stillQueued.action)

        val afterWindow =
            gate.requestTechnical("Move to the center of the camera frame.", 3_600L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, afterWindow.action)
    }

    @Test
    fun physicalSpeakDropsPendingTechnical() {
        val gate = readyGate()
        gate.markSpoken(
            CoachingTtsLane.PHYSICAL,
            1_000L,
            gate.requestPhysical("Keep your knees aligned with your toes.", 1_000L).message
        )
        gate.requestTechnical("Move to the center of the camera frame.", 1_100L)
        assertEquals("Move to the center of the camera frame.", gate.pendingTechnical)

        val nextPhysical =
            gate.requestPhysical("Keep your knees aligned with your toes.", 3_600L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, nextPhysical.action)
        assertNull(gate.pendingTechnical)
    }

    @Test
    fun notReadyQueuesWithoutMarkingSpoken() {
        val gate = CoachingTtsSpeechGate()
        val physical = gate.requestPhysical("Keep your knees aligned with your toes.", 1_000L)
        assertEquals(CoachingTtsAction.QUEUE_PENDING, physical.action)
        assertEquals(0L, gate.lastPhysicalSpokenAt)

        val flushed = gate.markReady(1_050L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, flushed!!.action)
        assertEquals(CoachingTtsLane.PHYSICAL, flushed.lane)
        gate.markSpoken(CoachingTtsLane.PHYSICAL, 1_050L, flushed.message)
        assertTrue(gate.lastPhysicalSpokenAt > 0L)
    }

    @Test
    fun cancelPendingAndClearTechnicalAreIndependentOfPhysicalHistory() {
        val gate = readyGate()
        gate.markSpoken(
            CoachingTtsLane.PHYSICAL,
            1_000L,
            gate.requestPhysical("Keep your knees aligned with your toes.", 1_000L).message
        )
        gate.requestTechnical("Move to the center of the camera frame.", 1_100L)
        gate.cancelPending()
        assertNull(gate.pendingTechnical)
        assertEquals(1_000L, gate.lastPhysicalSpokenAt)

        gate.clearTechnical()
        val again = gate.requestTechnical("Move to the center of the camera frame.", 1_200L)
        assertEquals(CoachingTtsAction.QUEUE_PENDING, again.action)
    }

    private fun readyGate(): CoachingTtsSpeechGate =
        CoachingTtsSpeechGate().also { it.markReady(0L) }
}
