package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Synthetic landmark frames for lying leg raise (image Y grows downward).
 * Hip ~400, shoulder ~300 ⇒ bodySpan ≈ 100; raise threshold ≈ 18.
 */
class ExerciseDetectorLyingLegRaiseTest {

    private lateinit var detector: ExerciseDetector
    private var t = 1_000L

    @Before
    fun setUp() {
        detector = ExerciseDetector()
        t = 1_000L
    }

    private fun tick(deltaMs: Long = 50L): Long {
        t += deltaMs
        return t
    }

    private fun frame(
        leftAnkleY: Float,
        rightAnkleY: Float,
        leftKneeAngle: Float = 170f,
        rightKneeAngle: Float = 170f,
        confidence: Float = 0.9f,
        leftHipY: Float = 400f,
        rightHipY: Float = 400f,
        leftShoulderY: Float = 300f,
        rightShoulderY: Float = 300f,
        nowMs: Long = tick()
    ) = detector.analyzeLyingLegRaiseMetrics(
        leftHipY = leftHipY,
        rightHipY = rightHipY,
        leftAnkleY = leftAnkleY,
        rightAnkleY = rightAnkleY,
        leftShoulderY = leftShoulderY,
        rightShoulderY = rightShoulderY,
        leftKneeAngle = leftKneeAngle,
        rightKneeAngle = rightKneeAngle,
        confidence = confidence,
        nowMs = nowMs
    )

    /** Lowered: ankles near/below hips (larger Y). Raised: ankles well above hips (smaller Y). */
    private fun holdLowered(frames: Int = 3) {
        repeat(frames) { frame(leftAnkleY = 520f, rightAnkleY = 520f) }
    }

    private fun holdRaised(frames: Int = 3) {
        repeat(frames) { frame(leftAnkleY = 300f, rightAnkleY = 300f) }
    }

    @Test
    fun validFullCycleCountsOneRep() {
        holdLowered()
        holdRaised()
        // Return to lowered with enough stable frames + interval
        t += 900
        val result = run {
            var last = frame(520f, 520f, nowMs = t)
            repeat(2) { last = frame(520f, 520f) }
            last
        }
        assertTrue(result.repCompleted || detector.lyingLegRaiseRepCountForTests() == 1)
        assertEquals(1, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun gibberishSmallOscillationCountsZero() {
        // Tiny ankle fidget around hips — never reaches raise ROM
        repeat(20) {
            frame(leftAnkleY = 405f + (it % 3), rightAnkleY = 405f - (it % 3))
        }
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun oneLegOnlyDoesNotCount() {
        holdLowered()
        repeat(5) { frame(leftAnkleY = 300f, rightAnkleY = 500f) }
        holdLowered()
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun bentKneesBlockCount() {
        holdLowered()
        repeat(4) {
            frame(leftAnkleY = 300f, rightAnkleY = 300f, leftKneeAngle = 120f, rightKneeAngle = 120f)
        }
        t += 900
        repeat(4) {
            frame(leftAnkleY = 520f, rightAnkleY = 520f, leftKneeAngle = 120f, rightKneeAngle = 120f)
        }
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun lowConfidenceDoesNotAdvance() {
        holdLowered()
        repeat(5) {
            frame(leftAnkleY = 300f, rightAnkleY = 300f, confidence = 0.2f)
        }
        assertEquals(ExerciseState.WAITING, detector.analyzeLyingLegRaiseMetrics(
            leftHipY = 400f,
            rightHipY = 400f,
            leftAnkleY = 300f,
            rightAnkleY = 300f,
            leftShoulderY = 300f,
            rightShoulderY = 300f,
            leftKneeAngle = 170f,
            rightKneeAngle = 170f,
            confidence = 0.2f,
            nowMs = tick()
        ).state)
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun sittingUpHipDriftBlocksCount() {
        holdLowered()
        // Large hip drift (sitting up) while ankles "raise"
        repeat(4) {
            frame(
                leftAnkleY = 300f,
                rightAnkleY = 300f,
                leftHipY = 280f,
                rightHipY = 280f
            )
        }
        t += 900
        holdLowered()
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun abruptJumpRejected() {
        holdLowered()
        // Impossible one-frame teleport
        val abrupt = frame(leftAnkleY = 50f, rightAnkleY = 50f)
        assertFalse(abrupt.repCompleted)
        assertTrue(abrupt.feedback.any { it.contains("abrupt", ignoreCase = true) })
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }

    @Test
    fun resetClearsRepCount() {
        holdLowered()
        holdRaised()
        t += 900
        repeat(3) { frame(520f, 520f) }
        assertEquals(1, detector.lyingLegRaiseRepCountForTests())
        detector.resetExercise()
        assertEquals(0, detector.lyingLegRaiseRepCountForTests())
    }
}
