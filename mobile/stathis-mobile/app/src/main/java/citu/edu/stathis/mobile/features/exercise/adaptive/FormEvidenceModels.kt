package citu.edu.stathis.mobile.features.exercise.adaptive

data class FormEvidenceEvent(
    val interventionId: String = "",
    val sessionId: String = "",
    val taskId: String? = null,
    val classroomId: String? = null,
    val attemptNumber: Int? = null,
    val exerciseType: String = "",
    val errorCode: FormErrorCode = FormErrorCode.UNKNOWN,
    val errorDescription: String = "",
    val correctionText: String = "",
    val capturedAtIso: String = "",
    /** Cycle sequence used to rebuild the same intervention id after process death. */
    val cycleSeq: Int = 0
)

interface FormEvidenceCapture {
    fun onConfirmedCoaching(event: FormEvidenceEvent)

    /**
     * Retries correction cycles that were confirmed before a usable preview frame existed.
     * Still one snapshot per intervention id.
     */
    fun onPreviewFrameAvailable() {}

    /** Intervention id of a snapshot recorded since the previous call; null when none. */
    fun consumeRecordedInterventionId(): String? = null

    /** Ids already enqueued, so a restored cycle does not snapshot them again. */
    fun adoptCapturedIds(ids: Collection<String>) {}

    /** Queue a compose retry for a claimed cycle whose JPEG is not finished yet. */
    fun schedulePendingCapture(event: FormEvidenceEvent) {}

    /** Drop in-memory dedup after an explicit new attempt. Does not delete queued files. */
    fun clearCapturedIds() {}
}

interface EvidenceQueue {
    fun enqueue(event: FormEvidenceEvent, jpeg: ByteArray)
    /** Snapshot of queued items. Must not delete records. */
    fun pending(): List<QueuedEvidence>
    /**
     * Removes rows whose JPEG can no longer be read, and deletes that unreadable file.
     * This is not an upload acknowledgement. The returned ids were not uploaded.
     * Pending rows with a readable JPEG stay pending.
     */
    fun discardMissing(): List<String> = emptyList()
    /** Remove the queue row and local JPEG only after a confirmed successful upload. */
    fun acknowledge(interventionId: String)
    fun requeueAfterFailure(failed: List<QueuedEvidence>): Int
    fun isEmpty(): Boolean
    fun clear()
}

data class QueuedEvidence(
    val event: FormEvidenceEvent,
    val jpeg: ByteArray,
    val attempts: Int = 0
)
