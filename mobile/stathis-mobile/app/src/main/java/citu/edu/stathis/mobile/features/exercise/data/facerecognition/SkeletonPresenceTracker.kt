package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import android.os.SystemClock
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Determines whether the user's skeleton is sufficiently visible in frame.
 * Re-verification is only required after the skeleton has been continuously
 * absent for [OUT_OF_FRAME_GRACE_MS] (5 seconds), so brief movement during
 * exercise does not interrupt recognition.
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
    /** Grace period before ending recognition / requiring face re-verify. */
    const val OUT_OF_FRAME_GRACE_MS = 5_000L

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
     * Tracks continuous absence of the skeleton using wall-clock time.
     * Re-verify only after 5 seconds continuously out of frame.
     */
    class Session {
        private var missingSinceElapsedMs: Long? = null

        fun onPose(pose: Pose?): SkeletonStatus {
            val now = SystemClock.elapsedRealtime()
            return if (isSkeletonInFrame(pose)) {
                missingSinceElapsedMs = null
                SkeletonStatus.IN_FRAME
            } else {
                val started = missingSinceElapsedMs ?: now.also { missingSinceElapsedMs = it }
                val absentMs = now - started
                if (absentMs >= OUT_OF_FRAME_GRACE_MS) {
                    SkeletonStatus.LEFT_FRAME
                } else {
                    SkeletonStatus.UNSTABLE
                }
            }
        }

        fun reset() {
            missingSinceElapsedMs = null
        }
    }

    enum class SkeletonStatus {
        IN_FRAME,
        UNSTABLE,
        LEFT_FRAME
    }
}
