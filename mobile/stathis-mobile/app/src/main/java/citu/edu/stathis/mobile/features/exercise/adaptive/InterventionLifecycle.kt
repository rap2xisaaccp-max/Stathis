package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Evidence-first intervention lifecycle (Phase 2).
 *
 * OBSERVING → ERROR_CANDIDATE → ERROR_CONFIRMED → FEEDBACK_DELIVERED →
 * RESPONSE_OBSERVATION → RESPONSE_CLOSED → COOLDOWN → OBSERVING
 *
 * Time cooldown is secondary to confirmation ticks and response-rep windows.
 */
enum class InterventionPhase {
    OBSERVING,
    ERROR_CANDIDATE,
    ERROR_CONFIRMED,
    FEEDBACK_DELIVERED,
    RESPONSE_OBSERVATION,
    RESPONSE_CLOSED,
    COOLDOWN
}

class InterventionLifecycle(
    private val confirmTicks: Int = 3,
    private val responseValidReps: Int = 3,
    private val maxPerMinute: Int = 4,
    private val minSeverity: Double = 0.25,
    private val highSeverity: Double = 0.75
) {
    var phase: InterventionPhase = InterventionPhase.OBSERVING
        private set

    private var pendingCode: FormErrorCode? = null
    private var pendingTicks: Int = 0
    private var openError: FormErrorCode? = null
    private var deliveredAt: Long = -1L
    private var responseStartReps: Int = 0
    private var lastInterventionAt: Long = -1L
    private val recentInterventionAt = ArrayDeque<Long>()
    private val escalationCounts = mutableMapOf<FormErrorCode, Int>()

    fun reset() {
        phase = InterventionPhase.OBSERVING
        pendingCode = null
        pendingTicks = 0
        openError = null
        deliveredAt = -1L
        responseStartReps = 0
        lastInterventionAt = -1L
        recentInterventionAt.clear()
        escalationCounts.clear()
    }

    fun intensityFor(errorCode: FormErrorCode): InstructionIntensity {
        val n = escalationCounts[errorCode] ?: 0
        return when {
            n <= 0 -> InstructionIntensity.REMINDER
            n == 1 -> InstructionIntensity.ESCALATION
            else -> InstructionIntensity.ESCALATION
        }
    }

    /**
     * Returns true when a new intervention may be delivered now.
     * Blocks while an unresolved intervention for the same error is open.
     */
    fun shouldDeliver(
        errorCode: FormErrorCode?,
        severity: Double,
        now: Long,
        cooldownMs: Long,
        currentReps: Int
    ): Boolean {
        prune(now)
        advanceResponseObservation(errorCode, severity, currentReps, now, cooldownMs)

        if (errorCode == null || severity < minSeverity) {
            if (phase == InterventionPhase.ERROR_CANDIDATE || phase == InterventionPhase.ERROR_CONFIRMED) {
                clearPending()
                if (openError == null) phase = InterventionPhase.OBSERVING
            }
            return false
        }

        // Unresolved same-error intervention: do not create a duplicate.
        if (openError == errorCode &&
            phase in setOf(
                InterventionPhase.FEEDBACK_DELIVERED,
                InterventionPhase.RESPONSE_OBSERVATION
            )
        ) {
            return false
        }

        if (phase == InterventionPhase.COOLDOWN) {
            val effectiveCooldown =
                if (severity >= highSeverity) (cooldownMs / 2).coerceAtLeast(3000L) else cooldownMs
            if (lastInterventionAt >= 0L && now - lastInterventionAt < effectiveCooldown) {
                return false
            }
            phase = InterventionPhase.OBSERVING
        }

        if (pendingCode == errorCode) {
            pendingTicks += 1
        } else {
            pendingCode = errorCode
            pendingTicks = 1
        }
        phase = if (pendingTicks < confirmTicks) {
            InterventionPhase.ERROR_CANDIDATE
        } else {
            InterventionPhase.ERROR_CONFIRMED
        }
        if (pendingTicks < confirmTicks) return false
        if (recentInterventionAt.size >= maxPerMinute) return false

        val effectiveCooldown =
            if (severity >= highSeverity) (cooldownMs / 2).coerceAtLeast(3000L) else cooldownMs
        if (lastInterventionAt >= 0L && now - lastInterventionAt < effectiveCooldown) {
            return false
        }
        return true
    }

    fun markDelivered(errorCode: FormErrorCode, now: Long, currentReps: Int) {
        openError = errorCode
        deliveredAt = now
        responseStartReps = currentReps
        lastInterventionAt = now
        recentInterventionAt.addLast(now)
        phase = InterventionPhase.FEEDBACK_DELIVERED
        escalationCounts[errorCode] = (escalationCounts[errorCode] ?: 0) + 1
        clearPending()
        phase = InterventionPhase.RESPONSE_OBSERVATION
        prune(now)
    }

    fun markReinforcementDelivered(now: Long) {
        // Soft positive cue — does not open a new response window.
        lastInterventionAt = now
        recentInterventionAt.addLast(now)
    }

    fun markResponseClosed(successful: Boolean) {
        val err = openError
        openError = null
        phase = InterventionPhase.RESPONSE_CLOSED
        if (successful && err != null) {
            escalationCounts[err] = 0
        }
        phase = InterventionPhase.COOLDOWN
    }

    fun hasOpenIntervention(): Boolean = openError != null

    fun openErrorCode(): FormErrorCode? = openError

    fun confirmedTicks(): Int = pendingTicks

    private fun advanceResponseObservation(
        errorCode: FormErrorCode?,
        severity: Double,
        currentReps: Int,
        now: Long,
        cooldownMs: Long
    ) {
        if (phase != InterventionPhase.RESPONSE_OBSERVATION || openError == null) return
        val repsProgressed = currentReps - responseStartReps
        val errorCleared = errorCode == null || errorCode != openError || severity < minSeverity
        if (repsProgressed >= responseValidReps || errorCleared) {
            markResponseClosed(successful = errorCleared)
            // Secondary cooldown already entered via markResponseClosed.
            val effectiveCooldown = cooldownMs.coerceAtLeast(3000L)
            if (now - lastInterventionAt >= effectiveCooldown) {
                phase = InterventionPhase.OBSERVING
            }
        }
    }

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
