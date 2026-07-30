package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class RctExperimentPrefsTest {

    @Test
    fun composeArmKeepsTaskArmClean() {
        assertEquals("ADAPTIVE", RctExperimentPrefs.composeArm("ADAPTIVE", RctExperimentPrefs.CONTEXT_TASK))
        assertEquals("STATIC", RctExperimentPrefs.composeArm("STATIC", RctExperimentPrefs.CONTEXT_TASK))
    }

    @Test
    fun composeArmTagsPracticeSessions() {
        assertEquals(
            "ADAPTIVE_PRACTICE",
            RctExperimentPrefs.composeArm("ADAPTIVE", RctExperimentPrefs.CONTEXT_PRACTICE)
        )
        assertEquals(
            "STATIC_PRACTICE",
            RctExperimentPrefs.composeArm("STATIC", RctExperimentPrefs.CONTEXT_PRACTICE)
        )
    }

    @Test
    fun composeArmNormalizesStaticPrefixes() {
        assertEquals(
            "STATIC_PRACTICE",
            RctExperimentPrefs.composeArm("STATIC_CONTROL", RctExperimentPrefs.CONTEXT_PRACTICE)
        )
    }
}
