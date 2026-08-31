package citu.edu.stathis.mobile.features.exercise.adaptive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingCatalogIntensityTest {

    @Test
    fun firstPhysicalClaimUsesReminderSentence() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        var now = 1_000L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last =
                engine.onFormSignal(
                    formIssues = emptyList(),
                    flags = listOf("KNEES_IN"),
                    severity = 0.7,
                    currentReps = 0,
                    now = now
                )
            now += 100L
        }
        val reminder =
            CoachingInstructionCatalog.messageText(
                "SQUATS",
                FormErrorCode.KNEES_IN,
                InstructionIntensity.REMINDER
            )
        assertEquals(reminder, last!!.message)
        assertEquals(reminder, capture.events.single().correctionText)
        assertEquals(1, delivery.spokeCount)
        assertTrue(last.speak)
        assertTrue(last.highlightJoints)
        assertEquals(
            ModalityHighlightTargets.forError(FormErrorCode.KNEES_IN, "SQUATS").joints,
            last.highlightLandmarkIds
        )
        assertEquals(FormErrorCode.KNEES_IN, capture.events.single().errorCode)
    }

    @Test
    fun genuineClearThenRecurrenceUsesEscalation() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                flags = listOf("KNEES_IN"),
                formIssues = emptyList(),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        val reminder =
            CoachingInstructionCatalog.messageText(
                "SQUATS",
                FormErrorCode.KNEES_IN,
                InstructionIntensity.REMINDER
            )
        assertEquals(reminder, capture.events[0].correctionText)
        repeat(3) {
            engine.onFormSignal(
                flags = emptyList(),
                formIssues = emptyList(),
                severity = 0.0,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        now += 9_000L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last =
                engine.onFormSignal(
                    flags = listOf("KNEES_IN"),
                    formIssues = emptyList(),
                    severity = 0.7,
                    currentReps = 0,
                    now = now
                )
            now += 100L
        }
        val escalation =
            CoachingInstructionCatalog.messageText(
                "SQUATS",
                FormErrorCode.KNEES_IN,
                InstructionIntensity.ESCALATION
            )
        assertEquals(2, delivery.spokeCount)
        assertEquals(escalation, last!!.message)
        assertEquals(escalation, capture.events[1].correctionText)
        assertEquals(FormErrorCode.KNEES_IN, capture.events[1].errorCode)
    }

    @Test
    fun heldErrorDoesNotSpeakEscalationOrAdvanceCycle() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("PUSH_UP")
        var now = 1_000L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last =
                engine.onFormSignal(
                    flags = listOf("SAG"),
                    formIssues = emptyList(),
                    severity = 0.7,
                    currentReps = 0,
                    now = now
                )
            now += 100L
        }
        val reminder =
            CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.SAG, InstructionIntensity.REMINDER)
        assertEquals(reminder, last!!.message)
        repeat(40) {
            last =
                engine.onFormSignal(
                    flags = listOf("SAG"),
                    formIssues = emptyList(),
                    severity = 0.7,
                    currentReps = 0,
                    now = now
                )
            now += 200L
        }
        assertEquals(1, delivery.spokeCount)
        assertEquals(1, capture.events.size)
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
    }

    @Test
    fun differentErrorWhileCycleOpenDoesNotSwitchCue() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                flags = listOf("KNEES_IN"),
                formIssues = emptyList(),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        val kneesHighlight = ModalityHighlightTargets.forError(FormErrorCode.KNEES_IN, "SQUATS")
        repeat(8) {
            val last =
                engine.onFormSignal(
                    flags = listOf("DEPTH_LOW", "CHEST_UP"),
                    formIssues = emptyList(),
                    severity = 0.7,
                    currentReps = 1,
                    now = now
                )
            now += 100L
            assertEquals(FormErrorCode.KNEES_IN, last!!.errorCode)
            assertEquals(kneesHighlight.joints, last.highlightLandmarkIds)
        }
        assertEquals(1, delivery.spokeCount)
        assertEquals(1, capture.events.size)
        assertEquals(FormErrorCode.KNEES_IN, capture.events.single().errorCode)
    }

    @Test
    fun simultaneousErrorsShareSelectedCodeAcrossTtsHighlightAndEvidence() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("PUSH_UP")
        var now = 1_000L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last =
                engine.onFormSignal(
                    flags = listOf("LOW_ROM", "PIKE", "SAG"),
                    formIssues = emptyList(),
                    severity = 0.8,
                    currentReps = 0,
                    now = now
                )
            now += 100L
        }
        assertEquals(FormErrorCode.SAG, last!!.errorCode)
        val sag =
            CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.SAG, InstructionIntensity.REMINDER)
        assertEquals(sag, last.message)
        assertEquals(
            ModalityHighlightTargets.forError(FormErrorCode.SAG, "PUSH_UP").joints,
            last.highlightLandmarkIds
        )
        assertEquals(FormErrorCode.SAG, capture.events.single().errorCode)
        assertEquals(sag, capture.events.single().correctionText)
    }

    @Test
    fun crossExerciseFallbackIsNotDelivered() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        var now = 1_000L
        repeat(6) {
            engine.onFormSignal(
                flags = listOf("PIKE"),
                formIssues = emptyList(),
                severity = 0.8,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(0, delivery.spokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertTrue(
            CoachingInstructionCatalog.messageText(
                "SQUATS",
                FormErrorCode.PIKE,
                InstructionIntensity.REMINDER
            ).isEmpty()
        )
    }

    @Test
    fun lowVisibilityFlagIsTechnicalOnlyAndNeverPhysicalFallback() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        val last =
            engine.onFormSignal(
                flags = listOf("LOW_VISIBILITY"),
                formIssues = emptyList(),
                severity = 0.9,
                currentReps = 0,
                now = 1_000L
            )
        assertEquals(0, delivery.spokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(FormErrorCode.LOW_VISIBILITY, last!!.errorCode)
        assertEquals(LiveCoachingUiPolicy.TECHNICAL_CHANNEL, last.deliveryChannel)
        assertFalse(last.speak)
        assertFalse(last.highlightJoints)
        assertFalse(
            last.message.contains("Adjust your form", ignoreCase = true)
        )
    }

    private fun engine(
        capture: RecordingEvidenceCapture,
        delivery: RecordingCoachingDelivery
    ) =
        AdaptiveFeedbackEngine(
            FakeAdaptiveApi(),
            delivery,
            AdaptiveOfflineQueue(),
            InMemoryEvidenceQueue(),
            capture
        )
}
