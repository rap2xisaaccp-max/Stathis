package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFramingGateTest {

    private val frameW = 720
    private val frameH = 1280

    @Test
    fun validCenteredFramingForEverySupportedExercise() {
        ExerciseType.entries.forEach { type ->
            val verdict = ExerciseFramingGate.evaluate(type, centeredGeometry(type))
            assertTrue("$type should be framed", verdict.ok)
            assertEquals(ExerciseFramingGate.Reason.OK, verdict.reason)
        }
    }

    @Test
    fun requiredLandmarkMissing() {
        val types =
            listOf(
                ExerciseType.PUSHUP,
                ExerciseType.SQUAT,
                ExerciseType.STATIC_LUNGE,
                ExerciseType.GLUTE_BRIDGE,
                ExerciseType.LYING_LEG_RAISE
            )
        types.forEach { type ->
            val geometry = centeredGeometry(type, omit = setOf(ModalityHighlightTargets.LEFT_ANKLE))
            val verdict = ExerciseFramingGate.evaluate(type, geometry)
            assertFalse(verdict.ok)
            assertEquals(ExerciseFramingGate.Reason.MISSING_LANDMARK, verdict.reason)
            assertEquals(FormErrorCode.BODY_NOT_VISIBLE, verdict.errorCode)
            assertTrue(verdict.guidance.contains("visible", ignoreCase = true))
        }
    }

    @Test
    fun requiredLandmarkNearOrOutsideFrameEdge() {
        val geometry =
            centeredGeometry(ExerciseType.SQUAT).shift(ankleY = 1268f)
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.SQUAT, geometry)
        assertFalse(verdict.ok)
        assertTrue(
            verdict.reason == ExerciseFramingGate.Reason.NEAR_EDGE ||
                verdict.reason == ExerciseFramingGate.Reason.OUTSIDE_USABLE_FRAME
        )
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, verdict.errorCode)
        assertTrue(verdict.guidance.contains("Step back", ignoreCase = true))
    }

    @Test
    fun offCenterStudent() {
        val geometry = centeredGeometry(ExerciseType.SQUAT).shift(dx = -160f)
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.SQUAT, geometry)
        assertFalse(verdict.ok)
        assertEquals(ExerciseFramingGate.Reason.OFF_CENTER, verdict.reason)
        assertTrue(verdict.guidance.contains("center", ignoreCase = true))
    }

    @Test
    fun pushUpMissingHipsAndAnkles() {
        val noHips =
            centeredGeometry(
                ExerciseType.PUSHUP,
                omit =
                    setOf(
                        ModalityHighlightTargets.LEFT_HIP,
                        ModalityHighlightTargets.RIGHT_HIP
                    )
            )
        val noAnkles =
            centeredGeometry(
                ExerciseType.PUSHUP,
                omit =
                    setOf(
                        ModalityHighlightTargets.LEFT_ANKLE,
                        ModalityHighlightTargets.RIGHT_ANKLE
                    )
            )
        val hips = ExerciseFramingGate.evaluate(ExerciseType.PUSHUP, noHips)
        val ankles = ExerciseFramingGate.evaluate(ExerciseType.PUSHUP, noAnkles)
        assertFalse(hips.ok)
        assertFalse(ankles.ok)
        assertEquals(ExerciseFramingGate.Reason.MISSING_LANDMARK, hips.reason)
        assertEquals(ExerciseFramingGate.Reason.MISSING_LANDMARK, ankles.reason)
        assertTrue(hips.guidance.contains("hips", ignoreCase = true))
    }

    @Test
    fun gluteBridgeMissingAnkles() {
        val geometry =
            centeredGeometry(
                ExerciseType.GLUTE_BRIDGE,
                omit =
                    setOf(
                        ModalityHighlightTargets.LEFT_ANKLE,
                        ModalityHighlightTargets.RIGHT_ANKLE
                    )
            )
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.GLUTE_BRIDGE, geometry)
        assertFalse(verdict.ok)
        assertEquals(ExerciseFramingGate.Reason.MISSING_LANDMARK, verdict.reason)
        assertTrue(verdict.guidance.contains("feet", ignoreCase = true))
    }

    @Test
    fun lungeBackAnkleOutsideUsableFrame() {
        val geometry =
            centeredGeometry(ExerciseType.STATIC_LUNGE).replace(
                ModalityHighlightTargets.LEFT_ANKLE,
                x = 8f,
                y = 1100f
            )
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.STATIC_LUNGE, geometry)
        assertFalse(verdict.ok)
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, verdict.errorCode)
        assertTrue(
            verdict.reason == ExerciseFramingGate.Reason.OUTSIDE_USABLE_FRAME ||
                verdict.reason == ExerciseFramingGate.Reason.NEAR_EDGE ||
                verdict.reason == ExerciseFramingGate.Reason.OFF_CENTER
        )
    }

    @Test
    fun lyingLegRaiseAnklesLeaveUsableRaiseRoom() {
        val geometry = centeredGeometry(ExerciseType.LYING_LEG_RAISE).shift(ankleY = 90f)
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.LYING_LEG_RAISE, geometry)
        assertFalse(verdict.ok)
        assertEquals(ExerciseFramingGate.Reason.INSUFFICIENT_RAISE_ROOM, verdict.reason)
        assertTrue(verdict.guidance.contains("raise", ignoreCase = true))
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, verdict.errorCode)
    }

    @Test
    fun fillCenterCropTreatsSideLandmarkAsOutsideUsableFrame() {
        val geometry = centeredGeometry(ExerciseType.SQUAT).replace(
            ModalityHighlightTargets.LEFT_ANKLE,
            x = 50f,
            y = 1100f
        )
        val fullFrame = ExerciseFramingGate.evaluate(ExerciseType.SQUAT, geometry)
        assertTrue(fullFrame.ok)
        val cropped =
            ExerciseFramingGate.evaluate(
                ExerciseType.SQUAT,
                geometry,
                previewWidth = 720,
                previewHeight = 1600
            )
        assertFalse(cropped.ok)
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, cropped.errorCode)
        val (sourceW, sourceH) =
            ExerciseFramingGate.uprightSourceSize(frameW, frameH, 0)
        val visible =
            ExerciseFramingGate.fillCenterVisibleRect(sourceW, sourceH, 720, 1600)
        assertTrue(visible.left > 40f)
        assertTrue(50f < visible.left)
    }

    @Test
    fun framedButLowLikelihoodIsLowConfidenceNotBodyNotVisible() {
        val geometry = centeredGeometry(ExerciseType.SQUAT, likelihood = 0.3f)
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.SQUAT, geometry)
        assertFalse(verdict.ok)
        assertEquals(ExerciseFramingGate.Reason.LOW_CONFIDENCE, verdict.reason)
        assertEquals(FormErrorCode.LOW_CONFIDENCE, verdict.errorCode)
        assertTrue(verdict.guidance.contains("Hold still", ignoreCase = true))
    }

    @Test
    fun frontCameraMirroredCoordinatesMatchOverlayStudentView() {
        val rawX = 18f
        val geometry =
            centeredGeometry(ExerciseType.SQUAT, mirrored = true).replace(
                ModalityHighlightTargets.LEFT_ANKLE,
                x = rawX,
                y = 1100f
            )
        val (sourceW, _) = ExerciseFramingGate.uprightSourceSize(frameW, frameH, 0)
        val studentX = ExerciseFramingGate.studentViewX(rawX, sourceW, mirrored = true)
        // Same horizontal flip as PoseOverlayRenderer.mapPoint: rx = sourceW - x when mirrored.
        assertEquals(sourceW - rawX, studentX, 0.01f)
        assertTrue(studentX > sourceW * 0.9f)
        val verdict = ExerciseFramingGate.evaluate(ExerciseType.SQUAT, geometry)
        assertFalse(verdict.ok)
        assertEquals(FormErrorCode.BODY_NOT_VISIBLE, verdict.errorCode)
    }

    @Test
    fun rotationSwapsUprightSourceSizeLikeOverlay() {
        val (w, h) = ExerciseFramingGate.uprightSourceSize(1280, 720, 90)
        assertEquals(720, w)
        assertEquals(1280, h)
    }

    private fun centeredGeometry(
        type: ExerciseType,
        omit: Set<Int> = emptySet(),
        likelihood: Float = 0.9f,
        mirrored: Boolean = false
    ): PoseGeometry {
        val points = mutableListOf<PoseLandmarkPoint>()
        fun add(typeId: Int, x: Float, y: Float) {
            if (typeId in omit) return
            points += PoseLandmarkPoint(typeId, x, y, likelihood)
        }
        add(ModalityHighlightTargets.LEFT_SHOULDER, 270f, 360f)
        add(ModalityHighlightTargets.RIGHT_SHOULDER, 450f, 360f)
        add(ModalityHighlightTargets.LEFT_ELBOW, 240f, 520f)
        add(ModalityHighlightTargets.RIGHT_ELBOW, 480f, 520f)
        add(ModalityHighlightTargets.LEFT_WRIST, 220f, 680f)
        add(ModalityHighlightTargets.RIGHT_WRIST, 500f, 680f)
        add(ModalityHighlightTargets.LEFT_HIP, 290f, 640f)
        add(ModalityHighlightTargets.RIGHT_HIP, 430f, 640f)
        add(ModalityHighlightTargets.LEFT_KNEE, 285f, 880f)
        add(ModalityHighlightTargets.RIGHT_KNEE, 435f, 880f)
        add(ModalityHighlightTargets.LEFT_ANKLE, 280f, 1100f)
        add(ModalityHighlightTargets.RIGHT_ANKLE, 440f, 1100f)
        add(ModalityHighlightTargets.NOSE, 360f, 240f)
        return PoseGeometry(
            landmarks = points,
            frameWidth = frameW,
            frameHeight = frameH,
            rotationDegrees = 0,
            mirrored = mirrored
        )
    }

    private fun PoseGeometry.shift(dx: Float = 0f, ankleY: Float? = null): PoseGeometry =
        copy(
            landmarks =
                landmarks.map { lm ->
                    val y =
                        if (ankleY != null &&
                            (lm.type == ModalityHighlightTargets.LEFT_ANKLE ||
                                lm.type == ModalityHighlightTargets.RIGHT_ANKLE)
                        ) {
                            ankleY
                        } else {
                            lm.y
                        }
                    lm.copy(x = lm.x + dx, y = y)
                }
        )

    private fun PoseGeometry.replace(type: Int, x: Float, y: Float): PoseGeometry =
        copy(
            landmarks =
                landmarks.map { lm ->
                    if (lm.type == type) lm.copy(x = x, y = y) else lm
                }
        )
}
