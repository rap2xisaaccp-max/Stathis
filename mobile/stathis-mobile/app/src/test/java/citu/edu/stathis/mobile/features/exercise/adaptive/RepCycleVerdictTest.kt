package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCycleVerdictTest {

    @Test
    fun eachExerciseRejectsOnlyItsOwnStableError() {
        val cases =
            listOf(
                Triple("SQUATS", FormErrorCode.DEPTH_LOW, "Squat deeper — bend your knees more."),
                Triple("SQUATS", FormErrorCode.KNEES_IN, "knees_in"),
                Triple("SQUATS", FormErrorCode.CHEST_UP, "chest_up"),
                Triple("PUSH_UP", FormErrorCode.PIKE, "pike"),
                Triple("PUSH_UP", FormErrorCode.SAG, "sag"),
                Triple("PUSH_UP", FormErrorCode.LOW_ROM, "Lower your chest closer to the ground."),
                Triple("STATIC_LUNGES", FormErrorCode.DEPTH_LOW, "Bend the front knee deeper into the lunge."),
                Triple("STATIC_LUNGES", FormErrorCode.KNEES_IN, "knees_in"),
                Triple("STATIC_LUNGES", FormErrorCode.CHEST_UP, "chest_up"),
                Triple("GLUTE_BRIDGE", FormErrorCode.LOW_ROM, "Drive your hips higher into a full bridge."),
                Triple("GLUTE_BRIDGE", FormErrorCode.SAG, "sag"),
                Triple("LYING_LEG_RAISES", FormErrorCode.LEGS_BENT, "Keep your legs straighter for better control."),
                Triple("LYING_LEG_RAISES", FormErrorCode.LOW_ROM, "Raise your legs higher while keeping them controlled."),
                Triple("LYING_LEG_RAISES", FormErrorCode.SAG, "Keep your hips and torso on the floor.")
            )
        for ((exercise, code, signal) in cases) {
            val verdict = RepCycleVerdict(exercise)
            repeat(3) { feed(verdict, signal) }
            val outcome = verdict.consume()
            assertTrue("$exercise $code should reject", outcome.rejected)
            assertFalse(outcome.valid)
            assertEquals(code, outcome.error)
            assertTrue(outcome.attempted)
        }
    }

    @Test
    fun crossExerciseAndTechnicalSignalsDoNotReject() {
        val squat = RepCycleVerdict("SQUATS")
        repeat(5) { squat.observe(listOf("Hips rising into a pike."), listOf("pike")) }
        repeat(5) { squat.observe(listOf("Low detection confidence"), emptyList()) }
        repeat(5) { squat.observe(listOf("Ensure major body parts are visible."), emptyList()) }
        val outcome = squat.consume()
        assertTrue(outcome.valid)
        assertFalse(outcome.rejected)
        assertNull(outcome.error)
    }

    @Test
    fun technicalFramesDoNotClearALatchedPhysicalError() {
        val verdict = RepCycleVerdict("SQUATS")
        repeat(3) { verdict.observe(emptyList(), listOf("depth_low")) }
        assertEquals(FormErrorCode.DEPTH_LOW, verdict.latched)
        repeat(5) { verdict.observe(listOf("Low detection confidence"), emptyList()) }
        assertEquals(FormErrorCode.DEPTH_LOW, verdict.latched)
        val outcome = verdict.consume()
        assertEquals(FormErrorCode.DEPTH_LOW, outcome.error)
    }

    @Test
    fun depthLatchSurvivesTheReturnToStandingUntilTheCycleCloses() {
        val verdict = RepCycleVerdict("SQUATS")
        repeat(3) { verdict.observe(listOf("Squat deeper — bend your knees more.")) }
        // Standing frames no longer report depth. One or two clean frames must not drop the latch.
        verdict.observe(emptyList())
        verdict.observe(emptyList())
        assertEquals(FormErrorCode.DEPTH_LOW, verdict.latched)
        val rejected = verdict.consume()
        assertTrue(rejected.rejected)
        val next = verdict.consume()
        assertTrue(next.valid)
        assertFalse(next.rejected)
    }

    @Test
    fun threeCleanWorkingFramesClearTheLatch() {
        val verdict = RepCycleVerdict("SQUATS")
        repeat(3) { verdict.observe(emptyList(), listOf("knees_in")) }
        repeat(3) { verdict.observe(emptyList()) }
        assertNull(verdict.latched)
        assertTrue(verdict.consume().valid)
    }

    @Test
    fun intervalBlockDoesNotCountTheMovement() {
        val verdict = RepCycleVerdict("PUSH_UP")
        repeat(3) { verdict.observe(emptyList(), listOf("sag")) }
        val blocked = verdict.consume(intervalAllowsNewAttempt = false)
        assertFalse(blocked.attempted)
        assertFalse(blocked.rejected)
        assertTrue(verdict.consume().valid)
    }

    private fun feed(verdict: RepCycleVerdict, signal: String) {
        if (signal == signal.lowercase() && signal.contains('_')) {
            verdict.observe(emptyList(), listOf(signal))
        } else {
            verdict.observe(listOf(signal))
        }
    }
}
