package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline-first intervention/response upload queue.
 *
 * Failed flushes are re-queued with a retry counter. Items exceeding [maxRetries] are dropped
 * into [deadLetterCount] so the session can continue without unbounded memory growth.
 */
class AdaptiveOfflineQueue(
    private val maxRetries: Int = 5
) {
    data class QueuedIntervention(
        val payload: InterventionRequestDto,
        val attempts: Int = 0
    )

    data class QueuedResponse(
        val payload: ResponseRequestDto,
        val attempts: Int = 0
    )

    private val interventions = ConcurrentLinkedQueue<QueuedIntervention>()
    private val responses = ConcurrentLinkedQueue<QueuedResponse>()
    private val _deadLetterCount = AtomicInteger(0)

    val deadLetterCount: Int get() = _deadLetterCount.get()
    val pendingInterventionCount: Int get() = interventions.size
    val pendingResponseCount: Int get() = responses.size

    fun enqueueIntervention(payload: InterventionRequestDto) {
        interventions.add(QueuedIntervention(payload))
    }

    fun enqueueResponse(payload: ResponseRequestDto) {
        responses.add(QueuedResponse(payload))
    }

    /** Snapshot and clear current pending items for an upload attempt. */
    fun drain(): Pair<List<QueuedIntervention>, List<QueuedResponse>> {
        val drainedInterventions = mutableListOf<QueuedIntervention>()
        while (true) {
            drainedInterventions.add(interventions.poll() ?: break)
        }
        val drainedResponses = mutableListOf<QueuedResponse>()
        while (true) {
            drainedResponses.add(responses.poll() ?: break)
        }
        return drainedInterventions to drainedResponses
    }

    /**
     * Re-queue failed items with incremented attempt counts.
     * @return number of items accepted back into the live queue
     */
    fun requeueAfterFailure(
        failedInterventions: List<QueuedIntervention>,
        failedResponses: List<QueuedResponse>
    ): Int {
        var accepted = 0
        failedInterventions.forEach { item ->
            val next = item.copy(attempts = item.attempts + 1)
            if (next.attempts > maxRetries) {
                _deadLetterCount.incrementAndGet()
            } else {
                interventions.add(next)
                accepted++
            }
        }
        failedResponses.forEach { item ->
            val next = item.copy(attempts = item.attempts + 1)
            if (next.attempts > maxRetries) {
                _deadLetterCount.incrementAndGet()
            } else {
                responses.add(next)
                accepted++
            }
        }
        return accepted
    }

    fun clear() {
        interventions.clear()
        responses.clear()
    }

    fun isEmpty(): Boolean = interventions.isEmpty() && responses.isEmpty()
}
