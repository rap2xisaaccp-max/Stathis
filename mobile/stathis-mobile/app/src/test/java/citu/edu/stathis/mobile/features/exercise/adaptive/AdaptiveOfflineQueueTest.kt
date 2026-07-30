package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveOfflineQueueTest {

    @Test
    fun enqueueAndDrainClearsPending() {
        val queue = AdaptiveOfflineQueue()
        queue.enqueueIntervention(
            InterventionRequestDto(
                physicalId = "FI-1",
                sessionId = "SES-1",
                exerciseType = "SQUAT",
                errorCode = "CHEST_UP",
                modality = "VERBAL_TEXT",
                baselineSeverity = 0.7,
                policySource = "DEFAULT"
            )
        )
        queue.enqueueResponse(
            ResponseRequestDto(
                physicalId = "FR-1",
                interventionPhysicalId = "FI-1",
                postSeverity = 0.3,
                delta = 0.4,
                success = true
            )
        )

        assertEquals(1, queue.pendingInterventionCount)
        assertEquals(1, queue.pendingResponseCount)

        val (interventions, responses) = queue.drain()
        assertEquals(1, interventions.size)
        assertEquals(1, responses.size)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun failedFlushRequeuesWithIncrementedAttempts() {
        val queue = AdaptiveOfflineQueue(maxRetries = 5)
        queue.enqueueIntervention(
            InterventionRequestDto(
                sessionId = "SES-1",
                exerciseType = "SQUAT",
                errorCode = "SAG",
                modality = "VISUAL_HIGHLIGHT",
                baselineSeverity = 0.5,
                policySource = "EXPLORE"
            )
        )
        val (drained, _) = queue.drain()
        val accepted = queue.requeueAfterFailure(drained, emptyList())

        assertEquals(1, accepted)
        assertEquals(1, queue.pendingInterventionCount)

        val (again, _) = queue.drain()
        assertEquals(1, again.first().attempts)
    }

    @Test
    fun exceedsMaxRetriesMovesToDeadLetter() {
        val queue = AdaptiveOfflineQueue(maxRetries = 2)
        var batch =
            listOf(
                AdaptiveOfflineQueue.QueuedIntervention(
                    payload =
                        InterventionRequestDto(
                            sessionId = "SES-1",
                            exerciseType = "PUSH_UP",
                            errorCode = "PIKE",
                            modality = "VERBAL_TEXT",
                            baselineSeverity = 0.6,
                            policySource = "DEFAULT"
                        ),
                    attempts = 2
                )
            )

        // attempts become 3 after failure → exceeds maxRetries=2
        val accepted = queue.requeueAfterFailure(batch, emptyList())
        assertEquals(0, accepted)
        assertEquals(1, queue.deadLetterCount)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun retryBudgetAllowsMultipleFailuresThenDrops() {
        val queue = AdaptiveOfflineQueue(maxRetries = 2)
        queue.enqueueResponse(
            ResponseRequestDto(
                interventionPhysicalId = "FI-9",
                postSeverity = 0.5,
                delta = 0.0,
                success = false
            )
        )

        repeat(2) {
            val (_, drained) = queue.drain()
            queue.requeueAfterFailure(emptyList(), drained)
        }
        assertEquals(1, queue.pendingResponseCount)

        val (_, drained) = queue.drain()
        queue.requeueAfterFailure(emptyList(), drained)
        assertEquals(0, queue.pendingResponseCount)
        assertEquals(1, queue.deadLetterCount)
    }
}
