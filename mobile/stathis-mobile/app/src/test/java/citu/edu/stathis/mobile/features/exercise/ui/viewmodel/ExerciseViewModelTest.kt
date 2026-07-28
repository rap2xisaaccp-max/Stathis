package citu.edu.stathis.mobile.features.exercise.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseViewModelTest {

    @Test
    fun normalizeScores_keepsProbabilitiesBalancedAndSorted() {
        val rawScores = mapOf(
            "Push-up" to 0.6f,
            "Squat" to 0.3f,
            "Rest" to 0.1f
        )

        val normalized = normalizeScores(rawScores)

        assertEquals(3, normalized.size)
        assertTrue(normalized[0].second >= normalized[1].second)
        assertTrue(normalized[1].second >= normalized[2].second)
        assertEquals(1.0f, normalized.sumOf { it.second.toDouble() }, 0.001)
    }
}
