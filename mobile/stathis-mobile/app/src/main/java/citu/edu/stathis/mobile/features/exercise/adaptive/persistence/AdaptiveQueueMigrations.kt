package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Non-destructive upgrades for [AdaptiveQueueDatabase].
 *
 * Version 1 (commit 367dd9c) already has `queued_interventions` and `queued_responses`
 * with the same columns as version 3. Those tables are not altered.
 * Version 2 added `queued_evidence` and its unique index. The 2-to-3 path does not
 * touch that table or its rows.
 *
 * A database whose user_version is anything other than 1, 2, or 3 has no migration
 * path. Room then throws IllegalStateException ("A migration from X to Y was required
 * but not found") inside the upgrade transaction, which rolls back. The original
 * user_version and rows stay on disk. There is no destructive fallback on [AdaptiveQueueDatabase.open].
 */
internal object AdaptiveQueueMigrations {
    const val CREATE_QUEUED_INTERVENTIONS =
        "CREATE TABLE IF NOT EXISTS `queued_interventions` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`physical_id` TEXT NOT NULL, " +
            "`student_id` TEXT, " +
            "`payload_json` TEXT NOT NULL, " +
            "`created_at_epoch_ms` INTEGER NOT NULL)"

    const val CREATE_QUEUED_RESPONSES =
        "CREATE TABLE IF NOT EXISTS `queued_responses` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`physical_id` TEXT NOT NULL, " +
            "`intervention_physical_id` TEXT NOT NULL, " +
            "`student_id` TEXT, " +
            "`payload_json` TEXT NOT NULL, " +
            "`created_at_epoch_ms` INTEGER NOT NULL)"

    const val CREATE_QUEUED_EVIDENCE =
        "CREATE TABLE IF NOT EXISTS `queued_evidence` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`intervention_physical_id` TEXT NOT NULL, " +
            "`payload_json` TEXT NOT NULL, " +
            "`file_path` TEXT NOT NULL, " +
            "`created_at_epoch_ms` INTEGER NOT NULL)"

    const val CREATE_EVIDENCE_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_queued_evidence_intervention_physical_id` " +
            "ON `queued_evidence` (`intervention_physical_id`)"

    const val CREATE_OPEN_CORRECTION_CYCLE =
        "CREATE TABLE IF NOT EXISTS `open_correction_cycle` (" +
            "`id` INTEGER NOT NULL, " +
            "`session_id` TEXT NOT NULL, " +
            "`exercise_type` TEXT NOT NULL, " +
            "`task_id` TEXT, " +
            "`classroom_id` TEXT, " +
            "`attempt_number` INTEGER, " +
            "`phase` TEXT NOT NULL, " +
            "`pending_code` TEXT, " +
            "`pending_ticks` INTEGER NOT NULL, " +
            "`clear_ticks` INTEGER NOT NULL, " +
            "`open_error` TEXT, " +
            "`delivered_at` INTEGER NOT NULL, " +
            "`last_intervention_at` INTEGER NOT NULL, " +
            "`cycle_seq` INTEGER NOT NULL, " +
            "`recent_intervention_at_json` TEXT NOT NULL, " +
            "`escalation_counts_json` TEXT NOT NULL, " +
            "`session_intervention_count` INTEGER NOT NULL, " +
            "`session_error_codes_json` TEXT NOT NULL, " +
            "`session_recorded` INTEGER NOT NULL, " +
            "`claimed_evidence_json` TEXT, " +
            "PRIMARY KEY(`id`))"

    const val CREATE_CAPTURED_INTERVENTION =
        "CREATE TABLE IF NOT EXISTS `captured_intervention` (" +
            "`intervention_id` TEXT NOT NULL, " +
            "`session_id` TEXT NOT NULL, " +
            "`exercise_type` TEXT NOT NULL, " +
            "`error_code` TEXT NOT NULL, " +
            "`cycle_seq` INTEGER NOT NULL, " +
            "PRIMARY KEY(`intervention_id`))"

    val MIGRATION_1_3: Migration = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_QUEUED_EVIDENCE)
            db.execSQL(CREATE_EVIDENCE_INDEX)
            db.execSQL(CREATE_OPEN_CORRECTION_CYCLE)
            db.execSQL(CREATE_CAPTURED_INTERVENTION)
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_OPEN_CORRECTION_CYCLE)
            db.execSQL(CREATE_CAPTURED_INTERVENTION)
        }
    }
}
