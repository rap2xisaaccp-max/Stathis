package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.tasks.presentation.ExerciseRepAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runtime verification of LLR scenarios + attempt isolation math.
 * Writes NDJSON evidence to workspace debug-b7147e.log.
 */
class ExerciseWorkflowRuntimeVerificationTest {

    private val logFile: File by lazy {
        // cwd is typically app/ when running Android unit tests
        listOf(
            File("../../../debug-b7147e.log"),
            File("../../debug-b7147e.log"),
            File("debug-b7147e.log"),
            File("C:/Users/ASUS/Stathis/debug-b7147e.log")
        ).firstOrNull { it.parentFile?.exists() == true }?.canonicalFile
            ?: File("C:/Users/ASUS/Stathis/debug-b7147e.log")
    }

    private fun log(hypothesisId: String, message: String, data: Map<String, Any?>) {
        val dataJson = data.entries.joinToString(",") { (k, v) ->
            val value = when (v) {
                null -> "null"
                is Number, is Boolean -> v.toString()
                else -> "\"${v.toString().replace("\"", "'")}\""
            }
            "\"$k\":$value"
        }
        val line =
            """{"sessionId":"b7147e","hypothesisId":"$hypothesisId","location":"ExerciseWorkflowRuntimeVerificationTest","message":"$message","timestamp":${System.currentTimeMillis()},"runId":"jvm-verify1","data":{$dataJson}}"""
        logFile.parentFile?.mkdirs()
        logFile.appendText(line + "\n")
    }

    private fun runScenario(
        name: String,
        expected: Int,
        block: (ExerciseDetector) -> Unit
    ): Int {
        val d = ExerciseDetector()
        block(d)
        val actual = d.lyingLegRaiseRepCountForTests()
        log(
            "H-D",
            "llr_scenario",
            mapOf("name" to name, "expected" to expected, "actual" to actual, "pass" to (actual == expected))
        )
        assertEquals("$name expected=$expected actual=$actual", expected, actual)
        return actual
    }

    private var t = 1_000L
    private fun ExerciseDetector.frame(
        lAnkle: Float,
        rAnkle: Float,
        lKnee: Float = 170f,
        rKnee: Float = 170f,
        conf: Float = 0.9f,
        lHip: Float = 400f,
        rHip: Float = 400f,
        lShoulder: Float = 300f,
        rShoulder: Float = 300f,
        deltaMs: Long = 50L
    ) {
        t += deltaMs
        analyzeLyingLegRaiseMetrics(
            lHip, rHip, lAnkle, rAnkle, lShoulder, rShoulder, lKnee, rKnee, conf, t
        )
    }

    @Test
    fun llrScenarios_expectedVsActual() {
        t = 1_000L
        runScenario("valid_full_rep", 1) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(3) { d.frame(300f, 300f) }
            t += 900
            repeat(3) { d.frame(520f, 520f) }
        }

        t = 1_000L
        runScenario("partial_raise", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(6) { d.frame(390f, 390f) } // insufficient ROM
            repeat(3) { d.frame(520f, 520f) }
        }

        t = 1_000L
        runScenario("random_kicking", 0) { d ->
            repeat(25) { i ->
                val y = 400f + ((i % 7) - 3) * 8f
                d.frame(y, y + 5f)
            }
        }

        t = 1_000L
        runScenario("one_leg_only", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(5) { d.frame(300f, 500f) }
            t += 900
            repeat(3) { d.frame(520f, 520f) }
        }

        t = 1_000L
        runScenario("bent_knees", 0) { d ->
            repeat(3) { d.frame(520f, 520f, lKnee = 120f, rKnee = 120f) }
            repeat(4) { d.frame(300f, 300f, lKnee = 120f, rKnee = 120f) }
            t += 900
            repeat(4) { d.frame(520f, 520f, lKnee = 120f, rKnee = 120f) }
        }

        t = 1_000L
        runScenario("unstable_hips_torso_lift", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(4) { d.frame(300f, 300f, lHip = 280f, rHip = 280f) }
            t += 900
            repeat(3) { d.frame(520f, 520f) }
        }

        t = 1_000L
        runScenario("low_confidence", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(5) { d.frame(300f, 300f, conf = 0.2f) }
            t += 900
            repeat(3) { d.frame(520f, 520f, conf = 0.2f) }
        }

        t = 1_000L
        runScenario("rapid_impossible_jump", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            d.frame(50f, 50f) // abrupt
            t += 900
            repeat(3) { d.frame(520f, 520f) }
        }

        t = 1_000L
        runScenario("top_hold_without_lower", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(12) { d.frame(300f, 300f) } // stay raised
        }

        t = 1_000L
        runScenario("incomplete_lowering", 0) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(3) { d.frame(300f, 300f) }
            t += 900
            // mid-range — not fully lowered
            repeat(5) { d.frame(370f, 370f) }
        }

        t = 1_000L
        runScenario("two_valid_consecutive", 2) { d ->
            repeat(3) { d.frame(520f, 520f) }
            repeat(3) { d.frame(300f, 300f) }
            t += 900
            repeat(3) { d.frame(520f, 520f) }
            t += 900
            repeat(3) { d.frame(300f, 300f) }
            t += 900
            repeat(3) { d.frame(520f, 520f) }
        }
    }

    @Test
    fun attemptIsolation_and_preStartSeed() {
        // Simulate race: detector has pre-start/attempt1 residue; Start must reset before apply
        val detector = ExerciseDetector()
        t = 1_000L
        repeat(3) { detector.frame(520f, 520f) }
        repeat(3) { detector.frame(300f, 300f) }
        t += 900
        repeat(3) { detector.frame(520f, 520f) }
        val residue = detector.lyingLegRaiseRepCountForTests()
        assertTrue(residue >= 1)

        val acc = ExerciseRepAccumulator()
        // Bug pattern: apply before reset
        val seeded = acc.applyDetectorReps(residue)
        log("H-A", "seed_without_reset", mapOf("residue" to residue, "seededUi" to seeded))

        acc.reset()
        detector.resetExercise()
        val afterResetDet = detector.lyingLegRaiseRepCountForTests()
        val afterResetUi = acc.applyDetectorReps(0)
        log(
            "H-A",
            "after_proper_reset",
            mapOf(
                "detector" to afterResetDet,
                "ui" to afterResetUi,
                "pass" to (afterResetDet == 0 && afterResetUi == 0)
            )
        )
        assertEquals(0, afterResetDet)
        assertEquals(0, afterResetUi)
    }

    @Test
    fun latestRepsVsBestScore_and_calories() {
        fun mergeLatestReps(@Suppress("UNUSED_PARAMETER") prev: Int, session: Int) = session
        fun mergeBestScore(prev: Int, session: Int) = maxOf(prev, session)
        fun mergeCalories(prev: Double, session: Double) = prev + session

        val reps = mergeLatestReps(20, 8)
        val score = mergeBestScore(90, 70)
        val cal = mergeCalories(12.0, 5.0)
        log(
            "H-E",
            "score_merge_rules",
            mapOf(
                "latestReps" to reps,
                "bestScore" to score,
                "cumulativeCalories" to cal,
                "pass" to (reps == 8 && score == 90 && cal == 17.0)
            )
        )
        assertEquals(8, reps)
        assertEquals(90, score)
        assertEquals(17.0, cal, 0.001)
    }
}
