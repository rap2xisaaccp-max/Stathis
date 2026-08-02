package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class ExerciseDetector {

    // General detection config
    private val defaultConfidenceThreshold: Float = 0.5f
    private val requiredStableFrames: Int = 3

    // --- Squat ---
    private var squatState: ExerciseState = ExerciseState.WAITING
    private var squatRepCount: Int = 0
    private var squatInDownPosition: Boolean = false
    private var squatStandingHipY: Float? = null
    private val squatHipKneeVerticalThresholdFactor = 0.1f
    private val squatMovementThreshold = 0.05f
    private var squatStableFrames: Int = 0
    private var squatLastRepTimeMs: Long = 0L
    private val squatMinRepIntervalMs: Long = 700L

    // --- Push-up ---
    private var pushupState: ExerciseState = ExerciseState.WAITING
    private var pushupRepCount: Int = 0
    private var pushupInDownPosition: Boolean = false
    private var pushupStableFrames: Int = 0
    private var pushupLastRepTimeMs: Long = 0L
    private val pushupMinRepIntervalMs: Long = 700L

    // --- Glute bridge ---
    private var gluteBridgeState: ExerciseState = ExerciseState.WAITING
    private var gluteBridgeRepCount: Int = 0
    private var gluteBridgeInRaisedPosition: Boolean = false
    private var gluteBridgeStableFrames: Int = 0
    private var gluteBridgeLastRepTimeMs: Long = 0L
    private val gluteBridgeMinRepIntervalMs: Long = 700L

    // --- Static lunge ---
    private var staticLungeState: ExerciseState = ExerciseState.WAITING
    private var staticLungeRepCount: Int = 0
    private var staticLungeInDownPosition: Boolean = false

    // --- Lying leg raise ---
    private var lyingLegRaiseState: ExerciseState = ExerciseState.WAITING
    private var lyingLegRaiseRepCount: Int = 0
    private var lyingLegRaiseInUpPosition: Boolean = false
    private var lyingLegRaiseStableFrames: Int = 0
    private var lyingLegRaiseLastRepTimeMs: Long = 0L
    private val lyingLegRaiseMinRepIntervalMs: Long = 800L
    private val lyingLegRaiseMinKneeAngle: Float = 150f
    private val lyingLegRaiseRaiseFactor: Float = 0.18f
    private val lyingLegRaiseLowerFactor: Float = 0.08f
    private val lyingLegRaiseMaxAnkleAsymmetryFactor: Float = 0.12f
    private val lyingLegRaiseMaxHipDriftFactor: Float = 0.08f
    private var lyingLegRaiseBaselineHipY: Float? = null
    private var lyingLegRaisePrevAnkleY: Float? = null

    // --- Sit-up ---
    private var situpState: ExerciseState = ExerciseState.WAITING
    private var situpRepCount: Int = 0
    private var situpInUpPosition: Boolean = false
    private var situpStableFrames: Int = 0
    private var situpLastRepTimeMs: Long = 0L
    private val situpMinRepIntervalMs: Long = 700L


    fun analyzeSquat(pose: Pose): ExerciseResult {
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        // For vertical reference, maybe shoulders and ankles
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)


        if (leftHip == null || rightHip == null || leftKnee == null || rightKnee == null || leftShoulder == null || rightShoulder == null || leftAnkle == null || rightAnkle == null) {
            feedback.add("Ensure major body parts are visible.")
            resetSquatStateInternals()
            return ExerciseResult(ExerciseState.INVALID, feedback, repCount = squatRepCount)
        }

        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2f
        val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2f
        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val avgAnkleY = (leftAnkle.position.y + rightAnkle.position.y) / 2f
        val bodyHeightEstimate = abs(avgShoulderY - avgAnkleY)
        val hipAngle = averageAngle(leftShoulder, leftHip, leftKnee, rightShoulder, rightHip, rightKnee)
        val avgKneeAngle = (angle(leftHip, leftKnee, leftAnkle) + angle(rightHip, rightKnee, rightAnkle)) / 2f
        val squatDownThreshold = bodyHeightEstimate * squatHipKneeVerticalThresholdFactor
        val squatStandThreshold = bodyHeightEstimate * squatMovementThreshold

        val confidence = (leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood) / 4f
        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            return ExerciseResult(squatState, feedback, repCompletedThisFrame, confidence, squatRepCount)
        }

        val isStandingReady = avgHipY < avgKneeY - squatStandThreshold && hipAngle >= 155f
        if (isStandingReady && squatStandingHipY == null) {
            squatStandingHipY = avgHipY
        }


        when (squatState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                val downCandidate = (avgHipY > avgKneeY + squatDownThreshold && hipAngle <= 145f) || avgKneeAngle <= 115f
                if (downCandidate) {
                    squatStableFrames++
                    if (squatStableFrames >= requiredStableFrames) {
                        squatState = ExerciseState.DOWN
                        squatInDownPosition = true
                        squatStableFrames = 0
                    }
                } else {
                    squatStableFrames = 0
                    squatState = ExerciseState.UP
                }
            }
            ExerciseState.DOWN -> {
                val completedRep = squatStandingHipY?.let { standingHipY ->
                    avgHipY <= standingHipY + squatStandThreshold && (hipAngle >= 150f || avgKneeAngle >= 160f)
                } == true || avgKneeAngle >= 160f

                if (completedRep) {
                    squatStableFrames++
                    if (squatStableFrames >= requiredStableFrames) {
                        squatState = ExerciseState.UP
                        val now = System.currentTimeMillis()
                        if (squatInDownPosition && now - squatLastRepTimeMs >= squatMinRepIntervalMs) {
                            squatRepCount++
                            repCompletedThisFrame = true
                            squatLastRepTimeMs = now
                        }
                        squatInDownPosition = false
                        squatStandingHipY = avgHipY
                        squatStableFrames = 0
                    }
                } else {
                    squatStableFrames = 0
                }
            }
            ExerciseState.INVALID -> {
                squatState = ExerciseState.WAITING
            }
        }

        return ExerciseResult(squatState, feedback, repCompletedThisFrame, confidence, squatRepCount)
    }

    fun analyzePushup(pose: Pose): ExerciseResult {
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)


        if (leftShoulder == null || rightShoulder == null || leftElbow == null || rightElbow == null || leftWrist == null || rightWrist == null) {
            feedback.add("Ensure shoulders, elbows, and wrists are visible.")
            resetPushupStateInternals()
            return ExerciseResult(ExerciseState.INVALID, feedback, repCount = pushupRepCount)
        }

        val avgElbowAngle = (angle(leftShoulder, leftElbow, leftWrist) + angle(rightShoulder, rightElbow, rightWrist)) / 2f
        val confidence = (leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood + leftElbow.inFrameLikelihood + rightElbow.inFrameLikelihood + leftWrist.inFrameLikelihood + rightWrist.inFrameLikelihood) / 6f
        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            return ExerciseResult(pushupState, feedback, repCompletedThisFrame, confidence, pushupRepCount)
        }

        when (pushupState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                val downCandidate = avgElbowAngle <= 95f
                if (downCandidate) {
                    pushupStableFrames++
                    if (pushupStableFrames >= requiredStableFrames) {
                        pushupState = ExerciseState.DOWN
                        pushupInDownPosition = true
                        pushupStableFrames = 0
                    }
                } else {
                    pushupStableFrames = 0
                    pushupState = ExerciseState.UP
                }
            }
            ExerciseState.DOWN -> {
                val upCandidate = avgElbowAngle >= 155f
                if (upCandidate) {
                    pushupStableFrames++
                    if (pushupStableFrames >= requiredStableFrames) {
                        pushupState = ExerciseState.UP
                        val now = System.currentTimeMillis()
                        if (pushupInDownPosition && now - pushupLastRepTimeMs >= pushupMinRepIntervalMs) {
                            pushupRepCount++
                            repCompletedThisFrame = true
                            pushupLastRepTimeMs = now
                        }
                        pushupInDownPosition = false
                        pushupStableFrames = 0
                    }
                } else {
                    pushupStableFrames = 0
                }
            }
            ExerciseState.INVALID -> {
                pushupState = ExerciseState.WAITING
            }
        }

        return ExerciseResult(pushupState, feedback, repCompletedThisFrame, confidence, pushupRepCount)
    }

    fun analyzeSitup(pose: Pose): ExerciseResult {
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null || leftKnee == null || rightKnee == null) {
            feedback.add("Keep shoulders, hips, and knees visible.")
            resetSitupStateInternals()
            return ExerciseResult(ExerciseState.INVALID, feedback, repCount = situpRepCount)
        }

        val torsoAngle = averageAngle(leftShoulder, leftHip, leftKnee, rightShoulder, rightHip, rightKnee)
        val confidence = (leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood + leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood) / 6f
        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            return ExerciseResult(situpState, feedback, repCompletedThisFrame, confidence, situpRepCount)
        }

        when (situpState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                val downCandidate = torsoAngle <= 115f
                if (downCandidate) {
                    situpStableFrames++
                    if (situpStableFrames >= requiredStableFrames) {
                        situpState = ExerciseState.DOWN
                        situpInUpPosition = true
                        situpStableFrames = 0
                    }
                } else {
                    situpStableFrames = 0
                    situpState = ExerciseState.UP
                }
            }
            ExerciseState.DOWN -> {
                val upCandidate = torsoAngle >= 145f
                if (upCandidate) {
                    situpStableFrames++
                    if (situpStableFrames >= requiredStableFrames) {
                        situpState = ExerciseState.UP
                        val now = System.currentTimeMillis()
                        if (situpInUpPosition && now - situpLastRepTimeMs >= situpMinRepIntervalMs) {
                            situpRepCount++
                            repCompletedThisFrame = true
                            situpLastRepTimeMs = now
                        }
                        situpInUpPosition = false
                        situpStableFrames = 0
                    }
                } else {
                    situpStableFrames = 0
                }
            }
            ExerciseState.INVALID -> situpState = ExerciseState.WAITING
        }

        return ExerciseResult(situpState, feedback, repCompletedThisFrame, confidence, situpRepCount)
    }

    fun analyzeGluteBridge(pose: Pose): ExerciseResult {
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null || leftKnee == null || rightKnee == null) {
            feedback.add("Keep shoulders, hips, and knees visible.")
            resetGluteBridgeStateInternals()
            return ExerciseResult(ExerciseState.INVALID, feedback, repCount = gluteBridgeRepCount)
        }

        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2f
        val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2f
        val hipAngle = averageAngle(leftShoulder, leftHip, leftKnee, rightShoulder, rightHip, rightKnee)
        val bodySpan = abs(avgKneeY - avgShoulderY).coerceAtLeast(1f)
        val raisedThreshold = bodySpan * 0.08f
        val confidence = (leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood + leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood) / 6f
        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            return ExerciseResult(gluteBridgeState, feedback, repCompletedThisFrame, confidence, gluteBridgeRepCount)
        }

        when (gluteBridgeState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                val raiseCandidate = hipAngle >= 150f && avgHipY < max(avgShoulderY, avgKneeY) - raisedThreshold
                if (raiseCandidate) {
                    gluteBridgeStableFrames++
                    if (gluteBridgeStableFrames >= requiredStableFrames) {
                        gluteBridgeState = ExerciseState.DOWN
                        gluteBridgeInRaisedPosition = true
                        gluteBridgeStableFrames = 0
                    }
                } else {
                    gluteBridgeStableFrames = 0
                }
            }
            ExerciseState.DOWN -> {
                val lowerCandidate = hipAngle <= 135f || avgHipY >= max(avgShoulderY, avgKneeY) - raisedThreshold * 0.3f
                if (lowerCandidate) {
                    gluteBridgeStableFrames++
                    if (gluteBridgeStableFrames >= requiredStableFrames) {
                        gluteBridgeState = ExerciseState.UP
                        val now = System.currentTimeMillis()
                        if (gluteBridgeInRaisedPosition && now - gluteBridgeLastRepTimeMs >= gluteBridgeMinRepIntervalMs) {
                            gluteBridgeRepCount++
                            repCompletedThisFrame = true
                            gluteBridgeLastRepTimeMs = now
                        }
                        gluteBridgeInRaisedPosition = false
                        gluteBridgeStableFrames = 0
                    }
                } else {
                    gluteBridgeStableFrames = 0
                }
            }
            ExerciseState.INVALID -> gluteBridgeState = ExerciseState.WAITING
        }

        return ExerciseResult(gluteBridgeState, feedback, repCompletedThisFrame, confidence, gluteBridgeRepCount)
    }


    fun analyzeStaticLunge(pose: Pose): ExerciseResult {
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftHip == null || rightHip == null || leftKnee == null || rightKnee == null || leftAnkle == null || rightAnkle == null || leftShoulder == null || rightShoulder == null) {
            feedback.add("Keep your hips, knees, and ankles visible.")
            resetStaticLungeStateInternals()
            return ExerciseResult(ExerciseState.INVALID, feedback, repCount = staticLungeRepCount)
        }

        val leftKneeAngle = angle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = angle(rightHip, rightKnee, rightAnkle)
        val leftHipY = leftHip.position.y
        val rightHipY = rightHip.position.y
        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val avgHipY = (leftHipY + rightHipY) / 2f
        val bodySpan = abs(avgShoulderY - avgHipY).coerceAtLeast(1f)
        val downThreshold = bodySpan * 0.12f

        val bentLegAngle = min(leftKneeAngle, rightKneeAngle)
        val straightLegAngle = max(leftKneeAngle, rightKneeAngle)

        when (staticLungeState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if (bentLegAngle <= 115f && straightLegAngle >= 140f && avgHipY > avgShoulderY - downThreshold) {
                    staticLungeState = ExerciseState.DOWN
                    staticLungeInDownPosition = true
                }
            }
            ExerciseState.DOWN -> {
                if (bentLegAngle >= 150f && straightLegAngle >= 150f) {
                    staticLungeState = ExerciseState.UP
                    if (staticLungeInDownPosition) {
                        staticLungeRepCount++
                        repCompletedThisFrame = true
                    }
                    staticLungeInDownPosition = false
                }
            }
            ExerciseState.INVALID -> staticLungeState = ExerciseState.WAITING
        }

        val confidence = (leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood + leftAnkle.inFrameLikelihood + rightAnkle.inFrameLikelihood) / 6f
        return ExerciseResult(staticLungeState, feedback, repCompletedThisFrame, confidence, staticLungeRepCount)
    }

    fun analyzeLyingLegRaise(pose: Pose): ExerciseResult {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftHip == null || rightHip == null || leftKnee == null || rightKnee == null ||
            leftAnkle == null || rightAnkle == null || leftShoulder == null || rightShoulder == null
        ) {
            resetLyingLegRaiseStateInternals()
            return ExerciseResult(
                ExerciseState.INVALID,
                listOf("Keep hips, knees, ankles, and shoulders visible."),
                repCount = lyingLegRaiseRepCount
            )
        }

        val leftKneeAngle = angle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = angle(rightHip, rightKnee, rightAnkle)
        val confidence =
            (leftHip.inFrameLikelihood + rightHip.inFrameLikelihood +
                leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood +
                leftAnkle.inFrameLikelihood + rightAnkle.inFrameLikelihood +
                leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood) / 8f

        return analyzeLyingLegRaiseMetrics(
            leftHipY = leftHip.position.y,
            rightHipY = rightHip.position.y,
            leftAnkleY = leftAnkle.position.y,
            rightAnkleY = rightAnkle.position.y,
            leftShoulderY = leftShoulder.position.y,
            rightShoulderY = rightShoulder.position.y,
            leftKneeAngle = leftKneeAngle,
            rightKneeAngle = rightKneeAngle,
            confidence = confidence,
            nowMs = System.currentTimeMillis()
        )
    }

    /**
     * Testable LLR state machine. Image Y grows downward (MediaPipe): raised legs ⇒ smaller ankle Y.
     */
    internal fun analyzeLyingLegRaiseMetrics(
        leftHipY: Float,
        rightHipY: Float,
        leftAnkleY: Float,
        rightAnkleY: Float,
        leftShoulderY: Float,
        rightShoulderY: Float,
        leftKneeAngle: Float,
        rightKneeAngle: Float,
        confidence: Float,
        nowMs: Long
    ): ExerciseResult {
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            lyingLegRaiseStableFrames = 0
            return ExerciseResult(
                lyingLegRaiseState,
                feedback,
                false,
                confidence,
                lyingLegRaiseRepCount
            )
        }

        val avgHipY = (leftHipY + rightHipY) / 2f
        val avgAnkleY = (leftAnkleY + rightAnkleY) / 2f
        val avgShoulderY = (leftShoulderY + rightShoulderY) / 2f
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2f
        // Prefer hip–shoulder span so threshold stays stable when ankles approach hips.
        val bodySpan = abs(avgHipY - avgShoulderY).coerceAtLeast(80f)
        val raiseThreshold = bodySpan * lyingLegRaiseRaiseFactor
        val lowerThreshold = bodySpan * lyingLegRaiseLowerFactor
        val maxAsymmetry = bodySpan * lyingLegRaiseMaxAnkleAsymmetryFactor
        val maxHipDrift = bodySpan * lyingLegRaiseMaxHipDriftFactor

        if (lyingLegRaiseBaselineHipY == null &&
            lyingLegRaiseState == ExerciseState.WAITING
        ) {
            lyingLegRaiseBaselineHipY = avgHipY
        }

        val leftRaised = leftAnkleY < leftHipY - raiseThreshold
        val rightRaised = rightAnkleY < rightHipY - raiseThreshold
        val bothRaised = leftRaised && rightRaised
        val bothLowered =
            leftAnkleY >= leftHipY - lowerThreshold && rightAnkleY >= rightHipY - lowerThreshold
        val legsStraight =
            leftKneeAngle >= lyingLegRaiseMinKneeAngle && rightKneeAngle >= lyingLegRaiseMinKneeAngle
        val anklesSymmetric = abs(leftAnkleY - rightAnkleY) <= maxAsymmetry
        val hipsStable =
            lyingLegRaiseBaselineHipY == null ||
                abs(avgHipY - lyingLegRaiseBaselineHipY!!) <= maxHipDrift

        // Reject one-frame spikes (impossible ROM jump relative to body span).
        val prevAnkle = lyingLegRaisePrevAnkleY
        lyingLegRaisePrevAnkleY = avgAnkleY
        if (prevAnkle != null && abs(avgAnkleY - prevAnkle) > bodySpan * 0.55f) {
            feedback.add("Movement too abrupt — slow the raise.")
            lyingLegRaiseStableFrames = 0
            return ExerciseResult(
                lyingLegRaiseState,
                feedback,
                false,
                confidence,
                lyingLegRaiseRepCount
            )
        }

        if (!legsStraight) {
            feedback.add("Keep your legs straighter for better control.")
        }
        if (!anklesSymmetric) {
            feedback.add("Raise both legs together.")
        }
        if (!hipsStable) {
            feedback.add("Keep your hips and torso on the floor.")
        }

        val formOk = legsStraight && anklesSymmetric && hipsStable
        val canCount =
            nowMs - lyingLegRaiseLastRepTimeMs >= lyingLegRaiseMinRepIntervalMs

        when (lyingLegRaiseState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if (bothRaised && formOk) {
                    lyingLegRaiseStableFrames++
                    if (lyingLegRaiseStableFrames >= requiredStableFrames) {
                        lyingLegRaiseState = ExerciseState.DOWN
                        lyingLegRaiseInUpPosition = true
                        lyingLegRaiseStableFrames = 0
                    }
                } else {
                    lyingLegRaiseStableFrames = 0
                }
            }
            ExerciseState.DOWN -> {
                if (bothLowered && formOk) {
                    lyingLegRaiseStableFrames++
                    if (lyingLegRaiseStableFrames >= requiredStableFrames) {
                        lyingLegRaiseState = ExerciseState.UP
                        if (lyingLegRaiseInUpPosition && canCount) {
                            lyingLegRaiseRepCount++
                            repCompletedThisFrame = true
                            lyingLegRaiseLastRepTimeMs = nowMs
                        }
                        lyingLegRaiseInUpPosition = false
                        lyingLegRaiseStableFrames = 0
                    }
                } else if (!bothRaised && !bothLowered) {
                    // Holding mid-range or partial — do not count
                    lyingLegRaiseStableFrames = 0
                } else {
                    lyingLegRaiseStableFrames = 0
                }
            }
            ExerciseState.INVALID -> {
                lyingLegRaiseState = ExerciseState.WAITING
                lyingLegRaiseStableFrames = 0
            }
        }

        return ExerciseResult(
            lyingLegRaiseState,
            feedback,
            repCompletedThisFrame,
            confidence,
            lyingLegRaiseRepCount
        )
    }

    private fun angle(first: PoseLandmark, center: PoseLandmark, second: PoseLandmark): Float {
        val radians = atan2(second.position.y - center.position.y, second.position.x - center.position.x) -
                atan2(first.position.y - center.position.y, first.position.x - center.position.x)
        val degrees = abs(Math.toDegrees(radians.toDouble())).toFloat()
        return if (degrees > 180f) 360f - degrees else degrees
    }

    private fun averageAngle(
        leftShoulder: PoseLandmark,
        leftHip: PoseLandmark,
        leftKnee: PoseLandmark,
        rightShoulder: PoseLandmark,
        rightHip: PoseLandmark,
        rightKnee: PoseLandmark
    ): Float {
        return (angle(leftShoulder, leftHip, leftKnee) + angle(rightShoulder, rightHip, rightKnee)) / 2f
    }

    private fun resetSquatStateInternals() {
        squatState = ExerciseState.WAITING
        squatInDownPosition = false
        squatStandingHipY = null
    }

    private fun resetPushupStateInternals() {
        pushupState = ExerciseState.WAITING
        pushupInDownPosition = false
    }

    private fun resetGluteBridgeStateInternals() {
        gluteBridgeState = ExerciseState.WAITING
        gluteBridgeInRaisedPosition = false
    }

    private fun resetStaticLungeStateInternals() {
        staticLungeState = ExerciseState.WAITING
        staticLungeInDownPosition = false
    }

    private fun resetLyingLegRaiseStateInternals() {
        lyingLegRaiseState = ExerciseState.WAITING
        lyingLegRaiseInUpPosition = false
        lyingLegRaiseStableFrames = 0
        lyingLegRaiseBaselineHipY = null
        lyingLegRaisePrevAnkleY = null
    }

    private fun resetSitupStateInternals() {
        situpState = ExerciseState.WAITING
        situpInUpPosition = false
    }


    fun resetExercise() {
        squatState = ExerciseState.WAITING
        squatRepCount = 0
        squatInDownPosition = false
        squatStandingHipY = null

        pushupState = ExerciseState.WAITING
        pushupRepCount = 0
        pushupInDownPosition = false

        gluteBridgeState = ExerciseState.WAITING
        gluteBridgeRepCount = 0
        gluteBridgeInRaisedPosition = false

        staticLungeState = ExerciseState.WAITING
        staticLungeRepCount = 0
        staticLungeInDownPosition = false

        lyingLegRaiseState = ExerciseState.WAITING
        lyingLegRaiseRepCount = 0
        lyingLegRaiseInUpPosition = false
        lyingLegRaiseStableFrames = 0
        lyingLegRaiseLastRepTimeMs = 0L
        lyingLegRaiseBaselineHipY = null
        lyingLegRaisePrevAnkleY = null

        situpState = ExerciseState.WAITING
        situpRepCount = 0
        situpInUpPosition = false
    }

    /** Test helper: current LLR absolute rep count. */
    internal fun lyingLegRaiseRepCountForTests(): Int = lyingLegRaiseRepCount

}