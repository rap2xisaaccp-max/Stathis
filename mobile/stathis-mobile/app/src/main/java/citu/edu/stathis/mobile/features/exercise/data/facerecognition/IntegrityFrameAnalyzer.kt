package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Combined CameraX analyzer for continuous exercise identity integrity.
 *
 * Runs pose estimation every tick (reps + skeletal identity) and periodically
 * runs facial recognition on the same frame so face-out exercises still get
 * opportunistic face checks without a separate camera pipeline.
 */
class IntegrityFrameAnalyzer(
    private val executor: Executor,
    private val faceRecognitionService: FaceRecognitionService,
    private val onFrame: (IntegrityFrame) -> Unit,
    private val poseIntervalMs: Long = 100L,
    private val faceIntervalMs: Long = 1_800L,
    private val isImageFlipped: Boolean = false
) : ImageAnalysis.Analyzer {

    data class IntegrityFrame(
        val pose: Pose?,
        val frameWidth: Int,
        val frameHeight: Int,
        val rotation: Int,
        val flipped: Boolean,
        val faceEmbedding: FloatArray?,
        val faceCount: Int,
        val faceQualityRejected: Boolean,
        val faceQualityMessage: String?,
        val ranFacePass: Boolean
    )

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private var lastPoseMs = 0L
    private var lastFaceMs = 0L
    private var busy = false
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (busy || now - lastPoseMs < poseIntervalMs) {
            imageProxy.close()
            return
        }
        lastPoseMs = now
        if (imageProxy.image == null) {
            imageProxy.close()
            return
        }

        busy = true
        val runFace = now - lastFaceMs >= faceIntervalMs
        if (runFace) lastFaceMs = now

        executor.execute {
            var bitmap: Bitmap? = null
            var rotated: Bitmap? = null
            try {
                val rotation = imageProxy.imageInfo.rotationDegrees
                bitmap = imageProxy.toBitmap()
                rotated = rotateBitmap(bitmap, rotation)
                if (rotated !== bitmap) {
                    bitmap.recycle()
                    bitmap = null
                }

                val frameBitmap = rotated ?: return@execute
                val inputImage = InputImage.fromBitmap(frameBitmap, 0)
                val pose = try {
                    Tasks.await(poseDetector.process(inputImage), 800, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    null
                }

                var faceEmbedding: FloatArray? = null
                var faceCount = 0
                var qualityRejected = false
                var qualityMessage: String? = null
                if (runFace) {
                    try {
                        val faceResult = faceRecognitionService.detectAndEmbedFromBitmap(frameBitmap, 0)
                        faceEmbedding = faceResult.embedding
                        faceCount = faceResult.faces.size
                        qualityRejected = faceResult.qualityRejected
                        qualityMessage = faceResult.qualityMessage
                    } catch (_: Exception) {
                        // Face pass is best-effort.
                    }
                }

                val frame = IntegrityFrame(
                    pose = pose,
                    frameWidth = frameBitmap.width,
                    frameHeight = frameBitmap.height,
                    rotation = 0,
                    flipped = isImageFlipped,
                    faceEmbedding = faceEmbedding,
                    faceCount = faceCount,
                    faceQualityRejected = qualityRejected,
                    faceQualityMessage = qualityMessage,
                    ranFacePass = runFace
                )
                mainHandler.post { onFrame(frame) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                rotated?.recycle()
                bitmap?.recycle()
                busy = false
                imageProxy.close()
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun close() {
        poseDetector.close()
    }
}
