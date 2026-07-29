package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MobileFaceNet (112x112 → 192-d L2-normalized embedding) for biometric identity matching.
 * Model asset: FaceMobileNet_Float32.tflite (sirius-ai MobileFaceNet).
 */
@Singleton
class MobileFaceNetEmbedder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val interpreter: Interpreter by lazy {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        Interpreter(loadModelFile(MODEL_ASSET), options)
    }

    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        .order(ByteOrder.nativeOrder())

    private val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }

    @Synchronized
    fun embed(faceBitmap: Bitmap): FloatArray {
        val prepared = prepareFaceBitmap(faceBitmap)
        fillInputBuffer(prepared)
        if (prepared !== faceBitmap) {
            prepared.recycle()
        }
        outputBuffer[0].fill(0f)
        interpreter.run(inputBuffer, outputBuffer)
        return l2Normalize(outputBuffer[0].copyOf())
    }

    private fun prepareFaceBitmap(source: Bitmap): Bitmap {
        val squared = toSquareBitmap(source)
        val scaled = if (squared.width == INPUT_SIZE && squared.height == INPUT_SIZE) {
            squared
        } else {
            Bitmap.createScaledBitmap(squared, INPUT_SIZE, INPUT_SIZE, true).also {
                if (it !== squared) squared.recycle()
            }
        }
        return scaled
    }

    private fun toSquareBitmap(source: Bitmap): Bitmap {
        val size = max(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val left = (size - source.width) / 2f
        val top = (size - source.height) / 2f
        canvas.drawBitmap(source, left, top, null)
        return output
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index++]
            // MobileFaceNet typically expects RGB normalized to [-1, 1]
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            inputBuffer.putFloat((r - 127.5f) / 128f)
            inputBuffer.putFloat((g - 127.5f) / 128f)
            inputBuffer.putFloat((b - 127.5f) / 128f)
        }
        inputBuffer.rewind()
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in values) sumSquares += v * v
        val norm = sqrt(sumSquares).toFloat().coerceAtLeast(1e-10f)
        for (i in values.indices) {
            values[i] = values[i] / norm
        }
        return values
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)
        FileInputStream(fileDescriptor.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    fun close() {
        try {
            interpreter.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MODEL_ASSET = "FaceMobileNet_Float32.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192
    }
}
