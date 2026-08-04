package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_interventions")
data class QueuedInterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "physical_id") val physicalId: String,
    @ColumnInfo(name = "student_id") val studentId: String?,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long = System.currentTimeMillis()
)
