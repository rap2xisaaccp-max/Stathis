package citu.edu.stathis.mobile.features.exercise.adaptive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFramingCoachingTest {

    @Test
    fun framingFailureProducesCameraGuidanceOnly() = runBlocking {
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
        engine.startSession("PUSH_UP")
        val last =
            engine.onFormSignal(
                formIssues = listOf("Step back so your head, hands, and feet stay in the camera frame."),
                flags = listOf("PIKE"),
                severity = 0.8,
                currentReps = 2,
                visibilityOk = false,
                now = 1_000L
            )
        assertEquals(0, delivery.spokeCount)
        assertEquals(1, delivery.technicalSpokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertFalse(last!!.highlightJoints)
        assertTrue(last.speak)
        assertEquals("", last.interventionId)
        assertEquals(LiveCoachingUiPolicy.TECHNICAL_CHANNEL, last.deliveryChannel)
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, last.errorCode)
        assertTrue(last.message.contains("Step back"))
        assertNotNull(LiveCoachingUiPolicy.studentTextBanner(last))
        assertTrue(
            LiveCoachingUiPolicy.showCameraGuidanceBanner(last.message, last.deliveryChannel)
        )
    }

    @Test
    fun visibleIssueIsNotSwallowedWhenVisibilityOkFalse() = runBlocking {
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                RecordingEvidenceCapture()
            )
        engine.startSession("SQUATS")
        val last =
            engine.onFormSignal(
                formIssues = listOf("Ensure major body parts are visible."),
                flags = emptyList(),
                severity = 0.5,
                currentReps = 0,
                visibilityOk = false,
                now = 1_000L
            )
        val banner = LiveCoachingUiPolicy.studentTextBanner(last)
        assertNotNull(banner)
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, banner!!.errorCode)
    }

    @Test
    fun framingFailureDoesNotClearPolicyBPhysicalCycle() = runBlocking {
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
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertEquals(1, capture.events.size)
        val spokeAfterClaim = delivery.spokeCount

        repeat(5) {
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
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertTrue(engine.activeFeedback()?.deliveryChannel == LiveCoachingUiPolicy.TECHNICAL_CHANNEL)
        assertFalse(engine.activeFeedback()?.highlightJoints == true)
    }

    @Test
    fun suppressPhysicalCoachingStopsHighlightWithoutClosingCycle() = runBlocking {
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                RecordingEvidenceCapture()
            )
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
        assertTrue(engine.activeFeedback()?.highlightJoints == true)
        engine.suppressPhysicalCoaching()
        assertFalse(engine.activeFeedback()?.highlightJoints == true)
        assertFalse(engine.activeFeedback()?.speak == true)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, delivery.stopCount)
    }

    @Test
    fun physicalCoachingRejectedWhenCountingInactive() {
        assertFalse(LiveCoachingUiPolicy.acceptPhysicalCoachingSignals(false))
        assertTrue(LiveCoachingUiPolicy.acceptPhysicalCoachingSignals(true))
    }
}
