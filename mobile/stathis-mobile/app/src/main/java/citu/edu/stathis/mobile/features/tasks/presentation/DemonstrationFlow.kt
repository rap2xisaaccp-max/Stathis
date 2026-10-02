package citu.edu.stathis.mobile.features.tasks.presentation

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Decides whether a task exercise opens the demonstration screen.
 * Watching a demonstration does not start a graded exercise session.
 * Practice never uses this gate.
 */
class DemonstrationFlow(
    val taskId: String,
    val exerciseTemplateId: String
) {
    var phase: DemonstrationPhase = DemonstrationPhase.Checking
        private set

    var sessionStarted: Boolean = false
        private set
    var reps: Int = 0
        private set
    var scoreAttempts: Int = 0
        private set
    var masteryUpdates: Int = 0
        private set
    var evidenceCaptures: Int = 0
        private set

    fun onMetadata(available: Boolean) {
        phase = if (available) {
            DemonstrationPhase.Demo(error = null, ready = false, filePath = null)
        } else {
            DemonstrationPhase.Identity
        }
    }

    fun onMetadataFailed(message: String) {
        phase = DemonstrationPhase.MetadataFailed(message)
    }

    fun onDownloadReady(filePath: String) {
        if (phase is DemonstrationPhase.Identity || phase is DemonstrationPhase.TaskDetails) {
            return
        }
        phase = DemonstrationPhase.Demo(error = null, ready = true, filePath = filePath)
    }

    fun onDownloadFailed(message: String) {
        if (phase is DemonstrationPhase.Identity || phase is DemonstrationPhase.TaskDetails) {
            return
        }
        phase = DemonstrationPhase.Demo(error = message, ready = false, filePath = null)
    }

    fun retry() {
        when (phase) {
            is DemonstrationPhase.MetadataFailed -> phase = DemonstrationPhase.Checking
            is DemonstrationPhase.Demo -> phase = DemonstrationPhase.Demo(error = null, ready = false, filePath = null)
            else -> Unit
        }
    }

    fun continueToIdentity() {
        if (phase is DemonstrationPhase.Demo) {
            phase = DemonstrationPhase.Identity
        }
    }

    fun back() {
        phase = DemonstrationPhase.TaskDetails
    }

    fun contentPath(): String {
        return "api/tasks/$taskId/exercises/$exerciseTemplateId/demonstration/content"
    }
}

sealed class DemonstrationPhase {
    data object Checking : DemonstrationPhase()
    data class MetadataFailed(val message: String) : DemonstrationPhase()
    data class Demo(val error: String?, val ready: Boolean, val filePath: String?) : DemonstrationPhase()
    data object Identity : DemonstrationPhase()
    data object TaskDetails : DemonstrationPhase()
}

fun demonstrationApplies(sessionContext: String): Boolean = sessionContext == "TASK"

object DemonstrationPlayback {
    const val UNABLE_TO_PLAY = "Unable to play demonstration. Please try again."

    fun formatClock(positionMs: Int): String {
        if (positionMs <= 0) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun canSeek(prepared: Boolean, durationMs: Int): Boolean = prepared && durationMs > 0

    fun clampSeek(positionMs: Int, durationMs: Int): Int {
        if (durationMs <= 0) return 0
        return positionMs.coerceIn(0, durationMs)
    }
}

object DemonstrationCache {
    fun write(input: InputStream, destination: File) {
        writeAtomically(input, destination, expectedBytes = null)
    }

    /**
     * Writes the full body to a temporary file, flushes it, checks the size, then
     * renames that file onto [destination]. The player must not open [destination]
     * until this returns.
     */
    fun writeAtomically(input: InputStream, destination: File, expectedBytes: Long?): Long {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".partial")
        var published = false
        try {
            FileOutputStream(temp).use { output ->
                input.copyTo(output)
                output.flush()
                try {
                    output.fd.sync()
                } catch (_: IOException) {
                    // A flush is enough when the platform cannot sync this descriptor.
                }
            }
            if (!isComplete(temp, expectedBytes)) {
                throw IOException("Demonstration download was empty or incomplete")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("Could not replace the cached demonstration")
            }
            moveIntoPlace(temp, destination)
            published = true
            if (!isComplete(destination, expectedBytes)) {
                throw IOException("Cached demonstration is missing")
            }
            return destination.length()
        } finally {
            if (!published) {
                delete(temp)
            }
        }
    }

    fun isComplete(file: File, expectedBytes: Long?): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return expectedBytes == null || expectedBytes <= 0L || file.length() == expectedBytes
    }

    fun versionToken(physicalId: String?, createdAt: String?, byteSize: Long?): String {
        val id = physicalId?.takeIf { it.isNotBlank() } ?: "novid"
        val time = createdAt?.takeIf { it.isNotBlank() } ?: "notime"
        val size = byteSize?.takeIf { it > 0L }?.toString() ?: "nosize"
        return sanitize("$id-$time-$size")
    }

    fun fileFor(
        directory: File,
        taskId: String,
        exerciseTemplateId: String,
        version: String,
        extension: String
    ): File {
        val name = "${sanitize(taskId)}_${sanitize(exerciseTemplateId)}_$version.$extension"
        return File(directory, name)
    }

    fun deleteOtherVersions(directory: File, taskId: String, exerciseTemplateId: String, keep: File) {
        val prefix = "${sanitize(taskId)}_${sanitize(exerciseTemplateId)}_"
        val legacy = "${sanitize(taskId)}_${sanitize(exerciseTemplateId)}."
        directory.listFiles()?.forEach { file ->
            if (file.absolutePath == keep.absolutePath) return@forEach
            val name = file.name
            if (name.startsWith(prefix) || name.startsWith(legacy)) {
                delete(file)
            }
        }
    }

    fun delete(file: File?) {
        if (file != null && file.exists()) {
            file.delete()
        }
    }

    private fun moveIntoPlace(temp: File, destination: File) {
        try {
            Files.move(
                temp.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
