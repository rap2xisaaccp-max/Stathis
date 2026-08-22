package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentHashMap
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

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        if (event.interventionId.isBlank()) return
        if (!FormErrorClassifier.isCoachable(event.errorCode)) {
            Timber.d("Skipping evidence snapshot for non-coachable %s", event.errorCode)
            return
        }
        if (!capturedInterventionIds.add(event.interventionId)) {
            return
        }
        val jpeg = frameBuffer.copyJpeg()
        if (jpeg == null || !JpegCompressor.isAcceptableSize(jpeg)) {
            capturedInterventionIds.remove(event.interventionId)
            Timber.w("No camera frame available for evidence %s", event.interventionId)
            return
        }
        evidenceQueue.enqueue(event, jpeg)
    }
}
