package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Dual-lane TTS coordination for one [android.speech.tts.TextToSpeech] engine.
 *
 * Physical coaching and camera/technical guidance share the speaker, but keep independent
 * debounce and pending slots so a framing prompt cannot consume the physical one-shot
 * window (and vice versa).
 */
internal enum class CoachingTtsLane {
    PHYSICAL,
    TECHNICAL,
    ENCOURAGEMENT
}

internal enum class CoachingTtsAction {
    SKIP_BLANK,
    DEBOUNCE_PHYSICAL,
    SKIP_SAME_TECHNICAL,
    SKIP_BLOCKED,
    QUEUE_PENDING,
    SPEAK_NOW
}

internal data class CoachingTtsDecision(
    val action: CoachingTtsAction,
    val lane: CoachingTtsLane? = null,
    val message: String = ""
)

internal class CoachingTtsSpeechGate(
    private val physicalDebounceMs: Long = PHYSICAL_DEBOUNCE_MS,
    private val technicalCooldownMs: Long = TECHNICAL_COOLDOWN_MS
) {
    var ready: Boolean = false
        private set

    var lastPhysicalSpokenAt: Long = 0L
        private set
    var lastTechnicalSpokenAt: Long = 0L
        private set
    var lastTechnicalMessage: String? = null
        private set
    var pendingPhysical: String? = null
        private set
    var pendingTechnical: String? = null
        private set
    var pendingEncouragement: String? = null
        private set
    var lastEncouragementSpokenAt: Long = 0L
        private set

    fun markReady(now: Long = 0L): CoachingTtsDecision? {
        ready = true
        return flushPending(now)
    }

    fun markInitFailed() {
        ready = false
        cancelPending()
    }

    fun requestPhysical(message: String, now: Long): CoachingTtsDecision {
        if (message.isBlank()) return CoachingTtsDecision(CoachingTtsAction.SKIP_BLANK)
        if (!ready) {
            pendingPhysical = message
            return CoachingTtsDecision(CoachingTtsAction.QUEUE_PENDING, CoachingTtsLane.PHYSICAL, message)
        }
        if (isPhysicalProtected(now)) {
            return CoachingTtsDecision(CoachingTtsAction.DEBOUNCE_PHYSICAL, CoachingTtsLane.PHYSICAL, message)
        }
        // A claimed physical cue is about to speak — drop leftover lower-priority prompts
        // so they cannot QUEUE_FLUSH the physical utterance.
        pendingTechnical = null
        pendingEncouragement = null
        return CoachingTtsDecision(CoachingTtsAction.SPEAK_NOW, CoachingTtsLane.PHYSICAL, message)
    }

    /**
     * Rejected-rep line. Same priority as a form correction, and it may replace a correction
     * that was just spoken so the student hears one combined sentence.
     */
    fun requestPhysicalImmediate(message: String, now: Long): CoachingTtsDecision {
        if (message.isBlank()) return CoachingTtsDecision(CoachingTtsAction.SKIP_BLANK)
        pendingTechnical = null
        pendingEncouragement = null
        if (!ready) {
            pendingPhysical = message
            return CoachingTtsDecision(CoachingTtsAction.QUEUE_PENDING, CoachingTtsLane.PHYSICAL, message)
        }
        pendingPhysical = null
        return CoachingTtsDecision(CoachingTtsAction.SPEAK_NOW, CoachingTtsLane.PHYSICAL, message)
    }

    fun requestEncouragement(message: String, now: Long): CoachingTtsDecision {
        if (message.isBlank()) return CoachingTtsDecision(CoachingTtsAction.SKIP_BLANK)
        if (!ready) {
            pendingEncouragement = message
            return CoachingTtsDecision(
                CoachingTtsAction.QUEUE_PENDING,
                CoachingTtsLane.ENCOURAGEMENT,
                message
            )
        }
        if (isPhysicalProtected(now) ||
            pendingPhysical != null ||
            pendingTechnical != null ||
            isTechnicalRecent(now)
        ) {
            return CoachingTtsDecision(
                CoachingTtsAction.SKIP_BLOCKED,
                CoachingTtsLane.ENCOURAGEMENT,
                message
            )
        }
        pendingEncouragement = null
        return CoachingTtsDecision(CoachingTtsAction.SPEAK_NOW, CoachingTtsLane.ENCOURAGEMENT, message)
    }

    fun requestTechnical(message: String, now: Long): CoachingTtsDecision {
        if (message.isBlank()) return CoachingTtsDecision(CoachingTtsAction.SKIP_BLANK)
        if (isSameTechnicalInCooldown(message, now)) {
            return CoachingTtsDecision(
                CoachingTtsAction.SKIP_SAME_TECHNICAL,
                CoachingTtsLane.TECHNICAL,
                message
            )
        }
        if (!ready || isPhysicalProtected(now) || pendingPhysical != null) {
            pendingTechnical = message
            pendingEncouragement = null
            return CoachingTtsDecision(CoachingTtsAction.QUEUE_PENDING, CoachingTtsLane.TECHNICAL, message)
        }
        pendingEncouragement = null
        return CoachingTtsDecision(CoachingTtsAction.SPEAK_NOW, CoachingTtsLane.TECHNICAL, message)
    }

    fun flushPending(now: Long): CoachingTtsDecision? {
        pendingPhysical?.let { msg ->
            pendingPhysical = null
            val decision = requestPhysical(msg, now)
            if (decision.action == CoachingTtsAction.SPEAK_NOW) return decision
            if (decision.action == CoachingTtsAction.QUEUE_PENDING ||
                decision.action == CoachingTtsAction.DEBOUNCE_PHYSICAL
            ) {
                pendingPhysical = msg
            }
            return null
        }
        pendingTechnical?.let { msg ->
            pendingTechnical = null
            val decision = requestTechnical(msg, now)
            return when (decision.action) {
                CoachingTtsAction.SPEAK_NOW -> decision
                CoachingTtsAction.QUEUE_PENDING -> {
                    pendingTechnical = msg
                    null
                }
                else -> null
            }
        }
        pendingEncouragement?.let { msg ->
            pendingEncouragement = null
            val decision = requestEncouragement(msg, now)
            return when (decision.action) {
                CoachingTtsAction.SPEAK_NOW -> decision
                CoachingTtsAction.QUEUE_PENDING -> {
                    pendingEncouragement = msg
                    null
                }
                else -> null
            }
        }
        return null
    }

    fun markSpoken(lane: CoachingTtsLane, now: Long, message: String) {
        when (lane) {
            CoachingTtsLane.PHYSICAL -> lastPhysicalSpokenAt = now
            CoachingTtsLane.TECHNICAL -> {
                lastTechnicalSpokenAt = now
                lastTechnicalMessage = message
            }
            CoachingTtsLane.ENCOURAGEMENT -> lastEncouragementSpokenAt = now
        }
    }

    fun clearTechnical() {
        lastTechnicalMessage = null
        lastTechnicalSpokenAt = 0L
        pendingTechnical = null
    }

    fun cancelPending() {
        pendingPhysical = null
        pendingTechnical = null
        pendingEncouragement = null
    }

    fun resetAll() {
        cancelPending()
        clearTechnical()
        lastPhysicalSpokenAt = 0L
    }

    private fun isPhysicalProtected(now: Long): Boolean =
        lastPhysicalSpokenAt > 0L && now - lastPhysicalSpokenAt < physicalDebounceMs

    private fun isTechnicalRecent(now: Long): Boolean =
        lastTechnicalSpokenAt > 0L && now - lastTechnicalSpokenAt < physicalDebounceMs

    private fun isSameTechnicalInCooldown(message: String, now: Long): Boolean =
        lastTechnicalMessage == message &&
            lastTechnicalSpokenAt > 0L &&
            now - lastTechnicalSpokenAt < technicalCooldownMs

    companion object {
        const val PHYSICAL_DEBOUNCE_MS = 2_500L
        const val TECHNICAL_COOLDOWN_MS = 8_000L
    }
}
