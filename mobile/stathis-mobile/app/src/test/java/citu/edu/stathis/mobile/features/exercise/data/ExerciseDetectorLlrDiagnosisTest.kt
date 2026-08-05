package citu.edu.stathis.mobile.features.exercise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Post-fix diagnosis — confirms previously blocking gates now allow valid motion. */
class ExerciseDetectorLlrDiagnosisTest {

    private val logFile: File = java.nio.file.Files.createTempDirectory("stathis-test-").resolve("debug-b7147e.log").toFile()
    private var t = 1_000L

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
            """{"sessionId":"b7147e","hypothesisId":"$hypothesisId","location":"LlrDiagnosis","message":"$message","timestamp":${System.currentTimeMillis()},"runId":"llr-post-fix","data":{$dataJson}}"""
        logFile.appendText(line + "\n")
    }

    private fun ExerciseDetector.step(
        lAnkle: Float,
        rAnkle: Float,
        lKnee: Float = 170f,
        rKnee: Float = 170f,
        lHip: Float = 400f,
        rHip: Float = 400f,
        lShoulder: Float = 300f,
        rShoulder: Float = 300f,
        conf: Float = 0.9f,
        dt: Long = 50L
    ) {
        t += dt
        analyzeLyingLegRaiseMetrics(
            lHip, rHip, lAnkle, rAnkle, lShoulder, rShoulder, lKnee, rKnee, conf, t
        )
    }

    @Test
    fun postFixGates() {
        logFile.parentFile?.mkdirs()
        if (logFile.exists()) logFile.writeText("")

        // H1 was blocking at 145° — should count now
        run {
            val d = ExerciseDetector()
            t = 1_000L
            repeat(3) { d.step(520f, 520f, lKnee = 145f, rKnee = 145f) }
            repeat(3) { d.step(300f, 300f, lKnee = 145f, rKnee = 145f) }
            t += 900
            repeat(3) { d.step(520f, 520f, lKnee = 145f, rKnee = 145f) }
            val reps = d.lyingLegRaiseRepCountForTests()
            log("H1", "slight_knee_bend_post_fix", mapOf("reps" to reps, "pass" to (reps == 1)))
            assertEquals(1, reps)
        }

        // H3: soft knees only on lower — raise quality already sampled
        run {
            val d = ExerciseDetector()
            t = 1_000L
            repeat(3) { d.step(520f, 520f) }
            repeat(3) { d.step(300f, 300f) }
            t += 900
            repeat(3) { d.step(520f, 520f, lKnee = 140f, rKnee = 140f) }
            val reps = d.lyingLegRaiseRepCountForTests()
            log("H3", "raise_ok_lower_soft_knees_post_fix", mapOf("reps" to reps, "pass" to (reps == 1)))
            assertEquals(1, reps)
        }

        // H5: mild asymmetry (~20px) should count with wider gate
        run {
            val d = ExerciseDetector()
            t = 1_000L
            repeat(3) { d.step(520f, 520f) }
            repeat(3) { d.step(300f, 320f) }
            t += 900
            repeat(3) { d.step(520f, 520f) }
            val reps = d.lyingLegRaiseRepCountForTests()
            log("H5", "mild_asymmetry_post_fix", mapOf("reps" to reps, "pass" to (reps == 1)))
            assertEquals(1, reps)
        }

        // Still reject tiny kicks after rest calibration
        run {
            val d = ExerciseDetector()
            t = 1_000L
            repeat(3) { d.step(520f, 520f) }
            repeat(5) { d.step(500f, 500f) }
            t += 900
            repeat(3) { d.step(520f, 520f) }
            val reps = d.lyingLegRaiseRepCountForTests()
            log("H2", "tiny_kick_still_rejected", mapOf("reps" to reps, "pass" to (reps == 0)))
            assertEquals(0, reps)
        }

        // Ideal control
        run {
            val d = ExerciseDetector()
            t = 1_000L
            repeat(3) { d.step(520f, 520f) }
            repeat(3) { d.step(300f, 300f) }
            t += 900
            repeat(3) { d.step(520f, 520f) }
            val reps = d.lyingLegRaiseRepCountForTests()
            log("H-control", "ideal_valid_cycle", mapOf("reps" to reps, "pass" to (reps == 1)))
            assertEquals(1, reps)
        }

        // Very bent knees still rejected
        run {
            val d = ExerciseDetector()
            t = 1_000L
            repeat(3) { d.step(520f, 520f) }
            repeat(3) { d.step(300f, 300f, lKnee = 110f, rKnee = 110f) }
            t += 900
            repeat(3) { d.step(520f, 520f) }
            val reps = d.lyingLegRaiseRepCountForTests()
            log("H1-strict", "very_bent_still_rejected", mapOf("reps" to reps, "pass" to (reps == 0)))
            assertEquals(0, reps)
        }

        assertTrue(logFile.exists() && logFile.length() > 0)
    }
}
