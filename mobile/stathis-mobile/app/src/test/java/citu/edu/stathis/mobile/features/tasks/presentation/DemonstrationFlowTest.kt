package citu.edu.stathis.mobile.features.tasks.presentation

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    private fun assertIsolation(flow: DemonstrationFlow) {
        assertFalse(flow.sessionStarted)
        assertEquals(0, flow.reps)
        assertEquals(0, flow.scoreAttempts)
        assertEquals(0, flow.masteryUpdates)
        assertEquals(0, flow.evidenceCaptures)
    }
}
