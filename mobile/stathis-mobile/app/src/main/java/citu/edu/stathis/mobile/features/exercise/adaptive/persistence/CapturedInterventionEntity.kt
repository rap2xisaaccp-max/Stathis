package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Written only after the matching evidence row is stored.
 * The primary key is the deterministic intervention id, so a second insert is a no-op.
 */
@Entity(tableName = "captured_intervention")
data class CapturedInterventionEntity(
    @PrimaryKey
    @ColumnInfo(name = "intervention_id")
    val interventionId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: String,
    @ColumnInfo(name = "error_code") val errorCode: String,
    @ColumnInfo(name = "cycle_seq") val cycleSeq: Int
)
