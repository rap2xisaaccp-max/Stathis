package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executor

/**
 * CameraX analyzer for biometric facial recognition (registration / verification).
 * Uses ML Kit face detection + MobileFaceNet embeddings — not landmark-only matching.
 */
class FaceAnalyzer(
    private val executor: Executor,
    private val faceRecognitionService: FaceRecognitionService,
    private val onResult: (FaceRecognitionService.DetectionResult) -> Unit,
    private val minAnalysisIntervalMs: Long = 250L
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTimestampMs: Long = 0L
    private var busy = false
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (busy || now - lastAnalysisTimestampMs < minAnalysisIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalysisTimestampMs = now
        if (imageProxy.image == null) {
            imageProxy.close()
            return
        }

        busy = true
        executor.execute {
            try {
                val result = faceRecognitionService.analyzeImageProxy(imageProxy)
                mainHandler.post { onResult(result) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                busy = false
                imageProxy.close()
            }
        }
    }
}