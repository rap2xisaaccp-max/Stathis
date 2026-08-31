package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Teacher-friendly physical-form copy shared with the evidence upload payload.
 * Technical codes are listed only so callers can skip them — they never create snapshots.
 */
object FormErrorCopy {
    data class Copy(val label: String, val explanation: String)

    private val COPY =
        mapOf(
            FormErrorCode.SAG to
                Copy("Hips sagging", "Hips or torso dropping below a straight body line."),
            FormErrorCode.LOW_ROM to
                Copy("Incomplete movement", "Movement did not travel through the full useful range."),
            FormErrorCode.DEPTH_LOW to
                Copy("Not deep enough", "Squat or lunge did not reach enough depth."),
            FormErrorCode.KNEES_IN to
                Copy("Knees moving inward", "Knees collapsing inward instead of tracking over the toes."),
            FormErrorCode.CHEST_UP to
                Copy("Torso leaning", "Torso not staying upright."),
            FormErrorCode.PIKE to
                Copy("Hips too high", "Hips rising above a straight push-up line."),
            FormErrorCode.LEGS_BENT to
                Copy("Legs not straight", "Knees bent during a straight-leg movement.")
        )

    private val BY_EXERCISE =
        mapOf(
            "SQUATS|DEPTH_LOW" to
                Copy("Not deep enough", "Squat did not reach enough depth."),
            "SQUATS|KNEES_IN" to
                Copy("Knees moving inward", "Knees collapsing inward instead of tracking over the toes."),
            "SQUATS|CHEST_UP" to
                Copy("Torso leaning", "Torso leaned forward instead of staying more upright during the squat."),
            "PUSH_UP|PIKE" to
                Copy("Hips too high", "Hips rising above a straight push-up line."),
            "PUSH_UP|SAG" to
                Copy("Hips sagging", "Hips dropping below a straight push-up line."),
            "PUSH_UP|LOW_ROM" to
                Copy("Shallow push-up", "Chest did not lower through a useful range."),
            "STATIC_LUNGES|DEPTH_LOW" to
                Copy("Not deep enough", "The front knee did not bend enough to reach useful lunge depth."),
            "STATIC_LUNGES|KNEES_IN" to
                Copy("Knee drifting inward", "A knee collapsed inward instead of tracking over the toes."),
            "STATIC_LUNGES|CHEST_UP" to
                Copy("Torso leaning", "Torso collapsing or leaning instead of staying upright in the lunge."),
            "GLUTE_BRIDGE|LOW_ROM" to
                Copy("Hips not high enough", "Bridge did not lift the hips through a full range."),
            "GLUTE_BRIDGE|SAG" to
                Copy("Hips dropping", "Hips dropped instead of staying lifted in the bridge."),
            "LYING_LEG_RAISES|LEGS_BENT" to
                Copy("Legs not straight", "Knees bent during a straight-leg raise."),
            "LYING_LEG_RAISES|LOW_ROM" to
                Copy("Legs not high enough", "Legs did not raise through a useful range."),
            "LYING_LEG_RAISES|SAG" to
                Copy("Lower back lifting", "Hips or lower back left the floor during the raise.")
        )

    fun label(code: FormErrorCode, exerciseType: String? = null): String {
        val exercise = CoachingInstructionCatalog.normalizeExercise(exerciseType)
        return BY_EXERCISE["$exercise|${code.name}"]?.label
            ?: COPY[code]?.label
            ?: code.name.replace('_', ' ')
    }

    fun explanation(code: FormErrorCode, exerciseType: String? = null): String {
        val exercise = CoachingInstructionCatalog.normalizeExercise(exerciseType)
        return BY_EXERCISE["$exercise|${code.name}"]?.explanation
            ?: COPY[code]?.explanation
            ?: ""
    }
}
