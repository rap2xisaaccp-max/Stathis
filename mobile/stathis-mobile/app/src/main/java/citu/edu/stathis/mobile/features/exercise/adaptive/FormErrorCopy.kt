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
                Copy("Chest / torso dropping", "Torso collapsing or not staying upright."),
            FormErrorCode.PIKE to
                Copy("Hips too high", "Hips rising above a straight push-up line."),
            FormErrorCode.LEGS_BENT to
                Copy("Legs not straight", "Knees bent during a straight-leg movement.")
        )

    fun label(code: FormErrorCode): String = COPY[code]?.label ?: code.name.replace('_', ' ')

    fun explanation(code: FormErrorCode): String = COPY[code]?.explanation ?: ""
}
