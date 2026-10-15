package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Display helpers for attempt-level Form Mastery. Mirrors backend FormMasteryMath
 * rounding so Profile and teacher charts show the same percent after refresh.
 */
object FormMasteryDisplay {
    const val NO_QUALIFYING_ATTEMPTS = "Form Mastery: Not enough classroom attempts yet"

    fun percent(formMasteryLevel: Double): Int =
        kotlin.math.round(formMasteryLevel.coerceIn(0.0, 1.0) * 100.0).toInt()

    fun percentLabel(formMasteryLevel: Double): String = "${percent(formMasteryLevel)}%"

    /**
     * Known states only. A missing or unrecognized state stays blank so the UI
     * does not present it as Learning.
     */
    fun stateLabel(state: String?): String =
        when (state?.trim()?.uppercase()) {
            "IMPROVING" -> "Improving"
            "MASTERED" -> "Mastered"
            "LEARNING" -> "Learning"
            else -> ""
        }

    fun qualifyingAttemptLabel(count: Int): String {
        val safe = count.coerceAtLeast(0)
        val noun = if (safe == 1) "attempt" else "attempts"
        return "$safe qualifying $noun"
    }

    fun labeledPercent(level: Double, state: String?, qualifyingAttempts: Int): String {
        val percent = percentLabel(level)
        val label = stateLabel(state)
        val attempts = qualifyingAttemptLabel(qualifyingAttempts)
        return if (label.isEmpty()) "$percent · $attempts" else "$percent · $label · $attempts"
    }

    fun resultLine(level: Double, state: String?, qualifyingAttempts: Int): String =
        "Form Mastery: ${labeledPercent(level, state, qualifyingAttempts)}"

    /** Reload Form Mastery only after the classroom attempt has been saved. */
    fun shouldRefreshAfterClassroomSave(submitSucceeded: Boolean): Boolean = submitSucceeded
}

/**
 * Ignores an older Form Mastery load that finishes after a newer one has started.
 * The result screen reloads after submit, and that result must win over the
 * load started before the score was saved.
 */
class FormMasteryLoadGate {
    private var generation = 0

    fun open(): Int {
        generation += 1
        return generation
    }

    fun isCurrent(token: Int): Boolean = token == generation
}
