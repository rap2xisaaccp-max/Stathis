package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.CaptureRecovery
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorCode
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EvidenceCaptureRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: AdaptiveQueueDatabase
    private lateinit var store: RoomCorrectionCycleStore
    private lateinit var queue: PersistentEvidenceQueue
    private val gson = Gson()
    private val jpeg = ByteArray(24) { 6 }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deleteSandboxDatabase()
        File(context.filesDir, "form-evidence").deleteRecursively()
        db = AdaptiveQueueDatabase.open(context)
        store = RoomCorrectionCycleStore(db, gson, context)
        queue = PersistentEvidenceQueue(context, db, gson)
    }

    @After
    fun teardown() {
        db.close()
        deleteSandboxDatabase()
        File(context.filesDir, "form-evidence").deleteRecursively()
    }

    @Test
    fun claimWithoutJpegComposesOnceAndASecondRecoveryIsANoOp() = runBlocking(Dispatchers.IO) {
        val event = event("FI-CLAIM")
        assertEquals(CaptureRecovery.NeedsCompose, store.recoverCapture(event))
        assertTrue(db.dao().allEvidence().isEmpty())
        assertEquals(0, db.dao().capturedCount())
        assertFalse(finalFile(event).exists())

        queue.enqueue(event, jpeg)
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertOneCompleteSnapshot(event)
    }

    @Test
    fun temporaryJpegIsDiscardedAndRecomposedOnce() = runBlocking(Dispatchers.IO) {
        val event = event("FI-TMP")
        EvidenceJpegFiles.tempFile(context, event.interventionId).apply {
            parentFile?.mkdirs()
            writeBytes(jpeg)
        }
        assertEquals(CaptureRecovery.NeedsCompose, store.recoverCapture(event))
        assertFalse(EvidenceJpegFiles.tempFile(context, event.interventionId).exists())
        assertTrue(db.dao().allEvidence().isEmpty())
        assertEquals(0, db.dao().capturedCount())

        queue.enqueue(event, jpeg)
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertOneCompleteSnapshot(event)
        assertFalse(EvidenceJpegFiles.tempFile(context, event.interventionId).exists())
    }

    @Test
    fun finishedJpegWithoutAnEvidenceRowIsRecordedWithoutRecompose() = runBlocking(Dispatchers.IO) {
        val event = event("FI-FINAL")
        val file = EvidenceJpegFiles.finalFile(context, event.interventionId)
        file.parentFile?.mkdirs()
        file.writeBytes(jpeg)
        assertEquals(CaptureRecovery.RecordedFromExistingJpeg, store.recoverCapture(event))
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertOneCompleteSnapshot(event)
        assertTrue(file.readBytes().contentEquals(jpeg))
    }

    @Test
    fun evidenceRowWithoutCapturedMarkerInsertsOnlyTheMarker() = runBlocking(Dispatchers.IO) {
        val event = event("FI-ROW")
        val file = EvidenceJpegFiles.promote(context, event.interventionId, jpeg)
        assertTrue(file != null)
        db.dao().enqueueEvidenceIfAbsent(
            QueuedEvidenceEntity(
                interventionPhysicalId = event.interventionId,
                payloadJson = gson.toJson(event),
                filePath = file!!.absolutePath
            )
        )
        assertEquals(0, db.dao().capturedCount())
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertOneCompleteSnapshot(event)
    }

    @Test
    fun capturedMarkerMakesRecoveryANoOp() = runBlocking(Dispatchers.IO) {
        val event = event("FI-DONE")
        queue.enqueue(event, jpeg)
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertEquals(CaptureRecovery.AlreadyCaptured, store.recoverCapture(event))
        assertOneCompleteSnapshot(event)
    }

    @Test
    fun capturedMarkerIsNotWrittenBeforeEvidence() = runBlocking(Dispatchers.IO) {
        val event = event("FI-ORDER")
        assertFalse(
            db.dao().insertCapturedIfEvidencePresent(
                CapturedInterventionEntity(
                    interventionId = event.interventionId,
                    sessionId = event.sessionId,
                    exerciseType = event.exerciseType,
                    errorCode = event.errorCode.name,
                    cycleSeq = event.cycleSeq
                )
            )
        )
        assertEquals(0, db.dao().capturedCount())
        assertTrue(db.dao().allEvidence().isEmpty())
        queue.enqueue(event, ByteArray(0))
        assertTrue(db.dao().allEvidence().isEmpty())
        assertEquals(0, db.dao().capturedCount())
        assertFalse(finalFile(event).exists())
        assertFalse(EvidenceJpegFiles.tempFile(context, event.interventionId).exists())
    }

    @Test
    fun zeroLengthFinalIsNotAFinishedSnapshot() = runBlocking(Dispatchers.IO) {
        val event = event("FI-EMPTY")
        val file = EvidenceJpegFiles.finalFile(context, event.interventionId)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(0))
        assertEquals(CaptureRecovery.NeedsCompose, store.recoverCapture(event))
        assertFalse(file.exists())
        queue.enqueue(event, jpeg)
        assertOneCompleteSnapshot(event)
        assertTrue(finalFile(event).readBytes().contentEquals(jpeg))
    }

    private fun assertOneCompleteSnapshot(event: FormEvidenceEvent) {
        val rows = db.dao().allEvidence()
        assertEquals(1, rows.size)
        assertEquals(event.interventionId, rows.single().interventionPhysicalId)
        assertEquals(1, db.dao().capturedCount())
        assertEquals(event.interventionId, db.dao().findCaptured(event.interventionId)?.interventionId)
        val file = finalFile(event)
        assertTrue(file.isFile)
        assertTrue(file.length() > 0L)
        assertFalse(EvidenceJpegFiles.tempFile(context, event.interventionId).exists())
    }

    private fun finalFile(event: FormEvidenceEvent): File =
        EvidenceJpegFiles.finalFile(context, event.interventionId)

    private fun event(id: String) =
        FormEvidenceEvent(
            interventionId = id,
            sessionId = "SES-RECOVERY",
            taskId = "TASK-1",
            classroomId = "ROOM-1",
            attemptNumber = 1,
            exerciseType = "SQUATS",
            errorCode = FormErrorCode.KNEES_IN,
            errorDescription = "Knees in",
            correctionText = "Keep your knees aligned with your toes.",
            capturedAtIso = "2026-10-14T00:00:00Z",
            cycleSeq = 1
        )

    private fun deleteSandboxDatabase() {
        val file = context.getDatabasePath(AdaptiveQueueDatabase.DB_NAME)
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
        file.delete()
    }
}
