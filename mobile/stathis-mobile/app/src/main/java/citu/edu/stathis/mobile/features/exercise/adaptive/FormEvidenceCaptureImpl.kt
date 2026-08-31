package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Composites one camera-frame + highlight JPEG for the first confirmed coaching event
 * in an attempt. Attempt identity is [FormEvidenceEvent.sessionId].
 *
 * Never runs from the camera frame loop. Never screenshots the Android UI.
 */
@Singleton
class FormEvidenceCaptureImpl @Inject constructor(
    private val frameBuffer: LatestFrameBuffer,
    private val evidenceQueue: EvidenceQueue
) : FormEvidenceCapture {

    /** Sessions (attempts) that already claimed their single evidence snapshot. */
    private val capturedSessionIds = ConcurrentHashMap.newKeySet<String>()

    /** Claimed events still waiting for a usable copied frame + pose. */
    private val awaitingFrame = ConcurrentHashMap<String, FormEvidenceEvent>()

    private val lastRecordedId = AtomicReference<String?>(null)

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        if (event.interventionId.isBlank()) return
        if (event.sessionId.isBlank()) return
        if (!FormErrorClassifier.isCoachableForExercise(event.exerciseType, event.errorCode)) {
            Timber.d(
                "Skipping evidence snapshot for non-coachable %s/%s",
                event.exerciseType,
                event.errorCode
            )
            return
        }
        // One snapshot per attempt/retry (session), not per later correction in the same attempt.
        if (!capturedSessionIds.add(event.sessionId)) {
            return
        }
        if (!enqueueSnapshot(event)) {
            awaitingFrame[event.sessionId] = event
            Timber.w(
                "No camera frame+pose buffered for evidence session %s; retrying on the next preview",
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
        val snapshot = frameBuffer.snapshot() ?: return false
        val pose = snapshot.pose
        if (pose == null || pose.landmarks.isEmpty()) {
            snapshot.recycleCopy()
            return false
        }
        val jpeg =
            runCatching {
                EvidenceHighlightCompositor.composeJpeg(
                    cameraFrame = snapshot.bitmap,
                    geometry = pose,
                    errorCode = event.errorCode,
                    exerciseType = event.exerciseType
                )
            }.onFailure { err ->
                Timber.w(err, "Evidence composite failed for session %s", event.sessionId)
            }.getOrNull()
        snapshot.recycleCopy()
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
