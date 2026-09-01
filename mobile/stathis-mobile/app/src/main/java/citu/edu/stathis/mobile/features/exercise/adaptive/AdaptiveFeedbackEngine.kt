package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.remote.api.AdaptiveApi
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * On-device coaching orchestrator.
 *
 * Confirmed physical form error → highlight + TTS cue; at most one evidence snapshot per attempt.
 * Form signals are serialized; lifecycle claims before delivery.
 */
@Singleton
class AdaptiveFeedbackEngine @Inject constructor(
    private val adaptiveApi: AdaptiveApi,
    private val delivery: CoachingDelivery,
    private val offlineQueue: OfflineQueue,
    private val evidenceQueue: EvidenceQueue,
    private val evidenceCapture: FormEvidenceCapture
) {
    private val pendingResponses = ConcurrentLinkedQueue<PendingIntervention>()
    private val signalMutex = Mutex()

    @Volatile private var sessionId: String = ""
    @Volatile private var taskId: String? = null
    @Volatile private var classroomId: String? = null
    @Volatile private var attemptNumber: Int? = null
    @Volatile private var exerciseType: String = "UNKNOWN"
    @Volatile private var cooldownMs: Long = 8000L
    @Volatile private var activeDelivery: DeliveredFeedback? = null
    private val interventionLifecycle = InterventionLifecycle()
    private val sessionErrorCodes = linkedSetOf<String>()
    @Volatile private var sessionInterventionCount: Int = 0
    @Volatile private var sessionRecorded: Boolean = false

    fun offlineQueueForTests(): AdaptiveOfflineQueue =
        if (offlineQueue is AdaptiveOfflineQueue) offlineQueue else throw IllegalStateException("offlineQueue is not AdaptiveOfflineQueue")

    fun lifecyclePhase(): InterventionPhase = interventionLifecycle.phase

    fun startSession(
        exerciseType: String,
        taskId: String? = null,
        classroomId: String? = null,
        attemptNumber: Int? = null
    ) {
        this.sessionId = "SES-${UUID.randomUUID().toString().uppercase()}"
        this.exerciseType = exerciseType
        this.taskId = taskId
        this.classroomId = classroomId
        this.attemptNumber = attemptNumber
        this.activeDelivery = null
        this.cooldownMs = 8000L
        this.sessionInterventionCount = 0
        this.sessionRecorded = false
        sessionErrorCodes.clear()
        interventionLifecycle.reset()
        pendingResponses.clear()
        delivery.resetSessionSpeech()
        delivery.ensureInitialized()
        Timber.d("Coaching session started exercise=%s attempt=%s", exerciseType, attemptNumber)
    }

    fun currentSessionId(): String = sessionId

    fun sessionSummary(): AdaptiveSessionSummary =
        AdaptiveSessionSummary(
            interventionCount = sessionInterventionCount,
            modalitiesUsed = if (sessionInterventionCount > 0) listOf("HIGHLIGHT_TTS") else emptyList(),
            errorCodes = sessionErrorCodes.toList(),
            syncPending = !offlineQueue.isEmpty() || !evidenceQueue.isEmpty()
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
        visibilityOk: Boolean = true,
        now: Long = System.currentTimeMillis()
    ): DeliveredFeedback? =
        signalMutex.withLock {
            clearActiveFeedbackIfExpired(now)
            closeExpiredWindows(now, severity)

            // Framing/camera problems must become Camera guidance, not a silent skip.
            // visibilityOk=false is a fallback when callers did not attach a technical issue.
            val effectiveIssues =
                if (!visibilityOk &&
                    formIssues.none { issue ->
                        FormErrorClassifier.isTechnical(
                            FormErrorMapper.resolve(emptyList(), listOf(issue), exerciseType)
                        )
                    }
                ) {
                    formIssues + "Make sure the required body regions are visible in the camera."
                } else {
                    formIssues
                }

            val errorCode = FormErrorMapper.resolve(flags, effectiveIssues, exerciseType)

            if (FormErrorClassifier.isTechnical(errorCode)) {
                val guidance =
                    technicalGuidanceMessage(effectiveIssues, errorCode!!, exerciseType)
                delivery.speakTechnical(guidance, now)
                val techUi =
                    DeliveredFeedback(
                        interventionId = "",
                        modality = FeedbackModality.VERBAL_TTS,
                        errorCode = errorCode,
                        message = guidance,
                        highlightJoints = false,
                        speak = true,
                        showTextBanner = true,
                        deliveryChannel = LiveCoachingUiPolicy.TECHNICAL_CHANNEL,
                        exerciseType = exerciseType
                    )
                activeDelivery = techUi
                return@withLock techUi
            }

            delivery.onTechnicalConditionCleared()
            if (activeDelivery?.deliveryChannel == LiveCoachingUiPolicy.TECHNICAL_CHANNEL) {
                activeDelivery = null
            }

            if (errorCode == FormErrorCode.UNKNOWN) {
                // Unmapped or cross-exercise signal: no coaching evidence, and do not treat
                // it as a successful form correction that re-arms the lifecycle.
                return@withLock activeDelivery
            }

            if (!FormErrorClassifier.isCoachableForExercise(exerciseType, errorCode)) {
                // No physical error this tick (student corrected, or no issue). Observe a
                // genuine clear so a later repeat can re-arm. Technical/camera signals
                // already returned above and must not count as form-corrected.
                interventionLifecycle.tryClaimDelivery(
                    null,
                    0.0,
                    now,
                    cooldownMs,
                    currentReps
                )
                return@withLock activeDelivery
            }

            if (!CoachingInstructionCatalog.hasReviewedInstruction(exerciseType, errorCode)) {
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

            val catalogText = CoachingInstructionCatalog.messageText(exerciseType, resolvedCode, intensity)
            val catalogCode = CoachingInstructionCatalog.messageCode(exerciseType, resolvedCode, intensity)

            val pending =
                PendingIntervention(
                    physicalId = interventionId,
                    sessionId = sessionId.ifBlank { "SES-LOCAL" },
                    taskId = taskId,
                    classroomId = classroomId,
                    exerciseType = exerciseType,
                    errorCode = resolvedCode,
                    modality = FeedbackModality.VERBAL_TTS,
                    messageCode = catalogCode,
                    messageText = catalogText,
                    deliveredAtEpochMs = now,
                    baselineSeverity = severity.coerceIn(0.0, 1.0),
                    policySource = PolicySource.DEFAULT,
                    experimentArm = null,
                    baselineReps = currentReps
                )

            pendingResponses.add(pending)
            offlineQueue.enqueueIntervention(pending.toRequestDto())
            interventionLifecycle.markDelivered(resolvedCode, now, currentReps)
            sessionInterventionCount += 1
            sessionErrorCodes.add(resolvedCode.name)

            val delivered =
                delivery.deliver(
                    DeliveredFeedback(
                        interventionId = interventionId,
                        modality = FeedbackModality.VERBAL_TTS,
                        errorCode = resolvedCode,
                        message = catalogText,
                        highlightJoints = false,
                        speak = false,
                        exerciseType = exerciseType
                    ),
                    now = now
                )
            activeDelivery = delivered
            evidenceCapture.onConfirmedCoaching(
                FormEvidenceEvent(
                    interventionId = interventionId,
                    sessionId = pending.sessionId,
                    taskId = taskId,
                    classroomId = classroomId,
                    attemptNumber = attemptNumber,
                    exerciseType = exerciseType,
                    errorCode = resolvedCode,
                    errorDescription = FormErrorCopy.explanation(resolvedCode, exerciseType).ifBlank {
                        FormErrorCopy.label(resolvedCode, exerciseType)
                    },
                    correctionText = catalogText,
                    capturedAtIso = Instant.ofEpochMilli(now).toString()
                )
            )
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

    /**
     * Ends the on-device response-observation *window* (banner / highlight timeout).
     * Does **not** re-arm the lifecycle: a continuously present error stays disarmed
     * until sustained clear frames or [startSession] reset.
     */
    private fun closeExpiredWindows(
        now: Long,
        currentSeverity: Double
    ) {
        val expired = pendingResponses.filter { now - it.deliveredAtEpochMs >= it.windowMs }
        expired.forEach { pending ->
            pendingResponses.remove(pending)
            val post = currentSeverity.coerceIn(0.0, 1.0)
            val delta = pending.baselineSeverity - post
            val success = delta >= 0.15
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
                        modality = FeedbackModality.VERBAL_TTS,
                        errorCode = pending.errorCode,
                        message = reinforce,
                        highlightJoints = false,
                        speak = false,
                        showTextBanner = true,
                        deliveryChannel = "text"
                    )
            } else if (activeDelivery?.interventionId == pending.physicalId) {
                activeDelivery = null
            }
        }
    }

    suspend fun flush() {
        pendingResponses.clear()

        val (queuedInterventions, _) = offlineQueue.drain()
        if (queuedInterventions.isNotEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    adaptiveApi.ingestBatch(
                        AdaptiveBatchIngestDto(
                            interventions = queuedInterventions.map { it.payload },
                            responses = emptyList()
                        )
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "Failed to flush coaching batch; re-queueing with retry budget")
                offlineQueue.requeueAfterFailure(queuedInterventions, emptyList())
            }
        }

        flushEvidence()

        if (!sessionRecorded && exerciseType.isNotBlank()) {
            sessionRecorded = true
            runCatching {
                withContext(Dispatchers.IO) {
                    adaptiveApi.recordSession(exerciseType)
                }
            }.onFailure { t ->
                sessionRecorded = false
                Timber.w(t, "Failed to record coaching session for %s", exerciseType)
            }
        }
    }

    private suspend fun flushEvidence() {
        val queued = evidenceQueue.pending()
        if (queued.isEmpty()) return
        queued.forEach { item ->
            try {
                withContext(Dispatchers.IO) {
                    adaptiveApi.uploadEvidence(
                        interventionId = textPart(item.event.interventionId),
                        sessionId = textPart(item.event.sessionId),
                        taskId = item.event.taskId?.let { textPart(it) },
                        classroomId = item.event.classroomId?.let { textPart(it) },
                        attemptNumber = item.event.attemptNumber?.let { textPart(it.toString()) },
                        exerciseType = textPart(item.event.exerciseType),
                        errorCode = textPart(item.event.errorCode.name),
                        errorDescription = textPart(item.event.errorDescription),
                        correctionText = textPart(item.event.correctionText),
                        capturedAt = textPart(item.event.capturedAtIso),
                        file =
                            MultipartBody.Part.createFormData(
                                "file",
                                "${item.event.interventionId}.jpg",
                                item.jpeg.toRequestBody("image/jpeg".toMediaType())
                            )
                    )
                }
                evidenceQueue.acknowledge(item.event.interventionId)
            } catch (t: Throwable) {
                Timber.w(t, "Evidence upload failed for %s; leaving queued for retry", item.event.interventionId)
            }
        }
    }

    /**
     * Hide live physical highlight/TTS without closing Policy B.
     * Used when identity re-verification freezes counting.
     */
    suspend fun suppressPhysicalCoaching() =
        signalMutex.withLock {
            delivery.stopSpeaking()
            val active = activeDelivery ?: return@withLock
            if (active.deliveryChannel == LiveCoachingUiPolicy.TECHNICAL_CHANNEL) {
                activeDelivery = active.copy(speak = false)
                return@withLock
            }
            if (active.interventionId.isBlank() && !active.highlightJoints && !active.speak) {
                return@withLock
            }
            activeDelivery =
                active.copy(
                    highlightJoints = false,
                    speak = false,
                    highlightLandmarkIds = emptySet(),
                    highlightBones = emptyList()
                )
        }

    fun endSession() {
        delivery.stopSpeaking()
        activeDelivery = null
    }

    private fun technicalGuidanceMessage(
        formIssues: List<String>,
        errorCode: FormErrorCode,
        exerciseType: String
    ): String {
        val fromLive =
            formIssues.firstOrNull { issue ->
                FormErrorMapper.resolve(emptyList(), listOf(issue), exerciseType) == errorCode &&
                    issue.isNotBlank()
            }
        return fromLive
            ?: CoachingInstructionCatalog.messageText(
                exerciseType,
                errorCode,
                InstructionIntensity.REMINDER
            )
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

        private fun textPart(value: String) = value.toRequestBody("text/plain".toMediaType())
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
