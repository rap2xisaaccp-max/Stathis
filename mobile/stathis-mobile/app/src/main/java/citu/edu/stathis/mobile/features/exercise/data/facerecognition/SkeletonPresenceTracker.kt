package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import android.os.SystemClock
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Determines whether the user's skeleton is sufficiently visible in frame.
 *
 * After initial face verification, re-verification is required only when the
 * recognized person's skeleton has been missing from the camera for
 * [OUT_OF_FRAME_GRACE_MS] (5 seconds). Brief gaps while exercising must not
 * interrupt the session with repeated verify prompts.
 */
object SkeletonPresenceTracker {

    /** Grace period before leaving the frame forces facial re-verification. */
    const val OUT_OF_FRAME_GRACE_MS = 5_000L

    private val TORSO_LANDMARKS = listOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP
    )

    private const val IN_FRAME_LIKELIHOOD = 0.45f
    private const val MIN_VISIBLE_TORSO_POINTS = 3
    /**
     * Short frame debounce so a few missed detections do not start the grace timer.
     * At ~30 fps this is roughly 0.4s of noise rejection before the 5s clock starts.
     */
    /** Debounce frames before the out-of-frame grace timer starts. */
    const val OUT_OF_FRAME_CONFIRM_FRAMES = 12

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
     * Tracks skeleton presence with a 5-second out-of-frame grace period.
     *
     * Flow:
     * 1. Skeleton visible → [SkeletonStatus.IN_FRAME]
     * 2. Brief gaps → [SkeletonStatus.UNSTABLE] (no re-verify)
     * 3. Confirmed missing → grace timer starts; still [SkeletonStatus.UNSTABLE]
     *    until [OUT_OF_FRAME_GRACE_MS] elapses
     * 4. Still missing after 5s → [SkeletonStatus.LEFT_FRAME] (re-verify)
     * 5. Skeleton returns during grace → timer resets; stay recognized
     */
    class Session(
        private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
    ) {
        private var consecutiveMissing = 0
        private var outOfFrameSinceMs: Long? = null

        fun onPose(pose: Pose?): SkeletonStatus {
            return if (isSkeletonInFrame(pose)) {
                consecutiveMissing = 0
                outOfFrameSinceMs = null
                SkeletonStatus.IN_FRAME
            } else {
                consecutiveMissing++
                if (consecutiveMissing < OUT_OF_FRAME_CONFIRM_FRAMES) {
                    return SkeletonStatus.UNSTABLE
                }

                val now = nowMs()
                val startedAt = outOfFrameSinceMs ?: now.also { outOfFrameSinceMs = it }
                val elapsed = now - startedAt

                if (elapsed >= OUT_OF_FRAME_GRACE_MS) {
                    SkeletonStatus.LEFT_FRAME
                } else {
                    SkeletonStatus.UNSTABLE
                }
            }
        }

        /** Milliseconds remaining in the grace window, or 0 if not timing out. */
        fun graceRemainingMs(): Long {
            val startedAt = outOfFrameSinceMs ?: return 0L
            val elapsed = nowMs() - startedAt
            return (OUT_OF_FRAME_GRACE_MS - elapsed).coerceAtLeast(0L)
        }

        fun reset() {
            consecutiveMissing = 0
            outOfFrameSinceMs = null
        }
    }

    enum class SkeletonStatus {
        IN_FRAME,
        UNSTABLE,
        LEFT_FRAME
    }
}