package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorCode
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorMapper
import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Synthetic geometry for the four exercises that previously had no landmark-fed detector tests.
 * Numbers follow the same idea as [ExerciseDetectorLyingLegRaiseTest]: image Y grows downward,
 * and each case drives the existing state machine. These are not camera recordings.
 */
class ExerciseDetectorSyntheticLandmarkTest {

    private lateinit var detector: ExerciseDetector
    private var t = 1_000L

    @Before
    fun setUp() {
        detector = ExerciseDetector()
        t = 1_000L
    }

    @Test
    fun squatValidRepCountsAndCrossExerciseOrLowConfidenceDoesNotReject() {
        squatStand()
        squatBottom()
        t += 900L
        val done = squatStand()
        assertEquals(1, done.repCount)
        assertFalse(done.repRejected)
        assertNull(done.rejectedErrorCode)

        val crossDetector = ExerciseDetector()
        repeat(3) { crossDetector.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t) }
        repeat(3) { crossDetector.analyzeSquatMetrics(480f, 400f, 100f, 550f, 110f, 100f, 0.9f, listOf("PIKE"), t) }
        t += 900L
        var cross = crossDetector.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t)
        repeat(2) { cross = crossDetector.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t) }
        assertEquals(1, cross.repCount)
        assertFalse(cross.repRejected)

        val low = ExerciseDetector()
        val blocked = low.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.1f)
        assertEquals(0, blocked.repCount)
        assertTrue(blocked.feedback.any { it.contains("confidence", ignoreCase = true) })
        assertFalse(blocked.repRejected)
    }

    @Test
    fun squatRejectsDepthKneesInAndChestUpOnly() {
        assertEquals(FormErrorCode.DEPTH_LOW, squatError(kneeAngle = 135f, hipAngle = 120f))
        assertEquals(FormErrorCode.KNEES_IN, squatError(flags = listOf("KNEES_IN")))
        assertEquals(FormErrorCode.CHEST_UP, squatError(flags = listOf("CHEST_UP")))
    }

    @Test
    fun pushUpValidRepCountsAndCrossExerciseOrLowConfidenceDoesNotReject() {
        pushUp(elbow = 170f)
        pushUp(elbow = 80f)
        t += 900L
        val done = pushUp(elbow = 170f)
        assertEquals(1, done.repCount)
        assertFalse(done.repRejected)

        val low = ExerciseDetector().analyzePushupMetrics(170f, 0.1f)
        assertEquals(0, low.repCount)
        assertTrue(low.feedback.any { it.contains("confidence", ignoreCase = true) })

        val crossDetector = ExerciseDetector()
        crossDetector.analyzePushupMetrics(170f, 0.9f, emptyList(), t)
        repeat(2) { crossDetector.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        repeat(3) { crossDetector.analyzePushupMetrics(80f, 0.9f, listOf("KNEES_IN"), t) }
        t += 900L
        var cross = crossDetector.analyzePushupMetrics(170f, 0.9f, emptyList(), t)
        repeat(2) { cross = crossDetector.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        assertEquals(1, cross.repCount)
        assertFalse(cross.repRejected)
    }

    @Test
    fun pushUpRejectsPikeSagAndLowRom() {
        assertEquals(FormErrorCode.PIKE, pushUpError(listOf("PIKE")))
        assertEquals(FormErrorCode.SAG, pushUpError(listOf("SAG")))
        assertEquals(FormErrorCode.LOW_ROM, pushUpError(listOf("LOW_ROM")))
    }

    @Test
    fun pushUpFullDepthRepIsNotRejectedWhenTheElbowOpensPast110() {
        val local = ExerciseDetector()
        repeat(3) { local.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        repeat(3) { local.analyzePushupMetrics(80f, 0.9f, emptyList(), t) }
        val ascent = local.analyzePushupMetrics(120f, 0.9f, emptyList(), t)
        assertEquals(ExerciseState.DOWN, ascent.state)
        assertFalse(ascent.feedback.any { it.contains("Lower your chest", ignoreCase = true) })
        assertTrue(FormErrorMapper.resolve(emptyList(), ascent.feedback, "PUSH_UP") != FormErrorCode.LOW_ROM)
        t += 900L
        var last = local.analyzePushupMetrics(170f, 0.9f, emptyList(), t)
        repeat(2) { last = local.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        assertEquals(1, last.repCount)
        assertEquals(1, last.attemptedRepCount)
        assertFalse(last.repRejected)
        assertNull(last.rejectedErrorCode)
    }

    @Test
    fun pushUpShallowerThanTheDownEntryDoesNotOpenARep() {
        val local = ExerciseDetector()
        repeat(6) { local.analyzePushupMetrics(120f, 0.9f, emptyList(), t) }
        t += 900L
        var last = local.analyzePushupMetrics(170f, 0.9f, emptyList(), t)
        repeat(2) { last = local.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        assertEquals(0, last.repCount)
        assertEquals(0, last.attemptedRepCount)
        assertFalse(last.repRejected)
        assertNull(last.rejectedErrorCode)
    }

    @Test
    fun gluteBridgeValidRepCountsAndCrossExerciseOrLowConfidenceDoesNotReject() {
        gluteRaised()
        t += 900L
        val done = gluteLowered()
        assertEquals(1, done.repCount)
        assertFalse(done.repRejected)

        val low = ExerciseDetector().analyzeGluteBridgeMetrics(500f, 500f, 500f, 110f, 0.1f)
        assertEquals(0, low.repCount)
        assertTrue(low.feedback.any { it.contains("confidence", ignoreCase = true) })

        val crossDetector = ExerciseDetector()
        repeat(3) { crossDetector.analyzeGluteBridgeMetrics(500f, 400f, 500f, 165f, 0.9f, listOf("PIKE"), t) }
        t += 900L
        var cross = crossDetector.analyzeGluteBridgeMetrics(500f, 500f, 500f, 100f, 0.9f, emptyList(), t)
        repeat(2) { cross = crossDetector.analyzeGluteBridgeMetrics(500f, 500f, 500f, 100f, 0.9f, emptyList(), t) }
        assertEquals(1, cross.repCount)
        assertFalse(cross.repRejected)
    }

    @Test
    fun gluteBridgeRejectsLowRomAndSag() {
        assertEquals(FormErrorCode.LOW_ROM, gluteError(hipAngle = 140f))
        assertEquals(FormErrorCode.SAG, gluteError(flags = listOf("SAG")))
    }

    @Test
    fun staticLungeValidRepCountsAndCrossExerciseOrLowConfidenceDoesNotReject() {
        lungeDown()
        val done = lungeUp()
        assertEquals(1, done.repCount)
        assertFalse(done.repRejected)

        val low = ExerciseDetector().analyzeStaticLungeMetrics(170f, 170f, 400f, 300f, 0.1f)
        assertEquals(0, low.repCount)
        assertTrue(low.feedback.any { it.contains("confidence", ignoreCase = true) })

        val crossDetector = ExerciseDetector()
        repeat(3) { crossDetector.analyzeStaticLungeMetrics(100f, 160f, 400f, 300f, 0.9f, listOf("LEGS_BENT")) }
        val cross = crossDetector.analyzeStaticLungeMetrics(160f, 160f, 300f, 300f, 0.9f)
        assertEquals(1, cross.repCount)
        assertFalse(cross.repRejected)
    }

    @Test
    fun staticLungeRejectsDepthKneesInAndChestUp() {
        assertEquals(FormErrorCode.DEPTH_LOW, lungeError(listOf("DEPTH_LOW")))
        assertEquals(FormErrorCode.KNEES_IN, lungeError(listOf("KNEES_IN")))
        assertEquals(FormErrorCode.CHEST_UP, lungeError(listOf("CHEST_UP")))
    }

    @Test
    fun staticLungeFullDepthRepIsNotRejectedWhenTheFrontKneeOpensPast120() {
        val local = ExerciseDetector()
        local.analyzeStaticLungeMetrics(100f, 160f, 400f, 300f, 0.9f)
        val opening = local.analyzeStaticLungeMetrics(130f, 160f, 400f, 300f, 0.9f)
        assertEquals(ExerciseState.DOWN, opening.state)
        assertFalse(opening.feedback.any { it.contains("deeper", ignoreCase = true) })
        assertTrue(
            FormErrorMapper.resolve(emptyList(), opening.feedback, "STATIC_LUNGES") !=
                FormErrorCode.DEPTH_LOW
        )
        val done = local.analyzeStaticLungeMetrics(160f, 160f, 300f, 300f, 0.9f)
        assertEquals(1, done.repCount)
        assertEquals(1, done.attemptedRepCount)
        assertFalse(done.repRejected)
        assertNull(done.rejectedErrorCode)
    }

    @Test
    fun staticLungeReturnStillReportsABentBackLegWithoutDepth() {
        val local = ExerciseDetector()
        local.analyzeStaticLungeMetrics(100f, 160f, 400f, 300f, 0.9f)
        val opening = local.analyzeStaticLungeMetrics(130f, 120f, 400f, 300f, 0.9f)
        assertEquals(ExerciseState.DOWN, opening.state)
        assertTrue(opening.feedback.any { it.contains("back leg", ignoreCase = true) })
        assertFalse(opening.feedback.any { it.contains("deeper", ignoreCase = true) })
        assertTrue(
            FormErrorMapper.resolve(emptyList(), opening.feedback, "STATIC_LUNGES") !=
                FormErrorCode.DEPTH_LOW
        )
    }

    @Test
    fun staticLungeShallowerThanTheDownEntryDoesNotOpenARep() {
        val local = ExerciseDetector()
        repeat(4) { local.analyzeStaticLungeMetrics(130f, 160f, 400f, 300f, 0.9f) }
        val done = local.analyzeStaticLungeMetrics(160f, 160f, 300f, 300f, 0.9f)
        assertEquals(0, done.repCount)
        assertEquals(0, done.attemptedRepCount)
        assertFalse(done.repRejected)
        assertNull(done.rejectedErrorCode)
    }

    private fun squatStand(): ExerciseResult =
        repeatFrame(3) {
            detector.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t)
        }

    private fun squatBottom(kneeAngle: Float = 100f, hipAngle: Float = 110f, flags: List<String> = emptyList()) =
        repeatFrame(3) {
            detector.analyzeSquatMetrics(480f, 400f, 100f, 550f, hipAngle, kneeAngle, 0.9f, flags, t)
        }

    private fun squatError(
        kneeAngle: Float = 100f,
        hipAngle: Float = 110f,
        flags: List<String> = emptyList()
    ): FormErrorCode? {
        val local = ExerciseDetector()
        repeat(3) { local.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t) }
        repeat(3) { local.analyzeSquatMetrics(480f, 400f, 100f, 550f, 110f, 100f, 0.9f, emptyList(), t) }
        repeat(3) { local.analyzeSquatMetrics(480f, 400f, 100f, 550f, hipAngle, kneeAngle, 0.9f, flags, t) }
        t += 900L
        var last = local.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t)
        repeat(2) { last = local.analyzeSquatMetrics(200f, 400f, 100f, 550f, 170f, 170f, 0.9f, emptyList(), t) }
        assertTrue(last.repRejected)
        return last.rejectedErrorCode
    }

    private fun pushUp(elbow: Float, flags: List<String> = emptyList()) =
        repeatFrame(3) { detector.analyzePushupMetrics(elbow, 0.9f, flags, t) }

    private fun pushUpError(flags: List<String>): FormErrorCode? {
        val local = ExerciseDetector()
        repeat(3) { local.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        repeat(3) { local.analyzePushupMetrics(80f, 0.9f, flags, t) }
        t += 900L
        var last = local.analyzePushupMetrics(170f, 0.9f, emptyList(), t)
        repeat(2) { last = local.analyzePushupMetrics(170f, 0.9f, emptyList(), t) }
        assertTrue(last.repRejected)
        return last.rejectedErrorCode
    }

    private fun gluteRaised(hipAngle: Float = 165f, flags: List<String> = emptyList()) =
        repeatFrame(3) {
            detector.analyzeGluteBridgeMetrics(500f, 400f, 500f, hipAngle, 0.9f, flags, t)
        }

    private fun gluteLowered() =
        repeatFrame(3) {
            detector.analyzeGluteBridgeMetrics(500f, 500f, 500f, 100f, 0.9f, emptyList(), t)
        }

    private fun gluteError(hipAngle: Float = 165f, flags: List<String> = emptyList()): FormErrorCode? {
        val local = ExerciseDetector()
        repeat(3) { local.analyzeGluteBridgeMetrics(500f, 400f, 500f, 165f, 0.9f, emptyList(), t) }
        repeat(3) { local.analyzeGluteBridgeMetrics(500f, 400f, 500f, hipAngle, 0.9f, flags, t) }
        t += 900L
        var last = local.analyzeGluteBridgeMetrics(500f, 500f, 500f, 100f, 0.9f, emptyList(), t)
        repeat(2) { last = local.analyzeGluteBridgeMetrics(500f, 500f, 500f, 100f, 0.9f, emptyList(), t) }
        assertTrue(last.repRejected)
        return last.rejectedErrorCode
    }

    private fun lungeDown(flags: List<String> = emptyList()) =
        repeatFrame(3) { detector.analyzeStaticLungeMetrics(100f, 160f, 400f, 300f, 0.9f, flags) }

    private fun lungeUp() = detector.analyzeStaticLungeMetrics(160f, 160f, 300f, 300f, 0.9f)

    private fun lungeError(flags: List<String>): FormErrorCode? {
        val local = ExerciseDetector()
        // The first down frame only enters DOWN. Observe runs while already down,
        // and the verdict latches after three of those frames. Depth, knees, and
        // chest reach the lunge verdict through flags; the depth sentence is
        // feedback after the state machine and is not passed into observe.
        repeat(4) { local.analyzeStaticLungeMetrics(100f, 160f, 400f, 300f, 0.9f, flags) }
        val last = local.analyzeStaticLungeMetrics(160f, 160f, 300f, 300f, 0.9f)
        assertTrue(last.repRejected)
        return last.rejectedErrorCode
    }

    private fun repeatFrame(times: Int, frame: () -> ExerciseResult): ExerciseResult {
        var last: ExerciseResult? = null
        repeat(times) { last = frame() }
        return last!!
    }
}
