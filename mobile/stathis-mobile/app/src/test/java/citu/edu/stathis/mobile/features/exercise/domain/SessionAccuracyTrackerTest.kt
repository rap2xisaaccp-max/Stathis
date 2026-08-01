package citu.edu.stathis.mobile.features.exercise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccuracyTrackerTest {

    @Test
    fun lastFrameZeroDoesNotWipeSessionMean_squatsBug() {
        val tracker = SessionAccuracyTracker()
        repeat(20) { tracker.record(0.90f) }
        tracker.record(0f)

        assertEquals(0f, tracker.currentAccuracy, 0.01f)
        assertTrue(tracker.sampleCount > 0)
        assertEquals(90f, tracker.accuracyForSubmit(), 0.5f)
    }

    @Test
    fun allZeroSamplesSubmitZero() {
        val tracker = SessionAccuracyTracker()
        tracker.record(0f)
        tracker.record(0f)
        assertEquals(0, tracker.sampleCount)
        assertEquals(0f, tracker.accuracyForSubmit(), 0.01f)
    }
}
