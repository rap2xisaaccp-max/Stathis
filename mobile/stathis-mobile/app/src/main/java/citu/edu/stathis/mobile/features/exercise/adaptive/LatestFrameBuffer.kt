package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

data class FramePoseSnapshot(
    val bitmap: Bitmap,
    val pose: PoseGeometry?
) {
    fun recycleCopy() {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

/**
 * One-slot copied camera preview + latest pose geometry. Overwrites on each analysis tick;
 * cloned only when a confirmed coaching event needs an evidence snapshot.
 * Never uploads from the frame loop. Never converts a live ImageProxy.
 */
@Singleton
class LatestFrameBuffer @Inject constructor() {
    private val lock = Any()
    private var frame: Bitmap? = null
    private var pose: PoseGeometry? = null

    fun updateFromBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val kept =
            runCatching {
                val scaled = JpegCompressor.scaleToMaxEdge(bitmap, JpegCompressor.MAX_EDGE_PX)
                if (scaled === bitmap) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    scaled
                }
            }.getOrNull() ?: return
        synchronized(lock) {
            val previous = frame
            frame = kept
            previous?.takeIf { !it.isRecycled && it !== kept }?.recycle()
        }
    }

    fun updatePose(geometry: PoseGeometry) {
        synchronized(lock) { pose = geometry }
    }

    /**
     * Independent copies for one evidence composite. Caller must recycle [FramePoseSnapshot.bitmap]
     * if it is not the live buffer instance (it never is — this always copies).
     */
    fun snapshot(): FramePoseSnapshot? =
        synchronized(lock) {
            val bmp = frame ?: return null
            if (bmp.isRecycled) return null
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            FramePoseSnapshot(copy, pose)
        }

    fun hasFrame(): Boolean = synchronized(lock) { frame != null && frame?.isRecycled == false }

    fun hasPose(): Boolean = synchronized(lock) { pose != null }

    /** Compress the latest camera pixels without overlay — used by ownership tests. */
    fun copyJpeg(): ByteArray? {
        val bmp = synchronized(lock) { frame?.takeIf { !it.isRecycled } } ?: return null
        return runCatching { JpegCompressor.compress(bmp, maxEdgePx = 480, quality = 60) }.getOrNull()
    }

    fun clear() {
        synchronized(lock) {
            frame?.takeIf { !it.isRecycled }?.recycle()
            frame = null
            pose = null
        }
    }
}
