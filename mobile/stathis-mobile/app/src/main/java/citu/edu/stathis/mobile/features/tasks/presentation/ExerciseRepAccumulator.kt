package citu.edu.stathis.mobile.features.tasks.presentation

/**
 * Accumulates counted reps across on-device detector resets (camera rebind / face re-verify).
 *
 * When the detector's absolute [repCount] drops, the previous peak is committed to an anchor
 * so session progress is not wiped, while new detector reps continue to add.
 */
class ExerciseRepAccumulator {
    private var anchor: Int = 0
    private var lastDetectorReps: Int = 0

    fun reset() {
        anchor = 0
        lastDetectorReps = 0
    }

    /** Apply absolute detector rep count; returns session-total reps. */
    fun applyDetectorReps(detectorReps: Int): Int {
        val safe = detectorReps.coerceAtLeast(0)
        if (safe < lastDetectorReps) {
            anchor += lastDetectorReps
        }
        lastDetectorReps = safe
        return total()
    }

    fun total(): Int = anchor + lastDetectorReps

    fun anchorForTests(): Int = anchor

    fun lastDetectorForTests(): Int = lastDetectorReps
}
