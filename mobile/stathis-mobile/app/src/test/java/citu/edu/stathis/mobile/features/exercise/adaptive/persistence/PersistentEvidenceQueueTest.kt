package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorCode
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceEvent
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistentEvidenceQueueTest {
    private lateinit var context: Context
    private lateinit var db: AdaptiveQueueDatabase
    private lateinit var queue: PersistentEvidenceQueue

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AdaptiveQueueDatabase.createInMemory(context)
        queue = PersistentEvidenceQueue(context, db, Gson())
    }

    @After
    fun teardown() {
        queue.clear()
        db.close()
    }

    @Test
    fun pendingDoesNotDeleteAndAcknowledgeRemovesJpeg() {
        val event =
            FormEvidenceEvent(
                interventionId = "FI-ACK",
                sessionId = "SES-1",
                exerciseType = "SQUATS",
                errorCode = FormErrorCode.SAG,
                errorDescription = "Hips sagging",
                correctionText = "Keep hips level",
                capturedAtIso = "2026-08-21T00:00:00Z"
            )
        val jpeg = ByteArray(32) { 7 }
        queue.enqueue(event, jpeg)
        queue.enqueue(event, jpeg)

        val first = queue.pending()
        assertEquals(1, first.size)
        assertFalse(queue.isEmpty())
        val jpegFile = File(context.filesDir, "form-evidence/FI-ACK.jpg")
        assertTrue(jpegFile.exists())

        val second = queue.pending()
        assertEquals(1, second.size)
        assertTrue(jpegFile.exists())

        queue.acknowledge("FI-ACK")
        assertTrue(queue.isEmpty())
        assertFalse(jpegFile.exists())
    }

    @Test
    fun missingJpegIsDiscardedWithoutBeingAcknowledgedAsUploaded() {
        val event =
            FormEvidenceEvent(
                interventionId = "FI-MISSING",
                sessionId = "SES-1",
                exerciseType = "PUSH_UP",
                errorCode = FormErrorCode.PIKE,
                errorDescription = "Hips too high",
                correctionText = "Lower your hips slightly so they stay in one line with your shoulders.",
                capturedAtIso = "2026-08-21T00:00:00Z"
            )
        queue.enqueue(event, ByteArray(24) { 3 })
        val jpegFile = File(context.filesDir, "form-evidence/FI-MISSING.jpg")
        assertTrue(jpegFile.delete())

        assertTrue(queue.pending().isEmpty())
        assertFalse(queue.isEmpty())

        val discarded = queue.discardMissing()
        assertEquals(listOf("FI-MISSING"), discarded)
        assertTrue(queue.isEmpty())
        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.discardMissing().isEmpty())
    }

    @Test
    fun failedUploadKeepsItsJpegAndAcknowledgeRemovesOnlyThatIntervention() {
        queue.enqueue(event("FI-FAIL", FormErrorCode.SAG), byteArrayOf(1, 2, 3))
        queue.enqueue(event("FI-OK", FormErrorCode.KNEES_IN), byteArrayOf(4, 5, 6))

        val beforeAck = queue.pending().associateBy { it.event.interventionId }
        assertEquals(2, beforeAck.size)
        assertTrue(beforeAck.getValue("FI-FAIL").jpeg.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(File(context.filesDir, "form-evidence/FI-FAIL.jpg").exists())

        queue.acknowledge("FI-OK")

        val remaining = queue.pending()
        assertEquals(listOf("FI-FAIL"), remaining.map { it.event.interventionId })
        assertTrue(remaining.single().jpeg.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(File(context.filesDir, "form-evidence/FI-FAIL.jpg").exists())
        assertFalse(File(context.filesDir, "form-evidence/FI-OK.jpg").exists())
        assertFalse(queue.isEmpty())
    }

    @Test
    fun emptyJpegIsDiscardedAndDoesNotBlockALaterImageForTheSameIntervention() {
        queue.enqueue(event("FI-EMPTY", FormErrorCode.LOW_ROM), byteArrayOf(9, 9))
        val jpegFile = File(context.filesDir, "form-evidence/FI-EMPTY.jpg")
        jpegFile.writeBytes(ByteArray(0))

        assertTrue(queue.pending().isEmpty())
        assertFalse(queue.isEmpty())

        assertEquals(listOf("FI-EMPTY"), queue.discardMissing())
        assertFalse(jpegFile.exists())
        assertTrue(queue.isEmpty())

        val replacement = byteArrayOf(8, 7, 6)
        queue.enqueue(event("FI-EMPTY", FormErrorCode.LOW_ROM), replacement)
        val queued = queue.pending().single()
        assertEquals("FI-EMPTY", queued.event.interventionId)
        assertTrue(queued.jpeg.contentEquals(replacement))
        assertTrue(jpegFile.exists())
    }

    private fun event(interventionId: String, error: FormErrorCode) =
        FormEvidenceEvent(
            interventionId = interventionId,
            sessionId = "SES-1",
            exerciseType = "PUSH_UP",
            errorCode = error,
            errorDescription = "description",
            correctionText = "correction",
            capturedAtIso = "2026-08-21T00:00:00Z"
        )
}
