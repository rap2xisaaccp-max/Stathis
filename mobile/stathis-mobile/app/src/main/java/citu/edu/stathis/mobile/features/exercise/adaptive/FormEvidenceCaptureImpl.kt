package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Copies the 1-slot preview JPEG only for the first confirmed coaching event in an attempt.
 *
 * Attempt identity is [FormEvidenceEvent.sessionId] (engine assigns a new SES-* per
 * [AdaptiveFeedbackEngine.startSession] / retry). Never runs from the camera frame loop.
 */
@Singleton
class FormEvidenceCaptureImpl @Inject constructor(
    private val frameBuffer: LatestFrameBuffer,
    private val evidenceQueue: EvidenceQueue
) : FormEvidenceCapture {

    /** Sessions (attempts) that already claimed their single evidence snapshot. */
    private val capturedSessionIds = ConcurrentHashMap.newKeySet<String>()

    /** Claimed events still waiting for the first usable preview frame. */
    private val awaitingFrame = ConcurrentHashMap<String, FormEvidenceEvent>()

    private val lastRecordedId = AtomicReference<String?>(null)

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        if (event.interventionId.isBlank()) return
        if (event.sessionId.isBlank()) return
        if (!FormErrorClassifier.isCoachable(event.errorCode)) {
            Timber.d("Skipping evidence snapshot for non-coachable %s", event.errorCode)
            return
        }
        // One snapshot per attempt/retry (session), not per later correction in the same attempt.
        if (!capturedSessionIds.add(event.sessionId)) {
            return
        }
        if (!enqueueSnapshot(event)) {
            // Keep the session claim so this attempt still yields exactly one snapshot,
            // taken from the next preview frame instead of being dropped for good.
            awaitingFrame[event.sessionId] = event
            Timber.w(
                "No camera frame buffered for evidence session %s; retrying on the next preview frame",
                event.sessionId
            )
        }
    }

    override fun onPreviewFrameAvailable() {
        if (awaitingFrame.isEmpty()) return
        val iterator = awaitingFrame.entries.iterator()
        while (iterator.hasNext()) {
            if (enqueueSnapshot(iterator.next().value)) {
                iterator.remove()
            }
        }
    }

    override fun consumeRecordedInterventionId(): String? = lastRecordedId.getAndSet(null)

    private fun enqueueSnapshot(event: FormEvidenceEvent): Boolean {
        val jpeg = frameBuffer.copyJpeg()
        if (jpeg == null || !JpegCompressor.isAcceptableSize(jpeg)) {
            return false
        }
        evidenceQueue.enqueue(event, jpeg)
        lastRecordedId.set(event.interventionId)
        Timber.d(
            "Evidence snapshot queued for attempt session=%s intervention=%s",
            event.sessionId,
            event.interventionId
        )
        return true
    }
}
