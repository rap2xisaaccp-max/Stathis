package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Decides whether a completed movement is a valid rep.
 *
 * Allowed physical errors are latched during the working phase (for example squat depth
 * while down) so they still reject the rep after the student stands up. Technical,
 * unknown, and cross-exercise signals never latch and never clear a physical latch.
 * One [consume] call closes the cycle so the same movement cannot reject twice.
 */
class RepCycleVerdict(
    private val exerciseType: String,
    private val stableFrames: Int = 3,
    private val clearFrames: Int = 3
) {
    var latched: FormErrorCode? = null
        private set

    private var pending: FormErrorCode? = null
    private var pendingTicks: Int = 0
    private var clearTicks: Int = 0

    fun observe(formIssues: List<String>, flags: List<String> = emptyList()) {
        val code = FormErrorMapper.resolve(flags, formIssues, exerciseType)
        when {
            FormErrorClassifier.isCoachableForExercise(exerciseType, code) && code != null -> {
                if (pending == code) {
                    pendingTicks += 1
                } else {
                    pending = code
                    pendingTicks = 1
                }
                clearTicks = 0
                if (pendingTicks >= stableFrames) {
                    latched = code
                }
            }
            FormErrorClassifier.isTechnical(code) -> Unit
            else -> {
                clearTicks += 1
                pending = null
                pendingTicks = 0
                if (clearTicks >= clearFrames) {
                    latched = null
                }
            }
        }
    }

    /**
     * Closes the current movement cycle.
     * When [intervalAllowsNewAttempt] is false the cycle ended too soon to be a new rep.
     */
    fun consume(intervalAllowsNewAttempt: Boolean = true): RepCycleOutcome {
        val error = latched
        reset()
        if (!intervalAllowsNewAttempt) {
            return RepCycleOutcome(valid = false, rejected = false, error = null)
        }
        return if (error != null) {
            RepCycleOutcome(valid = false, rejected = true, error = error)
        } else {
            RepCycleOutcome(valid = true, rejected = false, error = null)
        }
    }

    fun reset() {
        latched = null
        pending = null
        pendingTicks = 0
        clearTicks = 0
    }
}

data class RepCycleOutcome(
    val valid: Boolean,
    val rejected: Boolean,
    val error: FormErrorCode?
) {
    val attempted: Boolean get() = valid || rejected
}
