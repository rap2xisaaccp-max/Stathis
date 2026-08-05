package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.InterventionRequestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.ResponseRequestDto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class PersistentAdaptiveQueueTest {
    private lateinit var context: Context
    private lateinit var db: AdaptiveQueueDatabase
    private lateinit var queue: PersistentAdaptiveQueue
    private val gson = Gson()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AdaptiveQueueDatabase.createInMemory(context)
        queue = PersistentAdaptiveQueue(context, db, gson)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun enqueue_and_drain_interventions_and_responses() = runBlocking {
        val fi = InterventionRequestDto(
            physicalId = "FI-1",
            sessionId = "SES-1",
            taskId = null,
            classroomId = null,
            exerciseType = "GLUTE_BRIDGE",
            errorCode = "KNEE_OUT",
            modality = "VERBAL_TEXT",
            messageCode = null,
            messageText = "Fix knees",
            deliveredAt = null,
            baselineSeverity = 0.9,
            policySource = "DEFAULT",
            experimentArm = null
        )

        queue.enqueueIntervention(fi)
        // duplicate enqueue should be ignored
        queue.enqueueIntervention(fi)

        val fr = ResponseRequestDto(
            physicalId = "FR-1",
            interventionPhysicalId = "FI-1",
            windowEndAt = null,
            postSeverity = 0.1,
            delta = 0.8,
            repsInWindow = 2,
            success = true,
            confoundersJson = mapOf("sessionId" to "SES-1")
        )

        queue.enqueueResponse(fr)
        // duplicate response for same FI should be ignored
        queue.enqueueResponse(fr)

        val (interventions, responses) = queue.drain()
        assertEquals(1, interventions.size)
        assertEquals("FI-1", interventions[0].payload.physicalId)
        assertEquals(1, responses.size)
        assertEquals("FR-1", responses[0].payload.physicalId)

        // after drain, queue should be empty
        assertTrue(queue.isEmpty())
    }

    @Test
    fun requeue_after_failure_accepts_items() = runBlocking {
        val fi = InterventionRequestDto(
            physicalId = "FI-2",
            sessionId = "SES-2",
            taskId = null,
            classroomId = null,
            exerciseType = "SQUATS",
            errorCode = "HIP_DROP",
            modality = "VISUAL_HIGHLIGHT",
            messageCode = null,
            messageText = "Raise hips",
            deliveredAt = null,
            baselineSeverity = 0.7,
            policySource = "DEFAULT",
            experimentArm = null
        )
        queue.enqueueIntervention(fi)
        val (i1, r1) = queue.drain()
        assertEquals(1, i1.size)

        // requeue failed items
        val accepted = queue.requeueAfterFailure(i1, r1)
        assertEquals(1, accepted)

        val (i2, r2) = queue.drain()
        assertEquals(1, i2.size)
    }
}
