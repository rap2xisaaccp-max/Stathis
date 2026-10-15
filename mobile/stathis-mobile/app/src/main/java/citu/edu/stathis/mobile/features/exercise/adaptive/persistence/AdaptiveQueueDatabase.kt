package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QueuedInterventionEntity::class,
        QueuedResponseEntity::class,
        QueuedEvidenceEntity::class,
        OpenCorrectionCycleEntity::class,
        CapturedInterventionEntity::class
    ],
    version = 3
)
abstract class AdaptiveQueueDatabase : RoomDatabase() {
    abstract fun dao(): AdaptiveQueueDao

    companion object {
        internal const val DB_NAME = "adaptive_queue_db"

        /**
         * Throwaway database for unit tests. [Room.inMemoryDatabaseBuilder] never opens
         * [DB_NAME]. The destructive fallback stays only on this isolated builder.
         * It is not proof that a production file migrates safely.
         */
        fun createInMemory(context: Context): AdaptiveQueueDatabase =
            Room.inMemoryDatabaseBuilder(context, AdaptiveQueueDatabase::class.java)
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Production on-disk opener used by Hilt. Versions 1 and 2 migrate forward
         * without dropping queue tables. Any other existing user_version throws
         * IllegalStateException from Room and leaves the file unchanged.
         * There is no destructive fallback on this path.
         */
        fun open(context: Context): AdaptiveQueueDatabase =
            Room.databaseBuilder(context, AdaptiveQueueDatabase::class.java, DB_NAME)
                .addMigrations(
                    AdaptiveQueueMigrations.MIGRATION_1_3,
                    AdaptiveQueueMigrations.MIGRATION_2_3
                )
                .build()
    }
}
