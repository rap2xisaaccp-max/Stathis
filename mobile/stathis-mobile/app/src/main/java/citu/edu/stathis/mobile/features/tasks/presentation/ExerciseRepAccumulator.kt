package citu.edu.stathis.mobile.features.tasks.presentation

/**
 * Accumulates counted reps across on-device detector resets (camera rebind / face re-verify).
 *
 * When the detector's absolute [repCount] drops, the previous peak is committed to an anchor
 * so session progress is not wiped, while new detector reps continue to add.
 */
data class RepTotals(
    val valid: Int,
    val attempted: Int
)

class ExerciseRepAccumulator {
    private var anchor: Int = 0
    private var lastDetectorReps: Int = 0
    private var attemptedAnchor: Int = 0
    private var lastAttempted: Int = 0

    fun reset() {
        anchor = 0
        lastDetectorReps = 0
        attemptedAnchor = 0
        lastAttempted = 0
    }

    /** Apply absolute detector rep count; returns session-total valid reps. */
    fun applyDetectorReps(detectorReps: Int): Int = applyCounts(detectorReps, detectorReps).valid

    /**
     * Apply absolute detector totals. A drop anchors the previous peak so a camera
     * rebind does not wipe the attempt, and the next detector counts add on top.
     */
    fun applyCounts(validReps: Int, attemptedReps: Int): RepTotals {
        val safeValid = validReps.coerceAtLeast(0)
        val safeAttempted = attemptedReps.coerceAtLeast(safeValid)
        if (safeValid < lastDetectorReps) {
            anchor += lastDetectorReps
        }
        if (safeAttempted < lastAttempted) {
            attemptedAnchor += lastAttempted
        }
        lastDetectorReps = safeValid
        lastAttempted = safeAttempted
        return RepTotals(total(), attemptedAnchor + lastAttempted)
    }

    fun total(): Int = anchor + lastDetectorReps

    fun attemptedTotal(): Int = attemptedAnchor + lastAttempted

    fun anchorForTests(): Int = anchor

    fun lastDetectorForTests(): Int = lastDetectorReps
}
