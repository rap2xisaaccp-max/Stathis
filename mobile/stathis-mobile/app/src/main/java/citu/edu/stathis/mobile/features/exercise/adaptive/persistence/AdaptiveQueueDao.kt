package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AdaptiveQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIntervention(entity: QueuedInterventionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertResponse(entity: QueuedResponseEntity): Long

    @Query("SELECT * FROM queued_interventions ORDER BY created_at_epoch_ms ASC")
    fun allInterventions(): List<QueuedInterventionEntity>

    @Query("SELECT * FROM queued_responses ORDER BY created_at_epoch_ms ASC")
    fun allResponses(): List<QueuedResponseEntity>

    @Query("DELETE FROM queued_interventions WHERE id IN (:ids)")
    fun deleteInterventionsById(ids: List<Long>)

    @Query("DELETE FROM queued_responses WHERE id IN (:ids)")
    fun deleteResponsesById(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM queued_interventions WHERE physical_id = :physicalId")
    fun interventionExists(physicalId: String): Int

    @Query("SELECT COUNT(*) FROM queued_responses WHERE intervention_physical_id = :fi")
    fun responseExistsForIntervention(fi: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvidence(entity: QueuedEvidenceEntity): Long

    @Query("SELECT * FROM queued_evidence ORDER BY created_at_epoch_ms ASC")
    fun allEvidence(): List<QueuedEvidenceEntity>

    @Query("DELETE FROM queued_evidence WHERE id IN (:ids)")
    fun deleteEvidenceById(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM queued_evidence WHERE intervention_physical_id = :interventionId")
    fun evidenceExists(interventionId: String): Int

    @Query("SELECT * FROM queued_evidence WHERE intervention_physical_id = :interventionId LIMIT 1")
    fun findEvidenceByInterventionId(interventionId: String): QueuedEvidenceEntity?

    @Transaction
    fun enqueueInterventionIfAbsent(entity: QueuedInterventionEntity): Boolean {
        val exists = interventionExists(entity.physicalId) > 0
        if (exists) return false
        val row = insertIntervention(entity)
        return row != -1L
    }

    @Transaction
    fun enqueueResponseIfAbsent(entity: QueuedResponseEntity): Boolean {
        val exists = responseExistsForIntervention(entity.interventionPhysicalId) > 0
        if (exists) return false
        val row = insertResponse(entity)
        return row != -1L
    }

    @Transaction
    fun enqueueEvidenceIfAbsent(entity: QueuedEvidenceEntity): Boolean {
        if (evidenceExists(entity.interventionPhysicalId) > 0) return false
        val row = insertEvidence(entity)
        return row != -1L
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertOpenCycle(entity: OpenCorrectionCycleEntity)

    @Query("SELECT * FROM open_correction_cycle WHERE id = 1")
    fun loadOpenCycle(): OpenCorrectionCycleEntity?

    @Query("DELETE FROM open_correction_cycle WHERE id = 1")
    fun deleteOpenCycle()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertCaptured(entity: CapturedInterventionEntity): Long

    @Query("SELECT * FROM captured_intervention WHERE intervention_id = :interventionId LIMIT 1")
    fun findCaptured(interventionId: String): CapturedInterventionEntity?

    @Query("SELECT intervention_id FROM captured_intervention WHERE session_id = :sessionId")
    fun capturedIdsForSession(sessionId: String): List<String>

    @Query("DELETE FROM captured_intervention WHERE session_id = :sessionId")
    fun deleteCapturedForSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM captured_intervention")
    fun capturedCount(): Int

    /**
     * Evidence row first, captured marker second. A failed or missing evidence insert
     * returns false and does not write [CapturedInterventionEntity].
     * Both writes share one Room transaction and do not include file IO.
     */
    @Transaction
    fun recordEvidenceAndCapture(
        evidence: QueuedEvidenceEntity,
        captured: CapturedInterventionEntity
    ): Boolean {
        if (evidence.interventionPhysicalId.isBlank() || captured.interventionId.isBlank()) {
            return false
        }
        if (evidence.interventionPhysicalId != captured.interventionId) return false
        if (evidenceExists(evidence.interventionPhysicalId) == 0) {
            val row = insertEvidence(evidence)
            if (row == -1L && evidenceExists(evidence.interventionPhysicalId) == 0) return false
        }
        if (evidenceExists(evidence.interventionPhysicalId) == 0) return false
        if (findCaptured(captured.interventionId) == null) {
            insertCaptured(captured)
        }
        return findCaptured(captured.interventionId) != null
    }

    /** Captured marker only when the evidence row is already durable. */
    @Transaction
    fun insertCapturedIfEvidencePresent(captured: CapturedInterventionEntity): Boolean {
        if (captured.interventionId.isBlank()) return false
        if (evidenceExists(captured.interventionId) == 0) return false
        if (findCaptured(captured.interventionId) == null) {
            insertCaptured(captured)
        }
        return findCaptured(captured.interventionId) != null
    }

    @Transaction
    fun abandonCycle(sessionIds: List<String>) {
        deleteOpenCycle()
        sessionIds.filter { it.isNotBlank() }.distinct().forEach { deleteCapturedForSession(it) }
    }
}
