package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric facial recognition:
 * 1) ML Kit detects the face
 * 2) Face crop is embedded with MobileFaceNet (192-d)
 * 3) Cosine similarity is compared against the enrolled embedding stored in Supabase/Postgres
 *
 * Landmark-only matching is intentionally not used — different people must not pass.
 */
@Singleton
class FaceRecognitionService @Inject constructor(
    private val mobileFaceNetEmbedder: MobileFaceNetEmbedder
) {

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.2f)
            .build()
        FaceDetection.getClient(options)
    }

    data class DetectionResult(
        val faces: List<Face>,
        val embedding: FloatArray?,
        val similarity: Float? = null,
        val matched: Boolean = false
    )

    fun detectAndEmbed(image: InputImage, sourceBitmap: Bitmap? = null): DetectionResult {
        val faces = Tasks.await(detector.process(image))
        val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return DetectionResult(faces = faces, embedding = null)

        val bitmap = sourceBitmap ?: return DetectionResult(faces = faces, embedding = null)
        val crop = cropFace(bitmap, primary.boundingBox) ?: return DetectionResult(faces = faces, embedding = null)
        return try {
            val embedding = mobileFaceNetEmbedder.embed(crop)
            DetectionResult(faces = faces, embedding = embedding)
        } finally {
            if (crop !== bitmap) crop.recycle()
        }
    }

    fun detectAndEmbedFromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): DetectionResult {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return detectAndEmbed(image, bitmap)
    }

    /**
     * Analyze a CameraX frame: convert YUV → Bitmap, detect face, embed with MobileFaceNet.
     */
    fun analyzeImageProxy(imageProxy: ImageProxy): DetectionResult {
        val rotation = imageProxy.imageInfo.rotationDegrees
        val bitmap = imageProxy.toBitmap()
        val rotated = rotateBitmap(bitmap, rotation)
        if (rotated !== bitmap) bitmap.recycle()
        return try {
            detectAndEmbedFromBitmap(rotated, 0)
        } finally {
            rotated.recycle()
        }
    }

    fun verifyAgainstEnrollment(
        probe: FloatArray,
        enrolled: FloatArray,
        threshold: Float = MATCH_THRESHOLD
    ): Pair<Boolean, Float> {
        val similarity = cosineSimilarity(probe, enrolled)
        return (similarity >= threshold) to similarity
    }

    fun isMatch(probe: FloatArray, enrolled: FloatArray, threshold: Float = MATCH_THRESHOLD): Boolean {
        return verifyAgainstEnrollment(probe, enrolled, threshold).first
    }

    fun embeddingToJson(embedding: FloatArray): String {
        return embedding.joinToString(prefix = "[", postfix = "]") { "%.6f".format(it) }
    }

    fun embeddingFromJson(json: String): FloatArray? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return floatArrayOf()
        return try {
            val values = inner.split(",").map { it.trim().toFloat() }.toFloatArray()
            // Reject legacy landmark vectors (~22 dims) so users re-register with FaceNet embeddings
            if (values.size != MobileFaceNetEmbedder.EMBEDDING_SIZE) null else values
        } catch (_: Exception) {
            null
        }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom == 0.0) return 0f
        return (dot / denom).toFloat()
    }

    private fun cropFace(source: Bitmap, box: Rect): Bitmap? {
        val padding = (max(box.width(), box.height()) * 0.25f).toInt()
        val left = max(0, box.left - padding)
        val top = max(0, box.top - padding)
        val right = min(source.width, box.right + padding)
        val bottom = min(source.height, box.bottom + padding)
        val width = right - left
        val height = bottom - top
        if (width <= 16 || height <= 16) return null
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun close() {
        detector.close()
        mobileFaceNetEmbedder.close()
    }

    /**
     * Requires several consecutive biometric matches before accepting identity.
     * Prevents a single lucky/noisy frame from unlocking another user's session.
     */
    class MatchSession(
        private val requiredConsecutiveMatches: Int = REQUIRED_CONSECUTIVE_MATCHES
    ) {
        private var consecutiveMatches = 0
        private var lastSimilarity = 0f

        fun onProbe(
            service: FaceRecognitionService,
            probe: FloatArray?,
            enrolled: FloatArray
        ): MatchProgress {
            if (probe == null) {
                consecutiveMatches = 0
                return MatchProgress(false, consecutiveMatches, lastSimilarity, "Looking for your face...")
            }
            val (matched, similarity) = service.verifyAgainstEnrollment(probe, enrolled)
            lastSimilarity = similarity
            if (matched) {
                consecutiveMatches++
                val verified = consecutiveMatches >= requiredConsecutiveMatches
                val status = if (verified) {
                    "Identity verified"
                } else {
                    "Confirming identity… ${consecutiveMatches}/$requiredConsecutiveMatches"
                }
                return MatchProgress(verified, consecutiveMatches, similarity, status)
            }
            consecutiveMatches = 0
            return MatchProgress(
                verified = false,
                consecutiveMatches = 0,
                similarity = similarity,
                statusText = "Face does not match the assigned student."
            )
        }

        fun reset() {
            consecutiveMatches = 0
            lastSimilarity = 0f
        }
    }

    data class MatchProgress(
        val verified: Boolean,
        val consecutiveMatches: Int,
        val similarity: Float,
        val statusText: String
    )

    companion object {
        /** Cosine similarity threshold for MobileFaceNet same-person matching. */
        const val MATCH_THRESHOLD = 0.70f
        const val REQUIRED_CONSECUTIVE_MATCHES = 3
    }
}
