package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claim-before-async must prevent ~100ms intervention bursts.
 */
class InterventionOverfireRaceTest {

    @Test
    fun hundredFramesSameErrorCreateOneClaim() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3, maxPerMinute = 40)
        var claims = 0
        for (i in 0 until 100) {
            val cycle =
                life.tryClaimDelivery(
                    FormErrorCode.SAG,
                    0.7,
                    now = 1_000L + i * 100L,
                    cooldownMs = 8_000L,
                    currentReps = 0
                )
            if (cycle != null) claims++
        }
        assertEquals(1, claims)
    }

    @Test
    fun claimBlocksBurstBeforeMarkDelivered() {
        val life = InterventionLifecycle(confirmTicks = 3, responseValidReps = 3, maxPerMinute = 40)
        val now = 1_000L
        assertTrue(life.tryClaimDelivery(FormErrorCode.SAG, 0.55, now, 8_000L, 0) == null)
        assertTrue(life.tryClaimDelivery(FormErrorCode.SAG, 0.55, now + 50, 8_000L, 0) == null)
        assertTrue(life.tryClaimDelivery(FormErrorCode.SAG, 0.55, now + 100, 8_000L, 0) != null)

        var allowed = 0
        for (i in 0 until 10) {
            val ok =
                life.tryClaimDelivery(
                    FormErrorCode.SAG,
                    0.55,
                    now + 200L + i * 100L,
                    8_000L,
                    0
                )
            if (ok != null) allowed++
        }
        assertEquals(0, allowed)
    }

    @Test
    fun parallelCoroutinesCannotDoubleClaim() = runBlocking {
        val life = InterventionLifecycle(confirmTicks = 1, responseValidReps = 3, maxPerMinute = 40)
        val results =
            (0 until 8)
                .map { i ->
                    async {
                        delay(5L * i)
                        val cycle = life.tryClaimDelivery(FormErrorCode.SAG, 0.7, 10_000L + i, 8_000L, 0)
                        delay(80L)
                        cycle != null
                    }
                }
                .awaitAll()

        assertEquals(1, results.count { it })
    }

    @Test
    fun stableInterventionIdIsDeterministicForRetries() {
        val engineMaterial = "SES-1|GLUTE_BRIDGE|SAG|C1"
        val a = UUID.nameUUIDFromBytes(engineMaterial.toByteArray()).toString().uppercase()
        val b = UUID.nameUUIDFromBytes(engineMaterial.toByteArray()).toString().uppercase()
        assertEquals(a, b)
        assertNotEquals(
            a,
            UUID.nameUUIDFromBytes("SES-1|GLUTE_BRIDGE|SAG|C2".toByteArray()).toString().uppercase()
        )
    }

    @Test
    fun offlineQueueDedupesSameInterventionId() {
        val q = AdaptiveOfflineQueue()
        val dto =
            InterventionRequestDto(
                physicalId = "FI-SAME",
                sessionId = "SES-1",
                exerciseType = "SQUATS",
                errorCode = "SAG",
                modality = "VERBAL_TEXT",
                baselineSeverity = 0.5,
                policySource = "DEFAULT"
            )
        q.enqueueIntervention(dto)
        q.enqueueIntervention(dto.copy())
        assertEquals(1, q.pendingInterventionCount)
    }

    @Test
    fun technicalErrorsAreNotCoachable() {
        assertTrue(FormErrorClassifier.isTechnical(FormErrorCode.LOW_CONFIDENCE))
        assertTrue(FormErrorClassifier.isTechnical(FormErrorCode.BODY_NOT_VISIBLE))
        assertTrue(!FormErrorClassifier.isCoachable(FormErrorCode.LOW_CONFIDENCE))
        assertTrue(FormErrorClassifier.isCoachable(FormErrorCode.SAG))
    }
}
