package citu.edu.stathis.mobile.features.exercise.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalityHighlightTargetsTest {

    @Test
    fun depthLowFocusesLowerBody() {
        val target = ModalityHighlightTargets.forError(FormErrorCode.DEPTH_LOW)
        assertTrue(target.joints.contains(ModalityHighlightTargets.LEFT_KNEE))
        assertTrue(target.joints.contains(ModalityHighlightTargets.LEFT_ANKLE))
        assertTrue(target.bones.any { it.first == ModalityHighlightTargets.LEFT_HIP })
    }

    @Test
    fun chestUpFocusesTorso() {
        val target = ModalityHighlightTargets.forError(FormErrorCode.CHEST_UP)
        assertTrue(target.joints.contains(ModalityHighlightTargets.LEFT_SHOULDER))
        assertTrue(target.joints.contains(ModalityHighlightTargets.LEFT_HIP))
        assertEquals(4, target.bones.size)
    }

    @Test
    fun sagAndPikeShareCoreLineFocus() {
        val sag = ModalityHighlightTargets.forError(FormErrorCode.SAG)
        val pike = ModalityHighlightTargets.forError(FormErrorCode.PIKE)
        val lungeKnees = ModalityHighlightTargets.forError(FormErrorCode.KNEES_IN, "STATIC_LUNGES")
        val squatKnees = ModalityHighlightTargets.forError(FormErrorCode.KNEES_IN, "SQUATS")
        // Exercise-aware paths must resolve without throwing; targets may differ by anatomy.
        assertTrue(lungeKnees.joints.isNotEmpty() || squatKnees.joints.isNotEmpty())
        assertEquals(sag.joints, pike.joints)
        assertTrue(sag.joints.contains(ModalityHighlightTargets.LEFT_HIP))
        assertTrue(sag.joints.contains(ModalityHighlightTargets.LEFT_SHOULDER))
    }
}
