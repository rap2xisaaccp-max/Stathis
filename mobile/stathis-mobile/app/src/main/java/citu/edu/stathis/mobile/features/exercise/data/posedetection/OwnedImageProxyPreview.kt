package citu.edu.stathis.mobile.features.exercise.data.posedetection

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * Copies preview pixels while this thread still exclusively owns the [ImageProxy].
 * Never closes the proxy; [PoseAnalyzer] must [ImageProxy.close] once after ML Kit completes.
 */
object OwnedImageProxyPreview {

    fun copyWhileOwned(
        imageProxy: ImageProxy,
        onCopied: (Bitmap) -> Unit,
        convert: (ImageProxy) -> Bitmap? = { proxy ->
            runCatching { proxy.toBitmap() }.getOrNull()
        }
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
        convert: (ImageProxy) -> Bitmap? = { proxy ->
            runCatching { proxy.toBitmap() }.getOrNull()
        },
        startDetection: () -> Unit
    ) {
        try {
            if (onCopiedPreview != null) {
                copyWhileOwned(imageProxy, onCopiedPreview, convert)
            }
        } catch (_: RuntimeException) {
            // Preview copy must not skip pose detection or the guaranteed close.
        }
        startDetection()
    }
}
