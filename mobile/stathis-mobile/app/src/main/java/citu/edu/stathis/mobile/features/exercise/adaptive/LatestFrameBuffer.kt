package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-slot camera preview buffer. Overwrites on each analysis tick; cloned only when a
 * confirmed coaching event needs an evidence snapshot. Never uploads from the frame loop.
 */
@Singleton
class LatestFrameBuffer @Inject constructor() {
    private val lock = Any()
    private var jpeg: ByteArray? = null

    fun updateFromImageProxy(imageProxy: ImageProxy) {
        val bitmap = runCatching { imageProxy.toBitmap() }.getOrNull() ?: return
        updateFromBitmap(bitmap)
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    fun updateFromBitmap(bitmap: Bitmap) {
        val compressed =
            runCatching { JpegCompressor.compress(bitmap, maxEdgePx = 480, quality = 60) }
                .getOrNull()
                ?: return
        synchronized(lock) { jpeg = compressed }
    }

    /** Clone of the last compressed preview, or null if no frame has been seen. */
    fun copyJpeg(): ByteArray? = synchronized(lock) { jpeg?.copyOf() }

    fun clear() {
        synchronized(lock) { jpeg = null }
    }
}
