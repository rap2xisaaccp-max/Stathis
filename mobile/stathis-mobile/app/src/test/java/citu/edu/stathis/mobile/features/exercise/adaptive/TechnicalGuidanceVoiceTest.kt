package citu.edu.stathis.mobile.features.exercise.adaptive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalGuidanceVoiceTest {

    @Test
    fun bodyNotVisibleSpeaksTechnicalGuidanceOnceWhilePersistent() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("PUSH_UP")
        val message = "Step back so your head, hands, and feet stay in the camera frame."
        var now = 1_000L
        repeat(8) {
            engine.onFormSignal(
                formIssues = listOf(message),
                flags = listOf("BODY_NOT_VISIBLE"),
                severity = 0.9,
                currentReps = 0,
                visibilityOk = false,
                now = now
            )
            now += 100L
        }
        assertEquals(1, delivery.technicalSpokeCount)
        assertEquals(message, delivery.lastTechnicalMessage)
        assertEquals(0, delivery.spokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertEquals("", engine.activeFeedback()?.interventionId)
        assertFalse(engine.activeFeedback()?.highlightJoints == true)
        assertEquals(InterventionPhase.OBSERVING, engine.lifecyclePhase())
    }

    @Test
    fun offCenterGuidanceCanSpeak() = runBlocking {
        val delivery = RecordingCoachingDelivery()
        val engine = engine(RecordingEvidenceCapture(), delivery)
        engine.startSession("SQUATS")
        val last =
            engine.onFormSignal(
                formIssues = listOf("Move to the center of the camera frame."),
                flags = emptyList(),
                severity = 0.5,
                currentReps = 0,
                visibilityOk = false,
                now = 1_000L
            )
        assertEquals(1, delivery.technicalSpokeCount)
        assertEquals("Move to the center of the camera frame.", delivery.lastTechnicalMessage)
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, last!!.errorCode)
        assertTrue(last.speak)
        assertFalse(last.highlightJoints)
        assertNotNullBanner(last)
    }

    @Test
    fun changedTechnicalConditionCanSpeakNewMessage() = runBlocking {
        val delivery = RecordingCoachingDelivery()
        val engine = engine(RecordingEvidenceCapture(), delivery)
        engine.startSession("SQUATS")
        engine.onFormSignal(
            formIssues = listOf("Move to the center of the camera frame."),
            flags = emptyList(),
            severity = 0.5,
            currentReps = 0,
            visibilityOk = false,
            now = 1_000L
        )
        engine.onFormSignal(
            formIssues = listOf("Step back so your shoulders, hips, and feet stay in the camera frame."),
            flags = emptyList(),
            severity = 0.5,
            currentReps = 0,
            visibilityOk = false,
            now = 1_200L
        )
        assertEquals(2, delivery.technicalSpokeCount)
        assertEquals(
            "Step back so your shoulders, hips, and feet stay in the camera frame.",
            delivery.lastTechnicalMessage
        )
    }

    @Test
    fun technicalTtsCreatesNoInterventionEvidenceOrHighlight() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        val last =
            engine.onFormSignal(
                formIssues = listOf("Hold still briefly so your form can be read clearly."),
                flags = listOf("LOW_CONFIDENCE"),
                severity = 0.8,
                currentReps = 1,
                now = 1_000L
            )
        assertEquals(1, delivery.technicalSpokeCount)
        assertEquals(0, delivery.spokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertTrue(engine.sessionSummary().errorCodes.isEmpty())
        assertEquals("", last!!.interventionId)
        assertFalse(last.highlightJoints)
        assertTrue(last.highlightLandmarkIds.isEmpty())
        assertTrue(last.highlightBones.isEmpty())
        assertEquals(LiveCoachingUiPolicy.TECHNICAL_CHANNEL, last.deliveryChannel)
    }

    @Test
    fun technicalTtsDoesNotCloseOrRearmPolicyB() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertEquals(1, engine.sessionSummary().interventionCount)
        val spokeAfterClaim = delivery.spokeCount
        val technicalBefore = delivery.technicalSpokeCount

        now = 4_000L
        repeat(6) {
            engine.onFormSignal(
                formIssues = listOf("Move to the center of the camera frame."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                visibilityOk = false,
                now = now
            )
            now += 100L
        }

        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        assertEquals(spokeAfterClaim, delivery.spokeCount)
        assertEquals(technicalBefore + 1, delivery.technicalSpokeCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertFalse(engine.activeFeedback()?.highlightJoints == true)
    }

    @Test
    fun technicalDebounceIsIndependentOfPhysicalCoachingDebounce() = runBlocking {
        val delivery = RecordingCoachingDelivery()
        val engine = engine(RecordingEvidenceCapture(), delivery)
        engine.startSession("SQUATS")
        engine.onFormSignal(
            formIssues = listOf("Move to the center of the camera frame."),
            flags = emptyList(),
            severity = 0.5,
            currentReps = 0,
            visibilityOk = false,
            now = 1_000L
        )
        assertEquals(1, delivery.technicalSpokeCount)
        assertEquals(0, delivery.spokeCount)

        var now = 1_100L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last =
                engine.onFormSignal(
                    formIssues = listOf("Keep your knees aligned with your toes."),
                    flags = listOf("KNEES_IN"),
                    severity = 0.7,
                    currentReps = 0,
                    now = now
                )
            now += 100L
        }
        assertEquals(1, delivery.spokeCount)
        assertEquals(1, delivery.technicalSpokeCount)
        assertTrue(last!!.speak)
        assertTrue(last.highlightJoints)
        assertNotEquals(LiveCoachingUiPolicy.TECHNICAL_CHANNEL, last.deliveryChannel)
        assertNull(LiveCoachingUiPolicy.studentTextBanner(last))
    }

    @Test
    fun physicalCoachingCanSpeakAfterFramingBecomesValid() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine = engine(capture, delivery)
        engine.startSession("SQUATS")
        engine.onFormSignal(
            formIssues = listOf("Step back so your shoulders, hips, and feet stay in the camera frame."),
            flags = emptyList(),
            severity = 0.5,
            currentReps = 0,
            visibilityOk = false,
            now = 1_000L
        )
        assertEquals(1, delivery.technicalSpokeCount)
        assertTrue(capture.events.isEmpty())

        var now = 2_000L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last =
                engine.onFormSignal(
                    formIssues = listOf("Keep your knees aligned with your toes."),
                    flags = listOf("KNEES_IN"),
                    severity = 0.7,
                    currentReps = 0,
                    now = now
                )
            now += 100L
        }
        assertEquals(1, delivery.spokeCount)
        assertEquals(1, capture.events.size)
        assertTrue(last!!.highlightJoints)
        assertTrue(last.speak)
        assertEquals("KNEES_IN", engine.sessionSummary().errorCodes.single())
    }

    @Test
    fun endSessionCancelsTechnicalPendingSpeech() = runBlocking {
        val delivery = RecordingCoachingDelivery()
        val engine = engine(RecordingEvidenceCapture(), delivery)
        engine.startSession("SQUATS")
        repeat(3) { i ->
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = 1_000L + i * 100L
            )
        }
        assertEquals(1, delivery.spokeCount)
        engine.onFormSignal(
            formIssues = listOf("Move to the center of the camera frame."),
            flags = emptyList(),
            severity = 0.5,
            currentReps = 0,
            visibilityOk = false,
            now = 1_200L
        )
        assertEquals(0, delivery.technicalSpokeCount)
        engine.endSession()
        assertEquals(1, delivery.stopCount)
        assertNull(engine.activeFeedback())
        assertEquals(0, delivery.technicalSpokeCount)
    }

    @Test
    fun reverifyingCancelsTechnicalPendingSpeech() = runBlocking {
        val delivery = RecordingCoachingDelivery()
        val engine = engine(RecordingEvidenceCapture(), delivery)
        engine.startSession("SQUATS")
        repeat(3) { i ->
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = 1_000L + i * 100L
            )
        }
        assertEquals(1, delivery.spokeCount)
        engine.onFormSignal(
            formIssues = listOf("Move to the center of the camera frame."),
            flags = emptyList(),
            severity = 0.5,
            currentReps = 0,
            visibilityOk = false,
            now = 1_200L
        )
        assertEquals(0, delivery.technicalSpokeCount)
        engine.suppressPhysicalCoaching()
        assertEquals(1, delivery.stopCount)
        assertEquals(0, delivery.technicalSpokeCount)
        assertEquals(LiveCoachingUiPolicy.TECHNICAL_CHANNEL, engine.activeFeedback()?.deliveryChannel)
        assertFalse(engine.activeFeedback()?.speak == true)
    }

    private fun assertNotNullBanner(last: DeliveredFeedback) {
        val banner = LiveCoachingUiPolicy.studentTextBanner(last)
        assertTrue(banner != null)
        assertTrue(LiveCoachingUiPolicy.showCameraGuidanceBanner(banner!!.message, banner.deliveryChannel))
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
