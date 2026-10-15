package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Abstraction for the adaptive offline queue so implementations can be swapped via DI.
 * Methods mirror the existing in-memory AdaptiveOfflineQueue behavior.
 */
interface OfflineQueue {
    fun enqueueIntervention(payload: InterventionRequestDto)
    fun enqueueResponse(payload: ResponseRequestDto)
    fun drain(): Pair<List<QueuedIntervention>, List<QueuedResponse>>
    fun requeueAfterFailure(
        failedInterventions: List<QueuedIntervention>,
        failedResponses: List<QueuedResponse>
    ): Int
    fun clear()
    fun isEmpty(): Boolean
}
