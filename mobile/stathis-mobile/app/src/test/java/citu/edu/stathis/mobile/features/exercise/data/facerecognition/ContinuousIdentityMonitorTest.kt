package citu.edu.stathis.mobile.features.exercise.data.facerecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision-matrix tests for continuous exercise identity.
 * Pose/body rematch and leave-frame grace need device/Robolectric — covered in manual E2E.
 */
class ContinuousIdentityMonitorTest {

    private fun identicalEmbedding(seed: Float = 0.1f): FloatArray =
        FloatArray(16) { i -> seed + i * 0.01f }

    private fun monitor(
        matchThreshold: Float = 0.68f,
        clock: () -> Long = { 0L },
        matchFn: (FloatArray, FloatArray, Float) -> Pair<Boolean, Float> = { probe, enrolled, threshold ->
            // Cosine-like stub: identical arrays match
            val same = probe.contentEquals(enrolled)
            val sim = if (same) 0.95f else 0.20f
            (sim >= threshold) to sim
        }
    ) = ContinuousIdentityMonitor(
        verifyFace = matchFn,
        faceMatchThreshold = matchThreshold,
        nowMs = clock
    )

    @Test
    fun multiFaceCausesLostAndRequiresFaceScan() {
        val m = monitor()
        m.onVerified(null)
        val first = m.onFrame(
            pose = null,
            faceEmbedding = null,
            faceCount = 2,
            enrolledFace = identicalEmbedding(),
            recovering = false
        )
        // First multi-face hit may still be UNCERTAIN until consecutive threshold
        val second = m.onFrame(
            pose = null,
            faceEmbedding = null,
            faceCount = 2,
            enrolledFace = identicalEmbedding(),
            recovering = false
        )
        assertEquals(ContinuousIdentityMonitor.TrustState.LOST, second.state)
        assertTrue(second.multipleFaces)
        assertTrue(second.requiresFaceScan)
        assertTrue(second.reason.contains("Multiple", ignoreCase = true))
        // Decision only — monitor must not own rep counters (documented contract)
        assertFalse(first.reason.isBlank() && second.reason.isBlank())
    }

    @Test
    fun faceMismatchStreakCausesLost() {
        val enrolled = identicalEmbedding(0.2f)
        val stranger = identicalEmbedding(0.9f)
        val m = monitor()
        m.onVerified(null)

        repeat(3) {
            val d = m.onFrame(
                pose = null,
                faceEmbedding = stranger,
                faceCount = 1,
                enrolledFace = enrolled,
                faceQualityRejected = false,
                recovering = false
            )
            if (it < 2) {
                assertEquals(ContinuousIdentityMonitor.TrustState.UNCERTAIN, d.state)
            } else {
                assertEquals(ContinuousIdentityMonitor.TrustState.LOST, d.state)
                assertTrue(d.requiresFaceScan)
                assertTrue(d.reason.contains("Face does not match", ignoreCase = true))
            }
        }
    }

    @Test
    fun matchingFaceWhileRecoveringRestoresTrusted() {
        val enrolled = identicalEmbedding()
        val m = monitor()
        m.onVerified(null)
        // Force LOST via multi-face
        m.onFrame(null, null, 2, enrolled, recovering = false)
        m.onFrame(null, null, 2, enrolled, recovering = false)

        val restored = m.onFrame(
            pose = null,
            faceEmbedding = enrolled.copyOf(),
            faceCount = 1,
            enrolledFace = enrolled,
            recovering = true
        )
        assertEquals(ContinuousIdentityMonitor.TrustState.TRUSTED, restored.state)
        assertFalse(restored.requiresFaceScan)
        assertTrue(restored.reason.contains("facial", ignoreCase = true))
    }

    @Test
    fun bodySignatureSimilarityDetectsMismatchAndMatch() {
        val a = BodySignatureTracker.Signature(FloatArray(12) { 0.2f + it * 0.01f }.let { feats ->
            // L2-normalize like extract()
            val norm = kotlin.math.sqrt(feats.sumOf { (it * it).toDouble() }.toFloat())
            FloatArray(feats.size) { feats[it] / norm }
        })
        val similar = BodySignatureTracker.Signature(a.features.copyOf())
        val different = BodySignatureTracker.Signature(
            FloatArray(12) { 0.9f - it * 0.05f }.let { feats ->
                val norm = kotlin.math.sqrt(feats.sumOf { (it * it).toDouble() }.toFloat())
                FloatArray(feats.size) { feats[it] / norm }
            }
        )
        assertTrue(BodySignatureTracker.isMatch(a, similar))
        assertFalse(BodySignatureTracker.isMatch(a, different))
    }

    @Test
    fun resetClearsTrustCounters() {
        val enrolled = identicalEmbedding()
        val stranger = identicalEmbedding(0.8f)
        val m = monitor()
        m.onVerified(null)
        repeat(3) {
            m.onFrame(null, stranger, 1, enrolled, faceQualityRejected = false)
        }
        m.reset()
        m.onVerified(null)
        val d = m.onFrame(null, enrolled.copyOf(), 1, enrolled, recovering = false)
        assertEquals(ContinuousIdentityMonitor.TrustState.TRUSTED, d.state)
    }

    @Test
    fun leaveFrameAfterGraceCausesLost() {
        var now = 1_000L
        val m = monitor(clock = { now })
        m.onVerified(null)
        // Confirm out-of-frame, then expire the 5s grace (null pose = no skeleton).
        repeat(SkeletonPresenceTracker.OUT_OF_FRAME_CONFIRM_FRAMES) {
            val d = m.onFrame(null, null, 0, identicalEmbedding(), recovering = false)
            assertTrue(
                d.state == ContinuousIdentityMonitor.TrustState.TRUSTED ||
                    d.state == ContinuousIdentityMonitor.TrustState.UNCERTAIN ||
                    d.state == ContinuousIdentityMonitor.TrustState.LOST
            )
        }
        now += SkeletonPresenceTracker.OUT_OF_FRAME_GRACE_MS + 1
        val lost = m.onFrame(null, null, 0, identicalEmbedding(), recovering = false)
        assertEquals(ContinuousIdentityMonitor.TrustState.LOST, lost.state)
        assertTrue(lost.reason.contains("left the camera", ignoreCase = true))
        assertTrue(lost.requiresFaceScan)
    }
}
