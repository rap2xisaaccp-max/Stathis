package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormEvidenceCaptureImplTest {

    private lateinit var buffer: LatestFrameBuffer
    private lateinit var queue: InMemoryEvidenceQueue
    private lateinit var capture: FormEvidenceCaptureImpl

    @Before
    fun setup() {
        buffer = LatestFrameBuffer()
        queue = InMemoryEvidenceQueue()
        capture = FormEvidenceCaptureImpl(buffer, queue)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        buffer.updateFromBitmap(bitmap)
        bitmap.recycle()
    }

    @Test
    fun oneConfirmedInterventionEnqueuesAtMostOnceAcrossManyFrames() {
        val event = coachableEvent("FI-HOLD")
        repeat(50) { capture.onConfirmedCoaching(event.copy()) }
        assertEquals(1, queue.pendingCount)
        assertEquals("FI-HOLD", queue.pending().single().event.interventionId)
    }

    @Test
    fun technicalAndUnknownSignalsNeverEnqueue() {
        listOf(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorCode.LOW_VISIBILITY,
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorCode.UNKNOWN
        ).forEachIndexed { index, code ->
            capture.onConfirmedCoaching(coachableEvent("FI-TECH-$index").copy(errorCode = code))
        }
        capture.onConfirmedCoaching(coachableEvent("").copy(errorCode = FormErrorCode.SAG))
        assertTrue(queue.isEmpty())
    }

    @Test
    fun doesNotCaptureWithoutABufferedFrame() {
        buffer.clear()
        capture.onConfirmedCoaching(coachableEvent("FI-NO-FRAME"))
        assertTrue(queue.isEmpty())
    }

    private fun coachableEvent(id: String) =
        FormEvidenceEvent(
            interventionId = id,
            sessionId = "SES-1",
            taskId = "TASK-1",
            classroomId = "ROOM-1",
            attemptNumber = 1,
            exerciseType = "SQUATS",
            errorCode = FormErrorCode.SAG,
            errorDescription = "Hips sagging",
            correctionText = "Keep hips level",
            capturedAtIso = "2026-08-21T00:00:00Z"
        )
}
