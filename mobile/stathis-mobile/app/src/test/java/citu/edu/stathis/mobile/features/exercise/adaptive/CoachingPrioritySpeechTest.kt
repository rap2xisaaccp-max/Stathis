package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachingPrioritySpeechTest {

    @Test
    fun correctionOutranksCameraAndEncouragement() {
        val gate = CoachingTtsSpeechGate().also { it.markReady(0L) }
        gate.markSpoken(CoachingTtsLane.TECHNICAL, 1_000L, "Step back.")
        val praise = gate.requestEncouragement("Good rep!", 1_200L)
        assertEquals(CoachingTtsAction.SKIP_BLOCKED, praise.action)

        val correction = gate.requestPhysical("Keep your knees aligned with your toes.", 1_300L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, correction.action)
        gate.markSpoken(CoachingTtsLane.PHYSICAL, 1_300L, correction.message)

        val duringCorrection = gate.requestEncouragement("Nice form!", 1_400L)
        assertEquals(CoachingTtsAction.SKIP_BLOCKED, duringCorrection.action)
        val camera = gate.requestTechnical("Move to the center of the camera frame.", 1_500L)
        assertEquals(CoachingTtsAction.QUEUE_PENDING, camera.action)
    }

    @Test
    fun rejectedRepReplacesAnInWindowCorrectionWithoutQueuingPraise() {
        val gate = CoachingTtsSpeechGate().also { it.markReady(0L) }
        gate.markSpoken(
            CoachingTtsLane.PHYSICAL,
            1_000L,
            gate.requestPhysical("Keep your knees aligned with your toes.", 1_000L).message
        )
        gate.requestEncouragement("Great job!", 1_100L)
        val combined =
            gate.requestPhysicalImmediate("Rep not counted. Keep your knees aligned with your toes.", 1_200L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, combined.action)
        assertNull(gate.pendingEncouragement)
        assertEquals(
            "Rep not counted. Keep your knees aligned with your toes.",
            combined.message
        )
    }

    @Test
    fun encouragementSpeaksWhenHigherLanesAreIdle() {
        val gate = CoachingTtsSpeechGate().also { it.markReady(0L) }
        val praise = gate.requestEncouragement("You're doing well!", 5_000L)
        assertEquals(CoachingTtsAction.SPEAK_NOW, praise.action)
        assertEquals(CoachingTtsLane.ENCOURAGEMENT, praise.lane)
    }
}
