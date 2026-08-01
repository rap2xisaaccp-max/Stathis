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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * On-device closed-loop session logger + orchestrator.
 *
 * One meaningful intervention = confirmed error → one modality → one delivery → one response
 * window → one FR → cooldown. Form signals are serialized; lifecycle claims before async work.
 */
@Singleton
class AdaptiveFeedbackEngine @Inject constructor(
    private val adaptiveApi: AdaptiveApi,
    private val delivery: AdaptiveFeedbackDelivery
) {
    private val pendingResponses = ConcurrentLinkedQueue<PendingIntervention>()
    private val offlineQueue = AdaptiveOfflineQueue(maxRetries = 5)
    private val signalMutex = Mutex()

    @Volatile private var sessionId: String = ""
    @Volatile private var taskId: String? = null
    @Volatile private var classroomId: String? = null
    @Volatile private var exerciseType: String = "UNKNOWN"
    @Volatile private var staticControl: Boolean = false
    @Volatile private var sessionContext: String = RctExperimentPrefs.CONTEXT_TASK
    @Volatile private var cooldownMs: Long = 8000L
    @Volatile private var activeDelivery: DeliveredFeedback? = null
    @Volatile private var cachedRecommendation: AdaptiveRecommendation? = null
    private val interventionLifecycle = InterventionLifecycle()
    private val sessionModalities = linkedSetOf<String>()
    private val sessionErrorCodes = linkedSetOf<String>()
    @Volatile private var sessionInterventionCount: Int = 0
    @Volatile private var sessionRecorded: Boolean = false

    /** Exposed for tests / diagnostics. */
    fun offlineQueueForTests(): AdaptiveOfflineQueue = offlineQueue

    /** Exposed for diagnostics / UI status. */
    fun lifecyclePhase(): InterventionPhase = interventionLifecycle.phase

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
        interventionLifecycle.reset()
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
    ): DeliveredFeedback? =
        signalMutex.withLock {
            val now = System.currentTimeMillis()
            clearActiveFeedbackIfExpired(now)
            closeExpiredWindows(now, severity, currentReps, visibilityOk)

            if (!visibilityOk) {
                return@withLock activeDelivery
            }

            val errorCode = FormErrorMapper.resolve(flags, formIssues)

            // Technical camera/detection quality: UI guidance only — no coaching FI/FR cycle.
            if (FormErrorClassifier.isTechnical(errorCode)) {
                val guidance =
                    CoachingInstructionCatalog.messageText(
                        exerciseType,
                        errorCode!!,
                        InstructionIntensity.REMINDER
                    )
                val techUi =
                    DeliveredFeedback(
                        interventionId = "",
                        modality = FeedbackModality.VERBAL_TEXT,
                        errorCode = errorCode,
                        message = guidance,
                        highlightJoints = false,
                        speak = false,
                        showTextBanner = true,
                        deliveryChannel = "technical",
                        exerciseType = exerciseType
                    )
                activeDelivery = techUi
                return@withLock techUi
            }

            if (!FormErrorClassifier.isCoachable(errorCode)) {
                return@withLock activeDelivery
            }

            if (pendingResponses.isNotEmpty()) {
                return@withLock activeDelivery
            }

            val cycle =
                interventionLifecycle.tryClaimDelivery(
                    errorCode,
                    severity,
                    now,
                    cooldownMs,
                    currentReps
                )
            if (cycle == null) {
                return@withLock activeDelivery
            }

            val resolvedCode = errorCode!!
            val intensity = interventionLifecycle.intensityFor(resolvedCode)
            val interventionId =
                stableInterventionId(
                    sessionId = sessionId.ifBlank { "SES-LOCAL" },
                    exerciseType = exerciseType,
                    errorCode = resolvedCode,
                    cycle = cycle
                )

            val recommendation =
                try {
                    resolveRecommendation(resolvedCode, severity, intensity)
                } catch (t: Throwable) {
                    Timber.w(t, "Recommend failed after claim; aborting cycle")
                    interventionLifecycle.abortClaim(System.currentTimeMillis())
                    return@withLock activeDelivery
                }
            cooldownMs = recommendation.cooldownMs.toLong().coerceAtLeast(8_000L)

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
            interventionLifecycle.markDelivered(resolvedCode, now, currentReps)
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
                        speak = false,
                        exerciseType = exerciseType
                    ),
                    now = now
                )
            activeDelivery = delivered
            delivered
        }

    /** Deterministic FI id for a coaching cycle — retries reuse the same key. */
    internal fun stableInterventionId(
        sessionId: String,
        exerciseType: String,
        errorCode: FormErrorCode,
        cycle: Int
    ): String {
        val material = "$sessionId|$exerciseType|${errorCode.name}|C$cycle"
        val uuid = UUID.nameUUIDFromBytes(material.toByteArray(Charsets.UTF_8)).toString().uppercase()
        return "FI-$uuid"
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
        severity: Double,
        intensity: InstructionIntensity
    ): AdaptiveRecommendation {
        val catalogText = CoachingInstructionCatalog.messageText(exerciseType, errorCode, intensity)
        val catalogCode = CoachingInstructionCatalog.messageCode(exerciseType, errorCode, intensity)
        if (staticControl) {
            return AdaptiveRecommendation(
                modality = FeedbackModality.VERBAL_TEXT,
                errorCode = errorCode,
                messageCode = catalogCode,
                messageText = catalogText,
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
                // Prefer local intensity ladder so escalation is non-repetitive even if server returns reminder.
                messageCode = catalogCode,
                messageText = catalogText.ifBlank { remote.messageText ?: defaultMessage(errorCode) },
                policySource =
                    runCatching { PolicySource.valueOf(remote.policySource ?: "DEFAULT") }
                        .getOrDefault(PolicySource.DEFAULT),
                expectedDelta = remote.expectedDelta ?: 0.0,
                experimentArm = remote.experimentArm ?: "ADAPTIVE",
                cooldownMs = remote.cooldownMs ?: 8000
            ).also { cachedRecommendation = it }
        } catch (t: Throwable) {
            Timber.w(t, "Adaptive recommend failed; using local epsilon-greedy fallback")
            localRecommend(errorCode, intensity)
        }
    }

    private fun localRecommend(
        errorCode: FormErrorCode,
        intensity: InstructionIntensity
    ): AdaptiveRecommendation {
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
            messageCode = CoachingInstructionCatalog.messageCode(exerciseType, errorCode, intensity),
            messageText = CoachingInstructionCatalog.messageText(exerciseType, errorCode, intensity),
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
            val success = delta >= 0.15
            offlineQueue.enqueueResponse(
                ResponseRequestDto(
                    physicalId = "FR-${UUID.randomUUID().toString().uppercase()}",
                    interventionPhysicalId = pending.physicalId,
                    windowEndAt = Instant.ofEpochMilli(now).toString(),
                    postSeverity = post,
                    delta = delta,
                    repsInWindow = max(0, currentReps - pending.baselineReps),
                    success = success,
                    confoundersJson =
                        mapOf(
                            "visibilityOk" to visibilityOk,
                            "sessionId" to pending.sessionId
                        )
                )
            )
            interventionLifecycle.markResponseClosed(successful = success)
            if (success) {
                val reinforce =
                    CoachingInstructionCatalog.messageText(
                        pending.exerciseType,
                        pending.errorCode,
                        InstructionIntensity.REINFORCEMENT
                    )
                activeDelivery =
                    DeliveredFeedback(
                        interventionId = pending.physicalId,
                        modality = FeedbackModality.VERBAL_TEXT,
                        errorCode = pending.errorCode,
                        message = reinforce,
                        highlightJoints = false,
                        speak = false,
                        showTextBanner = true,
                        deliveryChannel = "text"
                    )
                interventionLifecycle.markReinforcementDelivered(now)
            } else if (activeDelivery?.interventionId == pending.physicalId) {
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
            CoachingInstructionCatalog.messageText(null, errorCode, InstructionIntensity.REMINDER)

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
