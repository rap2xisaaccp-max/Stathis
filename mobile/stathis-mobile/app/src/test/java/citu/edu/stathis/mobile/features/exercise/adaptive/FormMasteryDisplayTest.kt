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

    @Test
    fun resultLineShowsFormMasteryStateAndQualifyingAttempts() {
        assertEquals(
            "Form Mastery: 100% · Learning · 1 qualifying attempt",
            FormMasteryDisplay.resultLine(1.0, "LEARNING", 1)
        )
        assertEquals(
            "Form Mastery: 72% · Improving · 3 qualifying attempts",
            FormMasteryDisplay.resultLine(0.72, "IMPROVING", 3)
        )
        assertEquals(
            "Form Mastery: 90% · Mastered · 4 qualifying attempts",
            FormMasteryDisplay.resultLine(0.9, "MASTERED", 4)
        )
    }

    @Test
    fun missingStateIsBlankAndDoesNotDisplayLearning() {
        assertEquals("", FormMasteryDisplay.stateLabel(null))
        assertEquals("", FormMasteryDisplay.stateLabel(" "))
        assertEquals("", FormMasteryDisplay.stateLabel("NOT_A_STATE"))
        assertEquals("Learning", FormMasteryDisplay.stateLabel("LEARNING"))
        assertEquals(
            "Form Mastery: 100% · 1 qualifying attempt",
            FormMasteryDisplay.resultLine(1.0, null, 1)
        )
        assertEquals(
            "50% · 2 qualifying attempts",
            FormMasteryDisplay.labeledPercent(0.5, "NOT_A_STATE", 2)
        )
        assertEquals(false, FormMasteryDisplay.resultLine(1.0, null, 1).contains("Learning"))
    }

    @Test
    fun refreshesFormMasteryOnlyAfterClassroomSaveSucceeds() {
        assertEquals(false, FormMasteryDisplay.shouldRefreshAfterClassroomSave(false))
        assertEquals(true, FormMasteryDisplay.shouldRefreshAfterClassroomSave(true))
    }

    @Test
    fun newerFormMasteryLoadReplacesALoadStartedBeforeSave() {
        val gate = FormMasteryLoadGate()
        val beforeSave = gate.open()
        val afterSave = gate.open()
        assertEquals(false, gate.isCurrent(beforeSave))
        assertEquals(true, gate.isCurrent(afterSave))
    }
}
