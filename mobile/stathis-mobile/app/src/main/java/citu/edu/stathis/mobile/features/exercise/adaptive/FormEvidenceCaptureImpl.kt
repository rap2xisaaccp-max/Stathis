package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Copies the 1-slot preview JPEG only when a confirmed coaching intervention is claimed.
 * Never runs from the camera frame loop.
 */
@Singleton
class FormEvidenceCaptureImpl @Inject constructor(
    private val frameBuffer: LatestFrameBuffer,
    private val evidenceQueue: EvidenceQueue
) : FormEvidenceCapture {

    private val capturedInterventionIds = ConcurrentHashMap.newKeySet<String>()

    /** Claimed events still waiting for the first usable preview frame. */
    private val awaitingFrame = ConcurrentHashMap<String, FormEvidenceEvent>()

    private val lastRecordedId = AtomicReference<String?>(null)

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        if (event.interventionId.isBlank()) return
        if (!FormErrorClassifier.isCoachable(event.errorCode)) {
            Timber.d("Skipping evidence snapshot for non-coachable %s", event.errorCode)
            return
        }
        if (!capturedInterventionIds.add(event.interventionId)) {
            return
        }
        if (!enqueueSnapshot(event)) {
            // The claim is kept so this correction event still yields exactly one snapshot,
            // taken from the next preview frame instead of being dropped for good.
            awaitingFrame[event.interventionId] = event
            Timber.w(
                "No camera frame buffered for evidence %s; retrying on the next preview frame",
                event.interventionId
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
        Timber.d("Evidence snapshot queued for %s", event.interventionId)
        return true
    }
}
