package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Durable open correction cycle. The default no-op keeps engine unit tests on the
 * in-memory reset path. Production Hilt binds the Room implementation.
 */
interface CorrectionCycleStore {
    fun save(cycle: PersistedOpenCycle)

    fun load(): PersistedOpenCycle?

    /**
     * Deletes the singleton cycle row and captured-id markers for [sessionId] and for
     * whatever session is currently stored. Does not delete queued evidence, interventions,
     * responses, or final JPEG files.
     */
    fun abandon(sessionId: String?)

    fun recoverCapture(event: FormEvidenceEvent): CaptureRecovery

    fun capturedIdsForSession(sessionId: String): Set<String>
}

enum class CaptureRecovery {
    /** Captured marker already durable, or the marker was inserted beside an existing evidence row. */
    AlreadyCaptured,

    /** A finished JPEG existed without an evidence row. The row and marker were inserted from that file. */
    RecordedFromExistingJpeg,

    /** No finished snapshot yet. The caller may compose one while this cycle is still active. */
    NeedsCompose,

    /** Blank identity or a cycle that must not be snapshotted. */
    NotEligible
}

data class PersistedOpenCycle(
    val sessionId: String,
    val exerciseType: String,
    val taskId: String?,
    val classroomId: String?,
    val attemptNumber: Int?,
    val phase: InterventionPhase,
    val pendingCode: FormErrorCode?,
    val pendingTicks: Int,
    val clearTicks: Int,
    val openError: FormErrorCode?,
    val deliveredAt: Long,
    val lastInterventionAt: Long,
    val cycleSeq: Int,
    val recentInterventionAt: List<Long>,
    val escalationCounts: Map<FormErrorCode, Int>,
    val sessionInterventionCount: Int,
    val sessionErrorCodes: List<String>,
    val sessionRecorded: Boolean,
    val claimedEvidence: FormEvidenceEvent?
)

object NoOpCorrectionCycleStore : CorrectionCycleStore {
    override fun save(cycle: PersistedOpenCycle) = Unit

    override fun load(): PersistedOpenCycle? = null

    override fun abandon(sessionId: String?) = Unit

    override fun recoverCapture(event: FormEvidenceEvent): CaptureRecovery = CaptureRecovery.NotEligible

    override fun capturedIdsForSession(sessionId: String): Set<String> = emptySet()
}
