package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Separates physical-skill coaching errors from technical camera/detection quality signals,
 * and lists which physical codes each supported exercise may emit.
 *
 * Allowed sets are the union of [PostureRulesService] flags and on-device detector cues
 * that map confidently to a real physical error for that exercise.
 */
object FormErrorClassifier {
    private val TECHNICAL =
        setOf(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorCode.LOW_VISIBILITY,
            FormErrorCode.BODY_NOT_VISIBLE
        )

    private val ALLOWED_PHYSICAL: Map<String, Set<FormErrorCode>> =
        mapOf(
            "PUSH_UP" to
                setOf(FormErrorCode.PIKE, FormErrorCode.SAG, FormErrorCode.LOW_ROM),
            "SQUATS" to
                setOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
            "STATIC_LUNGES" to
                setOf(FormErrorCode.DEPTH_LOW, FormErrorCode.KNEES_IN, FormErrorCode.CHEST_UP),
            "GLUTE_BRIDGE" to
                setOf(FormErrorCode.LOW_ROM, FormErrorCode.SAG),
            "LYING_LEG_RAISES" to
                setOf(FormErrorCode.LEGS_BENT, FormErrorCode.LOW_ROM, FormErrorCode.SAG)
        )

    fun isTechnical(errorCode: FormErrorCode?): Boolean =
        errorCode != null && errorCode in TECHNICAL

    fun isCoachable(errorCode: FormErrorCode?): Boolean =
        errorCode != null && errorCode !in TECHNICAL && errorCode != FormErrorCode.UNKNOWN

    fun allowedPhysicalCodes(exerciseType: String?): Set<FormErrorCode> =
        ALLOWED_PHYSICAL[CoachingInstructionCatalog.normalizeExercise(exerciseType)].orEmpty()

    fun isAllowedPhysical(exerciseType: String?, errorCode: FormErrorCode?): Boolean =
        errorCode != null && errorCode in allowedPhysicalCodes(exerciseType)

    fun isCoachableForExercise(exerciseType: String?, errorCode: FormErrorCode?): Boolean =
        isCoachable(errorCode) && isAllowedPhysical(exerciseType, errorCode)
}
