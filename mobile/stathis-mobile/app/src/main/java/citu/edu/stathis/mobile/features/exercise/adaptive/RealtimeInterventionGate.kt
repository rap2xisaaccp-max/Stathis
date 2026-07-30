package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Gates real-time interventions to reduce pose-noise spam.
 *
 * - Debounces until the same error persists for [confirmTicks] frames
 * - Enforces cooldown between deliveries
 * - Caps interventions per rolling minute
 * - Allows severe errors to cut cooldown in half once confirmed
 */
class RealtimeInterventionGate(
    private val confirmTicks: Int = 3,
    private val maxPerMinute: Int = 4,
    private val minSeverity: Double = 0.25,
    private val highSeverity: Double = 0.75
) {
    private var pendingCode: FormErrorCode? = null
    private var pendingTicks: Int = 0
    private var lastInterventionAt: Long = -1L
    private val recentInterventionAt = ArrayDeque<Long>()

    fun reset() {
        pendingCode = null
        pendingTicks = 0
        lastInterventionAt = -1L
        recentInterventionAt.clear()
    }

    /**
     * Observe a candidate error. Returns true when an intervention may be delivered now.
     * Caller must invoke [markDelivered] after actually delivering.
     */
    fun shouldDeliver(
        errorCode: FormErrorCode?,
        severity: Double,
        now: Long,
        cooldownMs: Long
    ): Boolean {
        prune(now)
        if (errorCode == null || severity < minSeverity) {
            clearPending()
            return false
        }

        if (pendingCode == errorCode) {
            pendingTicks += 1
        } else {
            pendingCode = errorCode
            pendingTicks = 1
        }
        if (pendingTicks < confirmTicks) {
            return false
        }

        if (recentInterventionAt.size >= maxPerMinute) {
            return false
        }

        val effectiveCooldown =
            if (severity >= highSeverity) (cooldownMs / 2).coerceAtLeast(3000L) else cooldownMs
        if (lastInterventionAt >= 0L && now - lastInterventionAt < effectiveCooldown) {
            return false
        }
        return true
    }

    fun markDelivered(now: Long) {
        lastInterventionAt = now
        recentInterventionAt.addLast(now)
        clearPending()
        prune(now)
    }

    fun confirmedTicks(): Int = pendingTicks

    private fun clearPending() {
        pendingCode = null
        pendingTicks = 0
    }

    private fun prune(now: Long) {
        while (recentInterventionAt.isNotEmpty() && now - recentInterventionAt.first() > 60_000L) {
            recentInterventionAt.removeFirst()
        }
    }
}
