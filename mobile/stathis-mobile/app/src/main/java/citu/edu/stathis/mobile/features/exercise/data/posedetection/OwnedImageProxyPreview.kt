package citu.edu.stathis.mobile.features.exercise.data.posedetection

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import timber.log.Timber

/**
 * Copies preview pixels while this thread still exclusively owns the [ImageProxy].
 * Never closes the proxy; [PoseAnalyzer] must [ImageProxy.close] once after ML Kit completes.
 */
object OwnedImageProxyPreview {

    private fun defaultConvert(proxy: ImageProxy): Bitmap? =
        runCatching { proxy.toBitmap() }
            .onFailure { Timber.w(it, "Preview frame copy failed; no evidence snapshot source") }
            .getOrNull()

    fun copyWhileOwned(
        imageProxy: ImageProxy,
        onCopied: (Bitmap) -> Unit,
        convert: (ImageProxy) -> Bitmap? = ::defaultConvert
    ) {
        val bitmap = convert(imageProxy) ?: return
        try {
            onCopied(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Analyzer-thread protocol: convert while owned, then start detection.
     * [startDetection] must register the single [ImageProxy.close] on the complete path.
     */
    fun copyThenStartDetection(
        imageProxy: ImageProxy,
        onCopiedPreview: ((Bitmap) -> Unit)?,
        convert: (ImageProxy) -> Bitmap? = ::defaultConvert,
        startDetection: () -> Unit
    ) {
        try {
            if (onCopiedPreview != null) {
                copyWhileOwned(imageProxy, onCopiedPreview, convert)
            }
        } catch (t: RuntimeException) {
            // Preview copy must not skip pose detection or the guaranteed close.
            Timber.w(t, "Preview copy consumer failed; continuing with pose detection")
        }
        startDetection()
    }
}
