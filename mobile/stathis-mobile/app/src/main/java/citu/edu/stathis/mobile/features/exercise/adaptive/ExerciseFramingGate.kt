package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.ExerciseType
import kotlin.math.max
import kotlin.math.min

/**
 * Exercise-specific full-body framing gate in analysis-frame coordinates.
 *
 * Landmark x/y follow the same upright space as [PoseOverlayRenderer]: ML Kit points after
 * [InputImage] rotation, with [PoseGeometry.frameWidth]/[PoseGeometry.frameHeight] as the
 * unrotated ImageProxy size. Front-camera mirroring is applied the same way as the overlay
 * so off-center/edge checks match what the student sees.
 *
 * When [previewWidth]/[previewHeight] are known, the usable rect is the PreviewView
 * FILL_CENTER crop (max-scale, same as the overlay) before applying [EDGE_MARGIN_FRACTION].
 */
object ExerciseFramingGate {
    /** Matches [citu.edu.stathis.mobile.features.exercise.data.ExerciseDetector] confidence floor. */
    const val MIN_LANDMARK_LIKELIHOOD = 0.5f

    /**
     * Inset inside the visible FILL_CENTER rect, as a fraction of min(usable width, height).
     * 6% is enough to keep joints off the PreviewView crop and sensor edge (~43px on a 720px
     * short side) without rejecting a normally centered full-body pose.
     */
    const val EDGE_MARGIN_FRACTION = 0.06f

    /** Horizontal centroid band while the body does not already span the frame. */
    const val OFF_CENTER_MIN = 0.34f
    const val OFF_CENTER_MAX = 0.66f
    const val OFF_CENTER_MAX_BBOX_WIDTH_FRACTION = 0.72f

    /**
     * Lying leg raise: ankles must sit this far below the top of the usable frame (image Y
     * grows downward) so a raise can happen without leaving the frame. Aligns with the
     * detector's ~28% of leg-span travel without requiring a measured span.
     */
    const val LLR_RAISE_HEADROOM_FRACTION = 0.18f

    data class FrameRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun width(): Float = right - left
        fun height(): Float = bottom - top
    }

    enum class Reason {
        OK,
        MISSING_LANDMARK,
        OUTSIDE_USABLE_FRAME,
        NEAR_EDGE,
        OFF_CENTER,
        INSUFFICIENT_RAISE_ROOM,
        LOW_CONFIDENCE
    }

    data class Verdict(
        val ok: Boolean,
        val reason: Reason,
        val errorCode: FormErrorCode,
        val guidance: String,
        val minLikelihood: Float
    ) {
        val framingInvalid: Boolean get() = !ok
    }

    fun requiredLandmarkTypes(exerciseType: ExerciseType): Set<Int> =
        when (exerciseType) {
            ExerciseType.PUSHUP ->
                setOf(
                    ModalityHighlightTargets.LEFT_SHOULDER,
                    ModalityHighlightTargets.RIGHT_SHOULDER,
                    ModalityHighlightTargets.LEFT_ELBOW,
                    ModalityHighlightTargets.RIGHT_ELBOW,
                    ModalityHighlightTargets.LEFT_WRIST,
                    ModalityHighlightTargets.RIGHT_WRIST,
                    ModalityHighlightTargets.LEFT_HIP,
                    ModalityHighlightTargets.RIGHT_HIP,
                    ModalityHighlightTargets.LEFT_KNEE,
                    ModalityHighlightTargets.RIGHT_KNEE,
                    ModalityHighlightTargets.LEFT_ANKLE,
                    ModalityHighlightTargets.RIGHT_ANKLE
                )
            ExerciseType.SQUAT,
            ExerciseType.STATIC_LUNGE,
            ExerciseType.GLUTE_BRIDGE,
            ExerciseType.LYING_LEG_RAISE ->
                setOf(
                    ModalityHighlightTargets.LEFT_SHOULDER,
                    ModalityHighlightTargets.RIGHT_SHOULDER,
                    ModalityHighlightTargets.LEFT_HIP,
                    ModalityHighlightTargets.RIGHT_HIP,
                    ModalityHighlightTargets.LEFT_KNEE,
                    ModalityHighlightTargets.RIGHT_KNEE,
                    ModalityHighlightTargets.LEFT_ANKLE,
                    ModalityHighlightTargets.RIGHT_ANKLE
                )
            ExerciseType.SIT_UP ->
                setOf(
                    ModalityHighlightTargets.LEFT_SHOULDER,
                    ModalityHighlightTargets.RIGHT_SHOULDER,
                    ModalityHighlightTargets.LEFT_HIP,
                    ModalityHighlightTargets.RIGHT_HIP,
                    ModalityHighlightTargets.LEFT_KNEE,
                    ModalityHighlightTargets.RIGHT_KNEE
                )
        }

    fun uprightSourceSize(frameWidth: Int, frameHeight: Int, rotationDegrees: Int): Pair<Int, Int> {
        val rotated = rotationDegrees % 180 != 0
        val sourceW = if (rotated) frameHeight else frameWidth
        val sourceH = if (rotated) frameWidth else frameHeight
        return sourceW to sourceH
    }

    /**
     * Source-space rectangle visible after FILL_CENTER (max scale, centered), matching
     * [PoseOverlayRenderer.computeScale].
     */
    fun fillCenterVisibleRect(
        sourceW: Int,
        sourceH: Int,
        previewWidth: Int,
        previewHeight: Int
    ): FrameRect {
        if (sourceW <= 0 || sourceH <= 0) return FrameRect(0f, 0f, 0f, 0f)
        if (previewWidth <= 0 || previewHeight <= 0) {
            return FrameRect(0f, 0f, sourceW.toFloat(), sourceH.toFloat())
        }
        val scale =
            max(
                previewWidth.toFloat() / sourceW.toFloat(),
                previewHeight.toFloat() / sourceH.toFloat()
            )
        if (scale <= 0f) return FrameRect(0f, 0f, sourceW.toFloat(), sourceH.toFloat())
        val drawnW = sourceW * scale
        val drawnH = sourceH * scale
        val offsetX = (previewWidth - drawnW) / 2f
        val offsetY = (previewHeight - drawnH) / 2f
        val left = ((0f - offsetX) / scale).coerceIn(0f, sourceW.toFloat())
        val top = ((0f - offsetY) / scale).coerceIn(0f, sourceH.toFloat())
        val right = ((previewWidth - offsetX) / scale).coerceIn(0f, sourceW.toFloat())
        val bottom = ((previewHeight - offsetY) / scale).coerceIn(0f, sourceH.toFloat())
        return FrameRect(left, top, right, bottom)
    }

    fun insetRect(visible: FrameRect, marginFraction: Float = EDGE_MARGIN_FRACTION): FrameRect {
        val span = min(visible.width(), visible.height()).coerceAtLeast(1f)
        val m = span * marginFraction
        return FrameRect(
            visible.left + m,
            visible.top + m,
            visible.right - m,
            visible.bottom - m
        )
    }

    fun studentViewX(x: Float, sourceW: Int, mirrored: Boolean): Float =
        if (mirrored) sourceW - x else x

    fun evaluate(
        exerciseType: ExerciseType,
        geometry: PoseGeometry,
        previewWidth: Int = 0,
        previewHeight: Int = 0
    ): Verdict {
        val (sourceW, sourceH) = uprightSourceSize(
            geometry.frameWidth,
            geometry.frameHeight,
            geometry.rotationDegrees
        )
        if (sourceW <= 0 || sourceH <= 0) {
            return fail(
                Reason.MISSING_LANDMARK,
                FormErrorCode.BODY_NOT_VISIBLE,
                missingMessage(exerciseType),
                0f
            )
        }
        val required = requiredLandmarkTypes(exerciseType)
        val points = required.map { type -> type to geometry.landmark(type) }
        val missing = points.filter { it.second == null }
        if (missing.isNotEmpty()) {
            return fail(
                Reason.MISSING_LANDMARK,
                FormErrorCode.BODY_NOT_VISIBLE,
                missingMessage(exerciseType),
                0f
            )
        }

        val visible = fillCenterVisibleRect(sourceW, sourceH, previewWidth, previewHeight)
        val inset = insetRect(visible)
        val studentPoints =
            points.map { (_, lm) ->
                val p = lm!!
                Triple(
                    studentViewX(p.x, sourceW, geometry.mirrored),
                    p.y,
                    p.inFrameLikelihood
                )
            }

        val minLikelihood = studentPoints.minOf { it.third }
        val outside =
            studentPoints.filter { (x, y, _) ->
                x < visible.left || x > visible.right || y < visible.top || y > visible.bottom
            }
        if (outside.isNotEmpty()) {
            return fail(
                Reason.OUTSIDE_USABLE_FRAME,
                FormErrorCode.BODY_NOT_VISIBLE,
                cropOrCenterMessage(exerciseType, offCenter = isOffCenter(studentPoints, visible)),
                minLikelihood
            )
        }

        val nearEdge =
            studentPoints.filter { (x, y, _) ->
                x < inset.left || x > inset.right || y < inset.top || y > inset.bottom
            }
        if (nearEdge.isNotEmpty()) {
            val offCenter = isOffCenter(studentPoints, visible)
            return fail(
                if (offCenter) Reason.OFF_CENTER else Reason.NEAR_EDGE,
                FormErrorCode.BODY_NOT_VISIBLE,
                cropOrCenterMessage(exerciseType, offCenter = offCenter),
                minLikelihood
            )
        }

        if (isOffCenter(studentPoints, visible)) {
            return fail(
                Reason.OFF_CENTER,
                FormErrorCode.BODY_NOT_VISIBLE,
                centerMessage(),
                minLikelihood
            )
        }

        if (exerciseType == ExerciseType.LYING_LEG_RAISE) {
            val leftAnkle = geometry.landmark(ModalityHighlightTargets.LEFT_ANKLE)!!
            val rightAnkle = geometry.landmark(ModalityHighlightTargets.RIGHT_ANKLE)!!
            val minAnkleY = min(leftAnkle.y, rightAnkle.y)
            val headroom = visible.height() * LLR_RAISE_HEADROOM_FRACTION
            if (minAnkleY < visible.top + headroom) {
                return fail(
                    Reason.INSUFFICIENT_RAISE_ROOM,
                    FormErrorCode.BODY_NOT_VISIBLE,
                    "Move so your legs have room to raise without leaving the camera frame.",
                    minLikelihood
                )
            }
        }

        if (minLikelihood < MIN_LANDMARK_LIKELIHOOD) {
            return fail(
                Reason.LOW_CONFIDENCE,
                FormErrorCode.LOW_CONFIDENCE,
                "Hold still briefly so your form can be read clearly.",
                minLikelihood
            )
        }

        return Verdict(
            ok = true,
            reason = Reason.OK,
            errorCode = FormErrorCode.UNKNOWN,
            guidance = "",
            minLikelihood = minLikelihood
        )
    }

    private fun isOffCenter(points: List<Triple<Float, Float, Float>>, visible: FrameRect): Boolean {
        if (points.isEmpty() || visible.width() <= 0f) return false
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val bboxFrac = (maxX - minX) / visible.width()
        if (bboxFrac >= OFF_CENTER_MAX_BBOX_WIDTH_FRACTION) return false
        val cx = points.map { it.first }.average().toFloat()
        val nx = (cx - visible.left) / visible.width()
        return nx < OFF_CENTER_MIN || nx > OFF_CENTER_MAX
    }

    private fun fail(
        reason: Reason,
        code: FormErrorCode,
        guidance: String,
        minLikelihood: Float
    ): Verdict =
        Verdict(
            ok = false,
            reason = reason,
            errorCode = code,
            guidance = guidance,
            minLikelihood = minLikelihood
        )

    private fun missingMessage(exerciseType: ExerciseType): String =
        when (exerciseType) {
            ExerciseType.PUSHUP ->
                "Make sure your shoulders, arms, hips, and feet are visible in the camera."
            ExerciseType.SQUAT ->
                "Make sure your shoulders, hips, and feet are visible in the camera."
            ExerciseType.STATIC_LUNGE ->
                "Make sure your shoulders, hips, and both feet are visible in the camera."
            ExerciseType.GLUTE_BRIDGE ->
                "Make sure your shoulders, hips, and feet are visible in the camera."
            ExerciseType.LYING_LEG_RAISE ->
                "Make sure your shoulders, hips, and both legs are visible in the camera."
            ExerciseType.SIT_UP ->
                "Make sure your shoulders, hips, and knees are visible in the camera."
        }

    private fun cropOrCenterMessage(exerciseType: ExerciseType, offCenter: Boolean): String {
        if (offCenter) return centerMessage()
        return when (exerciseType) {
            ExerciseType.PUSHUP ->
                "Step back so your head, hands, and feet stay in the camera frame."
            ExerciseType.SQUAT ->
                "Step back so your shoulders, hips, and feet stay in the camera frame."
            ExerciseType.STATIC_LUNGE ->
                "Step back so both feet and your shoulders stay in the camera frame."
            ExerciseType.GLUTE_BRIDGE ->
                "Step back so your shoulders, hips, and feet stay in the camera frame."
            ExerciseType.LYING_LEG_RAISE ->
                "Step back so your shoulders, hips, and feet stay in the camera frame."
            ExerciseType.SIT_UP ->
                "Step back so your shoulders, hips, and knees stay in the camera frame."
        }
    }

    private fun centerMessage(): String = "Move to the center of the camera frame."
}
