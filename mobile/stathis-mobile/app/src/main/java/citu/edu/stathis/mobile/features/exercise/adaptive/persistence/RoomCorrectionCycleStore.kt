package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import citu.edu.stathis.mobile.features.exercise.adaptive.CaptureRecovery
import citu.edu.stathis.mobile.features.exercise.adaptive.CorrectionCycleStore
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorCode
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceEvent
import citu.edu.stathis.mobile.features.exercise.adaptive.InterventionPhase
import citu.edu.stathis.mobile.features.exercise.adaptive.PersistedOpenCycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Room-backed cycle row. Database transactions cover row writes only.
 * JPEG inspection happens before those transactions.
 */
class RoomCorrectionCycleStore(
    private val db: AdaptiveQueueDatabase,
    private val gson: Gson,
    private val context: Context
) : CorrectionCycleStore {
    private val dao = db.dao()
    private val longListType = object : TypeToken<List<Long>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val escalationType = object : TypeToken<Map<String, Int>>() {}.type

    override fun save(cycle: PersistedOpenCycle) = io {
        dao.upsertOpenCycle(
            OpenCorrectionCycleEntity(
                id = OpenCorrectionCycleEntity.SINGLETON_ID,
                sessionId = cycle.sessionId,
                exerciseType = cycle.exerciseType,
                taskId = cycle.taskId,
                classroomId = cycle.classroomId,
                attemptNumber = cycle.attemptNumber,
                phase = cycle.phase.name,
                pendingCode = cycle.pendingCode?.name,
                pendingTicks = cycle.pendingTicks,
                clearTicks = cycle.clearTicks,
                openError = cycle.openError?.name,
                deliveredAt = cycle.deliveredAt,
                lastInterventionAt = cycle.lastInterventionAt,
                cycleSeq = cycle.cycleSeq,
                recentInterventionAtJson = gson.toJson(cycle.recentInterventionAt),
                escalationCountsJson = gson.toJson(cycle.escalationCounts.mapKeys { it.key.name }),
                sessionInterventionCount = cycle.sessionInterventionCount,
                sessionErrorCodesJson = gson.toJson(cycle.sessionErrorCodes),
                sessionRecorded = cycle.sessionRecorded,
                claimedEvidenceJson = cycle.claimedEvidence?.let { gson.toJson(it) }
            )
        )
    }

    override fun load(): PersistedOpenCycle? = io { loadUnlocked() }

    private fun loadUnlocked(): PersistedOpenCycle? {
        val row = dao.loadOpenCycle() ?: return null
        val phase = enumOrNull<InterventionPhase>(row.phase) ?: return null
        val pending = row.pendingCode?.let { enumOrNull<FormErrorCode>(it) } ?: if (row.pendingCode != null) return null else null
        val open = row.openError?.let { enumOrNull<FormErrorCode>(it) } ?: if (row.openError != null) return null else null
        val recent = runCatching {
            gson.fromJson<List<Long>>(row.recentInterventionAtJson, longListType) ?: emptyList()
        }.getOrDefault(emptyList())
        val codes = runCatching {
            gson.fromJson<List<String>>(row.sessionErrorCodesJson, stringListType) ?: emptyList()
        }.getOrDefault(emptyList())
        val escalation = runCatching {
            val raw = gson.fromJson<Map<String, Int>>(row.escalationCountsJson, escalationType).orEmpty()
            raw.mapNotNull { (name, count) ->
                enumOrNull<FormErrorCode>(name)?.let { it to count }
            }.toMap()
        }.getOrDefault(emptyMap())
        val claimed = row.claimedEvidenceJson?.let { json ->
            runCatching { gson.fromJson(json, FormEvidenceEvent::class.java) }.getOrNull()
        }
        return PersistedOpenCycle(
            sessionId = row.sessionId,
            exerciseType = row.exerciseType,
            taskId = row.taskId,
            classroomId = row.classroomId,
            attemptNumber = row.attemptNumber,
            phase = phase,
            pendingCode = pending,
            pendingTicks = row.pendingTicks,
            clearTicks = row.clearTicks,
            openError = open,
            deliveredAt = row.deliveredAt,
            lastInterventionAt = row.lastInterventionAt,
            cycleSeq = row.cycleSeq,
            recentInterventionAt = recent,
            escalationCounts = escalation,
            sessionInterventionCount = row.sessionInterventionCount,
            sessionErrorCodes = codes,
            sessionRecorded = row.sessionRecorded,
            claimedEvidence = claimed
        )
    }

    override fun abandon(sessionId: String?) = io {
        val saved = loadUnlocked()
        val claimedId = saved?.claimedEvidence?.interventionId
        if (!claimedId.isNullOrBlank()) {
            EvidenceJpegFiles.discardTemp(context, claimedId)
        }
        val sessionIds = listOfNotNull(
            sessionId?.takeIf { it.isNotBlank() },
            saved?.sessionId?.takeIf { it.isNotBlank() }
        )
        dao.abandonCycle(sessionIds)
    }

    override fun recoverCapture(event: FormEvidenceEvent): CaptureRecovery = io {
        if (event.interventionId.isBlank()) return@io CaptureRecovery.NotEligible
        if (dao.findCaptured(event.interventionId) != null) return@io CaptureRecovery.AlreadyCaptured
        if (dao.evidenceExists(event.interventionId) > 0) {
            dao.insertCapturedIfEvidencePresent(capturedRow(event))
            return@io if (dao.findCaptured(event.interventionId) != null) {
                CaptureRecovery.AlreadyCaptured
            } else {
                CaptureRecovery.NeedsCompose
            }
        }
        when (val presence = EvidenceJpegFiles.classifyForRecovery(context, event.interventionId)) {
            is JpegPresence.ValidFinal -> {
                val recorded =
                    dao.recordEvidenceAndCapture(
                        QueuedEvidenceEntity(
                            interventionPhysicalId = event.interventionId,
                            payloadJson = gson.toJson(event),
                            filePath = presence.file.absolutePath
                        ),
                        capturedRow(event)
                    )
                if (recorded) CaptureRecovery.RecordedFromExistingJpeg else CaptureRecovery.NeedsCompose
            }
            JpegPresence.Incomplete -> CaptureRecovery.NeedsCompose
        }
    }

    override fun capturedIdsForSession(sessionId: String): Set<String> = io {
        if (sessionId.isBlank()) return@io emptySet()
        dao.capturedIdsForSession(sessionId).toSet()
    }

    private fun <T> io(block: () -> T): T =
        runBlocking {
            withContext(Dispatchers.IO) { block() }
        }

    private fun capturedRow(event: FormEvidenceEvent) =
        CapturedInterventionEntity(
            interventionId = event.interventionId,
            sessionId = event.sessionId,
            exerciseType = event.exerciseType,
            errorCode = event.errorCode.name,
            cycleSeq = event.cycleSeq
        )

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()
}
