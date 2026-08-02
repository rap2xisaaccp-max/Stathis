package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Pure planner for modality channels. Visual and TTS are supporting delivery channels
 * and must only activate when an intervention was already logged ([interventionId] present).
 */
object ModalityDeliveryPlanner {

    data class Plan(
        val showTextBanner: Boolean,
        val highlightSkeleton: Boolean,
        val speak: Boolean,
        val highlightJoints: Set<Int>,
        val highlightBones: List<Pair<Int, Int>>,
        val channel: String
    )

    data class DeliveryEvent(
        val interventionId: String,
        val modality: FeedbackModality,
        val channel: String,
        val spoke: Boolean,
        val highlighted: Boolean,
        val epochMs: Long
    )

    /**
     * @param interventionLogged when false, all channels are suppressed (gate behind logger).
     */
    fun plan(
        modality: FeedbackModality,
        errorCode: FormErrorCode,
        interventionLogged: Boolean,
        exerciseType: String? = null
    ): Plan {
        if (!interventionLogged) {
            return Plan(
                showTextBanner = false,
                highlightSkeleton = false,
                speak = false,
                highlightJoints = emptySet(),
                highlightBones = emptyList(),
                channel = "suppressed"
            )
        }
        val targets = ModalityHighlightTargets.forError(errorCode, exerciseType)
        return when (modality) {
            FeedbackModality.VERBAL_TEXT ->
                Plan(
                    showTextBanner = true,
                    highlightSkeleton = false,
                    speak = false,
                    highlightJoints = emptySet(),
                    highlightBones = emptyList(),
                    channel = "text"
                )
            FeedbackModality.VISUAL_HIGHLIGHT, FeedbackModality.DEMONSTRATION ->
                Plan(
                    showTextBanner = true,
                    highlightSkeleton = true,
                    speak = false,
                    highlightJoints = targets.joints,
                    highlightBones = targets.bones,
                    channel = "visual"
                )
            FeedbackModality.VERBAL_TTS ->
                Plan(
                    showTextBanner = true,
                    highlightSkeleton = false,
                    speak = true,
                    highlightJoints = emptySet(),
                    highlightBones = emptyList(),
                    channel = "tts"
                )
        }
    }

    fun toDeliveredFeedback(
        interventionId: String,
        modality: FeedbackModality,
        errorCode: FormErrorCode,
        message: String,
        exerciseType: String? = null
    ): DeliveredFeedback {
        val plan =
            plan(
                modality,
                errorCode,
                interventionLogged = interventionId.isNotBlank(),
                exerciseType = exerciseType
            )
        return DeliveredFeedback(
            interventionId = interventionId,
            modality = modality,
            errorCode = errorCode,
            message = message,
            highlightJoints = plan.highlightSkeleton,
            speak = plan.speak,
            highlightLandmarkIds = plan.highlightJoints,
            highlightBones = plan.highlightBones,
            showTextBanner = plan.showTextBanner,
            deliveryChannel = plan.channel
        )
    }
}
