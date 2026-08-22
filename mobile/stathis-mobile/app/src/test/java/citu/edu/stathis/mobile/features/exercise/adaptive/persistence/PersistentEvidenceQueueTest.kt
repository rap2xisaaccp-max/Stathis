package citu.edu.stathis.mobile.features.exercise.adaptive.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorCode
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceEvent
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistentEvidenceQueueTest {
    private lateinit var context: Context
    private lateinit var db: AdaptiveQueueDatabase
    private lateinit var queue: PersistentEvidenceQueue

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AdaptiveQueueDatabase.createInMemory(context)
        queue = PersistentEvidenceQueue(context, db, Gson())
    }

    @After
    fun teardown() {
        queue.clear()
        db.close()
    }

    @Test
    fun pendingDoesNotDeleteAndAcknowledgeRemovesJpeg() {
        val event =
            FormEvidenceEvent(
                interventionId = "FI-ACK",
                sessionId = "SES-1",
                exerciseType = "SQUATS",
                errorCode = FormErrorCode.SAG,
                errorDescription = "Hips sagging",
                correctionText = "Keep hips level",
                capturedAtIso = "2026-08-21T00:00:00Z"
            )
        val jpeg = ByteArray(32) { 7 }
        queue.enqueue(event, jpeg)
        queue.enqueue(event, jpeg)

        val first = queue.pending()
        assertEquals(1, first.size)
        assertFalse(queue.isEmpty())
        val jpegFile = File(context.filesDir, "form-evidence/FI-ACK.jpg")
        assertTrue(jpegFile.exists())

        val second = queue.pending()
        assertEquals(1, second.size)
        assertTrue(jpegFile.exists())

        queue.acknowledge("FI-ACK")
        assertTrue(queue.isEmpty())
        assertFalse(jpegFile.exists())
    }
}
