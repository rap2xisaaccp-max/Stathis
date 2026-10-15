package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (`id = 1`) for the correction cycle that should resume after process death.
 * Queue tables stay independent: deleting this row does not delete queued evidence.
 */
@Entity(tableName = "open_correction_cycle")
data class OpenCorrectionCycleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "classroom_id") val classroomId: String?,
    @ColumnInfo(name = "attempt_number") val attemptNumber: Int?,
    @ColumnInfo(name = "phase") val phase: String,
    @ColumnInfo(name = "pending_code") val pendingCode: String?,
    @ColumnInfo(name = "pending_ticks") val pendingTicks: Int,
    @ColumnInfo(name = "clear_ticks") val clearTicks: Int,
    @ColumnInfo(name = "open_error") val openError: String?,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long,
    @ColumnInfo(name = "last_intervention_at") val lastInterventionAt: Long,
    @ColumnInfo(name = "cycle_seq") val cycleSeq: Int,
    @ColumnInfo(name = "recent_intervention_at_json") val recentInterventionAtJson: String,
    @ColumnInfo(name = "escalation_counts_json") val escalationCountsJson: String,
    @ColumnInfo(name = "session_intervention_count") val sessionInterventionCount: Int,
    @ColumnInfo(name = "session_error_codes_json") val sessionErrorCodesJson: String,
    @ColumnInfo(name = "session_recorded") val sessionRecorded: Boolean,
    /** Claimed [FormEvidenceEvent] JSON so JPEG recovery does not recompute coaching text. */
    @ColumnInfo(name = "claimed_evidence_json") val claimedEvidenceJson: String?
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
