package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.remote.api.AdaptiveApi
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
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
 * Confirmed physical form error → highlight + TTS cue; one evidence snapshot per correction cycle.
 * Form signals are serialized; lifecycle claims before delivery.
 */
@Singleton
class AdaptiveFeedbackEngine @Inject constructor(
    private val adaptiveApi: AdaptiveApi,
    private val delivery: CoachingDelivery,
    private val offlineQueue: OfflineQueue,
    private val evidenceQueue: EvidenceQueue,
    private val evidenceCapture: FormEvidenceCapture,
    private val cycleStore: CorrectionCycleStore = NoOpCorrectionCycleStore
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
    private val encouragement = EncouragementPolicy()
    private val sessionErrorCodes = linkedSetOf<String>()
    @Volatile private var sessionInterventionCount: Int = 0
    @Volatile private var sessionRecorded: Boolean = false
    private val sessionGate = Any()
    private val attemptGeneration = AtomicInteger(0)
    private var cycleTracking: Boolean = false
    private var claimedEvidence: FormEvidenceEvent? = null

    fun offlineQueueForTests(): AdaptiveOfflineQueue =
        if (offlineQueue is AdaptiveOfflineQueue) offlineQueue else throw IllegalStateException("offlineQueue is not AdaptiveOfflineQueue")

    fun lifecyclePhase(): InterventionPhase = interventionLifecycle.phase

    fun startSession(
        exerciseType: String,
        taskId: String? = null,
        classroomId: String? = null,
        attemptNumber: Int? = null
    ) {
        synchronized(sessionGate) {
            attemptGeneration.incrementAndGet()
            cycleStore.abandon(sessionId.takeIf { it.isNotBlank() })
            evidenceCapture.clearCapturedIds()
            this.sessionId = "SES-${UUID.randomUUID().toString().uppercase()}"
            this.exerciseType = exerciseType
            this.taskId = normalizeOptional(taskId)
            this.classroomId = normalizeOptional(classroomId)
            this.attemptNumber = attemptNumber
            this.activeDelivery = null
            this.cooldownMs = 8000L
            this.sessionInterventionCount = 0
            this.sessionRecorded = false
            sessionErrorCodes.clear()
            interventionLifecycle.reset()
            encouragement.reset()
            pendingResponses.clear()
            delivery.resetSessionSpeech()
            delivery.ensureInitialized()
            claimedEvidence = null
            cycleTracking = true
            persistOpenCycleLocked()
            Timber.d("Coaching session started exercise=%s attempt=%s", exerciseType, attemptNumber)
        }
    }

    /**
     * Drops the durable cycle and this session's captured-id markers.
     * Queued evidence, interventions, responses, and final JPEGs stay queued.
     * The in-memory session id is kept so an in-flight flush token still matches.
     */
    fun abandonOpenCycle() {
        synchronized(sessionGate) {
            attemptGeneration.incrementAndGet()
            cycleStore.abandon(sessionId.takeIf { it.isNotBlank() })
            evidenceCapture.clearCapturedIds()
            interventionLifecycle.reset()
            claimedEvidence = null
            activeDelivery = null
            cycleTracking = false
        }
    }

    /**
     * Reinstates a stored attempt only when exercise, task, classroom, and attempt
     * all match. A mismatch leaves the stored row in place. Does not call [startSession].
     */
    fun restoreMatchingAttempt(
        exerciseType: String,
        taskId: String?,
        classroomId: String?,
        attemptNumber: Int?
    ): Boolean =
        synchronized(sessionGate) {
            val saved = cycleStore.load() ?: return@synchronized false
            if (!sameAttemptIdentity(saved, exerciseType, taskId, classroomId, attemptNumber)) {
                return@synchronized false
            }
            if (cycleTracking && sessionId.isNotBlank() && sessionId != saved.sessionId) {
                return@synchronized false
            }
            sessionId = saved.sessionId
            this.exerciseType = saved.exerciseType
            this.taskId = saved.taskId
            this.classroomId = saved.classroomId
            this.attemptNumber = saved.attemptNumber
            cooldownMs = 8000L
            sessionInterventionCount = saved.sessionInterventionCount
            sessionRecorded = saved.sessionRecorded
            sessionErrorCodes.clear()
            sessionErrorCodes.addAll(saved.sessionErrorCodes)
            interventionLifecycle.restore(
                InterventionLifecycleSnapshot(
                    phase = saved.phase,
                    pendingCode = saved.pendingCode,
                    pendingTicks = saved.pendingTicks,
                    clearTicks = saved.clearTicks,
                    openError = saved.openError,
                    deliveredAt = saved.deliveredAt,
                    lastInterventionAt = saved.lastInterventionAt,
                    cycleSeq = saved.cycleSeq,
                    recentInterventionAt = saved.recentInterventionAt,
                    escalationCounts = saved.escalationCounts
                )
            )
            claimedEvidence = saved.claimedEvidence
            activeDelivery = null
            cycleTracking = true
            val claimed = saved.claimedEvidence
            if (claimed != null && claimed.interventionId.isNotBlank() && saved.cycleSeq > 0) {
                when (cycleStore.recoverCapture(claimed)) {
                    CaptureRecovery.NeedsCompose -> evidenceCapture.schedulePendingCapture(claimed)
                    CaptureRecovery.AlreadyCaptured,
                    CaptureRecovery.RecordedFromExistingJpeg,
                    CaptureRecovery.NotEligible -> Unit
                }
            }
            evidenceCapture.adoptCapturedIds(cycleStore.capturedIdsForSession(saved.sessionId))
            true
        }

    fun currentSessionId(): String = sessionId

    fun currentCycleSeq(): Int = interventionLifecycle.currentCycleSeq()

    fun currentAttemptGeneration(): Int = attemptGeneration.get()

    /**
     * Snapshot of the live session for a flush that may outlive the screen.
     * Claiming [AdaptiveFlushToken.recordSession] here stops a second flush of the same
     * session from recording twice. A later [startSession] owns a new id, so this token
     * must not clear or end that newer session.
     */
    fun captureFlushToken(): AdaptiveFlushToken =
        synchronized(sessionGate) {
            val record = !sessionRecorded && exerciseType.isNotBlank() && sessionId.isNotBlank()
            if (record) {
                sessionRecorded = true
                persistOpenCycleLocked()
            }
            AdaptiveFlushToken(sessionId, exerciseType, record)
        }

    fun sessionSummary(): AdaptiveSessionSummary =
        AdaptiveSessionSummary(
            interventionCount = sessionInterventionCount,
            modalitiesUsed = if (sessionInterventionCount > 0) listOf("HIGHLIGHT_TTS") else emptyList(),
            errorCodes = sessionErrorCodes.toList(),
            syncPending = !offlineQueue.isEmpty() || !evidenceQueue.isEmpty()
        )

    fun activeFeedback(): DeliveredFeedback? = activeDelivery

    /**
     * A completed movement was rejected for a supported form error.
     * Speaks once. Does not claim an intervention and does not capture evidence.
     */
    fun onRepRejected(errorCode: FormErrorCode?, now: Long = System.currentTimeMillis()) {
        encouragement.onRejected()
        if (!FormErrorClassifier.isCoachableForExercise(exerciseType, errorCode) || errorCode == null) {
            return
        }
        sessionErrorCodes.add(errorCode.name)
        delivery.speakRepNotCounted(
            AttemptResultCopy.notCountedLine(exerciseType, errorCode),
            now
        )
    }

    /** A valid rep. Praise is skipped while a correction cycle is open or speech is busy. */
    fun onValidRep(now: Long = System.currentTimeMillis()) {
        val correctionOpen = interventionLifecycle.hasOpenIntervention()
        encouragement.onValidRep(now) { line ->
            if (correctionOpen) false else delivery.speakEncouragement(line, now)
        }
    }

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
            synchronized(sessionGate) {
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
                persistOpenCycleLocked()
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
                persistOpenCycleLocked()
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
            val resolvedSessionId = sessionId.ifBlank { "SES-LOCAL" }
            val evidenceEvent =
                FormEvidenceEvent(
                    interventionId = interventionId,
                    sessionId = resolvedSessionId,
                    taskId = taskId,
                    classroomId = classroomId,
                    attemptNumber = attemptNumber,
                    exerciseType = exerciseType,
                    errorCode = resolvedCode,
                    errorDescription = FormErrorCopy.explanation(resolvedCode, exerciseType).ifBlank {
                        FormErrorCopy.label(resolvedCode, exerciseType)
                    },
                    correctionText = catalogText,
                    capturedAtIso = Instant.ofEpochMilli(now).toString(),
                    cycleSeq = cycle
                )
            claimedEvidence = evidenceEvent
            // Durable claim before JPEG work so a crash retries this same intervention id.
            persistOpenCycleLocked()

            val pending =
                PendingIntervention(
                    physicalId = interventionId,
                    sessionId = resolvedSessionId,
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
            persistOpenCycleLocked()

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
            evidenceCapture.onConfirmedCoaching(evidenceEvent)
            delivered
            }
        }

    private fun persistOpenCycleLocked() {
        if (!cycleTracking || sessionId.isBlank()) return
        val life = interventionLifecycle.snapshot()
        cycleStore.save(
            PersistedOpenCycle(
                sessionId = sessionId,
                exerciseType = exerciseType,
                taskId = normalizeOptional(taskId),
                classroomId = normalizeOptional(classroomId),
                attemptNumber = attemptNumber,
                phase = life.phase,
                pendingCode = life.pendingCode,
                pendingTicks = life.pendingTicks,
                clearTicks = life.clearTicks,
                openError = life.openError,
                deliveredAt = life.deliveredAt,
                lastInterventionAt = life.lastInterventionAt,
                cycleSeq = life.cycleSeq,
                recentInterventionAt = life.recentInterventionAt,
                escalationCounts = life.escalationCounts,
                sessionInterventionCount = sessionInterventionCount,
                sessionErrorCodes = sessionErrorCodes.toList(),
                sessionRecorded = sessionRecorded,
                claimedEvidence = claimedEvidence
            )
        )
    }

    private fun sameAttemptIdentity(
        saved: PersistedOpenCycle,
        exerciseType: String,
        taskId: String?,
        classroomId: String?,
        attemptNumber: Int?
    ): Boolean {
        if (saved.exerciseType != exerciseType) return false
        if (!sameOptional(saved.taskId, taskId)) return false
        if (!sameOptional(saved.classroomId, classroomId)) return false
        return saved.attemptNumber == attemptNumber
    }

    private fun sameOptional(stored: String?, incoming: String?): Boolean =
        normalizeOptional(stored) == normalizeOptional(incoming)

    private fun normalizeOptional(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

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

    suspend fun flush(token: AdaptiveFlushToken? = null) {
        val owned = token ?: captureFlushToken()
        if (sessionId == owned.sessionId) {
            pendingResponses.clear()
        }

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

        if (owned.recordSession) {
            runCatching {
                withContext(Dispatchers.IO) {
                    adaptiveApi.recordSession(owned.exerciseType)
                }
            }.onFailure { t ->
                synchronized(sessionGate) {
                    if (sessionId == owned.sessionId) {
                        sessionRecorded = false
                        persistOpenCycleLocked()
                    }
                }
                Timber.w(t, "Failed to record coaching session for %s", owned.exerciseType)
            }
        }
    }

    private suspend fun flushEvidence() {
        val missing = evidenceQueue.discardMissing()
        if (missing.isNotEmpty()) {
            Timber.w(
                "Evidence JPEG missing for %s; removed from the pending queue without uploading",
                missing.joinToString()
            )
        }
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

    fun endSession(expectedSessionId: String? = null) {
        if (expectedSessionId != null && expectedSessionId != sessionId) {
            return
        }
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

data class AdaptiveFlushToken(
    val sessionId: String,
    val exerciseType: String,
    val recordSession: Boolean
)

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
