package citu.edu.stathis.mobile.features.exercise.adaptive

data class AdaptiveRecommendation(
    val modality: FeedbackModality = FeedbackModality.VERBAL_TEXT,
    val errorCode: FormErrorCode = FormErrorCode.UNKNOWN,
    val messageCode: String? = null,
    val messageText: String = "Adjust your form and try again.",
    val policySource: PolicySource = PolicySource.DEFAULT,
    val expectedDelta: Double = 0.0,
    val experimentArm: String = "ADAPTIVE",
    val cooldownMs: Int = 8000
)

data class PendingIntervention(
    val physicalId: String,
    val sessionId: String,
    val taskId: String?,
    val classroomId: String?,
    val exerciseType: String,
    val errorCode: FormErrorCode,
    val modality: FeedbackModality,
    val messageCode: String?,
    val messageText: String,
    val deliveredAtEpochMs: Long,
    val baselineSeverity: Double,
    val policySource: PolicySource,
    val experimentArm: String,
    val baselineReps: Int,
    val windowMs: Long = 10_000L
)

data class DeliveredFeedback(
    val interventionId: String,
    val modality: FeedbackModality,
    val errorCode: FormErrorCode,
    val message: String,
    val highlightJoints: Boolean,
    val speak: Boolean,
    val highlightLandmarkIds: Set<Int> = emptySet(),
    val highlightBones: List<Pair<Int, Int>> = emptyList(),
    val showTextBanner: Boolean = true,
    val deliveryChannel: String = "text"
)

data class InterventionRequestDto(
    val physicalId: String? = null,
    val sessionId: String,
    val taskId: String? = null,
    val classroomId: String? = null,
    val exerciseType: String,
    val errorCode: String,
    val modality: String,
    val messageCode: String? = null,
    val messageText: String? = null,
    val deliveredAt: String? = null,
    val baselineSeverity: Double,
    val policySource: String,
    val experimentArm: String? = null
)

data class ResponseRequestDto(
    val physicalId: String? = null,
    val interventionPhysicalId: String,
    val windowEndAt: String? = null,
    val postSeverity: Double,
    val delta: Double? = null,
    val repsInWindow: Int? = null,
    val success: Boolean? = null,
    val confoundersJson: Map<String, Any?>? = null
)

data class AdaptiveBatchIngestDto(
    val interventions: List<InterventionRequestDto> = emptyList(),
    val responses: List<ResponseRequestDto> = emptyList()
)

data class AdaptiveBatchResultDto(
    val interventionsSaved: Int = 0,
    val responsesSaved: Int = 0,
    val interventionPhysicalIds: List<String> = emptyList(),
    val responsePhysicalIds: List<String> = emptyList()
)

data class RecommendationRequestDto(
    val exerciseType: String,
    val errorCode: String,
    val currentSeverity: Double? = null,
    val staticControl: Boolean? = false
)

data class RecommendationResponseDto(
    val modality: String? = null,
    val errorCode: String? = null,
    val messageCode: String? = null,
    val messageText: String? = null,
    val policySource: String? = null,
    val expectedDelta: Double? = null,
    val experimentArm: String? = null,
    val cooldownMs: Int? = null
)
