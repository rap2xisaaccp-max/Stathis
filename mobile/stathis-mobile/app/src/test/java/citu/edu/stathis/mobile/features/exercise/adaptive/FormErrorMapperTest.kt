package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormErrorMapperTest {

    private val physicalCodes =
        FormErrorCode.entries.filter {
            !FormErrorClassifier.isTechnical(it) && it != FormErrorCode.UNKNOWN
        }

    @Test
    fun framingIssueBeatsStalePhysicalFlags() {
        assertEquals(
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorMapper.resolve(
                flags = listOf("pike"),
                formIssues = listOf("Step back so your head, hands, and feet stay in the camera frame."),
                exerciseType = "PUSH_UP"
            )
        )
        assertEquals(
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorMapper.resolve(
                flags = listOf("knees_in"),
                formIssues = listOf("Move to the center of the camera frame."),
                exerciseType = "SQUATS"
            )
        )
        assertEquals(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorMapper.resolve(
                flags = listOf("depth_low"),
                formIssues = listOf("Hold still briefly so your form can be read clearly."),
                exerciseType = "SQUATS"
            )
        )
    }

    @Test
    fun prefersBackendFlagsOverFormIssues() {
        val code =
            FormErrorMapper.resolve(
                flags = listOf("chest_up"),
                formIssues = emptyList(),
                exerciseType = "SQUATS"
            )
        assertEquals(FormErrorCode.CHEST_UP, code)
    }

    @Test
    fun simultaneousPhysicalErrorsAreDeterministicBySeverityThenPriority() {
        assertEquals(
            FormErrorCode.KNEES_IN,
            FormErrorMapper.resolve(
                flags = listOf("chest_up", "knees_in", "depth_low"),
                formIssues = emptyList(),
                exerciseType = "SQUATS"
            )
        )
        assertEquals(
            FormErrorCode.KNEES_IN,
            FormErrorMapper.resolve(
                flags = listOf("chest_up"),
                formIssues = listOf("Push knees outward over toes."),
                exerciseType = "SQUATS"
            )
        )
        assertEquals(
            FormErrorCode.SAG,
            FormErrorMapper.resolve(
                flags = listOf("low_rom", "sag", "pike"),
                formIssues = emptyList(),
                exerciseType = "PUSH_UP"
            )
        )
        assertEquals(
            FormErrorCode.SAG,
            FormErrorMapper.resolve(
                flags = listOf("low_rom", "sag"),
                formIssues = emptyList(),
                exerciseType = "GLUTE_BRIDGE"
            )
        )
        assertEquals(
            FormErrorCode.SAG,
            FormErrorMapper.resolve(
                flags = listOf("low_rom", "legs_bent", "sag"),
                formIssues = emptyList(),
                exerciseType = "LYING_LEG_RAISES"
            )
        )
        assertEquals(
            FormErrorCode.KNEES_IN,
            FormErrorMapper.resolve(
                flags = listOf("depth_low", "knees_in"),
                formIssues = emptyList(),
                exerciseType = "STATIC_LUNGES"
            )
        )
    }

    @Test
    fun mapsOnDeviceDetectorMessages() {
        assertEquals(
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Ensure major body parts are visible."),
                exerciseType = "SQUATS"
            )
        )
        assertEquals(
            FormErrorCode.LEGS_BENT,
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Keep your legs straighter for better control."),
                exerciseType = "LYING_LEG_RAISES"
            )
        )
        assertEquals(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Low detection confidence"),
                exerciseType = "PUSH_UP"
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
        assertNull(FormErrorMapper.resolve(emptyList(), emptyList(), "SQUATS"))
    }

    @Test
    fun unknownWhenUnmappedButNonEmpty() {
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(flags = listOf("weird_flag"), formIssues = emptyList(), exerciseType = "SQUATS")
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

    @Test
    fun eachExerciseAcceptsItsOwnValidCodes() {
        val expected =
            mapOf(
                "SQUATS" to setOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
                "PUSH_UP" to setOf(FormErrorCode.PIKE, FormErrorCode.SAG, FormErrorCode.LOW_ROM),
                "STATIC_LUNGES" to setOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
                "GLUTE_BRIDGE" to setOf(FormErrorCode.LOW_ROM, FormErrorCode.SAG),
                "LYING_LEG_RAISES" to setOf(FormErrorCode.LEGS_BENT, FormErrorCode.LOW_ROM, FormErrorCode.SAG)
            )
        expected.forEach { (exercise, allowed) ->
            assertEquals(allowed, FormErrorClassifier.allowedPhysicalCodes(exercise))
            allowed.forEach { code ->
                assertEquals(
                    code,
                    FormErrorMapper.resolve(listOf(code.name), emptyList(), exercise)
                )
                assertTrue(FormErrorClassifier.isCoachableForExercise(exercise, code))
                assertTrue(CoachingInstructionCatalog.hasReviewedInstruction(exercise, code))
                assertTrue(FormErrorCopy.label(code, exercise).isNotBlank())
                assertTrue(FormErrorCopy.explanation(code, exercise).isNotBlank())
                assertTrue(ModalityHighlightTargets.forError(code, exercise).joints.isNotEmpty())
            }
        }
    }

    @Test
    fun eachExerciseRejectsCrossExerciseFlags() {
        val exercises =
            listOf("SQUATS", "PUSH_UP", "STATIC_LUNGES", "GLUTE_BRIDGE", "LYING_LEG_RAISES")
        exercises.forEach { exercise ->
            val allowed = FormErrorClassifier.allowedPhysicalCodes(exercise)
            physicalCodes.filter { it !in allowed }.forEach { invalid ->
                assertEquals(
                    "$exercise must not emit $invalid",
                    FormErrorCode.UNKNOWN,
                    FormErrorMapper.resolve(listOf(invalid.name), emptyList(), exercise)
                )
                assertFalse(FormErrorClassifier.isCoachableForExercise(exercise, invalid))
            }
        }
    }

    @Test
    fun squatsNeverEmitPikeOrSag() {
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("pike"), emptyList(), "SQUATS")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("sag"), listOf("Avoid sagging hips."), "SQUATS")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(emptyList(), listOf("Keep a straight line from head to heels."), "SQUATS")
        )
        assertFalse(FormErrorClassifier.isCoachableForExercise("SQUATS", FormErrorCode.PIKE))
        assertFalse(FormErrorClassifier.isCoachableForExercise("SQUATS", FormErrorCode.SAG))
    }

    @Test
    fun squatHipHingeMapsToDepthNotVisibility() {
        assertEquals(
            FormErrorCode.DEPTH_LOW,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Hinge at the hips and sit back into the squat."),
                "SQUATS"
            )
        )
        assertEquals(
            FormErrorCode.DEPTH_LOW,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Squat deeper — bend your knees more."),
                "SQUATS"
            )
        )
    }

    @Test
    fun squatLockoutDoesNotInventKneesIn() {
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Stand tall and fully extend your knees."),
                "SQUATS"
            )
        )
    }

    @Test
    fun pushUpChestLoweringMapsToLowRomNotChestUp() {
        assertEquals(
            FormErrorCode.LOW_ROM,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Lower your chest closer to the ground."),
                "PUSH_UP"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("chest_up"), emptyList(), "PUSH_UP")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("depth_low"), emptyList(), "PUSH_UP")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("knees_in"), emptyList(), "PUSH_UP")
        )
        assertFalse(FormErrorClassifier.isAllowedPhysical("PUSH_UP", FormErrorCode.DEPTH_LOW))
        assertFalse(FormErrorClassifier.isAllowedPhysical("PUSH_UP", FormErrorCode.CHEST_UP))
    }

    @Test
    fun pushUpDoesNotReceiveSquatLowRomSemantics() {
        val pushUpRom =
            CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.LOW_ROM)
        val squatDepth =
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.DEPTH_LOW)
        assertTrue(pushUpRom.contains("chest", ignoreCase = true))
        assertFalse(pushUpRom.contains("thighs", ignoreCase = true))
        assertFalse(pushUpRom.contains("parallel", ignoreCase = true))
        assertNotEquals(pushUpRom, squatDepth)
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(emptyList(), listOf("Go deeper to at least parallel."), "PUSH_UP")
        )
    }

    @Test
    fun lungeBackLegDoesNotMapToLegsBent() {
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Keep the back leg more extended."),
                "STATIC_LUNGES"
            )
        )
        assertEquals(
            FormErrorCode.DEPTH_LOW,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Bend the front knee deeper into the lunge."),
                "STATIC_LUNGES"
            )
        )
        assertFalse(FormErrorClassifier.isAllowedPhysical("STATIC_LUNGES", FormErrorCode.LEGS_BENT))
    }

    @Test
    fun gluteHipLiftMapsToLowRomNotVisibility() {
        assertEquals(
            FormErrorCode.LOW_ROM,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Drive your hips higher into a full bridge."),
                "GLUTE_BRIDGE"
            )
        )
        assertEquals(
            FormErrorCode.SAG,
            FormErrorMapper.resolve(
                listOf("sag"),
                listOf("Keep your hips lifted; do not let them drop."),
                "GLUTE_BRIDGE"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("pike"), emptyList(), "GLUTE_BRIDGE")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Lower your hips with control before the next bridge."),
                "GLUTE_BRIDGE"
            )
        )
    }

    @Test
    fun lyingLegRaiseMapsHeightAndFloorContact() {
        assertEquals(
            FormErrorCode.LOW_ROM,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Raise your legs higher while keeping them controlled."),
                "LYING_LEG_RAISES"
            )
        )
        assertEquals(
            FormErrorCode.SAG,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Keep your hips and torso on the floor."),
                "LYING_LEG_RAISES"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(emptyList(), listOf("Raise both legs together."), "LYING_LEG_RAISES")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(listOf("pike"), emptyList(), "LYING_LEG_RAISES")
        )
    }

    @Test
    fun genericHipsSubstringDoesNotLeakAcrossExercises() {
        assertEquals(
            FormErrorCode.DEPTH_LOW,
            FormErrorMapper.resolve(emptyList(), listOf("Hinge at the hips and sit back into the squat."), "SQUATS")
        )
        assertEquals(
            FormErrorCode.LOW_ROM,
            FormErrorMapper.resolve(emptyList(), listOf("Drive your hips higher into a full bridge."), "GLUTE_BRIDGE")
        )
        assertEquals(
            FormErrorCode.SAG,
            FormErrorMapper.resolve(emptyList(), listOf("Keep your hips and torso on the floor."), "LYING_LEG_RAISES")
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(emptyList(), listOf("Watch your hips."), "PUSH_UP")
        )
    }

    @Test
    fun detectedButUncoachedStringsStayUnknown() {
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Fully extend your arms at the top."),
                "PUSH_UP"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Stand tall and fully extend your knees."),
                "SQUATS"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Keep the back leg more extended."),
                "STATIC_LUNGES"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Drop your back knee a little closer to the floor with control."),
                "STATIC_LUNGES"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Return to a tall stance between lunges."),
                "STATIC_LUNGES"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Lower your hips with control before the next bridge."),
                "GLUTE_BRIDGE"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Raise both legs together."),
                "LYING_LEG_RAISES"
            )
        )
        assertEquals(
            FormErrorCode.UNKNOWN,
            FormErrorMapper.resolve(
                emptyList(),
                listOf("Movement too abrupt — slow the raise."),
                "LYING_LEG_RAISES"
            )
        )
    }
}
