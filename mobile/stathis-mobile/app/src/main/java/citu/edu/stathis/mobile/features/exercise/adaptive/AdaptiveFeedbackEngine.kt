package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.remote.api.AdaptiveApi
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * On-device closed-loop session logger + orchestrator.
 *
 * Phase 4 focus: map errors → log interventions → measure response windows → offline queue with retry.
 */
@Singleton
class AdaptiveFeedbackEngine @Inject constructor(
    private val adaptiveApi: AdaptiveApi,
    private val delivery: AdaptiveFeedbackDelivery
) {
    private val pendingResponses = ConcurrentLinkedQueue<PendingIntervention>()
    private val offlineQueue = AdaptiveOfflineQueue(maxRetries = 5)

    @Volatile private var sessionId: String = ""
    @Volatile private var taskId: String? = null
    @Volatile private var classroomId: String? = null
    @Volatile private var exerciseType: String = "UNKNOWN"
    @Volatile private var staticControl: Boolean = false
    @Volatile private var sessionContext: String = RctExperimentPrefs.CONTEXT_TASK
    @Volatile private var cooldownMs: Long = 8000L
    @Volatile private var activeDelivery: DeliveredFeedback? = null
    @Volatile private var cachedRecommendation: AdaptiveRecommendation? = null
    private val interventionGate = RealtimeInterventionGate()
    private val sessionModalities = linkedSetOf<String>()
    private val sessionErrorCodes = linkedSetOf<String>()
    @Volatile private var sessionInterventionCount: Int = 0
    @Volatile private var sessionRecorded: Boolean = false

    /** Exposed for tests / diagnostics. */
    fun offlineQueueForTests(): AdaptiveOfflineQueue = offlineQueue

    fun startSession(
        exerciseType: String,
        taskId: String? = null,
        classroomId: String? = null,
        staticControl: Boolean = false,
        sessionContext: String = RctExperimentPrefs.CONTEXT_TASK
    ) {
        this.sessionId = "SES-${UUID.randomUUID().toString().uppercase()}"
        this.exerciseType = exerciseType
        this.taskId = taskId
        this.classroomId = classroomId
        this.staticControl = staticControl
        this.sessionContext = sessionContext
        this.activeDelivery = null
        this.cooldownMs = 8000L
        this.sessionInterventionCount = 0
        this.sessionRecorded = false
        sessionModalities.clear()
        sessionErrorCodes.clear()
        interventionGate.reset()
        pendingResponses.clear()
        delivery.ensureInitialized()
        Timber.d(
            "Adaptive session started context=%s staticControl=%s exercise=%s",
            sessionContext,
            staticControl,
            exerciseType
        )
    }

    fun currentSessionId(): String = sessionId

    fun sessionSummary(): AdaptiveSessionSummary =
        AdaptiveSessionSummary(
            interventionCount = sessionInterventionCount,
            modalitiesUsed = sessionModalities.toList(),
            errorCodes = sessionErrorCodes.toList(),
            syncPending = !offlineQueue.isEmpty()
        )

    fun activeFeedback(): DeliveredFeedback? = activeDelivery

    fun clearActiveFeedbackIfExpired(now: Long = System.currentTimeMillis()) {
        val active = activeDelivery ?: return
        val pending = pendingResponses.find { it.physicalId == active.interventionId }
        if (pending != null && now - pending.deliveredAtEpochMs > 4000L) {
            if (now - pending.deliveredAtEpochMs > pending.windowMs) {
                activeDelivery = null
            } else if (active.highlightJoints) {
                val stillHighlight = now - pending.deliveredAtEpochMs < 4000L
                activeDelivery =
                    active.copy(
                        highlightJoints = stillHighlight,
                        highlightLandmarkIds =
                            if (stillHighlight) active.highlightLandmarkIds else emptySet(),
                        highlightBones =
                            if (stillHighlight) active.highlightBones else emptyList()
                    )
            }
        }
    }

    /**
     * Called each analysis tick with current form issue strings and severity estimate 0..1.
     * Returns feedback to show, or null if suppressed by cooldown / no error.
     */
    suspend fun onFormSignal(
        formIssues: List<String>,
        flags: List<String> = emptyList(),
        severity: Double,
        currentReps: Int,
        visibilityOk: Boolean = true
    ): DeliveredFeedback? {
        val now = System.currentTimeMillis()
        clearActiveFeedbackIfExpired(now)
        closeExpiredWindows(now, severity, currentReps, visibilityOk)

        if (!visibilityOk) {
            return activeDelivery
        }
        // Wait out the open response window so deltas stay attributable.
        if (pendingResponses.isNotEmpty()) {
            return activeDelivery
        }

        val errorCode = FormErrorMapper.resolve(flags, formIssues)
        if (!interventionGate.shouldDeliver(errorCode, severity, now, cooldownMs)) {
            return activeDelivery
        }
        val resolvedCode = errorCode ?: return activeDelivery

        val recommendation = resolveRecommendation(resolvedCode, severity)
        cooldownMs = recommendation.cooldownMs.toLong().coerceAtLeast(5000L)

        val interventionId = "FI-${UUID.randomUUID().toString().uppercase()}"
        val pending =
            PendingIntervention(
                physicalId = interventionId,
                sessionId = sessionId.ifBlank { "SES-LOCAL" },
                taskId = taskId,
                classroomId = classroomId,
                exerciseType = exerciseType,
                errorCode = resolvedCode,
                modality = recommendation.modality,
                messageCode = recommendation.messageCode,
                messageText = recommendation.messageText,
                deliveredAtEpochMs = now,
                baselineSeverity = severity.coerceIn(0.0, 1.0),
                policySource = recommendation.policySource,
                experimentArm = resolveExperimentArm(recommendation.experimentArm),
                baselineReps = currentReps
            )

        pendingResponses.add(pending)
        offlineQueue.enqueueIntervention(pending.toRequestDto())
        interventionGate.markDelivered(now)
        sessionInterventionCount += 1
        sessionModalities.add(recommendation.modality.name)
        sessionErrorCodes.add(resolvedCode.name)

        val delivered =
            delivery.deliver(
                DeliveredFeedback(
                    interventionId = interventionId,
                    modality = recommendation.modality,
                    errorCode = resolvedCode,
                    message = recommendation.messageText,
                    highlightJoints = false,
                    speak = false
                ),
                now = now
            )
        activeDelivery = delivered

        return delivered
    }

    private fun resolveExperimentArm(recommendedArm: String?): String {
        val base =
            when {
                staticControl -> "STATIC"
                !recommendedArm.isNullOrBlank() &&
                    recommendedArm.contains("STATIC", ignoreCase = true) -> "STATIC"
                else -> "ADAPTIVE"
            }
        return RctExperimentPrefs.composeArm(base, sessionContext)
    }

    private suspend fun resolveRecommendation(
        errorCode: FormErrorCode,
        severity: Double
    ): AdaptiveRecommendation {
        if (staticControl) {
            return AdaptiveRecommendation(
                modality = FeedbackModality.VERBAL_TEXT,
                errorCode = errorCode,
                messageCode = errorCode.name,
                messageText = defaultMessage(errorCode),
                policySource = PolicySource.STATIC_CONTROL,
                experimentArm = resolveExperimentArm("STATIC"),
                cooldownMs = 8000
            )
        }

        return try {
            val remote =
                withContext(Dispatchers.IO) {
                    adaptiveApi.recommend(
                        RecommendationRequestDto(
                            exerciseType = exerciseType,
                            errorCode = errorCode.name,
                            currentSeverity = severity,
                            staticControl = false
                        )
                    )
                }
            AdaptiveRecommendation(
                modality =
                    runCatching { FeedbackModality.valueOf(remote.modality ?: "VERBAL_TEXT") }
                        .getOrDefault(FeedbackModality.VERBAL_TEXT),
                errorCode = errorCode,
                messageCode = remote.messageCode,
                messageText = remote.messageText ?: defaultMessage(errorCode),
                policySource =
                    runCatching { PolicySource.valueOf(remote.policySource ?: "DEFAULT") }
                        .getOrDefault(PolicySource.DEFAULT),
                expectedDelta = remote.expectedDelta ?: 0.0,
                experimentArm = remote.experimentArm ?: "ADAPTIVE",
                cooldownMs = remote.cooldownMs ?: 8000
            ).also { cachedRecommendation = it }
        } catch (t: Throwable) {
            Timber.w(t, "Adaptive recommend failed; using local epsilon-greedy fallback")
            localRecommend(errorCode)
        }
    }

    private fun localRecommend(errorCode: FormErrorCode): AdaptiveRecommendation {
        val explore = Random.nextDouble() < 0.2
        val modalities =
            listOf(
                FeedbackModality.VERBAL_TEXT,
                FeedbackModality.VISUAL_HIGHLIGHT,
                FeedbackModality.VERBAL_TTS
            )
        val modality =
            if (explore) modalities.random()
            else cachedRecommendation?.modality ?: FeedbackModality.VERBAL_TEXT
        return AdaptiveRecommendation(
            modality = modality,
            errorCode = errorCode,
            messageCode = errorCode.name,
            messageText = defaultMessage(errorCode),
            policySource = if (explore) PolicySource.EXPLORE else PolicySource.DEFAULT,
            experimentArm = resolveExperimentArm("ADAPTIVE"),
            cooldownMs = 8000
        )
    }

    private fun closeExpiredWindows(
        now: Long,
        currentSeverity: Double,
        currentReps: Int,
        visibilityOk: Boolean
    ) {
        val expired = pendingResponses.filter { now - it.deliveredAtEpochMs >= it.windowMs }
        expired.forEach { pending ->
            pendingResponses.remove(pending)
            val post = currentSeverity.coerceIn(0.0, 1.0)
            val delta = pending.baselineSeverity - post
            offlineQueue.enqueueResponse(
                ResponseRequestDto(
                    physicalId = "FR-${UUID.randomUUID().toString().uppercase()}",
                    interventionPhysicalId = pending.physicalId,
                    windowEndAt = Instant.ofEpochMilli(now).toString(),
                    postSeverity = post,
                    delta = delta,
                    repsInWindow = max(0, currentReps - pending.baselineReps),
                    success = delta >= 0.15,
                    confoundersJson =
                        mapOf(
                            "visibilityOk" to visibilityOk,
                            "sessionId" to pending.sessionId
                        )
                )
            )
            if (activeDelivery?.interventionId == pending.physicalId) {
                activeDelivery = null
            }
        }
    }

    suspend fun flush() {
        val now = System.currentTimeMillis()
        pendingResponses.forEach { pending ->
            offlineQueue.enqueueResponse(
                ResponseRequestDto(
                    physicalId = "FR-${UUID.randomUUID().toString().uppercase()}",
                    interventionPhysicalId = pending.physicalId,
                    windowEndAt = Instant.ofEpochMilli(now).toString(),
                    postSeverity = pending.baselineSeverity,
                    delta = 0.0,
                    repsInWindow = 0,
                    success = false,
                    confoundersJson = mapOf("flushed" to true)
                )
            )
        }
        pendingResponses.clear()

        val (queuedInterventions, queuedResponses) = offlineQueue.drain()
        val hasBatch = queuedInterventions.isNotEmpty() || queuedResponses.isNotEmpty()

        if (hasBatch) {
            try {
                withContext(Dispatchers.IO) {
                    adaptiveApi.ingestBatch(
                        AdaptiveBatchIngestDto(
                            interventions = queuedInterventions.map { it.payload },
                            responses = queuedResponses.map { it.payload }
                        )
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "Failed to flush adaptive batch; re-queueing with retry budget")
                offlineQueue.requeueAfterFailure(queuedInterventions, queuedResponses)
                // Still attempt session recording below so clean/empty sessions count.
            }
        }

        // Always bump mastery sessionsCount once per ended session, even with an empty queue
        // (clean sessions with zero interventions previously skipped recordSession).
        if (!sessionRecorded && exerciseType.isNotBlank()) {
            sessionRecorded = true
            runCatching {
                withContext(Dispatchers.IO) {
                    adaptiveApi.recordSession(exerciseType)
                }
            }.onFailure { t ->
                sessionRecorded = false
                Timber.w(t, "Failed to record adaptive session for %s", exerciseType)
            }
        }
    }

    fun endSession() {
        delivery.stopSpeaking()
        activeDelivery = null
    }

    companion object {
        fun defaultMessage(errorCode: FormErrorCode): String =
            when (errorCode) {
                FormErrorCode.DEPTH_LOW -> "Go deeper to at least parallel."
                FormErrorCode.KNEES_IN -> "Push knees outward over toes."
                FormErrorCode.CHEST_UP -> "Keep chest up and back straight."
                FormErrorCode.PIKE -> "Keep a straight line from head to heels."
                FormErrorCode.SAG -> "Avoid sagging hips."
                FormErrorCode.LOW_ROM -> "Increase trunk flexion."
                FormErrorCode.LOW_VISIBILITY, FormErrorCode.BODY_NOT_VISIBLE ->
                    "Keep major body parts visible in frame."
                FormErrorCode.LOW_CONFIDENCE -> "Hold still so form can be detected."
                FormErrorCode.LEGS_BENT -> "Keep your legs straighter for better control."
                FormErrorCode.UNKNOWN -> "Adjust your form and try again."
            }

        fun estimateSeverity(
            formIssues: List<String>,
            confidence: Float,
            flags: List<String> = emptyList(),
            ruleSeverity: Double? = null
        ): Double =
            FormErrorMapper.estimateSeverity(formIssues, confidence, flags, ruleSeverity)
    }
}

private fun PendingIntervention.toRequestDto(): InterventionRequestDto =
    InterventionRequestDto(
        physicalId = physicalId,
        sessionId = sessionId,
        taskId = taskId,
        classroomId = classroomId,
        exerciseType = exerciseType,
        errorCode = errorCode.name,
        modality = modality.name,
        messageCode = messageCode,
        messageText = messageText,
        deliveredAt = Instant.ofEpochMilli(deliveredAtEpochMs).toString(),
        baselineSeverity = baselineSeverity,
        policySource = policySource.name,
        experimentArm = experimentArm
    )
