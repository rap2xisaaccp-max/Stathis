package citu.edu.stathis.mobile.features.exercise.domain

/**
 * Tracks exercise accuracy as the mean of non-zero pose-confidence samples.
 * Last-frame zeros (missing landmarks) must not wipe a successful session.
 */
class SessionAccuracyTracker {
    var currentAccuracy: Float = 0f
        private set
    var sessionAccuracy: Float = 0f
        private set
    var sampleCount: Int = 0
        private set

    private var sum: Float = 0f

    fun record(confidence: Float) {
        val pct = (confidence * 100f).coerceIn(0f, 100f)
        currentAccuracy = pct
        if (confidence > 0.01f) {
            sum += pct
            sampleCount += 1
            sessionAccuracy = sum / sampleCount
        }
    }

    /** Value to persist on Finish/submit. */
    fun accuracyForSubmit(): Float =
        if (sampleCount > 0) sessionAccuracy else currentAccuracy
}
