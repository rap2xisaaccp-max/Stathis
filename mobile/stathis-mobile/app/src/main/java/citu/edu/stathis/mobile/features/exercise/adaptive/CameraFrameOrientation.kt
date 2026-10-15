package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * Rotates and front-camera-mirrors a copied analysis bitmap into the same upright
 * student-facing space used by PreviewView + [PoseOverlayRenderer].
 */
object CameraFrameOrientation {

    fun toStudentView(source: Bitmap, rotationDegrees: Int, mirrored: Boolean): Bitmap {
        if (source.width <= 0 || source.height <= 0) return source
        val rot = ((rotationDegrees % 360) + 360) % 360
        if (rot == 0 && !mirrored) return source

        var current = source
        if (rot != 0) {
            val matrix = Matrix().apply { postRotate(rot.toFloat()) }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            current = rotated
        }
        if (mirrored) {
            val flipped = flipHorizontal(current)
            if (current !== source && current !== flipped && !current.isRecycled) {
                current.recycle()
            }
            current = flipped
        }
        return current
    }

    fun flipHorizontal(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            row.reverse()
            out.setPixels(row, 0, width, 0, y, width, 1)
        }
        return out
    }
}
