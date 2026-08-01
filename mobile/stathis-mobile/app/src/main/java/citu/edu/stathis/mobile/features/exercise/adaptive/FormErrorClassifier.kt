package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Separates physical-skill coaching errors from technical camera/detection quality signals.
 * Technical signals must not drive modality-effectiveness / preferred-modality learning.
 */
object FormErrorClassifier {
    private val TECHNICAL =
        setOf(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorCode.LOW_VISIBILITY,
            FormErrorCode.BODY_NOT_VISIBLE
        )

    fun isTechnical(errorCode: FormErrorCode?): Boolean =
        errorCode != null && errorCode in TECHNICAL

    fun isCoachable(errorCode: FormErrorCode?): Boolean =
        errorCode != null && errorCode !in TECHNICAL && errorCode != FormErrorCode.UNKNOWN
}
