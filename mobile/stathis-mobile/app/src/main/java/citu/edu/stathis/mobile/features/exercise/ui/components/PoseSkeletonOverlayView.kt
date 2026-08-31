package citu.edu.stathis.mobile.features.exercise.ui.components

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import citu.edu.stathis.mobile.features.exercise.adaptive.PoseGeometry
import citu.edu.stathis.mobile.features.exercise.adaptive.PoseOverlayRenderer
import com.google.mlkit.vision.pose.Pose

class PoseSkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var geometry: PoseGeometry? = null
    private var highlightCorrection: Boolean = false
    private var highlightLandmarkIds: Set<Int> = emptySet()
    private var highlightBones: List<Pair<Int, Int>> = emptyList()

    fun updatePose(
        newPose: Pose?,
        w: Int,
        h: Int,
        rotation: Int,
        mirrored: Boolean,
        highlight: Boolean = false,
        highlightLandmarkIds: Set<Int> = emptySet(),
        highlightBones: List<Pair<Int, Int>> = emptyList()
    ) {
        geometry =
            newPose?.let {
                PoseGeometry.fromPose(
                    pose = it,
                    frameWidth = w,
                    frameHeight = h,
                    rotationDegrees = rotation,
                    mirrored = mirrored
                )
            }
        highlightCorrection = highlight
        this.highlightLandmarkIds = highlightLandmarkIds
        this.highlightBones = highlightBones
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = geometry ?: return
        PoseOverlayRenderer.draw(
            canvas = canvas,
            canvasWidth = width,
            canvasHeight = height,
            geometry = g,
            highlightCorrection = highlightCorrection,
            highlightLandmarkIds = highlightLandmarkIds,
            highlightBones = highlightBones
        )
    }
}
