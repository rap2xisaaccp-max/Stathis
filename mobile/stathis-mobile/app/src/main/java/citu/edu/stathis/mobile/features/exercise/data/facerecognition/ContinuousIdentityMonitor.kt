package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import com.google.mlkit.vision.pose.Pose

/**
 * Real-time identity integrity engine for exercise sessions.
 *
 * Combines:
 * - Facial recognition (when a face is visible)
 * - Skeletal body-signature matching (when face is out of view)
 * - Skeleton presence with a 5s out-of-frame grace
 * - Multi-face rejection (another person in frame)
 *
 * Rep counting should pause on [TrustState.LOST] and resume only after
 * [TrustState.TRUSTED] is restored. Progress must not be reset by this monitor.
 */
class ContinuousIdentityMonitor(
    private val faceService: FaceRecognitionService,
    private val faceMatchThreshold: Float = FaceRecognitionService.MATCH_THRESHOLD
) {
    enum class TrustState {
        /** Identity confidently matches the verified student. */
        TRUSTED,
        /** Temporary uncertainty — keep counting briefly, but escalate if unresolved. */
        UNCERTAIN,
        /** Must pause: swap, multi-person, leave-frame, or failed re-id. */
        LOST
    }

    data class Decision(
        val state: TrustState,
        val reason: String,
        val faceSimilarity: Float? = null,
        val bodySimilarity: Float? = null,
        val multipleFaces: Boolean = false,
        /** True when face scan UI should run to restore trust. */
        val requiresFaceScan: Boolean = false
    )

    private val skeletonSession = SkeletonPresenceTracker.Session()
    private val bodySession = BodySignatureTracker.Session()

    private var state: TrustState = TrustState.TRUSTED
    private var bodyEnrolled = false
    private var consecutiveFaceMismatch = 0
    private var consecutiveMultiFace = 0

    fun reset() {
        skeletonSession.reset()
        bodySession.clear()
        state = TrustState.TRUSTED
        bodyEnrolled = false
        consecutiveFaceMismatch = 0
        consecutiveMultiFace = 0
    }

    fun onVerified(pose: Pose?) {
        state = TrustState.TRUSTED
        consecutiveFaceMismatch = 0
        consecutiveMultiFace = 0
        skeletonSession.reset()
        bodySession.resetCounters()
        if (pose != null) {
            bodyEnrolled = bodySession.enroll(pose)
        }
    }

    /**
     * Called every pose frame while the session is active (trusted or recovering).
     *
     * @param faceEmbedding probe embedding when a face was analyzed this tick (may be null)
     * @param faceCount number of faces detected in the latest face pass (0 if face not run)
     * @param enrolledFace enrolled MobileFaceNet embedding for this student
     * @param faceQualityRejected true when a face was seen but quality-gated
     */
    fun onFrame(
        pose: Pose?,
        faceEmbedding: FloatArray?,
        faceCount: Int,
        enrolledFace: FloatArray?,
        faceQualityRejected: Boolean = false,
        recovering: Boolean = false
    ): Decision {
        // 1) Multiple people with visible faces → immediate integrity pause
        if (faceCount > 1) {
            consecutiveMultiFace++
            if (consecutiveMultiFace >= 2 || recovering) {
                state = TrustState.LOST
                return Decision(
                    state = TrustState.LOST,
                    reason = "Multiple people detected. Only the verified student may continue.",
                    multipleFaces = true,
                    requiresFaceScan = true
                )
            }
        } else {
            consecutiveMultiFace = 0
        }

        // 2) Skeleton presence (5s grace)
        val skeletonStatus = skeletonSession.onPose(pose)
        if (skeletonStatus == SkeletonPresenceTracker.SkeletonStatus.LEFT_FRAME) {
            state = TrustState.LOST
            skeletonSession.reset()
            return Decision(
                state = TrustState.LOST,
                reason = "You left the camera for 5 seconds. Verify your identity to resume.",
                requiresFaceScan = true
            )
        }

        // 3) Enroll / refresh body signature once we have a stable pose after verify
        if (!bodyEnrolled && pose != null && SkeletonPresenceTracker.isSkeletonInFrame(pose)) {
            bodyEnrolled = bodySession.enroll(pose)
        }

        // 4) Face probe when available
        var faceSimilarity: Float? = null
        if (faceEmbedding != null && enrolledFace != null) {
            val (matched, similarity) = faceService.verifyAgainstEnrollment(
                faceEmbedding,
                enrolledFace,
                faceMatchThreshold
            )
            faceSimilarity = similarity
            if (matched) {
                consecutiveFaceMismatch = 0
                if (recovering || state != TrustState.TRUSTED) {
                    state = TrustState.TRUSTED
                    if (pose != null) bodyEnrolled = bodySession.enroll(pose) || bodyEnrolled
                    bodySession.resetCounters()
                    return Decision(
                        state = TrustState.TRUSTED,
                        reason = "Identity confirmed by facial recognition.",
                        faceSimilarity = similarity,
                        requiresFaceScan = false
                    )
                }
            } else if (!faceQualityRejected && similarity < faceMatchThreshold - 0.06f) {
                consecutiveFaceMismatch++
                if (consecutiveFaceMismatch >= 3) {
                    state = TrustState.LOST
                    return Decision(
                        state = TrustState.LOST,
                        reason = "Face does not match the verified student. Session paused.",
                        faceSimilarity = similarity,
                        requiresFaceScan = true
                    )
                }
                state = TrustState.UNCERTAIN
                return Decision(
                    state = TrustState.UNCERTAIN,
                    reason = "Confirming identity… keep your face visible.",
                    faceSimilarity = similarity,
                    requiresFaceScan = false
                )
            }
        }

        // 5) Body signature — primary when face is unavailable (glute bridge, etc.)
        val bodyObs = bodySession.observe(pose)
        if (bodyObs != null) {
            if (bodyObs.sustainedMismatch) {
                state = TrustState.LOST
                return Decision(
                    state = TrustState.LOST,
                    reason = "Body movement pattern does not match the verified student. Session paused.",
                    faceSimilarity = faceSimilarity,
                    bodySimilarity = bodyObs.similarity,
                    requiresFaceScan = true
                )
            }
            if (recovering && bodyObs.sustainedRematch && skeletonStatus == SkeletonPresenceTracker.SkeletonStatus.IN_FRAME) {
                // Resume without face when face is out of view but skeleton identity is strong
                state = TrustState.TRUSTED
                return Decision(
                    state = TrustState.TRUSTED,
                    reason = "Identity confirmed by skeletal tracking.",
                    faceSimilarity = faceSimilarity,
                    bodySimilarity = bodyObs.similarity,
                    requiresFaceScan = false
                )
            }
            if (!bodyObs.matched && state == TrustState.TRUSTED) {
                state = TrustState.UNCERTAIN
                return Decision(
                    state = TrustState.UNCERTAIN,
                    reason = "Skeletal identity check in progress…",
                    faceSimilarity = faceSimilarity,
                    bodySimilarity = bodyObs.similarity
                )
            }
        }

        if (recovering) {
            return Decision(
                state = TrustState.LOST,
                reason = "Verify your face or return fully into frame to resume.",
                faceSimilarity = faceSimilarity,
                bodySimilarity = bodyObs?.similarity,
                requiresFaceScan = true
            )
        }

        state = TrustState.TRUSTED
        return Decision(
            state = TrustState.TRUSTED,
            reason = "Identity verified",
            faceSimilarity = faceSimilarity,
            bodySimilarity = bodyObs?.similarity
        )
    }
}
