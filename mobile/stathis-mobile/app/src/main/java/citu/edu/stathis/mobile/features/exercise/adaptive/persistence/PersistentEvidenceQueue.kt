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
                // File promotion finishes before the Room transaction. A temp or
                // zero-length file is not enqueued and is not a captured marker.
                val file =
                    EvidenceJpegFiles.promote(context, event.interventionId, jpeg)
                        ?: throw java.io.IOException(
                            "Incomplete evidence JPEG for ${event.interventionId}"
                        )
                val recorded =
                    dao.recordEvidenceAndCapture(
                        QueuedEvidenceEntity(
                            interventionPhysicalId = event.interventionId,
                            payloadJson = gson.toJson(event),
                            filePath = file.absolutePath
                        ),
                        CapturedInterventionEntity(
                            interventionId = event.interventionId,
                            sessionId = event.sessionId,
                            exerciseType = event.exerciseType,
                            errorCode = event.errorCode.name,
                            cycleSeq = event.cycleSeq
                        )
                    )
                if (!recorded) {
                    throw java.io.IOException(
                        "Evidence row was not stored for ${event.interventionId}"
                    )
                }
            }
        }
    }

    override fun discardMissing(): List<String> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val discarded = mutableListOf<String>()
                dao.allEvidence().forEach { entity ->
                    val file = File(entity.filePath)
                    val readable =
                        runCatching {
                            if (!file.isFile || file.length() <= 0L) null else file.readBytes()
                        }.getOrNull()
                    if (readable == null) {
                        // Remove the unreadable file too. enqueue writes only when the
                        // path is absent, so leaving a zero-length file would block a
                        // later JPEG for this intervention id. This is not an upload.
                        runCatching { file.delete() }
                        dao.deleteEvidenceById(listOf(entity.id))
                        discarded += entity.interventionPhysicalId
                    }
                }
                discarded
            }
        }
    }

    override fun pending(): List<QueuedEvidence> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                dao.allEvidence().mapNotNull { entity ->
                    val event = gson.fromJson(entity.payloadJson, FormEvidenceEvent::class.java)
                    val bytes = runCatching { File(entity.filePath).readBytes() }.getOrNull()
                    if (bytes == null || bytes.isEmpty()) {
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
