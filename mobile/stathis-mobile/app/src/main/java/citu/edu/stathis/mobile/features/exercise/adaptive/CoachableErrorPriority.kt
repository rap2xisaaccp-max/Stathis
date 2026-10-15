package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Deterministic choice when several allowed physical errors are present on one tick.
 *
 * Order: highest per-code base severity, then a stable exercise-specific list
 * (safety-critical alignment before range-of-motion when severity ties).
 *
 * Push-ups: SAG (0.70), PIKE (0.60), LOW_ROM (0.55)
 * Squats: KNEES_IN (0.65), DEPTH_LOW (0.55), CHEST_UP (0.50)
 * Static lunges: KNEES_IN (0.65), DEPTH_LOW (0.55), CHEST_UP (0.50)
 * Glute bridge: SAG (0.70), LOW_ROM (0.55)
 * Lying leg raises: SAG (0.70), LEGS_BENT (0.60), LOW_ROM (0.55)
 */
object CoachableErrorPriority {
    fun baseSeverity(code: FormErrorCode): Double =
        when (code) {
            FormErrorCode.SAG -> 0.70
            FormErrorCode.KNEES_IN -> 0.65
            FormErrorCode.PIKE -> 0.60
            FormErrorCode.LEGS_BENT -> 0.60
            FormErrorCode.DEPTH_LOW, FormErrorCode.LOW_ROM -> 0.55
            FormErrorCode.CHEST_UP -> 0.50
            else -> 0.40
        }

    fun tieBreakOrder(exerciseType: String?): List<FormErrorCode> =
        when (CoachingInstructionCatalog.normalizeExercise(exerciseType)) {
            "PUSH_UP" ->
                listOf(FormErrorCode.SAG, FormErrorCode.PIKE, FormErrorCode.LOW_ROM)
            "SQUATS", "STATIC_LUNGES" ->
                listOf(FormErrorCode.KNEES_IN, FormErrorCode.DEPTH_LOW, FormErrorCode.CHEST_UP)
            "GLUTE_BRIDGE" ->
                listOf(FormErrorCode.SAG, FormErrorCode.LOW_ROM)
            "LYING_LEG_RAISES" ->
                listOf(FormErrorCode.SAG, FormErrorCode.LEGS_BENT, FormErrorCode.LOW_ROM)
            else -> emptyList()
        }

    fun select(exerciseType: String?, codes: Collection<FormErrorCode>): FormErrorCode? {
        if (codes.isEmpty()) return null
        val order = tieBreakOrder(exerciseType)
        return codes.minWith(
            compareByDescending<FormErrorCode> { baseSeverity(it) }
                .thenBy { idx ->
                    val i = order.indexOf(idx)
                    if (i < 0) Int.MAX_VALUE else i
                }
                .thenBy { it.name }
        )
    }
}
