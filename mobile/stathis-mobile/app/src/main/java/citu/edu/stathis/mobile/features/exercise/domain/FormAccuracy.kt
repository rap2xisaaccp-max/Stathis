package citu.edu.stathis.mobile.features.exercise.domain

import kotlin.math.abs

/**
 * Helpers for scoring exercise form quality (not pose-detection confidence).
 * Scores are in [0, 1] unless noted as percent.
 */
object FormAccuracy {

    fun nearIdeal(
        value: Float,
        ideal: Float,
        fullCreditTolerance: Float,
        zeroCreditAt: Float
    ): Float {
        val delta = abs(value - ideal)
        if (delta <= fullCreditTolerance) return 1f
        if (delta >= zeroCreditAt) return 0f
        val span = (zeroCreditAt - fullCreditTolerance).coerceAtLeast(0.0001f)
        return (1f - (delta - fullCreditTolerance) / span).coerceIn(0f, 1f)
    }

    fun atLeast(value: Float, goodAt: Float, poorAt: Float): Float {
        if (value >= goodAt) return 1f
        if (value <= poorAt) return 0f
        val span = (goodAt - poorAt).coerceAtLeast(0.0001f)
        return ((value - poorAt) / span).coerceIn(0f, 1f)
    }

    fun atMost(value: Float, goodAt: Float, poorAt: Float): Float {
        if (value <= goodAt) return 1f
        if (value >= poorAt) return 0f
        val span = (poorAt - goodAt).coerceAtLeast(0.0001f)
        return (1f - (value - goodAt) / span).coerceIn(0f, 1f)
    }

    fun combine(phaseScore: Float, formIssueCount: Int, issuePenalty: Float = 0.18f): Float =
        (phaseScore - formIssueCount * issuePenalty).coerceIn(0f, 1f)
}

/**
 * Session form accuracy as the mean of assessable form samples.
 * Defaults to 0 until the student is performing with measurable form.
 */
class SessionAccuracyTracker {
    private var sampleSum = 0.0
    private var sampleCount = 0

    fun reset() {
        sampleSum = 0.0
        sampleCount = 0
    }

    /**
     * Records a form score in [0, 1]. Null samples are ignored so accuracy stays 0
     * until form can actually be assessed.
     */
    fun record(formScore01: Float?) {
        if (formScore01 == null) return
        sampleSum += formScore01.coerceIn(0f, 1f) * 100.0
        sampleCount++
    }

    fun currentAccuracyPercent(): Float =
        if (sampleCount == 0) 0f else (sampleSum / sampleCount).toFloat().coerceIn(0f, 100f)

    fun accuracyForSubmit(): Float = currentAccuracyPercent()

    fun sampleCount(): Int = sampleCount
}
