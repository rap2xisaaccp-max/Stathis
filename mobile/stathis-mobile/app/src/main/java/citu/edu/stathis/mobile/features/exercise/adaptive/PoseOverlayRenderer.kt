package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.roundToInt

/**
 * Single source of truth for student-facing skeleton + error highlight drawing.
 * Used by the live overlay and by offscreen evidence compositing so the JPEG cannot
 * disagree with [ModalityHighlightTargets] / the overlay the student saw.
 *
 * Draws only pose geometry onto the supplied [Canvas] — never Compose chrome, buttons,
 * timers, banners, names, or a full-screen [android.view.View.draw].
 */
object PoseOverlayRenderer {
    const val MIN_IN_FRAME = 0.3f
    const val JOINT_COLOR = Color.GREEN
    const val BONE_COLOR = Color.CYAN
    val HIGHLIGHT_JOINT_COLOR: Int = Color.parseColor("#FFB300")
    val HIGHLIGHT_BONE_COLOR: Int = Color.parseColor("#FF6F00")

    val SKELETON_CONNECTIONS: List<Pair<Int, Int>> =
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

    fun highlightTarget(errorCode: FormErrorCode, exerciseType: String? = null): ModalityHighlightTargets.Target =
        ModalityHighlightTargets.forError(errorCode, exerciseType)

    fun draw(
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        geometry: PoseGeometry,
        highlightCorrection: Boolean,
        highlightLandmarkIds: Set<Int>,
        highlightBones: List<Pair<Int, Int>>,
        targetBitmap: Bitmap? = null
    ) {
        if (geometry.landmarks.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) return

        val jointPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = JOINT_COLOR
                style = Paint.Style.FILL
                strokeWidth = 6f
            }
        val bonePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BONE_COLOR
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
        val highlightJointPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = HIGHLIGHT_JOINT_COLOR
                style = Paint.Style.FILL
                strokeWidth = 8f
            }
        val highlightBonePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = HIGHLIGHT_BONE_COLOR
                style = Paint.Style.STROKE
                strokeWidth = 7f
            }

        val (scaleX, scaleY) = computeScale(canvasWidth, canvasHeight, geometry)
        val targeted = highlightCorrection && highlightLandmarkIds.isNotEmpty()

        for ((start, end) in SKELETON_CONNECTIONS) {
            val paint =
                if (highlightCorrection && !targeted) {
                    highlightBonePaint
                } else {
                    bonePaint
                }
            drawConnection(
                canvas,
                canvasWidth,
                canvasHeight,
                geometry,
                start,
                end,
                scaleX,
                scaleY,
                paint,
                targetBitmap
            )
        }

        if (targeted) {
            for ((start, end) in highlightBones) {
                drawConnection(
                    canvas,
                    canvasWidth,
                    canvasHeight,
                    geometry,
                    start,
                    end,
                    scaleX,
                    scaleY,
                    highlightBonePaint,
                    targetBitmap
                )
            }
        }

        for (lm in geometry.landmarks) {
            if (lm.inFrameLikelihood < MIN_IN_FRAME) continue
            val (vx, vy) = mapPoint(lm.x, lm.y, canvasWidth, canvasHeight, geometry, scaleX, scaleY)
            val isTarget = targeted && highlightLandmarkIds.contains(lm.type)
            val paint =
                when {
                    isTarget -> highlightJointPaint
                    highlightCorrection && !targeted -> highlightJointPaint
                    else -> jointPaint
                }
            val radius = if (isTarget || (highlightCorrection && !targeted)) 9f else 6f
            canvas.drawCircle(vx, vy, radius, paint)
            stampDisk(targetBitmap, vx.roundToInt(), vy.roundToInt(), radius.roundToInt(), paint.color)
        }
    }

    fun computeScale(canvasWidth: Int, canvasHeight: Int, geometry: PoseGeometry): Pair<Float, Float> {
        if (geometry.frameWidth == 0 || geometry.frameHeight == 0 || canvasWidth == 0 || canvasHeight == 0) {
            return 1f to 1f
        }
        val rotated = geometry.rotationDegrees % 180 != 0
        val sourceW = if (rotated) geometry.frameHeight else geometry.frameWidth
        val sourceH = if (rotated) geometry.frameWidth else geometry.frameHeight
        val scale = maxOf(canvasWidth.toFloat() / sourceW, canvasHeight.toFloat() / sourceH)
        return scale to scale
    }

    fun mapPoint(
        x: Float,
        y: Float,
        canvasWidth: Int,
        canvasHeight: Int,
        geometry: PoseGeometry,
        scaleX: Float,
        scaleY: Float
    ): Pair<Float, Float> {
        val sourceW = if (geometry.rotationDegrees % 180 == 0) geometry.frameWidth else geometry.frameHeight
        val sourceH = if (geometry.rotationDegrees % 180 == 0) geometry.frameHeight else geometry.frameWidth

        var rx = x
        val ry = y
        if (geometry.mirrored) rx = sourceW - rx

        val (sx, sy) = computeScale(canvasWidth, canvasHeight, geometry)
        val drawnW = sourceW * sx
        val drawnH = sourceH * sy
        val offsetX = (canvasWidth - drawnW) / 2f
        val offsetY = (canvasHeight - drawnH) / 2f

        val vx = offsetX + rx * sx
        val vy = offsetY + ry * sy
        return vx to vy
    }

    private fun drawConnection(
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        geometry: PoseGeometry,
        startType: Int,
        endType: Int,
        scaleX: Float,
        scaleY: Float,
        paint: Paint,
        targetBitmap: Bitmap?
    ) {
        val sLm = geometry.landmark(startType) ?: return
        val eLm = geometry.landmark(endType) ?: return
        if (sLm.inFrameLikelihood < MIN_IN_FRAME || eLm.inFrameLikelihood < MIN_IN_FRAME) return
        val (sx, sy) = mapPoint(sLm.x, sLm.y, canvasWidth, canvasHeight, geometry, scaleX, scaleY)
        val (ex, ey) = mapPoint(eLm.x, eLm.y, canvasWidth, canvasHeight, geometry, scaleX, scaleY)
        canvas.drawLine(sx, sy, ex, ey, paint)
        stampLine(
            targetBitmap,
            sx.roundToInt(),
            sy.roundToInt(),
            ex.roundToInt(),
            ey.roundToInt(),
            paint.color,
            thickness = if (paint.strokeWidth >= 6f) 3 else 2
        )
    }

    private fun stampDisk(bitmap: Bitmap?, cx: Int, cy: Int, radius: Int, color: Int) {
        val bmp = bitmap?.takeIf { it.isMutable && !it.isRecycled } ?: return
        val r = radius.coerceAtLeast(2)
        val minX = (cx - r).coerceAtLeast(0)
        val maxX = (cx + r).coerceAtMost(bmp.width - 1)
        val minY = (cy - r).coerceAtLeast(0)
        val maxY = (cy + r).coerceAtMost(bmp.height - 1)
        val r2 = r * r
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) {
                    bmp.setPixel(x, y, color)
                }
            }
        }
    }

    private fun stampLine(
        bitmap: Bitmap?,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Int,
        thickness: Int
    ) {
        val bmp = bitmap?.takeIf { it.isMutable && !it.isRecycled } ?: return
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        val t = thickness.coerceAtLeast(1)
        while (true) {
            stampDisk(bmp, x, y, t, color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }
}
