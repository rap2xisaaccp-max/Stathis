package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Lightweight skeletal identity signature from pose landmark proportions.
 *
 * Used when the face is partially/fully out of view (e.g. glute bridge) so the
 * session can still tell whether the same body is performing the exercise.
 * Ratios are scale-invariant (normalized by torso length).
 */
object BodySignatureTracker {

    /** Minimum cosine similarity to treat the live skeleton as the enrolled body. */
    const val MATCH_THRESHOLD = 0.92f

    /** Sustained mismatches before declaring a body swap. */
    const val MISMATCH_HITS_REQUIRED = 5

    /** Sustained matches before restoring trust without a face (face-out exercises). */
    const val REMATCH_HITS_REQUIRED = 6

    data class Signature(val features: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Signature) return false
            return features.contentEquals(other.features)
        }

        override fun hashCode(): Int = features.contentHashCode()
    }

    /**
     * Build a signature when enough torso/limb landmarks are confident.
     * Returns null if the pose is too incomplete (do not update identity).
     */
    fun extract(pose: Pose?): Signature? {
        if (pose == null) return null

        fun lm(type: Int): PoseLandmark? {
            val landmark = pose.getPoseLandmark(type) ?: return null
            return if (landmark.inFrameLikelihood >= 0.40f) landmark else null
        }

        val ls = lm(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rs = lm(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val lh = lm(PoseLandmark.LEFT_HIP) ?: return null
        val rh = lm(PoseLandmark.RIGHT_HIP) ?: return null

        val shoulderWidth = dist(ls, rs)
        val hipWidth = dist(lh, rh)
        val midShoulderX = (ls.position.x + rs.position.x) / 2f
        val midShoulderY = (ls.position.y + rs.position.y) / 2f
        val midHipX = (lh.position.x + rh.position.x) / 2f
        val midHipY = (lh.position.y + rh.position.y) / 2f
        val torsoLen = hypot(midShoulderX - midHipX, midShoulderY - midHipY)
        if (torsoLen < 1f || shoulderWidth < 1f) return null

        val le = lm(PoseLandmark.LEFT_ELBOW)
        val re = lm(PoseLandmark.RIGHT_ELBOW)
        val lw = lm(PoseLandmark.LEFT_WRIST)
        val rw = lm(PoseLandmark.RIGHT_WRIST)
        val lk = lm(PoseLandmark.LEFT_KNEE)
        val rk = lm(PoseLandmark.RIGHT_KNEE)
        val la = lm(PoseLandmark.LEFT_ANKLE)
        val ra = lm(PoseLandmark.RIGHT_ANKLE)

        fun ratio(a: PoseLandmark?, b: PoseLandmark?, fallback: Float): Float {
            if (a == null || b == null) return fallback
            return (dist(a, b) / torsoLen).coerceIn(0f, 4f)
        }

        val features = floatArrayOf(
            shoulderWidth / torsoLen,
            hipWidth / torsoLen,
            hipWidth / shoulderWidth.coerceAtLeast(1f),
            ratio(ls, le, 0.45f),
            ratio(rs, re, 0.45f),
            ratio(le, lw, 0.40f),
            ratio(re, rw, 0.40f),
            ratio(lh, lk, 0.70f),
            ratio(rh, rk, 0.70f),
            ratio(lk, la, 0.65f),
            ratio(rk, ra, 0.65f),
            // Relative limb span as a soft height proxy when ankles visible
            run {
                val leftLeg = if (lk != null && la != null) dist(lh, lk) + dist(lk, la) else torsoLen
                val rightLeg = if (rk != null && ra != null) dist(rh, rk) + dist(rk, ra) else torsoLen
                ((leftLeg + rightLeg) / 2f) / torsoLen
            }
        )
        return Signature(l2Normalize(features))
    }

    fun similarity(a: Signature, b: Signature): Float {
        val n = minOf(a.features.size, b.features.size)
        if (n == 0) return 0f
        var dot = 0f
        for (i in 0 until n) dot += a.features[i] * b.features[i]
        return dot.coerceIn(-1f, 1f)
    }

    fun isMatch(a: Signature, b: Signature, threshold: Float = MATCH_THRESHOLD): Boolean {
        return similarity(a, b) >= threshold
    }

    class Session {
        var enrolled: Signature? = null
            private set
        private var mismatchHits = 0
        private var rematchHits = 0
        private var lastSimilarity = 0f

        fun enroll(pose: Pose?): Boolean {
            val signature = extract(pose) ?: return false
            enrolled = signature
            mismatchHits = 0
            rematchHits = 0
            lastSimilarity = 1f
            return true
        }

        fun clear() {
            enrolled = null
            mismatchHits = 0
            rematchHits = 0
            lastSimilarity = 0f
        }

        /**
         * Compare live pose against the enrolled body signature.
         * @return null when pose is incomplete (ignore frame)
         */
        fun observe(pose: Pose?): BodyObservation? {
            val enrolledSig = enrolled ?: return null
            val live = extract(pose) ?: return null
            val sim = similarity(enrolledSig, live)
            lastSimilarity = sim
            return if (sim >= MATCH_THRESHOLD) {
                mismatchHits = 0
                rematchHits++
                BodyObservation(
                    similarity = sim,
                    matched = true,
                    sustainedMismatch = false,
                    sustainedRematch = rematchHits >= REMATCH_HITS_REQUIRED
                )
            } else {
                rematchHits = 0
                mismatchHits++
                BodyObservation(
                    similarity = sim,
                    matched = false,
                    sustainedMismatch = mismatchHits >= MISMATCH_HITS_REQUIRED,
                    sustainedRematch = false
                )
            }
        }

        fun resetCounters() {
            mismatchHits = 0
            rematchHits = 0
        }
    }

    data class BodyObservation(
        val similarity: Float,
        val matched: Boolean,
        val sustainedMismatch: Boolean,
        val sustainedRematch: Boolean
    )

    private fun dist(a: PoseLandmark, b: PoseLandmark): Float {
        return hypot(
            a.position.x - b.position.x,
            a.position.y - b.position.y
        )
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sumSq = 0f
        for (v in values) sumSq += v * v
        val norm = sqrt(sumSq)
        if (norm < 1e-6f) return values
        return FloatArray(values.size) { values[it] / norm }
    }
}
