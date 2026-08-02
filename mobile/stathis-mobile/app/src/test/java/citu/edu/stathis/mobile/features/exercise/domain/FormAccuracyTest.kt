package citu.edu.stathis.mobile.features.exercise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormAccuracyTest {

    @Test
    fun nearIdeal_fullCreditInsideTolerance() {
        assertEquals(1f, FormAccuracy.nearIdeal(100f, 100f, 20f, 55f), 0.001f)
        assertEquals(1f, FormAccuracy.nearIdeal(110f, 100f, 20f, 55f), 0.001f)
    }

    @Test
    fun nearIdeal_zeroOutsideRange() {
        assertEquals(0f, FormAccuracy.nearIdeal(160f, 100f, 20f, 55f), 0.001f)
    }

    @Test
    fun nearIdeal_partialBetweenTolerances() {
        val score = FormAccuracy.nearIdeal(135f, 100f, 20f, 55f)
        assertTrue(score in 0.01f..0.99f)
    }

    @Test
    fun atLeast_and_atMost() {
        assertEquals(1f, FormAccuracy.atLeast(160f, 155f, 120f), 0.001f)
        assertEquals(0f, FormAccuracy.atLeast(110f, 155f, 120f), 0.001f)
        assertEquals(1f, FormAccuracy.atMost(90f, 95f, 130f), 0.001f)
        assertEquals(0f, FormAccuracy.atMost(140f, 95f, 130f), 0.001f)
    }

    @Test
    fun combine_appliesIssuePenalty() {
        assertEquals(1f, FormAccuracy.combine(1f, 0), 0.001f)
        assertEquals(0.64f, FormAccuracy.combine(1f, 2), 0.001f)
        assertEquals(0f, FormAccuracy.combine(0.1f, 3), 0.001f)
    }
}

class SessionAccuracyTrackerTest {

    @Test
    fun defaultsToZeroWithNoSamples() {
        val tracker = SessionAccuracyTracker()
        assertEquals(0f, tracker.currentAccuracyPercent(), 0.01f)
        assertEquals(0f, tracker.accuracyForSubmit(), 0.01f)
    }

    @Test
    fun ignoresNullSamples() {
        val tracker = SessionAccuracyTracker()
        tracker.record(null)
        tracker.record(null)
        assertEquals(0f, tracker.currentAccuracyPercent(), 0.01f)
        assertEquals(0, tracker.sampleCount())
    }

    @Test
    fun averagesFormScoresAsPercent() {
        val tracker = SessionAccuracyTracker()
        tracker.record(1.0f)
        tracker.record(0.8f)
        assertEquals(90f, tracker.accuracyForSubmit(), 0.5f)
    }

    @Test
    fun resetClearsSamplesBackToZero() {
        val tracker = SessionAccuracyTracker()
        tracker.record(1.0f)
        tracker.reset()
        assertEquals(0f, tracker.currentAccuracyPercent(), 0.01f)
        assertEquals(0, tracker.sampleCount())
    }
}
