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
}
