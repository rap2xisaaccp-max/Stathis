package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class FormMasteryDisplayTest {

    @Test
    fun measuredZeroFiftyAndHundredMatchBackendRounding() {
        assertEquals(0, FormMasteryDisplay.percent(0.0))
        assertEquals(50, FormMasteryDisplay.percent(0.5))
        assertEquals(100, FormMasteryDisplay.percent(1.0))
        assertEquals("0%", FormMasteryDisplay.percentLabel(0.0))
        assertEquals("50%", FormMasteryDisplay.percentLabel(0.5))
        assertEquals("100%", FormMasteryDisplay.percentLabel(1.0))
    }

    @Test
    fun averagesToSameDisplayPercentAsTeacherCharts() {
        val level = (40.0 + 60.0) / 2.0 / 100.0
        assertEquals(50, FormMasteryDisplay.percent(level))
    }

    @Test
    fun measuredZeroRequiresPositiveRepsOnBackendAndShowsZeroPercent() {
        assertEquals("0%", FormMasteryDisplay.percentLabel(0.0))
    }
}
