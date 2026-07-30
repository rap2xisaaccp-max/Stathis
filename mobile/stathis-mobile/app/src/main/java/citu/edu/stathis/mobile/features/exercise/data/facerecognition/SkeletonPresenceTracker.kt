package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Determines whether the user's skeleton is sufficiently visible in frame.
 * When the torso leaves the camera, facial re-verification is required.
 */
object SkeletonPresenceTracker {

    private val TORSO_LANDMARKS = listOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP
    )

    private const val IN_FRAME_LIKELIHOOD = 0.45f
    private const val MIN_VISIBLE_TORSO_POINTS = 3
    /** Slightly longer debounce so brief occlusion does not end recognition. */
    private const val OUT_OF_FRAME_CONFIRM_FRAMES = 12

    /**
     * Returns true when enough torso landmarks are confidently in frame.
     */
    fun isSkeletonInFrame(pose: Pose?): Boolean {
        if (pose == null) return false
        val visible = TORSO_LANDMARKS.count { type ->
            val landmark = pose.getPoseLandmark(type) ?: return@count false
            landmark.inFrameLikelihood >= IN_FRAME_LIKELIHOOD
        }
        return visible >= MIN_VISIBLE_TORSO_POINTS
    }

    /**
     * Debounces brief detection gaps so a single missed frame does not force re-verify.
     */
    class Session {
        private var consecutiveMissing = 0

        fun onPose(pose: Pose?): SkeletonStatus {
            return if (isSkeletonInFrame(pose)) {
                consecutiveMissing = 0
                SkeletonStatus.IN_FRAME
            } else {
                consecutiveMissing++
                if (consecutiveMissing >= OUT_OF_FRAME_CONFIRM_FRAMES) {
                    SkeletonStatus.LEFT_FRAME
                } else {
                    SkeletonStatus.UNSTABLE
                }
            }
        }

        fun reset() {
            consecutiveMissing = 0
        }
    }

    enum class SkeletonStatus {
        IN_FRAME,
        UNSTABLE,
        LEFT_FRAME
    }
}
