package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline-first intervention/response upload queue (in-memory).
 *
 * Implements OfflineQueue so callers may be switched to a persistent implementation via DI.
 */
class AdaptiveOfflineQueue(
    private val maxRetries: Int = 5
) : OfflineQueue {

    private val interventions = ConcurrentLinkedQueue<QueuedIntervention>()
    private val responses = ConcurrentLinkedQueue<QueuedResponse>()
    private val _deadLetterCount = AtomicInteger(0)

    val deadLetterCount: Int get() = _deadLetterCount.get()
    val pendingInterventionCount: Int get() = interventions.size
    val pendingResponseCount: Int get() = responses.size

    override fun enqueueIntervention(payload: InterventionRequestDto) {
        val id = payload.physicalId
        if (!id.isNullOrBlank() && interventions.any { it.payload.physicalId == id }) {
            return // idempotent: retries reuse the same FI physicalId
        }
        interventions.add(QueuedIntervention(payload))
    }

    override fun enqueueResponse(payload: ResponseRequestDto) {
        val fi = payload.interventionPhysicalId
        if (fi.isNotBlank() && responses.any { it.payload.interventionPhysicalId == fi }) {
            return // one FR per FI
        }
        responses.add(QueuedResponse(payload))
    }

    /** Snapshot and clear current pending items for an upload attempt. */
    override fun drain(): Pair<List<QueuedIntervention>, List<QueuedResponse>> {
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
    override fun requeueAfterFailure(
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

    override fun clear() {
        interventions.clear()
        responses.clear()
    }

    override fun isEmpty(): Boolean = interventions.isEmpty() && responses.isEmpty()
}
