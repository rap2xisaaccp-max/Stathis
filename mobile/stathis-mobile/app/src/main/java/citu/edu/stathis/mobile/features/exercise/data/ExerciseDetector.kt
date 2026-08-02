package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState
import citu.edu.stathis.mobile.features.exercise.domain.FormAccuracy
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
            return ExerciseResult(squatState, feedback, repCompletedThisFrame, confidence, squatRepCount, formScore = null)
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

        when (squatState) {
            ExerciseState.DOWN -> {
                if (avgKneeAngle > 130f) feedback.add("Squat deeper — bend your knees more.")
                if (hipAngle > 155f) feedback.add("Hinge at the hips and sit back into the squat.")
            }
            ExerciseState.UP -> {
                if (avgKneeAngle < 150f) feedback.add("Stand tall and fully extend your knees.")
            }
            else -> Unit
        }

        val formScore = assessActiveFormScore(
            state = squatState,
            phaseScore = when (squatState) {
                ExerciseState.DOWN -> min(
                    FormAccuracy.nearIdeal(avgKneeAngle, 100f, 20f, 55f),
                    FormAccuracy.atMost(hipAngle, 145f, 170f)
                )
                ExerciseState.UP -> min(
                    FormAccuracy.atLeast(avgKneeAngle, 160f, 130f),
                    FormAccuracy.atLeast(hipAngle, 155f, 125f)
                )
                else -> 0f
            },
            formIssueCount = feedback.size
        )

        return ExerciseResult(squatState, feedback, repCompletedThisFrame, confidence, squatRepCount, formScore)
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
            return ExerciseResult(pushupState, feedback, repCompletedThisFrame, confidence, pushupRepCount, formScore = null)
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

        when (pushupState) {
            ExerciseState.DOWN -> {
                if (avgElbowAngle > 110f) feedback.add("Lower your chest closer to the ground.")
            }
            ExerciseState.UP -> {
                if (avgElbowAngle < 150f) feedback.add("Fully extend your arms at the top.")
            }
            else -> Unit
        }

        val formScore = assessActiveFormScore(
            state = pushupState,
            phaseScore = when (pushupState) {
                ExerciseState.DOWN -> FormAccuracy.nearIdeal(avgElbowAngle, 90f, 20f, 50f)
                ExerciseState.UP -> FormAccuracy.atLeast(avgElbowAngle, 155f, 120f)
                else -> 0f
            },
            formIssueCount = feedback.size
        )

        return ExerciseResult(pushupState, feedback, repCompletedThisFrame, confidence, pushupRepCount, formScore)
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
            return ExerciseResult(situpState, feedback, repCompletedThisFrame, confidence, situpRepCount, formScore = null)
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

        when (situpState) {
            ExerciseState.DOWN -> {
                if (torsoAngle < 130f) feedback.add("Curl up higher — bring your shoulders toward your knees.")
            }
            ExerciseState.UP -> {
                if (torsoAngle > 130f) feedback.add("Lower your torso with control before the next rep.")
            }
            else -> Unit
        }

        val formScore = assessActiveFormScore(
            state = situpState,
            phaseScore = when (situpState) {
                // DOWN here means the crunch/up phase in this detector
                ExerciseState.DOWN -> FormAccuracy.atLeast(torsoAngle, 145f, 115f)
                ExerciseState.UP -> FormAccuracy.atMost(torsoAngle, 115f, 145f)
                else -> 0f
            },
            formIssueCount = feedback.size
        )

        return ExerciseResult(situpState, feedback, repCompletedThisFrame, confidence, situpRepCount, formScore)
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
            return ExerciseResult(gluteBridgeState, feedback, repCompletedThisFrame, confidence, gluteBridgeRepCount, formScore = null)
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

        when (gluteBridgeState) {
            ExerciseState.DOWN -> {
                if (hipAngle < 145f) feedback.add("Drive your hips higher into a full bridge.")
            }
            ExerciseState.UP -> {
                if (hipAngle > 145f) feedback.add("Lower your hips with control before the next bridge.")
            }
            else -> Unit
        }

        val formScore = assessActiveFormScore(
            state = gluteBridgeState,
            phaseScore = when (gluteBridgeState) {
                // DOWN = raised bridge phase in this detector
                ExerciseState.DOWN -> FormAccuracy.atLeast(hipAngle, 155f, 130f)
                ExerciseState.UP -> FormAccuracy.atMost(hipAngle, 135f, 160f)
                else -> 0f
            },
            formIssueCount = feedback.size
        )

        return ExerciseResult(gluteBridgeState, feedback, repCompletedThisFrame, confidence, gluteBridgeRepCount, formScore)
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
        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            return ExerciseResult(staticLungeState, feedback, repCompletedThisFrame, confidence, staticLungeRepCount, formScore = null)
        }

        when (staticLungeState) {
            ExerciseState.DOWN -> {
                if (bentLegAngle > 120f) feedback.add("Bend the front knee deeper into the lunge.")
                if (straightLegAngle < 140f) feedback.add("Keep the back leg more extended.")
            }
            ExerciseState.UP -> {
                if (bentLegAngle < 150f || straightLegAngle < 150f) {
                    feedback.add("Return to a tall stance between lunges.")
                }
            }
            else -> Unit
        }

        val formScore = assessActiveFormScore(
            state = staticLungeState,
            phaseScore = when (staticLungeState) {
                ExerciseState.DOWN -> min(
                    FormAccuracy.nearIdeal(bentLegAngle, 95f, 20f, 50f),
                    FormAccuracy.atLeast(straightLegAngle, 145f, 120f)
                )
                ExerciseState.UP -> min(
                    FormAccuracy.atLeast(bentLegAngle, 155f, 130f),
                    FormAccuracy.atLeast(straightLegAngle, 155f, 130f)
                )
                else -> 0f
            },
            formIssueCount = feedback.size
        )

        return ExerciseResult(staticLungeState, feedback, repCompletedThisFrame, confidence, staticLungeRepCount, formScore)
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
        if (confidence < defaultConfidenceThreshold) {
            feedback.add("Low detection confidence")
            return ExerciseResult(lyingLegRaiseState, feedback, repCompletedThisFrame, confidence, lyingLegRaiseRepCount, formScore = null)
        }

        when (lyingLegRaiseState) {
            ExerciseState.DOWN -> {
                if (avgAnkleY >= avgHipY - raiseThreshold * 0.7f) {
                    feedback.add("Raise your legs higher while keeping them controlled.")
                }
            }
            else -> Unit
        }

        val formScore = assessActiveFormScore(
            state = lyingLegRaiseState,
            phaseScore = when (lyingLegRaiseState) {
                // DOWN = legs raised phase in this detector
                ExerciseState.DOWN -> min(
                    FormAccuracy.atLeast(avgKneeAngle, 160f, 130f),
                    FormAccuracy.atLeast((avgHipY - avgAnkleY) / bodySpan, 0.2f, 0.05f)
                )
                ExerciseState.UP -> FormAccuracy.atLeast(avgKneeAngle, 160f, 130f)
                else -> 0f
            },
            formIssueCount = feedback.size
        )

        return ExerciseResult(lyingLegRaiseState, feedback, repCompletedThisFrame, confidence, lyingLegRaiseRepCount, formScore)
    }

    /**
     * Form accuracy is only sampled while the student is in an active exercise phase.
     * Waiting / invalid frames leave session accuracy at its default of 0.
     */
    private fun assessActiveFormScore(
        state: ExerciseState,
        phaseScore: Float,
        formIssueCount: Int
    ): Float? {
        if (state != ExerciseState.UP && state != ExerciseState.DOWN) return null
        return FormAccuracy.combine(phaseScore, formIssueCount)
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