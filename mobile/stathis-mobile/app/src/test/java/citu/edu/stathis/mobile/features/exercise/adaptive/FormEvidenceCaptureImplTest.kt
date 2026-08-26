package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun oneConfirmedAttemptEnqueuesAtMostOnceAcrossManyCorrections() {
        val first = coachableEvent("FI-HOLD", sessionId = "SES-HOLD")
        repeat(50) { index ->
            capture.onConfirmedCoaching(
                first.copy(
                    interventionId = "FI-HOLD-$index",
                    errorCode = if (index % 2 == 0) FormErrorCode.SAG else FormErrorCode.KNEES_IN
                )
            )
        }
        assertEquals(1, queue.pendingCount)
        assertEquals("FI-HOLD-0", queue.pending().single().event.interventionId)
    }

    @Test
    fun eachRetrySessionMayEnqueueItsOwnSnapshot() {
        capture.onConfirmedCoaching(coachableEvent("FI-A1", sessionId = "SES-ATTEMPT-1"))
        capture.onConfirmedCoaching(coachableEvent("FI-A1b", sessionId = "SES-ATTEMPT-1"))
        capture.onConfirmedCoaching(coachableEvent("FI-A2", sessionId = "SES-ATTEMPT-2", attemptNumber = 2))
        capture.onConfirmedCoaching(coachableEvent("FI-A3", sessionId = "SES-ATTEMPT-3", attemptNumber = 3))

        assertEquals(3, queue.pendingCount)
        assertEquals(
            listOf("FI-A1", "FI-A2", "FI-A3"),
            queue.pending().map { it.event.interventionId }
        )
    }

    @Test
    fun technicalAndUnknownSignalsNeverEnqueue() {
        listOf(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorCode.LOW_VISIBILITY,
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorCode.UNKNOWN
        ).forEachIndexed { index, code ->
            capture.onConfirmedCoaching(
                coachableEvent("FI-TECH-$index", sessionId = "SES-TECH-$index").copy(errorCode = code)
            )
        }
        capture.onConfirmedCoaching(
            coachableEvent("", sessionId = "SES-BLANK").copy(errorCode = FormErrorCode.SAG)
        )
        assertTrue(queue.isEmpty())
    }

    @Test
    fun doesNotCaptureWithoutABufferedFrame() {
        buffer.clear()
        capture.onConfirmedCoaching(coachableEvent("FI-NO-FRAME", sessionId = "SES-NO-FRAME"))
        assertTrue(queue.isEmpty())
    }

    @Test
    fun coldBufferAtConfirmationStillCapturesExactlyOnceOnALaterFrame() {
        buffer.clear()
        capture.onConfirmedCoaching(coachableEvent("FI-LATE", sessionId = "SES-LATE"))
        assertTrue(queue.isEmpty())

        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        buffer.updateFromBitmap(bitmap)
        bitmap.recycle()
        repeat(20) { capture.onPreviewFrameAvailable() }

        assertEquals(1, queue.pendingCount)
        assertEquals("FI-LATE", queue.pending().single().event.interventionId)
    }

    @Test
    fun recordedInterventionIdIsReportedOnlyOnce() {
        capture.onConfirmedCoaching(coachableEvent("FI-NOTICE", sessionId = "SES-NOTICE"))
        assertEquals("FI-NOTICE", capture.consumeRecordedInterventionId())
        assertNull(capture.consumeRecordedInterventionId())
    }

    private fun coachableEvent(
        id: String,
        sessionId: String = "SES-1",
        attemptNumber: Int = 1
    ) =
        FormEvidenceEvent(
            interventionId = id,
            sessionId = sessionId,
            taskId = "TASK-1",
            classroomId = "ROOM-1",
            attemptNumber = attemptNumber,
            exerciseType = "SQUATS",
            errorCode = FormErrorCode.SAG,
            errorDescription = "Hips sagging",
            correctionText = "Keep hips level",
            capturedAtIso = "2026-08-21T00:00:00Z"
        )
}
