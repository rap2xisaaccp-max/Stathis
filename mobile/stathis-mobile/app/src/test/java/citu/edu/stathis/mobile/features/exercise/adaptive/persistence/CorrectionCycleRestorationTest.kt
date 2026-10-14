package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveFeedbackEngine
import citu.edu.stathis.mobile.features.exercise.adaptive.EvidenceQueue
import citu.edu.stathis.mobile.features.exercise.adaptive.FakeAdaptiveApi
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorCode
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceCapture
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceEvent
import citu.edu.stathis.mobile.features.exercise.adaptive.InterventionPhase
import citu.edu.stathis.mobile.features.exercise.adaptive.RecordingCoachingDelivery
import citu.edu.stathis.mobile.features.exercise.adaptive.shouldApplyRestoredAttempt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CorrectionCycleRestorationTest {
    private lateinit var context: Context
    private lateinit var db: AdaptiveQueueDatabase
    private lateinit var store: RoomCorrectionCycleStore
    private lateinit var evidence: PersistentEvidenceQueue
    private lateinit var offline: PersistentAdaptiveQueue
    private val gson = Gson()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deleteSandboxDatabase()
        File(context.filesDir, "form-evidence").deleteRecursively()
        db = AdaptiveQueueDatabase.open(context)
        store = RoomCorrectionCycleStore(db, gson, context)
        evidence = PersistentEvidenceQueue(context, db, gson)
        offline = PersistentAdaptiveQueue(context, db, gson)
    }

    @After
    fun teardown() {
        db.close()
        deleteSandboxDatabase()
        File(context.filesDir, "form-evidence").deleteRecursively()
    }

    @Test
    fun restoreReinstatesSessionPhaseCycleAndInterventionId() = runBlocking(Dispatchers.IO) {
        val capture = QueueCapture(evidence)
        val engine = engine(capture)
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        holdKneesIn(engine)
        val sessionId = engine.currentSessionId()
        val cycle = engine.currentCycleSeq()
        val interventionId =
            engine.stableInterventionId(sessionId, "SQUATS", FormErrorCode.KNEES_IN, cycle)
        assertEquals(1, cycle)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertEquals(interventionId, capture.events.single().interventionId)
        assertEquals(1, db.dao().allEvidence().size)
        assertEquals(1, db.dao().allInterventions().size)

        val revivedCapture = QueueCapture(evidence)
        val revived = engine(revivedCapture)
        assertTrue(revived.restoreMatchingAttempt("SQUATS", "TASK-1", "ROOM-1", 1))
        assertEquals(sessionId, revived.currentSessionId())
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, revived.lifecyclePhase())
        assertEquals(cycle, revived.currentCycleSeq())
        assertEquals(
            interventionId,
            revived.stableInterventionId(
                revived.currentSessionId(),
                "SQUATS",
                FormErrorCode.KNEES_IN,
                revived.currentCycleSeq()
            )
        )
        assertTrue(revived.restoreMatchingAttempt("SQUATS", "TASK-1", "ROOM-1", 1))
        holdKneesIn(revived)
        assertEquals(0, revivedCapture.events.size)
        assertEquals(1, db.dao().allEvidence().size)
        assertEquals(1, db.dao().allInterventions().size)
        assertEquals(1, db.dao().capturedCount())
    }

    @Test
    fun startAndRetryClearTheCycleAndKeepQueuedEvidence() = runBlocking(Dispatchers.IO) {
        val capture = QueueCapture(evidence)
        val engine = engine(capture)
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        holdKneesIn(engine)
        val abandoned = engine.currentSessionId()
        val interventionId = capture.events.single().interventionId
        val jpeg = File(context.filesDir, "form-evidence/$interventionId.jpg")
        assertTrue(jpeg.isFile)
        assertTrue(jpeg.length() > 0L)
        db.dao().enqueueResponseIfAbsent(
            QueuedResponseEntity(
                physicalId = "FR-KEEP",
                interventionPhysicalId = interventionId,
                studentId = "stu",
                payloadJson = "keep-response"
            )
        )

        engine.abandonOpenCycle()
        assertNull(store.load())
        assertTrue(db.dao().capturedIdsForSession(abandoned).isEmpty())
        assertEquals(1, db.dao().allEvidence().size)
        assertEquals(1, db.dao().allInterventions().size)
        assertEquals("keep-response", db.dao().allResponses().single().payloadJson)
        assertTrue(jpeg.isFile)

        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        assertNotEquals(abandoned, engine.currentSessionId())
        assertEquals(0, engine.currentCycleSeq())
        assertEquals(InterventionPhase.OBSERVING, engine.lifecyclePhase())
        assertEquals(engine.currentSessionId(), store.load()?.sessionId)
        assertEquals(1, db.dao().allEvidence().size)
        assertEquals(1, db.dao().allInterventions().size)
        assertEquals("keep-response", db.dao().allResponses().single().payloadJson)
        assertTrue(jpeg.isFile)
        assertTrue(db.dao().capturedIdsForSession(abandoned).isEmpty())
    }

    @Test
    fun mismatchedIdentityDoesNotAdoptOrDeleteTheStoredCycle() = runBlocking(Dispatchers.IO) {
        val engine = engine(QueueCapture(evidence))
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        holdKneesIn(engine)
        val sessionId = engine.currentSessionId()
        val evidenceCount = db.dao().allEvidence().size
        val interventionCount = db.dao().allInterventions().size

        assertFalse(engine.restoreMatchingAttempt("PUSH_UP", "TASK-1", "ROOM-1", 1))
        assertFalse(engine.restoreMatchingAttempt("SQUATS", "OTHER-TASK", "ROOM-1", 1))
        assertFalse(engine.restoreMatchingAttempt("SQUATS", "TASK-1", "OTHER-ROOM", 1))
        assertFalse(engine.restoreMatchingAttempt("SQUATS", "TASK-1", "ROOM-1", 2))
        assertFalse(engine.restoreMatchingAttempt("SQUATS", null, "ROOM-1", 1))
        assertEquals(sessionId, engine.currentSessionId())
        assertEquals(sessionId, store.load()?.sessionId)
        assertEquals(evidenceCount, db.dao().allEvidence().size)
        assertEquals(interventionCount, db.dao().allInterventions().size)

        val blank = engine(QueueCapture(evidence))
        blank.startSession("SQUATS", taskId = " ", classroomId = "", attemptNumber = 3)
        val blankSession = blank.currentSessionId()
        val resumed = engine(QueueCapture(evidence))
        assertTrue(resumed.restoreMatchingAttempt("SQUATS", null, null, 3))
        assertEquals(blankSession, resumed.currentSessionId())
    }

    @Test
    fun explicitStartDoesNotResurrectTheAbandonedSession() = runBlocking(Dispatchers.IO) {
        val engine = engine(QueueCapture(evidence))
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        holdKneesIn(engine)
        val abandoned = engine.currentSessionId()
        val generationAtStart = engine.currentAttemptGeneration()
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        val fresh = engine.currentSessionId()
        assertNotEquals(abandoned, fresh)
        val restored = engine.restoreMatchingAttempt("SQUATS", "TASK-1", "ROOM-1", 1)
        assertTrue(restored)
        assertEquals(fresh, engine.currentSessionId())
        assertFalse(
            shouldApplyRestoredAttempt(
                blocked = false,
                generationAtStart = generationAtStart,
                generationNow = engine.currentAttemptGeneration(),
                restored = restored
            )
        )
        val jpeg = db.dao().allEvidence().single().filePath
        assertTrue(File(jpeg).isFile)
        engine.abandonOpenCycle()
        assertFalse(engine.restoreMatchingAttempt("SQUATS", "TASK-1", "ROOM-1", 1))
        assertNotEquals(abandoned, engine.currentSessionId())
        assertEquals(1, db.dao().allEvidence().size)
        assertTrue(File(jpeg).isFile)
    }

    @Test
    fun endSessionAndFlushDoNotEraseTheDurableCycle() = runBlocking(Dispatchers.IO) {
        val capture = QueueCapture(evidence)
        val engine = engine(capture)
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        holdKneesIn(engine)
        val sessionId = engine.currentSessionId()
        val interventionId = capture.events.single().interventionId
        engine.endSession()
        assertEquals(sessionId, store.load()?.sessionId)
        assertEquals(1, db.dao().allEvidence().size)
        assertTrue(File(context.filesDir, "form-evidence/$interventionId.jpg").isFile)

        engine.flush()
        assertEquals(sessionId, store.load()?.sessionId)
        assertEquals(listOf(interventionId), db.dao().capturedIdsForSession(sessionId))
        val revived = engine(QueueCapture(evidence))
        assertTrue(revived.restoreMatchingAttempt("SQUATS", "TASK-1", "ROOM-1", 1))
        assertEquals(sessionId, revived.currentSessionId())
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, revived.lifecyclePhase())
        holdKneesIn(revived)
        assertEquals(0, db.dao().allEvidence().size)
        assertEquals(1, db.dao().capturedCount())
    }

    private fun engine(capture: FormEvidenceCapture) =
        AdaptiveFeedbackEngine(
            FakeAdaptiveApi(),
            RecordingCoachingDelivery(),
            offline,
            evidence,
            capture,
            store
        )

    private suspend fun holdKneesIn(engine: AdaptiveFeedbackEngine) {
        var now = 1_000L
        repeat(4) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
    }

    private fun deleteSandboxDatabase() {
        val file = context.getDatabasePath(AdaptiveQueueDatabase.DB_NAME)
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
        file.delete()
    }

    private class QueueCapture(
        private val queue: EvidenceQueue
    ) : FormEvidenceCapture {
        val events = mutableListOf<FormEvidenceEvent>()

        override fun onConfirmedCoaching(event: FormEvidenceEvent) {
            events += event
            queue.enqueue(event, ByteArray(16) { 4 })
        }
    }
}
