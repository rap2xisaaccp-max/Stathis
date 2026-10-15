package citu.edu.stathis.mobile.features.exercise.adaptive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingCopyContractTest {

    private val allowedMatrix =
        mapOf(
            "PUSH_UP" to listOf(FormErrorCode.PIKE, FormErrorCode.SAG, FormErrorCode.LOW_ROM),
            "SQUATS" to listOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
            "STATIC_LUNGES" to listOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
            "GLUTE_BRIDGE" to listOf(FormErrorCode.LOW_ROM, FormErrorCode.SAG),
            "LYING_LEG_RAISES" to listOf(FormErrorCode.LEGS_BENT, FormErrorCode.LOW_ROM, FormErrorCode.SAG)
        )

    @Test
    fun reviewedCatalogCoverageEqualsAllowedMatrix() {
        val expected =
            allowedMatrix.flatMap { (exercise, codes) ->
                codes.map { "$exercise|${it.name}" }
            }.toSet()
        assertEquals(expected, CoachingInstructionCatalog.reviewedPhysicalKeys())
        assertEquals(expected, FormErrorClassifier.allowedPhysicalCodes("PUSH_UP").map { "PUSH_UP|${it.name}" }.toSet() +
            FormErrorClassifier.allowedPhysicalCodes("SQUATS").map { "SQUATS|${it.name}" }.toSet() +
            FormErrorClassifier.allowedPhysicalCodes("STATIC_LUNGES").map { "STATIC_LUNGES|${it.name}" }.toSet() +
            FormErrorClassifier.allowedPhysicalCodes("GLUTE_BRIDGE").map { "GLUTE_BRIDGE|${it.name}" }.toSet() +
            FormErrorClassifier.allowedPhysicalCodes("LYING_LEG_RAISES").map { "LYING_LEG_RAISES|${it.name}" }.toSet())
    }

    @Test
    fun eachAllowedPairHasDistinctNonEmptyReminderAndEscalation() {
        allowedMatrix.forEach { (exercise, codes) ->
            codes.forEach { code ->
                val reminder =
                    CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.REMINDER)
                val escalation =
                    CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.ESCALATION)
                assertTrue("$exercise|$code reminder", reminder.isNotBlank())
                assertTrue("$exercise|$code escalation", escalation.isNotBlank())
                assertNotEquals("$exercise|$code", reminder, escalation)
                assertTrue(CoachingInstructionCatalog.hasReviewedInstruction(exercise, code))
            }
        }
    }

    @Test
    fun sentencesDoNotMentionAnatomyTheTriggerDoesNotGuarantee() {
        assertFalse(joined("PUSH_UP", FormErrorCode.PIKE).contains("head", ignoreCase = true))
        assertFalse(containsAny(joined("GLUTE_BRIDGE", FormErrorCode.LOW_ROM), "heel", "heels", "ankle", "ankles", "foot", "feet"))
        assertFalse(containsAny(joined("STATIC_LUNGES", FormErrorCode.DEPTH_LOW), "back knee", "rear knee"))
        assertFalse(containsAny(joined("STATIC_LUNGES", FormErrorCode.KNEES_IN), "front knee"))
        assertTrue(containsAny(joined("STATIC_LUNGES", FormErrorCode.DEPTH_LOW), "front knee"))
        assertFalse(containsAny(joined("LYING_LEG_RAISES", FormErrorCode.LOW_ROM), "grounded", "lower back", "on the floor"))
        assertFalse(containsAny(joined("LYING_LEG_RAISES", FormErrorCode.SAG), "higher", "farther up"))
        assertFalse(containsAny(joined("SQUATS", FormErrorCode.CHEST_UP), "chest dropping", "look forward"))
        assertFalse(containsAny(joined("SQUATS", FormErrorCode.DEPTH_LOW), "hinge", "parallel"))
        assertFalse(containsAny(joined("PUSH_UP", FormErrorCode.LOW_ROM), "elbow"))
        assertTrue(containsAny(joined("PUSH_UP", FormErrorCode.LOW_ROM), "chest"))
    }

    @Test
    fun lungeDepthCopyMatchesFrontKneeMeasurement() {
        val reminder =
            CoachingInstructionCatalog.messageText(
                "STATIC_LUNGES",
                FormErrorCode.DEPTH_LOW,
                InstructionIntensity.REMINDER
            )
        val escalation =
            CoachingInstructionCatalog.messageText(
                "STATIC_LUNGES",
                FormErrorCode.DEPTH_LOW,
                InstructionIntensity.ESCALATION
            )
        assertTrue(reminder.contains("front knee", ignoreCase = true))
        assertTrue(escalation.contains("front knee", ignoreCase = true))
        assertFalse(reminder.contains("back knee", ignoreCase = true))
        assertFalse(escalation.contains("back knee", ignoreCase = true))
        assertTrue(
            FormErrorCopy.explanation(FormErrorCode.DEPTH_LOW, "STATIC_LUNGES")
                .contains("front knee", ignoreCase = true)
        )
    }

    @Test
    fun gluteLowRomDoesNotMentionHeels() {
        val copy = joined("GLUTE_BRIDGE", FormErrorCode.LOW_ROM)
        assertFalse(copy.contains("heel", ignoreCase = true))
        assertTrue(copy.contains("hip", ignoreCase = true))
        val highlights = ModalityHighlightTargets.forError(FormErrorCode.LOW_ROM, "GLUTE_BRIDGE")
        assertFalse(highlights.joints.contains(ModalityHighlightTargets.LEFT_ANKLE))
        assertTrue(highlights.joints.contains(ModalityHighlightTargets.LEFT_HIP))
    }

    @Test
    fun lyingLegRaiseLowRomAndSagCopyStayDistinct() {
        val rom =
            joined("LYING_LEG_RAISES", FormErrorCode.LOW_ROM)
        val sag =
            joined("LYING_LEG_RAISES", FormErrorCode.SAG)
        assertNotEquals(rom, sag)
        assertTrue(containsAny(rom, "higher", "farther up"))
        assertTrue(containsAny(sag, "floor", "hips"))
        assertFalse(rom.contains("floor", ignoreCase = true))
        assertFalse(sag.contains("higher", ignoreCase = true))
        assertNotEquals(
            FormErrorCopy.label(FormErrorCode.LOW_ROM, "LYING_LEG_RAISES"),
            FormErrorCopy.label(FormErrorCode.SAG, "LYING_LEG_RAISES")
        )
    }

    @Test
    fun teacherLabelAndDescriptionMatchEachAllowedPair() {
        allowedMatrix.forEach { (exercise, codes) ->
            codes.forEach { code ->
                val label = FormErrorCopy.label(code, exercise)
                val explanation = FormErrorCopy.explanation(code, exercise)
                assertTrue("$exercise|$code label", label.isNotBlank())
                assertTrue("$exercise|$code explanation", explanation.isNotBlank())
                assertFalse(label.contains('_'))
            }
        }
        assertEquals("Torso leaning", FormErrorCopy.label(FormErrorCode.CHEST_UP, "SQUATS"))
        assertFalse(
            FormErrorCopy.explanation(FormErrorCode.CHEST_UP, "SQUATS")
                .contains("Chest dropping", ignoreCase = true)
        )
        assertEquals("Knee drifting inward", FormErrorCopy.label(FormErrorCode.KNEES_IN, "STATIC_LUNGES"))
        assertFalse(FormErrorCopy.label(FormErrorCode.KNEES_IN, "STATIC_LUNGES").contains("front", ignoreCase = true))
        assertEquals("Shallow push-up", FormErrorCopy.label(FormErrorCode.LOW_ROM, "PUSH_UP"))
        assertEquals("Hips not high enough", FormErrorCopy.label(FormErrorCode.LOW_ROM, "GLUTE_BRIDGE"))
        assertEquals("Legs not high enough", FormErrorCopy.label(FormErrorCode.LOW_ROM, "LYING_LEG_RAISES"))
        assertEquals("Lower back lifting", FormErrorCopy.label(FormErrorCode.SAG, "LYING_LEG_RAISES"))
    }

    @Test
    fun spokenCorrectionMatchesEvidenceForTheSelectedError() = runBlocking {
        allowedMatrix.forEach { (exercise, codes) ->
            codes.forEach { code ->
                val capture = RecordingEvidenceCapture()
                val delivery = RecordingCoachingDelivery()
                val engine =
                    AdaptiveFeedbackEngine(
                        FakeAdaptiveApi(),
                        delivery,
                        AdaptiveOfflineQueue(),
                        InMemoryEvidenceQueue(),
                        capture
                    )
                engine.startSession(exercise)
                var now = 1_000L
                var last: DeliveredFeedback? = null
                repeat(3) {
                    last =
                        engine.onFormSignal(
                            formIssues = emptyList(),
                            flags = listOf(code.name),
                            severity = 0.7,
                            currentReps = 0,
                            now = now
                        )
                    now += 100L
                }
                val reminder =
                    CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.REMINDER)
                assertEquals("$exercise|$code spoken", reminder, last!!.message)
                assertEquals("$exercise|$code evidence", reminder, capture.events.single().correctionText)
                assertEquals(
                    "$exercise|$code teacher",
                    FormErrorCopy.explanation(code, exercise),
                    capture.events.single().errorDescription
                )
                assertEquals(code, capture.events.single().errorCode)
                assertEquals(exercise, capture.events.single().exerciseType)
                assertEquals(
                    ModalityHighlightTargets.forError(code, exercise).joints,
                    last.highlightLandmarkIds
                )
            }
        }
    }

    @Test
    fun crossExerciseCodesCannotDeliverAnotherExerciseSentence() {
        val physical =
            FormErrorCode.entries.filter {
                !FormErrorClassifier.isTechnical(it) && it != FormErrorCode.UNKNOWN
            }
        allowedMatrix.keys.forEach { exercise ->
            val allowed = allowedMatrix.getValue(exercise).toSet()
            physical.filter { it !in allowed }.forEach { invalid ->
                assertFalse(
                    CoachingInstructionCatalog.hasReviewedInstruction(exercise, invalid)
                )
                assertTrue(
                    CoachingInstructionCatalog.messageText(
                        exercise,
                        invalid,
                        InstructionIntensity.REMINDER
                    ).isEmpty()
                )
                assertTrue(
                    CoachingInstructionCatalog.messageText(
                        exercise,
                        invalid,
                        InstructionIntensity.ESCALATION
                    ).isEmpty()
                )
            }
        }
        val pushSag =
            CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.SAG, InstructionIntensity.REMINDER)
        val squatSag =
            CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.SAG, InstructionIntensity.REMINDER)
        assertTrue(pushSag.isNotBlank())
        assertTrue(squatSag.isEmpty())
        assertNotEquals(pushSag, squatSag)
    }

    @Test
    fun crossExerciseFlagsAreNotDeliveredByTheEngine() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS")
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = emptyList(),
                flags = listOf("SAG"),
                severity = 0.9,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(0, delivery.spokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(0, engine.sessionSummary().interventionCount)
    }

    private fun joined(exercise: String, code: FormErrorCode): String =
        CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.REMINDER) +
            " " +
            CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.ESCALATION)

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it, ignoreCase = true) }
}
