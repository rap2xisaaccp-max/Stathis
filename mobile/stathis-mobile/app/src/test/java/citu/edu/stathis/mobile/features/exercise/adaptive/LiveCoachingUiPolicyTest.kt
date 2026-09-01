package citu.edu.stathis.mobile.features.exercise.adaptive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCoachingUiPolicyTest {

    @Test
    fun claimedPhysicalErrorHasNoStudentTextBanner() {
        val claimed =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = "FI-1",
                modality = FeedbackModality.VERBAL_TTS,
                errorCode = FormErrorCode.KNEES_IN,
                message = "Keep your knees aligned with your toes.",
                exerciseType = "SQUATS"
            )
        assertTrue(claimed.speak)
        assertTrue(claimed.highlightJoints)
        assertTrue(claimed.showTextBanner)
        assertNull(LiveCoachingUiPolicy.studentTextBanner(claimed))
        assertFalse(
            LiveCoachingUiPolicy.showCameraGuidanceBanner(
                claimed.message,
                claimed.deliveryChannel
            )
        )
    }

    @Test
    fun technicalGuidanceShowsBannerWithoutHighlightOrPhysicalClaim() {
        val technical =
            DeliveredFeedback(
                interventionId = "",
                modality = FeedbackModality.VERBAL_TTS,
                errorCode = FormErrorCode.BODY_NOT_VISIBLE,
                message = "Keep your full body visible in the camera frame.",
                highlightJoints = false,
                speak = true,
                showTextBanner = true,
                deliveryChannel = LiveCoachingUiPolicy.TECHNICAL_CHANNEL,
                exerciseType = "SQUATS"
            )
        val banner = LiveCoachingUiPolicy.studentTextBanner(technical)
        assertNotNull(banner)
        assertEquals(technical.message, banner!!.message)
        assertTrue(banner.speak)
        assertFalse(banner.highlightJoints)
        assertEquals("", banner.interventionId)
        assertTrue(
            LiveCoachingUiPolicy.showCameraGuidanceBanner(banner.message, banner.deliveryChannel)
        )
    }

    @Test
    fun reinforcementAndHighlightTtsChannelsAreNotLiveTextBanners() {
        val reinforce =
            DeliveredFeedback(
                interventionId = "FI-1",
                modality = FeedbackModality.VERBAL_TTS,
                errorCode = FormErrorCode.KNEES_IN,
                message = "Good correction. Maintain that knee position.",
                highlightJoints = false,
                speak = false,
                showTextBanner = true,
                deliveryChannel = "text"
            )
        assertNull(LiveCoachingUiPolicy.studentTextBanner(reinforce))
        assertNull(LiveCoachingUiPolicy.studentTextBanner(null))
    }

    @Test
    fun classroomPracticeHidesClassifierDebugFormCuesAndLiveAccuracy() {
        assertFalse(LiveCoachingUiPolicy.showClassifierDebug(explicitDebugOverlayEnabled = false))
        assertTrue(LiveCoachingUiPolicy.showClassifierDebug(explicitDebugOverlayEnabled = true))
        assertFalse(LiveCoachingUiPolicy.showLiveFormQualityOverlay(explicitDebugOverlayEnabled = false))
        assertTrue(LiveCoachingUiPolicy.showLiveFormQualityOverlay(explicitDebugOverlayEnabled = true))
        assertTrue(LiveCoachingUiPolicy.showPostAttemptAccuracy())
        assertTrue(
            LiveCoachingUiPolicy.studentLiveFormCueIssues(
                listOf("Squat deeper — bend your knees more.", "Keep your knees aligned with your toes.")
            ).isEmpty()
        )
    }

    @Test
    fun engineClaimStillSpeaksAndHighlightsWithoutStudentTextBanner() = runBlocking {
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
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        var last: DeliveredFeedback? = null
        var now = 1_000L
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
        assertTrue(last!!.highlightJoints)
        assertTrue(last!!.speak)
        assertNull(LiveCoachingUiPolicy.studentTextBanner(last))
        assertEquals(1, capture.events.size)
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertTrue(engine.sessionSummary().errorCodes.contains("KNEES_IN"))
        assertFalse(
            LiveCoachingUiPolicy.showCameraGuidanceBanner(last!!.message, last.deliveryChannel)
        )
    }

    @Test
    fun engineTechnicalSignalIsTextOnlyAndDoesNotCapture() = runBlocking {
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
        val last =
            engine.onFormSignal(
                formIssues = listOf("Ensure major body parts are visible."),
                flags = listOf("BODY_NOT_VISIBLE"),
                severity = 0.9,
                currentReps = 0,
                now = 1_000L
            )
        assertEquals(0, delivery.spokeCount)
        assertEquals(1, delivery.technicalSpokeCount)
        assertTrue(capture.events.isEmpty())
        assertEquals(0, engine.sessionSummary().interventionCount)
        val banner = LiveCoachingUiPolicy.studentTextBanner(last)
        assertNotNull(banner)
        assertTrue(banner!!.speak)
        assertFalse(banner.highlightJoints)
        assertEquals(LiveCoachingUiPolicy.TECHNICAL_CHANNEL, banner.deliveryChannel)
        assertTrue(
            LiveCoachingUiPolicy.showCameraGuidanceBanner(
                banner.message,
                banner.deliveryChannel
            )
        )
    }

    @Test
    fun detectorStringsStillReachMappingWithoutBeingRendered() {
        val mapped =
            FormErrorMapper.resolve(
                flags = emptyList(),
                formIssues = listOf("Squat deeper — bend your knees more."),
                exerciseType = "SQUATS"
            )
        assertEquals(FormErrorCode.DEPTH_LOW, mapped)
        assertTrue(
            LiveCoachingUiPolicy.studentLiveFormCueIssues(
                listOf("Squat deeper — bend your knees more.")
            ).isEmpty()
        )
    }

    @Test
    fun evidenceQueueDoesNotDependOnEvidenceNotice() {
        val queue = InMemoryEvidenceQueue()
        val jpeg = ByteArray(64) { 0x7F }
        queue.enqueue(
            FormEvidenceEvent(
                interventionId = "FI-NO-NOTICE",
                sessionId = "SES-NO-NOTICE",
                exerciseType = "SQUATS",
                errorCode = FormErrorCode.KNEES_IN,
                errorDescription = "Knees caving in",
                correctionText = "Keep your knees aligned with your toes."
            ),
            jpeg
        )
        assertEquals(1, queue.pendingCount)
        assertEquals("FI-NO-NOTICE", queue.pending().single().event.interventionId)
        assertFalse(
            LiveCoachingUiPolicy.showCameraGuidanceBanner(
                "Form correction recorded.",
                "highlight_tts"
            )
        )
        assertFalse(
            LiveCoachingUiPolicy.showCameraGuidanceBanner("Keep your knees aligned with your toes.", null)
        )
    }
}
