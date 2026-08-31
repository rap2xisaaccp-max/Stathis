package citu.edu.stathis.mobile.features.exercise.data.analysis

import citu.edu.stathis.mobile.features.exercise.adaptive.ExerciseFramingGate
import citu.edu.stathis.mobile.features.exercise.adaptive.PoseGeometry
import citu.edu.stathis.mobile.features.exercise.data.ExerciseDetector
import citu.edu.stathis.mobile.features.exercise.data.ExerciseResult
import citu.edu.stathis.mobile.features.exercise.data.ExerciseType
import citu.edu.stathis.mobile.features.exercise.data.OnDeviceFeedback
import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState
import com.google.mlkit.vision.pose.Pose
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceExerciseAnalyzer @Inject constructor() {
    private val exerciseDetector = ExerciseDetector()

    fun analyzePose(
        pose: Pose,
        exerciseType: ExerciseType,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
        mirrored: Boolean,
        previewWidth: Int = 0,
        previewHeight: Int = 0
    ): OnDeviceFeedback {
        val geometry =
            PoseGeometry.fromPose(
                pose = pose,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                rotationDegrees = rotationDegrees,
                mirrored = mirrored
            )
        val framing =
            ExerciseFramingGate.evaluate(
                exerciseType = exerciseType,
                geometry = geometry,
                previewWidth = previewWidth,
                previewHeight = previewHeight
            )
        if (!framing.ok) {
            exerciseDetector.resetPhaseKeepReps()
            return OnDeviceFeedback(
                exerciseType = exerciseType,
                exerciseState = ExerciseState.INVALID,
                repCount = exerciseDetector.currentRepCount(exerciseType),
                formIssues = listOf(framing.guidance),
                confidence = framing.minLikelihood,
                angleData = emptyMap(),
                formScore = null,
                framingInvalid = true
            )
        }

        val exerciseResult: ExerciseResult = when (exerciseType) {
            ExerciseType.SQUAT -> exerciseDetector.analyzeSquat(pose)
            ExerciseType.PUSHUP -> exerciseDetector.analyzePushup(pose)
            ExerciseType.SIT_UP -> exerciseDetector.analyzeSitup(pose)
            ExerciseType.GLUTE_BRIDGE -> exerciseDetector.analyzeGluteBridge(pose)
            ExerciseType.STATIC_LUNGE -> exerciseDetector.analyzeStaticLunge(pose)
            ExerciseType.LYING_LEG_RAISE -> exerciseDetector.analyzeLyingLegRaise(pose)
        }

        return OnDeviceFeedback(
            exerciseType = exerciseType,
            exerciseState = exerciseResult.state,
            repCount = exerciseResult.repCount,
            formIssues = exerciseResult.feedback,
            confidence = exerciseResult.confidence ?: 0.0f,
            angleData = emptyMap(),
            formScore = exerciseResult.formScore
        )
    }

    fun resetExerciseState() {
        exerciseDetector.resetExercise()
    }
}
