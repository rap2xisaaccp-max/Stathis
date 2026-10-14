package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Composites one camera-frame + highlight JPEG per confirmed correction cycle.
 * Cycle identity is [FormEvidenceEvent.interventionId]. The same id is not snapshotted
 * again while that error is held. A later cycle (new id) in the same attempt may snapshot.
 *
 * Never runs from the camera frame loop. Never screenshots the Android UI.
 */
@Singleton
class FormEvidenceCaptureImpl @Inject constructor(
    private val frameBuffer: LatestFrameBuffer,
    private val evidenceQueue: EvidenceQueue
) : FormEvidenceCapture {

    /** Correction cycles whose snapshot has been enqueued. Not reserved before success. */
    private val capturedInterventionIds = ConcurrentHashMap.newKeySet<String>()

    /** Claimed events still waiting for a usable copied frame + pose. */
    private val awaitingFrame = ConcurrentHashMap<String, FormEvidenceEvent>()

    private val lastRecordedId = AtomicReference<String?>(null)
    private val captureLock = Any()

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
        synchronized(captureLock) {
            // One snapshot per confirmed cycle. A repeated id (held error or upload retry) is ignored.
            // A new intervention id is a later cycle and may snapshot during the same attempt.
            if (capturedInterventionIds.contains(event.interventionId)) return
            if (awaitingFrame.containsKey(event.interventionId)) return
            if (enqueueSnapshot(event)) {
                capturedInterventionIds.add(event.interventionId)
            } else {
                awaitingFrame[event.interventionId] = event
                Timber.w(
                    "No camera frame+pose buffered for evidence cycle %s; retrying on the next preview",
                    event.interventionId
                )
            }
        }
    }

    override fun onPreviewFrameAvailable() {
        synchronized(captureLock) {
            if (awaitingFrame.isEmpty()) return
            val iterator = awaitingFrame.entries.iterator()
            while (iterator.hasNext()) {
                val event = iterator.next().value
                if (capturedInterventionIds.contains(event.interventionId)) {
                    iterator.remove()
                    continue
                }
                if (enqueueSnapshot(event)) {
                    capturedInterventionIds.add(event.interventionId)
                    iterator.remove()
                }
            }
        }
    }

    override fun adoptCapturedIds(ids: Collection<String>) {
        synchronized(captureLock) {
            ids.forEach { id ->
                if (id.isNotBlank()) {
                    capturedInterventionIds.add(id)
                    awaitingFrame.remove(id)
                }
            }
        }
    }

    override fun schedulePendingCapture(event: FormEvidenceEvent) {
        if (event.interventionId.isBlank()) return
        synchronized(captureLock) {
            if (capturedInterventionIds.contains(event.interventionId)) return
            awaitingFrame.putIfAbsent(event.interventionId, event)
        }
    }

    override fun clearCapturedIds() {
        synchronized(captureLock) {
            capturedInterventionIds.clear()
            awaitingFrame.clear()
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
        val queued = runCatching { evidenceQueue.enqueue(event, jpeg) }.isSuccess
        if (!queued) return false
        lastRecordedId.set(event.interventionId)
        Timber.d(
            "Evidence snapshot queued for attempt session=%s intervention=%s",
            event.sessionId,
            event.interventionId
        )
        return true
    }
}
