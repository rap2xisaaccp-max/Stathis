package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Event-snapshot JPEG helper: scale, compress, no EXIF (compressToByteArray writes raw JPEG).
 */
object JpegCompressor {
    const val MAX_EDGE_PX = 720
    const val QUALITY = 70
    const val MAX_BYTES = 800_000
    const val TARGET_BYTES = 250_000

    fun compress(source: Bitmap, maxEdgePx: Int = MAX_EDGE_PX, quality: Int = QUALITY): ByteArray {
        val scaled = scaleToMaxEdge(source, maxEdgePx)
        return try {
            encodeJpeg(scaled, quality.coerceIn(40, 90))
        } finally {
            if (scaled !== source) {
                scaled.recycle()
            }
        }
    }

    fun scaleToMaxEdge(source: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdgePx || source.width <= 0 || source.height <= 0) {
            return source
        }
        val scale = maxEdgePx.toFloat() / longest.toFloat()
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun isAcceptableSize(bytes: ByteArray): Boolean = bytes.isNotEmpty() && bytes.size <= MAX_BYTES
}
