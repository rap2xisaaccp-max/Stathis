package citu.edu.stathis.mobile.features.tasks.presentation

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DemonstrationFlowTest {

    @Test
    fun demonstrationAvailableOpensTheDemoScreen() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(true)
        val phase = flow.phase as DemonstrationPhase.Demo
        assertFalse(phase.ready)
        assertIsolation(flow)
    }

    @Test
    fun missingDemonstrationSkipsToIdentity() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(false)
        assertTrue(flow.phase is DemonstrationPhase.Identity)
        assertIsolation(flow)
    }

    @Test
    fun continueMovesToIdentityWithoutStartingASession() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(true)
        flow.onDownloadReady("/tmp/push.mp4")
        flow.continueToIdentity()
        assertTrue(flow.phase is DemonstrationPhase.Identity)
        assertIsolation(flow)
    }

    @Test
    fun backReturnsToTaskDetails() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(true)
        flow.back()
        assertTrue(flow.phase is DemonstrationPhase.TaskDetails)
        assertIsolation(flow)
    }

    @Test
    fun downloadFailureStaysOnTheDemoAndRetryCanRecover() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(true)
        flow.onDownloadFailed("network")
        val failed = flow.phase as DemonstrationPhase.Demo
        assertEquals("network", failed.error)
        assertFalse(failed.ready)
        assertIsolation(flow)
        flow.retry()
        flow.onDownloadReady("/tmp/push.mp4")
        val ready = flow.phase as DemonstrationPhase.Demo
        assertNull(ready.error)
        assertTrue(ready.ready)
        assertIsolation(flow)
    }

    @Test
    fun playbackFailureDoesNotCreateASession() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(true)
        flow.onDownloadReady("/tmp/push.mp4")
        flow.onDownloadFailed("Could not play the demonstration")
        assertTrue(flow.phase is DemonstrationPhase.Demo)
        assertIsolation(flow)
    }

    @Test
    fun pushAndSquatStayIsolated() {
        val push = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        val squat = DemonstrationFlow("TASK-A", "EXERCISE-SQUAT")
        assertNotEquals(push.contentPath(), squat.contentPath())
        push.onMetadata(true)
        squat.onMetadata(false)
        assertTrue(push.phase is DemonstrationPhase.Demo)
        assertTrue(squat.phase is DemonstrationPhase.Identity)
        assertIsolation(push)
        assertIsolation(squat)
    }

    @Test
    fun practiceDoesNotUseAssignmentDemonstrations() {
        assertFalse(demonstrationApplies("PRACTICE"))
        assertTrue(demonstrationApplies("TASK"))
    }

    @Test
    fun cacheFileIsNotKeptAfterDelete() {
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val file = dir.resolve("push.mp4")
        DemonstrationCache.write(ByteArrayInputStream(byteArrayOf(1, 2, 3)), file)
        assertTrue(file.exists())
        DemonstrationCache.delete(file)
        assertFalse(file.exists())
        dir.delete()
    }

    @Test
    fun successfulDownloadIsClosedBeforeThePlayerPathExists() {
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val file = dir.resolve("push.mp4")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val written = DemonstrationCache.writeAtomically(ByteArrayInputStream(bytes), file, 4L)
        assertEquals(4L, written)
        assertTrue(DemonstrationCache.isComplete(file, 4L))
        assertFalse(File(dir, "push.mp4.partial").exists())
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    @Test
    fun zeroByteDownloadIsRejectedAndLeavesNoCache() {
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val file = dir.resolve("push.mp4")
        assertThrows(IOException::class.java) {
            DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf()), file, null)
        }
        assertFalse(file.exists())
        assertFalse(File(dir, "push.mp4.partial").exists())
        dir.delete()
    }

    @Test
    fun incompleteDownloadIsRejectedAndLeavesNoCache() {
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val file = dir.resolve("push.mp4")
        assertThrows(IOException::class.java) {
            DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf(1, 2)), file, 8L)
        }
        assertFalse(file.exists())
        assertFalse(File(dir, "push.mp4.partial").exists())
        dir.delete()
    }

    @Test
    fun replacementUsesADifferentCacheFileAndDropsTheOldOne() {
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val firstVersion = DemonstrationCache.versionToken("DEMO-1", "2026-10-01T00:00:00Z", 4L)
        val replaced = DemonstrationCache.versionToken("DEMO-1", "2026-10-02T00:00:00Z", 5L)
        assertNotEquals(firstVersion, replaced)
        val oldFile = DemonstrationCache.fileFor(dir, "TASK-A", "EXERCISE-PUSH", firstVersion, "mp4")
        val newFile = DemonstrationCache.fileFor(dir, "TASK-A", "EXERCISE-SQUAT", replaced, "mp4")
        DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), oldFile, 4L)
        val squat = DemonstrationCache.fileFor(dir, "TASK-A", "EXERCISE-SQUAT", replaced, "mp4")
        DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf(9, 8, 7, 6, 5)), squat, 5L)
        val pushReplacement = DemonstrationCache.fileFor(dir, "TASK-A", "EXERCISE-PUSH", replaced, "mp4")
        DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf(5, 4, 3, 2, 1)), pushReplacement, 5L)
        DemonstrationCache.deleteOtherVersions(dir, "TASK-A", "EXERCISE-PUSH", pushReplacement)
        assertFalse(oldFile.exists())
        assertTrue(pushReplacement.exists())
        assertTrue(squat.exists())
        assertEquals(newFile.absolutePath, squat.absolutePath)
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    @Test
    fun retryAfterFailureCanStoreACompleteFile() {
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val file = dir.resolve("push.mp4")
        assertThrows(IOException::class.java) {
            DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf()), file, 3L)
        }
        assertFalse(file.exists())
        DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf(1, 2, 3)), file, 3L)
        assertTrue(DemonstrationCache.isComplete(file, 3L))
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    @Test
    fun seekStaysInsideThePreparedDuration() {
        assertFalse(DemonstrationPlayback.canSeek(prepared = false, durationMs = 62_000))
        assertFalse(DemonstrationPlayback.canSeek(prepared = true, durationMs = 0))
        assertTrue(DemonstrationPlayback.canSeek(prepared = true, durationMs = 62_000))
        assertEquals(0, DemonstrationPlayback.clampSeek(-400, 62_000))
        assertEquals(45_000, DemonstrationPlayback.clampSeek(45_000, 62_000))
        assertEquals(62_000, DemonstrationPlayback.clampSeek(90_000, 62_000))
        assertEquals("00:14", DemonstrationPlayback.formatClock(14_000))
        assertEquals("01:02", DemonstrationPlayback.formatClock(62_000))
        assertEquals("00:00", DemonstrationPlayback.formatClock(-1))
    }

    @Test
    fun videoFitsThePlayerWithoutStretching() {
        val squareHost = DemonstrationPlayback.fitScale(1920, 1080, 1000, 1000)
        assertEquals(1f, squareHost!!.first, 0.01f)
        assertTrue(squareHost.second < 1f)
        val matched = DemonstrationPlayback.fitScale(1920, 1080, 1600, 900)
        assertEquals(1f, matched!!.first, 0.01f)
        assertEquals(1f, matched.second, 0.01f)
        assertNull(DemonstrationPlayback.fitScale(0, 1080, 1600, 900))
    }

    @Test
    fun leavingTheDemonstrationDoesNotRequireDeletingAReadyFile() {
        val flow = DemonstrationFlow("TASK-A", "EXERCISE-PUSH")
        flow.onMetadata(true)
        flow.onDownloadReady("/tmp/push.mp4")
        flow.back()
        assertTrue(flow.phase is DemonstrationPhase.TaskDetails)
        val dir = Files.createTempDirectory("stathis-demo").toFile()
        val file = dir.resolve("push.mp4")
        DemonstrationCache.writeAtomically(ByteArrayInputStream(byteArrayOf(1, 2, 3)), file, 3L)
        flow.continueToIdentity()
        assertTrue(file.exists())
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    private fun assertIsolation(flow: DemonstrationFlow) {
        assertFalse(flow.sessionStarted)
        assertEquals(0, flow.reps)
        assertEquals(0, flow.scoreAttempts)
        assertEquals(0, flow.masteryUpdates)
        assertEquals(0, flow.evidenceCaptures)
    }
}
