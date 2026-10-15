package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migrations go through production [AdaptiveQueueDatabase.open] on a Robolectric
 * sandbox file named adaptive_queue_db. This is not a device database.
 */
@RunWith(RobolectricTestRunner::class)
class AdaptiveQueueMigrationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deleteSandboxDatabase()
    }

    @After
    fun teardown() {
        deleteSandboxDatabase()
    }

    @Test
    fun migration2To3PreservesQueueRowsAndCreatesEmptyCycleTables() = runBlocking(Dispatchers.IO) {
        insertLegacy(
            version = 2,
            withEvidence = true
        )
        val db = AdaptiveQueueDatabase.open(context)
        try {
            val dao = db.dao()
            val intervention = dao.allInterventions().single()
            assertEquals(7L, intervention.id)
            assertEquals("FI-V2-INTERVENTION", intervention.physicalId)
            assertEquals("stu-v2", intervention.studentId)
            assertEquals("payload-intervention", intervention.payloadJson)
            assertEquals(1_111L, intervention.createdAtEpochMs)

            val response = dao.allResponses().single()
            assertEquals(8L, response.id)
            assertEquals("FR-V2", response.physicalId)
            assertEquals("FI-V2-INTERVENTION", response.interventionPhysicalId)
            assertEquals("payload-response", response.payloadJson)
            assertEquals(2_222L, response.createdAtEpochMs)

            val evidence = dao.allEvidence().single()
            assertEquals(9L, evidence.id)
            assertEquals("FI-V2-EVIDENCE", evidence.interventionPhysicalId)
            assertEquals("payload-evidence", evidence.payloadJson)
            assertEquals("C:/sentinel/keep.jpg", evidence.filePath)
            assertEquals(3_333L, evidence.createdAtEpochMs)

            assertEquals(null, dao.loadOpenCycle())
            assertEquals(0, dao.capturedCount())
            assertEquals(3, userVersion(db))
            assertEquals(5, columnCount(db, "queued_interventions"))
            assertEquals(6, columnCount(db, "queued_responses"))
            assertEquals(5, columnCount(db, "queued_evidence"))
            assertTrue(indexExists(db, "index_queued_evidence_intervention_physical_id"))
        } finally {
            db.close()
        }

        val reopened = AdaptiveQueueDatabase.open(context)
        try {
            assertEquals("payload-intervention", reopened.dao().allInterventions().single().payloadJson)
            assertEquals("C:/sentinel/keep.jpg", reopened.dao().allEvidence().single().filePath)
            assertEquals(3, userVersion(reopened))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun migration1To3PreservesInterventionAndResponseRows() = runBlocking(Dispatchers.IO) {
        insertLegacy(version = 1, withEvidence = false)
        val db = AdaptiveQueueDatabase.open(context)
        try {
            val dao = db.dao()
            assertEquals(7L, dao.allInterventions().single().id)
            assertEquals("FI-V1-INTERVENTION", dao.allInterventions().single().physicalId)
            assertEquals("payload-intervention", dao.allInterventions().single().payloadJson)
            assertEquals(8L, dao.allResponses().single().id)
            assertEquals("payload-response", dao.allResponses().single().payloadJson)
            assertTrue(dao.allEvidence().isEmpty())
            assertEquals(null, dao.loadOpenCycle())
            assertEquals(0, dao.capturedCount())
            assertTrue(indexExists(db, "index_queued_evidence_intervention_physical_id"))
            assertEquals(3, userVersion(db))
            assertEquals(5, columnCount(db, "queued_interventions"))
        } finally {
            db.close()
        }
    }

    @Test
    fun productionOpenMigratesVersion1AndVersion2() = runBlocking(Dispatchers.IO) {
        insertLegacy(version = 1, withEvidence = false)
        val version1 = AdaptiveQueueDatabase.open(context)
        try {
            assertEquals(3, userVersion(version1))
            assertEquals("FI-V1-INTERVENTION", version1.dao().allInterventions().single().physicalId)
        } finally {
            version1.close()
        }
        deleteSandboxDatabase()
        insertLegacy(version = 2, withEvidence = true)
        val version2 = AdaptiveQueueDatabase.open(context)
        try {
            assertEquals(3, userVersion(version2))
            assertEquals("C:/sentinel/keep.jpg", version2.dao().allEvidence().single().filePath)
            assertEquals("FI-V2-INTERVENTION", version2.dao().allInterventions().single().physicalId)
        } finally {
            version2.close()
        }
    }

    @Test
    fun unsupportedVersionFailsWithoutChangingTheFile() = runBlocking(Dispatchers.IO) {
        val file = sandboxFile()
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL(
            "CREATE TABLE sentinel (id INTEGER PRIMARY KEY NOT NULL, note TEXT NOT NULL)"
        )
        raw.execSQL("INSERT INTO sentinel (id, note) VALUES (1, 'keep-me')")
        raw.execSQL(AdaptiveQueueMigrations.CREATE_QUEUED_INTERVENTIONS)
        raw.execSQL(
            "INSERT INTO queued_interventions " +
                "(id, physical_id, student_id, payload_json, created_at_epoch_ms) " +
                "VALUES (4, 'FI-KEEP', 'stu', 'untouched', 9)"
        )
        raw.version = 4
        raw.close()

        val opened = AdaptiveQueueDatabase.open(context)
        val failure = runCatching { opened.openHelper.writableDatabase }
        opened.close()
        val error = failure.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(
            error?.message.orEmpty().contains("migration", ignoreCase = true)
        )

        val after = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals(4, after.version)
            val sentinel = after.rawQuery("SELECT note FROM sentinel WHERE id = 1", null)
            assertTrue(sentinel.moveToFirst())
            assertEquals("keep-me", sentinel.getString(0))
            sentinel.close()
            val queued = after.rawQuery(
                "SELECT payload_json FROM queued_interventions WHERE id = 4",
                null
            )
            assertTrue(queued.moveToFirst())
            assertEquals("untouched", queued.getString(0))
            queued.close()
        } finally {
            after.close()
        }
    }

    @Test
    fun inMemoryBuilderDoesNotOpenTheProductionFile() = runBlocking(Dispatchers.IO) {
        deleteSandboxDatabase()
        val memory = AdaptiveQueueDatabase.createInMemory(context)
        try {
            assertNotEquals(AdaptiveQueueDatabase.DB_NAME, memory.openHelper.databaseName)
            memory.openHelper.writableDatabase
            assertFalse(sandboxFile().exists())
        } finally {
            memory.close()
        }
    }

    private fun insertLegacy(version: Int, withEvidence: Boolean) {
        val file = sandboxFile()
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        val physicalId = if (version == 1) "FI-V1-INTERVENTION" else "FI-V2-INTERVENTION"
        raw.execSQL(AdaptiveQueueMigrations.CREATE_QUEUED_INTERVENTIONS)
        raw.execSQL(AdaptiveQueueMigrations.CREATE_QUEUED_RESPONSES)
        raw.execSQL(
            "INSERT INTO queued_interventions " +
                "(id, physical_id, student_id, payload_json, created_at_epoch_ms) " +
                "VALUES (7, '$physicalId', 'stu-v2', 'payload-intervention', 1111)"
        )
        raw.execSQL(
            "INSERT INTO queued_responses " +
                "(id, physical_id, intervention_physical_id, student_id, payload_json, created_at_epoch_ms) " +
                "VALUES (8, 'FR-V2', '$physicalId', 'stu-v2', 'payload-response', 2222)"
        )
        if (withEvidence) {
            raw.execSQL(AdaptiveQueueMigrations.CREATE_QUEUED_EVIDENCE)
            raw.execSQL(AdaptiveQueueMigrations.CREATE_EVIDENCE_INDEX)
            raw.execSQL(
                "INSERT INTO queued_evidence " +
                    "(id, intervention_physical_id, payload_json, file_path, created_at_epoch_ms) " +
                    "VALUES (9, 'FI-V2-EVIDENCE', 'payload-evidence', 'C:/sentinel/keep.jpg', 3333)"
            )
        }
        raw.version = version
        raw.close()
    }

    private fun userVersion(db: AdaptiveQueueDatabase): Int {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA user_version")
        cursor.use {
            check(it.moveToFirst())
            return it.getInt(0)
        }
    }

    private fun columnCount(db: AdaptiveQueueDatabase, table: String): Int {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)")
        cursor.use { return it.count }
    }

    private fun indexExists(db: AdaptiveQueueDatabase, name: String): Boolean {
        val cursor =
            db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$name'"
            )
        cursor.use { return it.moveToFirst() }
    }

    private fun sandboxFile(): File = context.getDatabasePath(AdaptiveQueueDatabase.DB_NAME)

    private fun deleteSandboxDatabase() {
        val file = sandboxFile()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
        file.delete()
    }
}
