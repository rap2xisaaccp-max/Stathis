package citu.edu.stathis.mobile.features.exercise.domain

/**
 * Estimates calories burned from exercise type, reps, and body weight (kg).
 * Mirrors backend ExerciseCalorieService so the UI can show calories before save.
 */
object ExerciseCalorieCalculator {
    private const val REFERENCE_WEIGHT_KG = 70.0
    private const val DEFAULT_WEIGHT_KG = 70.0

    fun calculate(exerciseType: String?, reps: Int, weightKg: Double? = null): Double {
        if (reps <= 0) return 0.0
        val weight = weightKg?.takeIf { it > 0 } ?: DEFAULT_WEIGHT_KG
        val calories = reps * caloriesPerRep(exerciseType) * (weight / REFERENCE_WEIGHT_KG)
        return (Math.round(calories * 10.0) / 10.0)
    }

    private fun caloriesPerRep(exerciseType: String?): Double {
        if (exerciseType.isNullOrBlank()) return 0.25
        return when (exerciseType.trim().lowercase().replace('-', '_').replace(' ', '_')) {
            "push_up", "pushup", "pushups", "push_ups", "wall_pushup", "wall_pushups" -> 0.35
            "squats", "squat" -> 0.32
            "sit_up", "sit_ups", "situp", "situps", "crunch", "crunches" -> 0.25
            "glute_bridge", "glute_bridges" -> 0.20
            "static_lunges", "static_lunge", "lunge", "lunges" -> 0.30
            "lying_leg_raises", "lying_leg_raise", "leg_raise", "leg_raises" -> 0.20
            else -> 0.25
        }
    }
}
