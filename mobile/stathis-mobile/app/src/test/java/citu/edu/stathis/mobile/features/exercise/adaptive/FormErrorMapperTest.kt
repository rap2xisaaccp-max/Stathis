package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormErrorMapperTest {

    @Test
    fun prefersBackendFlagsOverFormIssues() {
        val code =
            FormErrorMapper.resolve(
                flags = listOf("chest_up"),
                formIssues = listOf("Push knees outward over toes.")
            )
        assertEquals(FormErrorCode.CHEST_UP, code)
    }

    @Test
    fun mapsOnDeviceDetectorMessages() {
        assertEquals(
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Ensure major body parts are visible.")
            )
        )
        assertEquals(
            FormErrorCode.LEGS_BENT,
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Keep your legs straighter for better control.")
            )
        )
        assertEquals(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Low detection confidence")
            )
        )
    }

    @Test
    fun mapsPostureRuleFlags() {
        assertEquals(FormErrorCode.DEPTH_LOW, FormErrorCode.fromFlag("depth_low"))
        assertEquals(FormErrorCode.KNEES_IN, FormErrorCode.fromFlag("knees_in"))
        assertEquals(FormErrorCode.SAG, FormErrorCode.fromFlag("sag"))
        assertEquals(FormErrorCode.PIKE, FormErrorCode.fromFlag("pike"))
        assertEquals(FormErrorCode.LOW_ROM, FormErrorCode.fromFlag("low_rom"))
    }

    @Test
    fun returnsNullWhenNoSignal() {
        assertNull(FormErrorMapper.resolve(emptyList(), emptyList()))
    }

    @Test
    fun unknownWhenUnmappedButNonEmpty() {
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(flags = listOf("weird_flag"), formIssues = emptyList())
        )
    }

    @Test
    fun severityIncreasesWithMoreIssues() {
        val low = FormErrorMapper.estimateSeverity(listOf("issue"), 1f)
        val high = FormErrorMapper.estimateSeverity(listOf("a", "b", "c"), 0.5f)
        assertTrue(high > low)
        assertTrue(high <= 1.0)
    }

    @Test
    fun prefersRuleSeverityWhenProvided() {
        val severity =
            FormErrorMapper.estimateSeverity(
                formIssues = listOf("a", "b", "c"),
                confidence = 0.2f,
                flags = listOf("sag"),
                ruleSeverity = 0.82
            )
        assertEquals(0.82, severity, 1e-6)
    }

    @Test
    fun severityFromFlagsMatchesBackendWeights() {
        assertEquals(0.7, FormErrorMapper.severityFromFlags(listOf("sag")), 1e-6)
        assertTrue(
            FormErrorMapper.severityFromFlags(listOf("sag", "pike"))
                > FormErrorMapper.severityFromFlags(listOf("sag"))
        )
    }
}
