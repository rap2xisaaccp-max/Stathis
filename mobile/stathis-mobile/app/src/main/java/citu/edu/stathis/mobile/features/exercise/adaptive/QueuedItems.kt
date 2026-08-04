package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveOfflineQueue

/**
 * Shared queued item types for the offline queue API.
 */
data class QueuedIntervention(
    val payload: InterventionRequestDto,
    val attempts: Int = 0
)

data class QueuedResponse(
    val payload: ResponseRequestDto,
    val attempts: Int = 0
)
