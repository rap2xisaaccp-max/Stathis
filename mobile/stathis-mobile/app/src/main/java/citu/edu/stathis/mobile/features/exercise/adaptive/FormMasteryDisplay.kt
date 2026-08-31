package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Display helpers for attempt-level Form Mastery. Mirrors backend FormMasteryMath
 * rounding so Profile and teacher charts show the same percent after refresh.
 */
object FormMasteryDisplay {
    fun percent(formMasteryLevel: Double): Int =
        kotlin.math.round(formMasteryLevel.coerceIn(0.0, 1.0) * 100.0).toInt()

    fun percentLabel(formMasteryLevel: Double): String = "${percent(formMasteryLevel)}%"
}
