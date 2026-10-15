package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalityDeliveryPlannerTest {

    @Test
    fun suppressesChannelsWhenInterventionNotLogged() {
        val plan =
            ModalityDeliveryPlanner.plan(
                FeedbackModality.VERBAL_TTS,
                FormErrorCode.SAG,
                interventionLogged = false
            )
        assertFalse(plan.showTextBanner)
        assertFalse(plan.highlightSkeleton)
        assertFalse(plan.speak)
        assertEquals("suppressed", plan.channel)
    }

    @Test
    fun loggedInterventionAlwaysHighlightsAndSpeaks() {
        val plan =
            ModalityDeliveryPlanner.plan(
                FeedbackModality.VERBAL_TEXT,
                FormErrorCode.DEPTH_LOW,
                interventionLogged = true
            )
        assertTrue(plan.showTextBanner)
        assertTrue(plan.highlightSkeleton)
        assertTrue(plan.speak)
        assertEquals("highlight_tts", plan.channel)
        assertTrue(plan.highlightJoints.contains(ModalityHighlightTargets.LEFT_KNEE))
    }

    @Test
    fun deliveredFeedbackCarriesBothChannels() {
        val delivered =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = "FI-TEST",
                modality = FeedbackModality.VERBAL_TTS,
                errorCode = FormErrorCode.SAG,
                message = "Keep your hips level."
            )
        assertTrue(delivered.highlightJoints)
        assertTrue(delivered.speak)
        assertTrue(delivered.highlightLandmarkIds.contains(ModalityHighlightTargets.LEFT_HIP))
        assertEquals("highlight_tts", delivered.deliveryChannel)
        assertEquals("FI-TEST", delivered.interventionId)
    }

    @Test
    fun blankInterventionIdSuppressesDelivery() {
        val delivered =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = "",
                modality = FeedbackModality.VERBAL_TTS,
                errorCode = FormErrorCode.PIKE,
                message = "Keep a straight line."
            )
        assertFalse(delivered.speak)
        assertFalse(delivered.highlightJoints)
        assertFalse(delivered.showTextBanner)
    }
}
