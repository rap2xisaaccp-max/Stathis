package citu.edu.stathis.mobile.features.exercise.data.posedetection

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import citu.edu.stathis.mobile.features.exercise.adaptive.LatestFrameBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OwnedImageProxyPreviewTest {

    @Test
    fun copyThenStartDetectionConvertsBeforeCloseAndNeverAfter() {
        val events = mutableListOf<String>()
        val proxy = ClosedTrackingImageProxy { events += "close" }

        OwnedImageProxyPreview.copyThenStartDetection(
            imageProxy = proxy,
            onCopiedPreview = { events += "copied" },
            convert = {
                assertFalse("ImageProxy converted after close", proxy.closed)
                events += "convert"
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            },
            startDetection = {
                events += "process"
                proxy.close()
            }
        )

        assertEquals(listOf("convert", "copied", "process", "close"), events)
        assertTrue(proxy.closed)
        assertEquals(0, proxy.formatReadsAfterClose)
        assertEquals(0, proxy.planeReadsAfterClose)
    }

    @Test
    fun snapshotPathFillsBufferBeforeOwnershipReleased() {
        val buffer = LatestFrameBuffer()
        val proxy = ClosedTrackingImageProxy()

        OwnedImageProxyPreview.copyThenStartDetection(
            imageProxy = proxy,
            onCopiedPreview = { bitmap ->
                assertFalse(proxy.closed)
                buffer.updateFromBitmap(bitmap)
            },
            convert = {
                assertFalse("toBitmap-equivalent after close", proxy.closed)
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            },
            startDetection = { proxy.close() }
        )

        assertTrue(proxy.closed)
        val jpeg = buffer.copyJpeg()
        assertNotNull(jpeg)
        assertTrue(jpeg!!.isNotEmpty())
    }

    @Test
    fun skipsPixelAccessWhenNoPreviewConsumer() {
        var converted = false
        val proxy = ClosedTrackingImageProxy()

        OwnedImageProxyPreview.copyThenStartDetection(
            imageProxy = proxy,
            onCopiedPreview = null,
            convert = {
                converted = true
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            },
            startDetection = { proxy.close() }
        )

        assertFalse(converted)
        assertTrue(proxy.closed)
    }

    @Test
    fun previewCopyFailureStillStartsDetectionSoCloseCanRun() {
        val proxy = ClosedTrackingImageProxy()
        var detectionStarted = false

        OwnedImageProxyPreview.copyThenStartDetection(
            imageProxy = proxy,
            onCopiedPreview = { error("copy consumer failed") },
            convert = { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) },
            startDetection = {
                detectionStarted = true
                proxy.close()
            }
        )

        assertTrue(detectionStarted)
        assertTrue(proxy.closed)
    }
}

/**
 * Records native-style pixel access after [close], matching the SIGSEGV we must never reintroduce.
 */
@SuppressLint("UnsafeOptInUsageError")
private class ClosedTrackingImageProxy(
    private val onClosed: () -> Unit = {}
) : ImageProxy {
    var closed: Boolean = false
        private set
    var formatReadsAfterClose: Int = 0
        private set
    var planeReadsAfterClose: Int = 0
        private set

    override fun close() {
        if (!closed) {
            closed = true
            onClosed()
        }
    }

    override fun getCropRect(): Rect = Rect(0, 0, width, height)

    override fun setCropRect(rect: Rect?) = Unit

    override fun getFormat(): Int {
        if (closed) formatReadsAfterClose += 1
        return ImageFormat.YUV_420_888
    }

    override fun getHeight(): Int = 8

    override fun getWidth(): Int = 8

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> {
        if (closed) planeReadsAfterClose += 1
        return emptyArray()
    }

    override fun getImageInfo(): ImageInfo {
        error("ImageInfo is not used by OwnedImageProxyPreview tests")
    }

    override fun getImage(): Image? = null
}
