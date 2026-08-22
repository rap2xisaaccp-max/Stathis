package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queued_evidence",
    indices = [Index(value = ["intervention_physical_id"], unique = true)]
)
data class QueuedEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "intervention_physical_id") val interventionPhysicalId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long = System.currentTimeMillis()
)
