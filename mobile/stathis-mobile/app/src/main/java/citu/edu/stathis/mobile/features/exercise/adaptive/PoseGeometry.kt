package citu.edu.stathis.mobile.features.exercise.adaptive

import com.google.mlkit.vision.pose.Pose

/**
 * Copied landmark geometry for overlay drawing and evidence compositing.
 * Independent of a live [Pose] / [androidx.camera.core.ImageProxy] so neither path
 * can race with native close.
 */
data class PoseLandmarkPoint(
    val type: Int,
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float
)

data class PoseGeometry(
    val landmarks: List<PoseLandmarkPoint>,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int,
    val mirrored: Boolean
) {
    fun landmark(type: Int): PoseLandmarkPoint? = landmarks.firstOrNull { it.type == type }

    companion object {
        fun fromPose(
            pose: Pose,
            frameWidth: Int,
            frameHeight: Int,
            rotationDegrees: Int,
            mirrored: Boolean
        ): PoseGeometry {
            val points =
                pose.allPoseLandmarks.map { lm ->
                    PoseLandmarkPoint(
                        type = lm.landmarkType,
                        x = lm.position.x,
                        y = lm.position.y,
                        inFrameLikelihood = lm.inFrameLikelihood
                    )
                }
            return PoseGeometry(
                landmarks = points,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                rotationDegrees = rotationDegrees,
                mirrored = mirrored
            )
        }
    }
}
