package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Maps form errors to MediaPipe PoseLandmark type indices for visual feedback.
 * Indices match [com.google.mlkit.vision.pose.PoseLandmark] constants.
 */
object ModalityHighlightTargets {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    data class Target(
        val joints: Set<Int>,
        val bones: List<Pair<Int, Int>>
    )

    fun forError(errorCode: FormErrorCode): Target =
        when (errorCode) {
            FormErrorCode.DEPTH_LOW ->
                Target(
                    joints = setOf(LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE),
                    bones =
                        listOf(
                            LEFT_HIP to LEFT_KNEE,
                            LEFT_KNEE to LEFT_ANKLE,
                            RIGHT_HIP to RIGHT_KNEE,
                            RIGHT_KNEE to RIGHT_ANKLE
                        )
                )
            FormErrorCode.KNEES_IN, FormErrorCode.LEGS_BENT ->
                Target(
                    joints = setOf(LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE),
                    bones =
                        listOf(
                            LEFT_HIP to LEFT_KNEE,
                            LEFT_KNEE to LEFT_ANKLE,
                            RIGHT_HIP to RIGHT_KNEE,
                            RIGHT_KNEE to RIGHT_ANKLE,
                            LEFT_KNEE to RIGHT_KNEE
                        )
                )
            FormErrorCode.CHEST_UP ->
                Target(
                    joints = setOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, NOSE),
                    bones =
                        listOf(
                            LEFT_SHOULDER to RIGHT_SHOULDER,
                            LEFT_SHOULDER to LEFT_HIP,
                            RIGHT_SHOULDER to RIGHT_HIP,
                            LEFT_HIP to RIGHT_HIP
                        )
                )
            FormErrorCode.PIKE, FormErrorCode.SAG ->
                Target(
                    joints = setOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, LEFT_ANKLE, RIGHT_ANKLE),
                    bones =
                        listOf(
                            LEFT_SHOULDER to LEFT_HIP,
                            RIGHT_SHOULDER to RIGHT_HIP,
                            LEFT_HIP to LEFT_ANKLE,
                            RIGHT_HIP to RIGHT_ANKLE,
                            LEFT_SHOULDER to RIGHT_SHOULDER,
                            LEFT_HIP to RIGHT_HIP
                        )
                )
            FormErrorCode.LOW_ROM ->
                Target(
                    joints = setOf(NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP),
                    bones =
                        listOf(
                            LEFT_SHOULDER to RIGHT_SHOULDER,
                            LEFT_SHOULDER to LEFT_HIP,
                            RIGHT_SHOULDER to RIGHT_HIP,
                            LEFT_HIP to RIGHT_HIP
                        )
                )
            FormErrorCode.LOW_VISIBILITY,
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorCode.UNKNOWN ->
                Target(
                    joints =
                        setOf(
                            LEFT_SHOULDER,
                            RIGHT_SHOULDER,
                            LEFT_HIP,
                            RIGHT_HIP,
                            LEFT_KNEE,
                            RIGHT_KNEE,
                            LEFT_ANKLE,
                            RIGHT_ANKLE
                        ),
                    bones =
                        listOf(
                            LEFT_SHOULDER to RIGHT_SHOULDER,
                            LEFT_HIP to RIGHT_HIP,
                            LEFT_SHOULDER to LEFT_HIP,
                            RIGHT_SHOULDER to RIGHT_HIP,
                            LEFT_HIP to LEFT_KNEE,
                            RIGHT_HIP to RIGHT_KNEE
                        )
                )
        }
}
