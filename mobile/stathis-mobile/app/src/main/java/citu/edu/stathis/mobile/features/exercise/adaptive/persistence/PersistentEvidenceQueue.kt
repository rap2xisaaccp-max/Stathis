package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.EvidenceQueue
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceEvent
import citu.edu.stathis.mobile.features.exercise.adaptive.JpegCompressor
import citu.edu.stathis.mobile.features.exercise.adaptive.QueuedEvidence
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PersistentEvidenceQueue(
    private val context: Context,
    private val db: AdaptiveQueueDatabase,
    private val gson: Gson = Gson()
) : EvidenceQueue {

    private val dao = db.dao()

    override fun enqueue(event: FormEvidenceEvent, jpeg: ByteArray) {
        if (event.interventionId.isBlank() || !JpegCompressor.isAcceptableSize(jpeg)) return
        runBlocking {
            withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "form-evidence").apply { mkdirs() }
                val file = File(dir, "${event.interventionId}.jpg")
                if (!file.exists()) {
                    file.writeBytes(jpeg)
                }
                dao.enqueueEvidenceIfAbsent(
                    QueuedEvidenceEntity(
                        interventionPhysicalId = event.interventionId,
                        payloadJson = gson.toJson(event),
                        filePath = file.absolutePath
                    )
                )
            }
        }
    }

    override fun pending(): List<QueuedEvidence> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                dao.allEvidence().mapNotNull { entity ->
                    val event = gson.fromJson(entity.payloadJson, FormEvidenceEvent::class.java)
                    val bytes = runCatching { File(entity.filePath).readBytes() }.getOrNull()
                    if (bytes == null) {
                        null
                    } else {
                        QueuedEvidence(event, bytes)
                    }
                }
            }
        }
    }

    override fun acknowledge(interventionId: String) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val entity = dao.findEvidenceByInterventionId(interventionId) ?: return@withContext
                File(entity.filePath).delete()
                dao.deleteEvidenceById(listOf(entity.id))
            }
        }
    }

    override fun requeueAfterFailure(failed: List<QueuedEvidence>): Int {
        var accepted = 0
        failed.forEach { item ->
            enqueue(item.event, item.jpeg)
            accepted++
        }
        return accepted
    }

    override fun isEmpty(): Boolean {
        return runBlocking {
            withContext(Dispatchers.IO) { dao.allEvidence().isEmpty() }
        }
    }

    override fun clear() {
        runBlocking {
            withContext(Dispatchers.IO) {
                val rows = dao.allEvidence()
                rows.forEach { File(it.filePath).delete() }
                dao.deleteEvidenceById(rows.map { it.id })
            }
        }
    }
}
