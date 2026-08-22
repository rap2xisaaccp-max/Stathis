package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryEvidenceQueueTest {

    @Test
    fun enqueueIsIdempotentPerInterventionId() {
        val queue = InMemoryEvidenceQueue()
        val event =
            FormEvidenceEvent(
                interventionId = "FI-SAME",
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
        val jpeg = ByteArray(64) { 1 }
        queue.enqueue(event, jpeg)
        queue.enqueue(event.copy(), jpeg)
        assertEquals(1, queue.pendingCount)
        assertEquals(1, queue.pending().size)
        queue.acknowledge("FI-SAME")
        assertTrue(queue.isEmpty())
    }

    @Test
    fun rejectsOversizedJpeg() {
        val queue = InMemoryEvidenceQueue()
        val event =
            FormEvidenceEvent(
                interventionId = "FI-BIG",
                sessionId = "SES-1",
                taskId = null,
                classroomId = null,
                attemptNumber = null,
                exerciseType = "SQUATS",
                errorCode = FormErrorCode.SAG,
                errorDescription = "Hips sagging",
                correctionText = "Keep hips level",
                capturedAtIso = "2026-08-21T00:00:00Z"
            )
        queue.enqueue(event, ByteArray(JpegCompressor.MAX_BYTES + 1))
        assertTrue(queue.isEmpty())
    }

    @Test
    fun technicalClassifierBlocksCoachableFalse() {
        assertTrue(FormErrorClassifier.isTechnical(FormErrorCode.LOW_CONFIDENCE))
        assertFalse(FormErrorClassifier.isCoachable(FormErrorCode.BODY_NOT_VISIBLE))
        assertTrue(FormErrorClassifier.isCoachable(FormErrorCode.SAG))
        assertEquals("Hips sagging", FormErrorCopy.label(FormErrorCode.SAG))
    }
}
