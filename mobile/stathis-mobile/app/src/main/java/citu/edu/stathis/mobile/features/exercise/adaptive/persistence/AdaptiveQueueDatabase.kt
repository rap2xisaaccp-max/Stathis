package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QueuedInterventionEntity::class,
        QueuedResponseEntity::class,
        QueuedEvidenceEntity::class
    ],
    version = 2
)
abstract class AdaptiveQueueDatabase : RoomDatabase() {
    abstract fun dao(): AdaptiveQueueDao

    companion object {
        private const val DB_NAME = "adaptive_queue_db"

        fun createInMemory(context: Context): AdaptiveQueueDatabase =
            Room.inMemoryDatabaseBuilder(context, AdaptiveQueueDatabase::class.java)
                .fallbackToDestructiveMigration()
                .build()

        fun open(context: Context): AdaptiveQueueDatabase =
            Room.databaseBuilder(context, AdaptiveQueueDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
