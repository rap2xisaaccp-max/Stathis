package citu.edu.stathis.mobile.features.tasks.presentation

import java.io.File
import java.io.InputStream

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

object DemonstrationCache {
    fun write(input: InputStream, destination: File) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> input.copyTo(output) }
    }

    fun delete(file: File?) {
        if (file != null && file.exists()) {
            file.delete()
        }
    }
}
