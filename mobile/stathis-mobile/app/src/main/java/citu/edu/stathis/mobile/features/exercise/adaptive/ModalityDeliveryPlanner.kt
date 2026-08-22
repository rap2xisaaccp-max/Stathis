package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Fixed dual-channel planner: visual highlight of the incorrect region plus TTS.
 * Channels activate only after an intervention id is logged.
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
        return Plan(
            showTextBanner = true,
            highlightSkeleton = true,
            speak = true,
            highlightJoints = targets.joints,
            highlightBones = targets.bones,
            channel = "highlight_tts"
        )
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
