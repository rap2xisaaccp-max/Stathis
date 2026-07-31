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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric facial recognition (MobileFaceNet embeddings):
 * detect → quality-gate → crop → 192-d embed → cosine match vs enrolled profile.
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
            .setMinFaceSize(0.18f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    data class DetectionResult(
        val faces: List<Face>,
        val embedding: FloatArray?,
        val qualityRejected: Boolean = false,
        val qualityMessage: String? = null
    )

    fun detectAndEmbed(image: InputImage, sourceBitmap: Bitmap): DetectionResult {
        val faces = Tasks.await(detector.process(image))
        val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return DetectionResult(faces = faces, embedding = null)

        val quality = assessFaceQuality(primary, sourceBitmap.width, sourceBitmap.height)
        if (!quality.ok) {
            return DetectionResult(
                faces = faces,
                embedding = null,
                qualityRejected = true,
                qualityMessage = quality.message
            )
        }

        val crop = cropFace(sourceBitmap, primary.boundingBox)
            ?: return DetectionResult(faces = faces, embedding = null)
        return try {
            DetectionResult(faces = faces, embedding = mobileFaceNetEmbedder.embed(crop))
        } finally {
            if (crop !== sourceBitmap) crop.recycle()
        }
    }

    fun detectAndEmbedFromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): DetectionResult {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return detectAndEmbed(image, bitmap)
    }

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

    private data class QualityResult(val ok: Boolean, val message: String?)

    private fun assessFaceQuality(face: Face, imageWidth: Int, imageHeight: Int): QualityResult {
        if (abs(face.headEulerAngleY) > MAX_YAW_DEG) {
            return QualityResult(false, "Face the camera directly.")
        }
        if (abs(face.headEulerAngleZ) > MAX_ROLL_DEG) {
            return QualityResult(false, "Keep your head level.")
        }
        if (abs(face.headEulerAngleX) > MAX_PITCH_DEG) {
            return QualityResult(false, "Hold your head upright.")
        }
        val box = face.boundingBox
        val areaRatio = (box.width().toFloat() * box.height()) /
                (imageWidth.toFloat() * imageHeight.toFloat()).coerceAtLeast(1f)
        if (areaRatio < MIN_FACE_AREA_RATIO) {
            return QualityResult(false, "Move closer so your face fills the frame.")
        }
        // Prefer faces fully inside the image
        if (box.left < 4 || box.top < 4 || box.right > imageWidth - 4 || box.bottom > imageHeight - 4) {
            return QualityResult(false, "Center your face in the frame.")
        }
        return QualityResult(true, null)
    }

    private fun cropFace(source: Bitmap, box: Rect): Bitmap? {
        val padding = (max(box.width(), box.height()) * 0.30f).toInt()
        val left = max(0, box.left - padding)
        val top = max(0, box.top - padding)
        val right = min(source.width, box.right + padding)
        val bottom = min(source.height, box.bottom + padding)
        val width = right - left
        val height = bottom - top
        if (width <= 24 || height <= 24) return null
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
     * Stable one-shot verification session.
     * Uses a rolling similarity window so brief flickers do not reset progress,
     * and requires sustained high similarity before accepting identity.
     */
    class MatchSession(
        private val requiredHits: Int = REQUIRED_MATCH_HITS,
        private val windowSize: Int = MATCH_WINDOW_SIZE
    ) {
        private val recentScores = ArrayDeque<Float>()
        private var acceptedHits = 0

        fun onProbe(
            service: FaceRecognitionService,
            probe: FloatArray?,
            enrolled: FloatArray,
            qualityMessage: String? = null
        ): MatchProgress {
            if (probe == null) {
                // Soft decay — do not wipe progress on a single empty frame
                if (acceptedHits > 0) acceptedHits = (acceptedHits - 1).coerceAtLeast(0)
                return MatchProgress(
                    verified = false,
                    hits = acceptedHits,
                    similarity = recentMedian(),
                    statusText = qualityMessage ?: "Looking for your face..."
                )
            }

            val (_, similarity) = service.verifyAgainstEnrollment(probe, enrolled)
            recentScores.addLast(similarity)
            while (recentScores.size > windowSize) recentScores.removeFirst()

            val median = recentMedian()
            if (similarity >= MATCH_THRESHOLD) {
                acceptedHits++
            } else if (similarity < MATCH_THRESHOLD - 0.08f) {
                // Clear mismatch — reset (different person)
                acceptedHits = 0
                return MatchProgress(
                    verified = false,
                    hits = 0,
                    similarity = similarity,
                    statusText = "Face does not match the registered student."
                )
            } else {
                // Borderline — hold progress
                acceptedHits = (acceptedHits - 1).coerceAtLeast(0)
            }

            val verified = acceptedHits >= requiredHits && median >= MATCH_THRESHOLD
            val status = when {
                verified -> "Identity verified"
                acceptedHits > 0 -> "Confirming identity… $acceptedHits/$requiredHits"
                else -> "Hold still and look at the camera."
            }
            return MatchProgress(verified, acceptedHits, similarity, status)
        }

        private fun recentMedian(): Float {
            if (recentScores.isEmpty()) return 0f
            val sorted = recentScores.sorted()
            return sorted[sorted.size / 2]
        }

        fun reset() {
            recentScores.clear()
            acceptedHits = 0
        }
    }

    data class MatchProgress(
        val verified: Boolean,
        val hits: Int,
        val similarity: Float,
        val statusText: String
    )

    companion object {
        /** Cosine similarity for MobileFaceNet same-person match. */
        const val MATCH_THRESHOLD = 0.68f
        const val REQUIRED_MATCH_HITS = 4
        const val MATCH_WINDOW_SIZE = 7
        private const val MAX_YAW_DEG = 22f
        private const val MAX_ROLL_DEG = 18f
        private const val MAX_PITCH_DEG = 22f
        private const val MIN_FACE_AREA_RATIO = 0.045f
    }
}