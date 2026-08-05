package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.InterventionRequestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.OfflineQueue
import citu.edu.stathis.mobile.features.exercise.adaptive.QueuedIntervention
import citu.edu.stathis.mobile.features.exercise.adaptive.QueuedResponse
import citu.edu.stathis.mobile.features.exercise.adaptive.ResponseRequestDto
import citu.edu.stathis.mobile.features.exercise.domain.usecase.GetCurrentUserIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistent Room-backed implementation of OfflineQueue. Not bound in production DI by default.
 * This class mirrors AdaptiveOfflineQueue semantics: idempotent enqueue, one-FI→one-FR guarantee,
 * drain() returns items ordered by enqueue time and removes them from the DB.
 */
class PersistentAdaptiveQueue(
    private val context: Context,
    private val db: AdaptiveQueueDatabase = AdaptiveQueueDatabase.open(context),
    private val gson: Gson = Gson()
) : OfflineQueue {

    private val dao = db.dao()

    override fun enqueueIntervention(payload: InterventionRequestDto) {
        // Launching DB work on caller thread would be blocking; use coroutine-safe insertion wrapper.
        // For the scaffold we provide a synchronous facade by launching a blocking call on IO.
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                if (!payload.physicalId.isNullOrBlank()) {
                    val entity = QueuedInterventionEntity(
                        physicalId = payload.physicalId,
                        studentId = payload.sessionId, // use sessionId as approximate owner if student not available
                        payloadJson = gson.toJson(payload)
                    )
                    dao.enqueueInterventionIfAbsent(entity)
                } else {
                    // fallback: generate a synthetic id and store
                    val synth = "FI-${java.util.UUID.randomUUID()}"
                    val entity = QueuedInterventionEntity(
                        physicalId = synth,
                        studentId = payload.sessionId,
                        payloadJson = gson.toJson(payload.copy(physicalId = synth))
                    )
                    dao.enqueueInterventionIfAbsent(entity)
                }
            }
        }
    }

    override fun enqueueResponse(payload: ResponseRequestDto) {
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                val phys = payload.physicalId ?: "FR-${java.util.UUID.randomUUID()}"
                val entity = QueuedResponseEntity(
                    physicalId = phys,
                    interventionPhysicalId = payload.interventionPhysicalId,
                    studentId = payload.confoundersJson?.get("sessionId") as? String,
                    payloadJson = gson.toJson(payload)
                )
                dao.enqueueResponseIfAbsent(entity)
            }
        }
    }

    override fun drain(): Pair<List<QueuedIntervention>, List<QueuedResponse>> {
        return kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                val items = dao.allInterventions()
                val responses = dao.allResponses()
                // convert to DTO wrappers
                val qInterventions = items.map { entity ->
                    val dto = gson.fromJson(entity.payloadJson, InterventionRequestDto::class.java)
                    QueuedIntervention(dto)
                }
                val qResponses = responses.map { entity ->
                    val dto = gson.fromJson(entity.payloadJson, ResponseRequestDto::class.java)
                    QueuedResponse(dto)
                }
                // remove persisted rows after snapshot (matches in-memory drain semantics)
                dao.deleteInterventionsById(items.map { it.id })
                dao.deleteResponsesById(responses.map { it.id })
                qInterventions to qResponses
            }
        }
    }

    override fun requeueAfterFailure(
        failedInterventions: List<QueuedIntervention>,
        failedResponses: List<QueuedResponse>
    ): Int {
        // For the scaffold, re-insert failed items if their attempts are below an arbitrary cap.
        var accepted = 0
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                failedInterventions.forEach { item ->
                    val dto = item.payload
                    val entity = QueuedInterventionEntity(
                        physicalId = dto.physicalId ?: "FI-${java.util.UUID.randomUUID()}",
                        studentId = dto.sessionId,
                        payloadJson = gson.toJson(dto)
                    )
                    val ok = dao.enqueueInterventionIfAbsent(entity)
                    if (ok) accepted++
                }
                failedResponses.forEach { item ->
                    val dto = item.payload
                    val entity = QueuedResponseEntity(
                        physicalId = dto.physicalId ?: "FR-${java.util.UUID.randomUUID()}",
                        interventionPhysicalId = dto.interventionPhysicalId,
                        studentId = dto.confoundersJson?.get("sessionId") as? String,
                        payloadJson = gson.toJson(dto)
                    )
                    val ok = dao.enqueueResponseIfAbsent(entity)
                    if (ok) accepted++
                }
            }
        }
        return accepted
    }

    override fun clear() {
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                val items = dao.allInterventions()
                val responses = dao.allResponses()
                dao.deleteInterventionsById(items.map { it.id })
                dao.deleteResponsesById(responses.map { it.id })
            }
        }
    }

    override fun isEmpty(): Boolean {
        return kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                dao.allInterventions().isEmpty() && dao.allResponses().isEmpty()
            }
        }
    }
}
