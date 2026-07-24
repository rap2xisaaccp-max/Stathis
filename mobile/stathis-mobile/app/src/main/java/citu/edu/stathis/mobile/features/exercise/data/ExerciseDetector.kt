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

    // --- Squat ---
    private var squatState: ExerciseState = ExerciseState.WAITING
    private var squatRepCount: Int = 0
    private var squatInDownPosition: Boolean = false
    private var squatStandingHipY: Float? = null
    private val squatHipKneeVerticalThresholdFactor = 0.1f
    private val squatMovementThreshold = 0.05f

    // --- Push-up ---
    private var pushupState: ExerciseState = ExerciseState.WAITING
    private var pushupRepCount: Int = 0
    private var pushupInDownPosition: Boolean = false

    // --- Glute bridge ---
    private var gluteBridgeState: ExerciseState = ExerciseState.WAITING
    private var gluteBridgeRepCount: Int = 0
    private var gluteBridgeInRaisedPosition: Boolean = false

    // --- Static lunge ---
    private var staticLungeState: ExerciseState = ExerciseState.WAITING
    private var staticLungeRepCount: Int = 0
    private var staticLungeInDownPosition: Boolean = false

    // --- Lying leg raise ---
    private var lyingLegRaiseState: ExerciseState = ExerciseState.WAITING
    private var lyingLegRaiseRepCount: Int = 0
    private var lyingLegRaiseInUpPosition: Boolean = false

    // --- Sit-up ---
    private var situpState: ExerciseState = ExerciseState.WAITING
    private var situpRepCount: Int = 0
    private var situpInUpPosition: Boolean = false


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

        val isStandingReady = avgHipY < avgKneeY - squatStandThreshold && hipAngle >= 155f
        if (isStandingReady && squatStandingHipY == null) {
            squatStandingHipY = avgHipY
        }


        when (squatState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if ((avgHipY > avgKneeY + squatDownThreshold && hipAngle <= 145f) || avgKneeAngle <= 115f) {
                    squatState = ExerciseState.DOWN
                    squatInDownPosition = true
                } else {
                    squatState = ExerciseState.UP
                }
            }
            ExerciseState.DOWN -> {
                val completedRep = squatStandingHipY?.let { standingHipY ->
                    avgHipY <= standingHipY + squatStandThreshold && (hipAngle >= 150f || avgKneeAngle >= 160f)
                } == true || avgKneeAngle >= 160f

                if (completedRep) {
                    squatState = ExerciseState.UP
                    if (squatInDownPosition) {
                        squatRepCount++
                        repCompletedThisFrame = true
                    }
                    squatInDownPosition = false
                    squatStandingHipY = avgHipY
                }
            }
            ExerciseState.INVALID -> {
                squatState = ExerciseState.WAITING
            }
        }
        val confidence = (leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood) / 4f

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

        when (pushupState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if (avgElbowAngle <= 95f) {
                    pushupState = ExerciseState.DOWN
                    pushupInDownPosition = true
                } else {
                    pushupState = ExerciseState.UP
                }
            }
            ExerciseState.DOWN -> {
                if (avgElbowAngle >= 155f) {
                    pushupState = ExerciseState.UP
                    if (pushupInDownPosition) {
                        pushupRepCount++
                        repCompletedThisFrame = true
                    }
                    pushupInDownPosition = false
                }
            }
            ExerciseState.INVALID -> {
                pushupState = ExerciseState.WAITING
            }
        }

        val confidence = (leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood + leftElbow.inFrameLikelihood + rightElbow.inFrameLikelihood + leftWrist.inFrameLikelihood + rightWrist.inFrameLikelihood) / 6f

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

        when (situpState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if (torsoAngle <= 115f) {
                    situpState = ExerciseState.DOWN
                    situpInUpPosition = true
                } else {
                    situpState = ExerciseState.UP
                }
            }
            ExerciseState.DOWN -> {
                if (torsoAngle >= 145f) {
                    situpState = ExerciseState.UP
                    if (situpInUpPosition) {
                        situpRepCount++
                        repCompletedThisFrame = true
                    }
                    situpInUpPosition = false
                }
            }
            ExerciseState.INVALID -> situpState = ExerciseState.WAITING
        }

        val confidence = (leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood + leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood) / 6f
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

        when (gluteBridgeState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if (hipAngle >= 150f && avgHipY < max(avgShoulderY, avgKneeY) - raisedThreshold) {
                    gluteBridgeState = ExerciseState.DOWN
                    gluteBridgeInRaisedPosition = true
                }
            }
            ExerciseState.DOWN -> {
                if (hipAngle <= 135f || avgHipY >= max(avgShoulderY, avgKneeY) - raisedThreshold * 0.3f) {
                    gluteBridgeState = ExerciseState.UP
                    if (gluteBridgeInRaisedPosition) {
                        gluteBridgeRepCount++
                        repCompletedThisFrame = true
                    }
                    gluteBridgeInRaisedPosition = false
                }
            }
            ExerciseState.INVALID -> gluteBridgeState = ExerciseState.WAITING
        }

        val confidence = (leftShoulder.inFrameLikelihood + rightShoulder.inFrameLikelihood + leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood) / 6f
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
        val feedback = mutableListOf<String>()
        var repCompletedThisFrame = false

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftHip == null || rightHip == null || leftKnee == null || rightKnee == null || leftAnkle == null || rightAnkle == null) {
            feedback.add("Keep hips, knees, and ankles visible.")
            resetLyingLegRaiseStateInternals()
            return ExerciseResult(ExerciseState.INVALID, feedback, repCount = lyingLegRaiseRepCount)
        }

        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2f
        val avgAnkleY = (leftAnkle.position.y + rightAnkle.position.y) / 2f
        val avgKneeAngle = (angle(leftHip, leftKnee, leftAnkle) + angle(rightHip, rightKnee, rightAnkle)) / 2f
        val bodySpan = abs(avgAnkleY - avgHipY).coerceAtLeast(1f)
        val raiseThreshold = bodySpan * 0.12f

        when (lyingLegRaiseState) {
            ExerciseState.WAITING, ExerciseState.UP -> {
                if (avgAnkleY < avgHipY - raiseThreshold) {
                    lyingLegRaiseState = ExerciseState.DOWN
                    lyingLegRaiseInUpPosition = true
                }
            }
            ExerciseState.DOWN -> {
                if (avgAnkleY >= avgHipY - raiseThreshold * 0.4f) {
                    lyingLegRaiseState = ExerciseState.UP
                    if (lyingLegRaiseInUpPosition) {
                        lyingLegRaiseRepCount++
                        repCompletedThisFrame = true
                    }
                    lyingLegRaiseInUpPosition = false
                }
            }
            ExerciseState.INVALID -> lyingLegRaiseState = ExerciseState.WAITING
        }

        if (avgKneeAngle < 150f) {
            feedback.add("Keep your legs straighter for better control.")
        }

        val confidence = (leftHip.inFrameLikelihood + rightHip.inFrameLikelihood + leftKnee.inFrameLikelihood + rightKnee.inFrameLikelihood + leftAnkle.inFrameLikelihood + rightAnkle.inFrameLikelihood) / 6f
        return ExerciseResult(lyingLegRaiseState, feedback, repCompletedThisFrame, confidence, lyingLegRaiseRepCount)
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

        situpState = ExerciseState.WAITING
        situpRepCount = 0
        situpInUpPosition = false
    }

}