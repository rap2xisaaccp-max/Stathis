package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Offscreen evidence image: camera frame + skeleton context + the same error highlight
 * the live overlay uses. Never screenshots the Android UI and never composites per frame.
 */
object EvidenceHighlightCompositor {

    fun compose(
        cameraFrame: Bitmap,
        geometry: PoseGeometry,
        errorCode: FormErrorCode,
        exerciseType: String?
    ): Bitmap? {
        if (cameraFrame.isRecycled || cameraFrame.width <= 0 || cameraFrame.height <= 0) return null
        if (geometry.landmarks.isEmpty()) return null

        val oriented =
            CameraFrameOrientation.toStudentView(
                cameraFrame,
                geometry.rotationDegrees,
                geometry.mirrored
            )
        val output =
            if (oriented.isMutable && oriented !== cameraFrame) {
                oriented
            } else {
                val copy = oriented.copy(Bitmap.Config.ARGB_8888, true) ?: return null
                if (oriented !== cameraFrame && oriented !== copy && !oriented.isRecycled) {
                    oriented.recycle()
                }
                copy
            }

        val canvas = Canvas(output)
        val target = PoseOverlayRenderer.highlightTarget(errorCode, exerciseType)
        PoseOverlayRenderer.draw(
            canvas = canvas,
            canvasWidth = output.width,
            canvasHeight = output.height,
            geometry = geometry,
            highlightCorrection = true,
            highlightLandmarkIds = target.joints,
            highlightBones = target.bones,
            targetBitmap = output
        )
        return output
    }

    fun composeJpeg(
        cameraFrame: Bitmap,
        geometry: PoseGeometry,
        errorCode: FormErrorCode,
        exerciseType: String?
    ): ByteArray? {
        val composed = compose(cameraFrame, geometry, errorCode, exerciseType) ?: return null
        return try {
            val jpeg = JpegCompressor.compress(composed)
            jpeg.takeIf { JpegCompressor.isAcceptableSize(it) }
        } finally {
            if (composed !== cameraFrame && !composed.isRecycled) {
                composed.recycle()
            }
        }
    }
}
