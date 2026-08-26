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
    val capturedAtIso: String = ""
)

interface FormEvidenceCapture {
    fun onConfirmedCoaching(event: FormEvidenceEvent)

    /**
     * Retries confirmed events that had no usable preview frame yet. Still at most one
     * snapshot per confirmed event.
     */
    fun onPreviewFrameAvailable() {}

    /** Intervention id of a snapshot recorded since the previous call; null when none. */
    fun consumeRecordedInterventionId(): String? = null
}

interface EvidenceQueue {
    fun enqueue(event: FormEvidenceEvent, jpeg: ByteArray)
    /** Snapshot of queued items. Must not delete records. */
    fun pending(): List<QueuedEvidence>
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
