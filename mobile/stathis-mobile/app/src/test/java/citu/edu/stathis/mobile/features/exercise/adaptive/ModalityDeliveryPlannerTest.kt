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
    fun verbalTextIsBannerOnly() {
        val plan =
            ModalityDeliveryPlanner.plan(
                FeedbackModality.VERBAL_TEXT,
                FormErrorCode.DEPTH_LOW,
                interventionLogged = true
            )
        assertTrue(plan.showTextBanner)
        assertFalse(plan.highlightSkeleton)
        assertFalse(plan.speak)
        assertEquals("text", plan.channel)
    }

    @Test
    fun visualHighlightTargetsErrorJoints() {
        val plan =
            ModalityDeliveryPlanner.plan(
                FeedbackModality.VISUAL_HIGHLIGHT,
                FormErrorCode.KNEES_IN,
                interventionLogged = true
            )
        assertFalse(plan.showTextBanner)
        assertTrue(plan.highlightSkeleton)
        assertFalse(plan.speak)
        assertTrue(plan.highlightJoints.contains(ModalityHighlightTargets.LEFT_KNEE))
        assertTrue(plan.highlightJoints.contains(ModalityHighlightTargets.RIGHT_KNEE))
        assertEquals("visual", plan.channel)
    }

    @Test
    fun ttsSpeaksWithoutSkeletonHighlight() {
        val plan =
            ModalityDeliveryPlanner.plan(
                FeedbackModality.VERBAL_TTS,
                FormErrorCode.CHEST_UP,
                interventionLogged = true
            )
        assertFalse(plan.showTextBanner)
        assertFalse(plan.highlightSkeleton)
        assertTrue(plan.speak)
        assertEquals("tts", plan.channel)
    }

    @Test
    fun deliveredFeedbackCarriesInstrumentationFields() {
        val delivered =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = "FI-TEST",
                modality = FeedbackModality.VISUAL_HIGHLIGHT,
                errorCode = FormErrorCode.SAG,
                message = "Avoid sagging hips."
            )
        assertTrue(delivered.highlightJoints)
        assertFalse(delivered.speak)
        assertTrue(delivered.highlightLandmarkIds.contains(ModalityHighlightTargets.LEFT_HIP))
        assertEquals("visual", delivered.deliveryChannel)
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
