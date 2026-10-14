package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import citu.edu.stathis.mobile.features.exercise.adaptive.JpegCompressor
import java.io.File

/**
 * JPEG promotion for one intervention id.
 *
 * A `.jpg.tmp` file is never a finished snapshot. The final `.jpg` is published only
 * after the temp file is fully written and its size is acceptable. File IO stays
 * outside Room transactions; callers insert queue rows after [promote] returns.
 */
internal object EvidenceJpegFiles {
    private val lock = Any()

    fun finalFile(context: Context, interventionId: String): File =
        File(directory(context), "$interventionId.jpg")

    fun tempFile(context: Context, interventionId: String): File =
        File(directory(context), "$interventionId.jpg.tmp")

    fun isFinishedSnapshot(file: File): Boolean =
        file.isFile && file.length() > 0L && file.length() <= JpegCompressor.MAX_BYTES

    /**
     * Writes [jpeg] to a temp file and renames it onto the final path when that path
     * is missing or empty. An existing non-empty final file is left unchanged.
     * Returns null when the bytes or the temp file are not a finished snapshot.
     */
    fun promote(context: Context, interventionId: String, jpeg: ByteArray): File? {
        if (interventionId.isBlank() || !JpegCompressor.isAcceptableSize(jpeg)) return null
        return synchronized(lock) {
            val dir = directory(context)
            if (!dir.exists() && !dir.mkdirs()) return@synchronized null
            val finalFile = finalFile(context, interventionId)
            val temp = tempFile(context, interventionId)
            if (isFinishedSnapshot(finalFile)) {
                if (temp.exists()) temp.delete()
                return@synchronized finalFile
            }
            if (finalFile.exists()) finalFile.delete()
            if (temp.exists()) temp.delete()
            temp.writeBytes(jpeg)
            if (temp.length() != jpeg.size.toLong() || !isFinishedSnapshot(temp)) {
                temp.delete()
                return@synchronized null
            }
            if (!publishTemp(temp, finalFile) || !isFinishedSnapshot(finalFile)) {
                temp.delete()
                if (!isFinishedSnapshot(finalFile)) finalFile.delete()
                return@synchronized null
            }
            finalFile
        }
    }

    fun classifyForRecovery(context: Context, interventionId: String): JpegPresence {
        if (interventionId.isBlank()) return JpegPresence.Incomplete
        return synchronized(lock) {
            val finalFile = finalFile(context, interventionId)
            val temp = tempFile(context, interventionId)
            if (isFinishedSnapshot(finalFile)) {
                if (temp.exists()) temp.delete()
                JpegPresence.ValidFinal(finalFile)
            } else {
                if (temp.exists()) temp.delete()
                if (finalFile.exists()) finalFile.delete()
                JpegPresence.Incomplete
            }
        }
    }

    /** Removes an incomplete temp file only. A finished `.jpg` is left on disk. */
    fun discardTemp(context: Context, interventionId: String) {
        if (interventionId.isBlank()) return
        synchronized(lock) {
            val temp = tempFile(context, interventionId)
            if (temp.exists()) temp.delete()
        }
    }

    private fun publishTemp(temp: File, finalFile: File): Boolean {
        if (isFinishedSnapshot(finalFile)) {
            temp.delete()
            return true
        }
        if (finalFile.exists()) finalFile.delete()
        if (temp.renameTo(finalFile) && isFinishedSnapshot(finalFile)) return true
        return runCatching {
            temp.copyTo(finalFile, overwrite = true)
            temp.delete()
            isFinishedSnapshot(finalFile)
        }.getOrDefault(false)
    }

    private fun directory(context: Context): File = File(context.filesDir, "form-evidence")
}

internal sealed class JpegPresence {
    data class ValidFinal(val file: File) : JpegPresence()
    data object Incomplete : JpegPresence()
}
