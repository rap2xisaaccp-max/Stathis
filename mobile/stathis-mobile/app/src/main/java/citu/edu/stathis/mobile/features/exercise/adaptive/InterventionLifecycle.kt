package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Evidence-first intervention lifecycle.
 *
 * OBSERVING → ERROR_CANDIDATE → ERROR_CONFIRMED → INTERVENTION_PENDING →
 * FEEDBACK_DELIVERED → RESPONSE_OBSERVATION → RESPONSE_CLOSED → COOLDOWN → OBSERVING
 *
 * [tryClaimDelivery] must be called **before** any async recommend/API work so concurrent
 * frames cannot create parallel interventions for the same error.
 *
 * Default cooldown: 8000ms per exercise/error (halved to ≥4000ms only for high severity).
 * Chosen to span roughly one coaching cycle + a few reps without allowing ~100ms bursts.
 */
enum class InterventionPhase {
    OBSERVING,
    ERROR_CANDIDATE,
    ERROR_CONFIRMED,
    INTERVENTION_PENDING,
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
    private val highSeverity: Double = 0.75,
    /** Sustained clear frames required before closing response early on flicker. */
    private val clearConfirmTicks: Int = 3
) {
    var phase: InterventionPhase = InterventionPhase.OBSERVING
        private set

    private var pendingCode: FormErrorCode? = null
    private var pendingTicks: Int = 0
    private var clearTicks: Int = 0
    private var openError: FormErrorCode? = null
    private var deliveredAt: Long = -1L
    private var responseStartReps: Int = 0
    private var lastInterventionAt: Long = -1L
    private var cycleSeq: Int = 0
    private val recentInterventionAt = ArrayDeque<Long>()
    private val escalationCounts = mutableMapOf<FormErrorCode, Int>()

    fun reset() {
        phase = InterventionPhase.OBSERVING
        pendingCode = null
        pendingTicks = 0
        clearTicks = 0
        openError = null
        deliveredAt = -1L
        responseStartReps = 0
        lastInterventionAt = -1L
        cycleSeq = 0
        recentInterventionAt.clear()
        escalationCounts.clear()
    }

    fun intensityFor(errorCode: FormErrorCode): InstructionIntensity {
        val n = escalationCounts[errorCode] ?: 0
        return when {
            n <= 0 -> InstructionIntensity.REMINDER
            else -> InstructionIntensity.ESCALATION
        }
    }

    /**
     * Atomically decide whether a new intervention cycle may begin and, if so, claim it
     * into [InterventionPhase.INTERVENTION_PENDING] so concurrent frames are blocked.
     *
     * @return claimed cycle sequence (>0) when delivery may proceed; null when blocked.
     */
    fun tryClaimDelivery(
        errorCode: FormErrorCode?,
        severity: Double,
        now: Long,
        cooldownMs: Long,
        currentReps: Int
    ): Int? {
        prune(now)
        advanceResponseObservation(errorCode, severity, currentReps, now, cooldownMs)

        if (errorCode == null || severity < minSeverity) {
            if (phase == InterventionPhase.ERROR_CANDIDATE ||
                phase == InterventionPhase.ERROR_CONFIRMED
            ) {
                clearPending()
                if (openError == null) phase = InterventionPhase.OBSERVING
            }
            return null
        }

        // Any unresolved / in-flight cycle blocks a new equivalent (and typically any) delivery.
        if (phase in
            setOf(
                InterventionPhase.INTERVENTION_PENDING,
                InterventionPhase.FEEDBACK_DELIVERED,
                InterventionPhase.RESPONSE_OBSERVATION
            )
        ) {
            return null
        }

        if (phase == InterventionPhase.COOLDOWN) {
            val effectiveCooldown = effectiveCooldownMs(severity, cooldownMs)
            if (lastInterventionAt >= 0L && now - lastInterventionAt < effectiveCooldown) {
                return null
            }
            phase = InterventionPhase.OBSERVING
        }

        // Different error while cooling / observing candidate: restart confirmation.
        if (pendingCode == errorCode) {
            pendingTicks += 1
        } else {
            pendingCode = errorCode
            pendingTicks = 1
        }
        phase =
            if (pendingTicks < confirmTicks) {
                InterventionPhase.ERROR_CANDIDATE
            } else {
                InterventionPhase.ERROR_CONFIRMED
            }
        if (pendingTicks < confirmTicks) return null
        if (recentInterventionAt.size >= maxPerMinute) return null

        val effectiveCooldown = effectiveCooldownMs(severity, cooldownMs)
        if (lastInterventionAt >= 0L && now - lastInterventionAt < effectiveCooldown) {
            return null
        }

        // Claim BEFORE async work.
        cycleSeq += 1
        openError = errorCode
        deliveredAt = now
        responseStartReps = currentReps
        lastInterventionAt = now
        recentInterventionAt.addLast(now)
        phase = InterventionPhase.INTERVENTION_PENDING
        escalationCounts[errorCode] = (escalationCounts[errorCode] ?: 0) + 1
        clearPending()
        prune(now)
        return cycleSeq
    }

    /** Backward-compatible gate probe used by older tests; prefer [tryClaimDelivery]. */
    fun shouldDeliver(
        errorCode: FormErrorCode?,
        severity: Double,
        now: Long,
        cooldownMs: Long,
        currentReps: Int
    ): Boolean = tryClaimDelivery(errorCode, severity, now, cooldownMs, currentReps) != null

    fun markDelivered(errorCode: FormErrorCode, now: Long, currentReps: Int) {
        openError = errorCode
        deliveredAt = now
        responseStartReps = currentReps
        if (lastInterventionAt < 0L) {
            lastInterventionAt = now
            recentInterventionAt.addLast(now)
        }
        phase = InterventionPhase.FEEDBACK_DELIVERED
        phase = InterventionPhase.RESPONSE_OBSERVATION
        clearTicks = 0
        prune(now)
    }

    /** Abort a claimed cycle that failed before delivery (e.g. recommend threw hard). */
    fun abortClaim(now: Long) {
        if (phase != InterventionPhase.INTERVENTION_PENDING) return
        openError = null
        phase = InterventionPhase.COOLDOWN
        lastInterventionAt = now
    }

    fun markReinforcementDelivered(now: Long) {
        lastInterventionAt = now
        recentInterventionAt.addLast(now)
    }

    fun markResponseClosed(successful: Boolean) {
        val err = openError
        openError = null
        clearTicks = 0
        phase = InterventionPhase.RESPONSE_CLOSED
        if (successful && err != null) {
            escalationCounts[err] = 0
        }
        phase = InterventionPhase.COOLDOWN
    }

    fun hasOpenIntervention(): Boolean = openError != null

    fun openErrorCode(): FormErrorCode? = openError

    fun confirmedTicks(): Int = pendingTicks

    fun currentCycleSeq(): Int = cycleSeq

    private fun effectiveCooldownMs(severity: Double, cooldownMs: Long): Long {
        // Floor 4000ms even for high severity — never allow ~100ms re-entry.
        return if (severity >= highSeverity) {
            (cooldownMs / 2).coerceAtLeast(4_000L)
        } else {
            cooldownMs.coerceAtLeast(8_000L)
        }
    }

    private fun advanceResponseObservation(
        errorCode: FormErrorCode?,
        severity: Double,
        currentReps: Int,
        now: Long,
        cooldownMs: Long
    ) {
        if (phase != InterventionPhase.RESPONSE_OBSERVATION || openError == null) return
        val repsProgressed = currentReps - responseStartReps
        val looksCleared = errorCode == null || errorCode != openError || severity < minSeverity
        if (looksCleared) {
            clearTicks += 1
        } else {
            clearTicks = 0
        }
        val errorCleared = clearTicks >= clearConfirmTicks
        val repsDone = repsProgressed >= responseValidReps
        if (!repsDone && !errorCleared) return

        markResponseClosed(successful = errorCleared)
        val effectiveCooldown = effectiveCooldownMs(severity, cooldownMs)
        if (now - lastInterventionAt >= effectiveCooldown) {
            phase = InterventionPhase.OBSERVING
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
