package citu.edu.stathis.mobile.features.exercise.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseSkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pose: Pose? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private var rotationDegrees: Int = 0
    private var isMirrored: Boolean = true
    private var highlightCorrection: Boolean = false
    private var highlightLandmarkIds: Set<Int> = emptySet()
    private var highlightBones: List<Pair<Int, Int>> = emptyList()

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 6f
    }

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val highlightJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300")
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val highlightBonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6F00")
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }

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
        pose = newPose
        frameWidth = w
        frameHeight = h
        rotationDegrees = rotation
        isMirrored = mirrored
        highlightCorrection = highlight
        this.highlightLandmarkIds = highlightLandmarkIds
        this.highlightBones = highlightBones
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = pose ?: return
        val landmarks = p.allPoseLandmarks
        if (landmarks.isEmpty()) return

        val (scaleX, scaleY) = computeScale()
        val targeted = highlightCorrection && highlightLandmarkIds.isNotEmpty()

        val connections =
            listOf(
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
                PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
                PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
                PoseLandmark.NOSE to PoseLandmark.LEFT_EYE,
                PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE,
                PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EAR,
                PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EAR
            )

        for ((start, end) in connections) {
            val paint =
                if (highlightCorrection && !targeted) {
                    highlightBonePaint
                } else {
                    bonePaint
                }
            drawConnection(canvas, p, start, end, scaleX, scaleY, paint)
        }

        if (targeted) {
            for ((start, end) in highlightBones) {
                drawConnection(canvas, p, start, end, scaleX, scaleY, highlightBonePaint)
            }
        }

        for (lm in landmarks) {
            val conf = lm.inFrameLikelihood
            if (conf < 0.3f) continue
            val (vx, vy) = mapPoint(lm.position.x, lm.position.y, scaleX, scaleY)
            val isTarget = targeted && highlightLandmarkIds.contains(lm.landmarkType)
            val paint =
                when {
                    isTarget -> highlightJointPaint
                    highlightCorrection && !targeted -> highlightJointPaint
                    else -> jointPaint
                }
            val radius = if (isTarget || (highlightCorrection && !targeted)) 9f else 6f
            canvas.drawCircle(vx, vy, radius, paint)
        }
    }

    private fun drawConnection(
        canvas: Canvas,
        pose: Pose,
        startType: Int,
        endType: Int,
        scaleX: Float,
        scaleY: Float,
        paint: Paint = bonePaint
    ) {
        val sLm = pose.getPoseLandmark(startType) ?: return
        val eLm = pose.getPoseLandmark(endType) ?: return
        if (sLm.inFrameLikelihood < 0.3f || eLm.inFrameLikelihood < 0.3f) return
        val (sx, sy) = mapPoint(sLm.position.x, sLm.position.y, scaleX, scaleY)
        val (ex, ey) = mapPoint(eLm.position.x, eLm.position.y, scaleX, scaleY)
        canvas.drawLine(sx, sy, ex, ey, paint)
    }

    private fun computeScale(): Pair<Float, Float> {
        if (frameWidth == 0 || frameHeight == 0 || width == 0 || height == 0) return 1f to 1f
        val rotated = rotationDegrees % 180 != 0
        val sourceW = if (rotated) frameHeight else frameWidth
        val sourceH = if (rotated) frameWidth else frameHeight
        val scale = maxOf(width.toFloat() / sourceW, height.toFloat() / sourceH)
        return scale to scale
    }

    private fun mapPoint(x: Float, y: Float, scaleX: Float, scaleY: Float): Pair<Float, Float> {
        val sourceW = if (rotationDegrees % 180 == 0) frameWidth else frameHeight
        val sourceH = if (rotationDegrees % 180 == 0) frameHeight else frameWidth

        var rx = x
        val ry = y
        if (isMirrored) rx = sourceW - rx

        val (sx, sy) = computeScale()
        val drawnW = sourceW * sx
        val drawnH = sourceH * sy
        val offsetX = (width - drawnW) / 2f
        val offsetY = (height - drawnH) / 2f

        val vx = offsetX + rx * sx
        val vy = offsetY + ry * sy
        return vx to vy
    }
}
