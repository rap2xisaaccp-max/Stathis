package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncouragementPolicyTest {

    @Test
    fun complimentsAreNotEveryRepAndARejectIsNeverPraised() {
        val spoken = mutableListOf<String>()
        val policy = EncouragementPolicy(nextGap = { 2 })
        policy.onValidRep(1_000L) { line ->
            spoken += line
            true
        }
        assertTrue(spoken.isEmpty())
        policy.onRejected()
        assertTrue(spoken.isEmpty())
        policy.onValidRep(2_000L) { line ->
            spoken += line
            true
        }
        policy.onValidRep(3_000L) { line ->
            spoken += line
            true
        }
        assertEquals(1, spoken.size)
        assertTrue(spoken.single() in EncouragementPolicy.COMPLIMENTS)
    }

    @Test
    fun blockedSpeechStaysDueAndDoesNotPraiseARejectedRep() {
        val policy = EncouragementPolicy(nextGap = { 2 })
        policy.onValidRep(1_000L) { false }
        policy.onValidRep(2_000L) { false }
        val spoken = mutableListOf<String>()
        policy.onRejected()
        policy.onValidRep(3_000L) { line ->
            spoken += line
            true
        }
        assertEquals(listOf(EncouragementPolicy.COMPLIMENTS.first()), spoken)
    }

    @Test
    fun motivationalLineFollowsAValidStreak() {
        val policy = EncouragementPolicy(nextGap = { 2 }, motivationalAfterValidStreak = 4, motivationalCooldownMs = 20_000L)
        val spoken = mutableListOf<String>()
        repeat(4) { index ->
            policy.onValidRep(1_000L + index * 1_000L) { line ->
                spoken += line
                true
            }
        }
        assertTrue(spoken.contains(EncouragementPolicy.MOTIVATIONAL))
        assertFalse(spoken.any { it == "Rep not counted." })
    }
}
