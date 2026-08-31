package citu.edu.stathis.mobile.features.exercise.adaptive

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class FormEvidenceCaptureImplTest {

    private lateinit var buffer: LatestFrameBuffer
    private lateinit var queue: InMemoryEvidenceQueue
    private lateinit var capture: FormEvidenceCaptureImpl

    @Before
    fun setup() {
        buffer = LatestFrameBuffer()
        queue = InMemoryEvidenceQueue()
        capture = FormEvidenceCaptureImpl(buffer, queue)
        seedFrameAndPose()
    }

    @Test
    fun oneConfirmedAttemptEnqueuesAtMostOnceAcrossManyCorrections() {
        val first = coachableEvent("FI-HOLD", sessionId = "SES-HOLD")
        repeat(50) { index ->
            capture.onConfirmedCoaching(
                first.copy(
                    interventionId = "FI-HOLD-$index",
                    errorCode = if (index % 2 == 0) FormErrorCode.KNEES_IN else FormErrorCode.DEPTH_LOW
                )
            )
        }
        assertEquals(1, queue.pendingCount)
        assertEquals("FI-HOLD-0", queue.pending().single().event.interventionId)
        assertTrue(isJpeg(queue.pending().single().jpeg))
    }

    @Test
    fun eachRetrySessionMayEnqueueItsOwnSnapshot() {
        capture.onConfirmedCoaching(coachableEvent("FI-A1", sessionId = "SES-ATTEMPT-1"))
        capture.onConfirmedCoaching(coachableEvent("FI-A1b", sessionId = "SES-ATTEMPT-1"))
        capture.onConfirmedCoaching(coachableEvent("FI-A2", sessionId = "SES-ATTEMPT-2", attemptNumber = 2))
        capture.onConfirmedCoaching(coachableEvent("FI-A3", sessionId = "SES-ATTEMPT-3", attemptNumber = 3))

        assertEquals(3, queue.pendingCount)
        assertEquals(
            listOf("FI-A1", "FI-A2", "FI-A3"),
            queue.pending().map { it.event.interventionId }
        )
    }

    @Test
    fun technicalAndUnknownSignalsNeverEnqueue() {
        listOf(
            FormErrorCode.LOW_CONFIDENCE,
            FormErrorCode.LOW_VISIBILITY,
            FormErrorCode.BODY_NOT_VISIBLE,
            FormErrorCode.UNKNOWN
        ).forEachIndexed { index, code ->
            capture.onConfirmedCoaching(
                coachableEvent("FI-TECH-$index", sessionId = "SES-TECH-$index").copy(errorCode = code)
            )
        }
        capture.onConfirmedCoaching(
            coachableEvent("", sessionId = "SES-BLANK").copy(errorCode = FormErrorCode.KNEES_IN)
        )
        assertTrue(queue.isEmpty())
    }

    @Test
    fun crossExerciseMappingsNeverEnqueue() {
        capture.onConfirmedCoaching(
            coachableEvent("FI-SQUAT-PIKE", sessionId = "SES-X1").copy(errorCode = FormErrorCode.PIKE)
        )
        capture.onConfirmedCoaching(
            coachableEvent("FI-PUSH-DEPTH", sessionId = "SES-X2", exerciseType = "PUSH_UP")
                .copy(errorCode = FormErrorCode.DEPTH_LOW)
        )
        assertTrue(queue.isEmpty())
    }

    @Test
    fun doesNotCaptureWithoutABufferedFrame() {
        buffer.clear()
        capture.onConfirmedCoaching(coachableEvent("FI-NO-FRAME", sessionId = "SES-NO-FRAME"))
        assertTrue(queue.isEmpty())
    }

    @Test
    fun missingPoseDoesNotFabricateHighlightAndWaitsSafely() {
        buffer.clear()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        buffer.updateFromBitmap(bitmap)
        bitmap.recycle()
        capture.onConfirmedCoaching(coachableEvent("FI-NO-POSE", sessionId = "SES-NO-POSE"))
        assertTrue(queue.isEmpty())

        buffer.updatePose(samplePose())
        capture.onPreviewFrameAvailable()
        assertEquals(1, queue.pendingCount)
        assertEquals("FI-NO-POSE", queue.pending().single().event.interventionId)
    }

    @Test
    fun coldBufferAtConfirmationStillCapturesExactlyOnceOnALaterFrame() {
        buffer.clear()
        capture.onConfirmedCoaching(coachableEvent("FI-LATE", sessionId = "SES-LATE"))
        assertTrue(queue.isEmpty())

        seedFrameAndPose()
        repeat(20) { capture.onPreviewFrameAvailable() }

        assertEquals(1, queue.pendingCount)
        assertEquals("FI-LATE", queue.pending().single().event.interventionId)
    }

    @Test
    fun repeatedPreviewFramesDoNotDuplicateTheSameSessionJpeg() {
        capture.onConfirmedCoaching(coachableEvent("FI-ONCE", sessionId = "SES-ONCE"))
        repeat(15) {
            seedFrameAndPose()
            capture.onPreviewFrameAvailable()
            capture.onConfirmedCoaching(coachableEvent("FI-ONCE-AGAIN", sessionId = "SES-ONCE"))
        }
        assertEquals(1, queue.pendingCount)
    }

    @Test
    fun recordedInterventionIdIsReportedOnlyOnce() {
        capture.onConfirmedCoaching(coachableEvent("FI-NOTICE", sessionId = "SES-NOTICE"))
        assertEquals("FI-NOTICE", capture.consumeRecordedInterventionId())
        assertNull(capture.consumeRecordedInterventionId())
    }

    @Test
    fun snapshotEnqueuesWithoutConsumingRecordedInterventionId() {
        capture.onConfirmedCoaching(coachableEvent("FI-NO-UI", sessionId = "SES-NO-UI"))
        // UI no longer calls consumeRecordedInterventionId(); enqueue must not depend on it.
        assertEquals(1, queue.pendingCount)
        assertEquals("FI-NO-UI", queue.pending().single().event.interventionId)
        assertTrue(isJpeg(queue.pending().single().jpeg))
        assertEquals("FI-NO-UI", capture.consumeRecordedInterventionId())
    }

    @Test
    fun compositedSnapshotIsAValidJpegNotAUiScreenshotSize() {
        capture.onConfirmedCoaching(coachableEvent("FI-JPEG", sessionId = "SES-JPEG"))
        val jpeg = queue.pending().single().jpeg
        assertTrue(isJpeg(jpeg))
        val decoded = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertNotNull(decoded)
        assertEquals(32, decoded!!.width)
        assertEquals(32, decoded.height)
    }

    private fun seedFrameAndPose() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        buffer.updateFromBitmap(bitmap)
        bitmap.recycle()
        buffer.updatePose(samplePose())
    }

    private fun samplePose() =
        PoseGeometry(
            landmarks =
                listOf(
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_HIP, 10f, 16f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_HIP, 22f, 16f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_KNEE, 10f, 24f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_KNEE, 22f, 24f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_ANKLE, 10f, 30f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_ANKLE, 22f, 30f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_SHOULDER, 10f, 8f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_SHOULDER, 22f, 8f, 1f)
                ),
            frameWidth = 32,
            frameHeight = 32,
            rotationDegrees = 0,
            mirrored = false
        )

    private fun coachableEvent(
        id: String,
        sessionId: String = "SES-1",
        attemptNumber: Int = 1,
        exerciseType: String = "SQUATS"
    ) =
        FormEvidenceEvent(
            interventionId = id,
            sessionId = sessionId,
            taskId = "TASK-1",
            classroomId = "ROOM-1",
            attemptNumber = attemptNumber,
            exerciseType = exerciseType,
            errorCode = FormErrorCode.KNEES_IN,
            errorDescription = "Knees caving",
            correctionText = "Push knees out",
            capturedAtIso = "2026-08-21T00:00:00Z"
        )

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte()
}

@RunWith(RobolectricTestRunner::class)
class EvidenceHighlightCompositorTest {

    @Test
    fun cameraPlusHighlightProducesValidJpeg() {
        val frame = solidFrame(80, 80, Color.RED)
        val geometry = squatGeometry(80, 80, mirrored = false)
        val jpeg =
            EvidenceHighlightCompositor.composeJpeg(
                frame,
                geometry,
                FormErrorCode.KNEES_IN,
                "SQUATS"
            )
        assertNotNull(jpeg)
        assertTrue(jpeg!!.size >= 4)
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
        val decoded = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertNotNull(decoded)
        assertEquals(80, decoded!!.width)
        assertEquals(80, decoded.height)
    }

    @Test
    fun usesTheSameHighlightTargetAsTheLiveOverlayPlanner() {
        for (code in listOf(FormErrorCode.KNEES_IN, FormErrorCode.DEPTH_LOW, FormErrorCode.CHEST_UP)) {
            val overlay = PoseOverlayRenderer.highlightTarget(code, "SQUATS")
            val catalog = ModalityHighlightTargets.forError(code, "SQUATS")
            val planned =
                ModalityDeliveryPlanner.plan(
                    FeedbackModality.VERBAL_TTS,
                    code,
                    interventionLogged = true,
                    exerciseType = "SQUATS"
                )
            assertEquals(catalog, overlay)
            assertEquals(catalog.joints, planned.highlightJoints)
            assertEquals(catalog.bones, planned.highlightBones)
        }
        for (code in listOf(FormErrorCode.PIKE, FormErrorCode.SAG, FormErrorCode.LOW_ROM)) {
            val overlay = PoseOverlayRenderer.highlightTarget(code, "PUSH_UP")
            val catalog = ModalityHighlightTargets.forError(code, "PUSH_UP")
            assertEquals(catalog, overlay)
            assertTrue(overlay.joints.isNotEmpty())
        }
    }

    @Test
    fun squatKneesInPaintsHighlightAtMappedKneeNotUiChrome() {
        val frame = solidFrame(80, 80, Color.BLUE)
        val geometry = squatGeometry(80, 80, mirrored = false)
        val composed =
            EvidenceHighlightCompositor.compose(frame, geometry, FormErrorCode.KNEES_IN, "SQUATS")
        assertNotNull(composed)
        val (hx, hy) =
            PoseOverlayRenderer.mapPoint(
                20f,
                50f,
                composed!!.width,
                composed.height,
                geometry,
                1f,
                1f
            )
        assertTrue(
            "expected highlight near mapped knee ($hx,$hy)",
            hasHighlightNear(composed, hx.roundToInt(), hy.roundToInt())
        )
        assertTrue(isMostlyBlue(composed.getPixel(2, 2)))
        assertEquals(80, composed.width)
        assertEquals(80, composed.height)
    }

    @Test
    fun frontCameraMirroringPlacesHighlightOnTheMirroredSide() {
        val frame = solidFrame(80, 80, Color.BLUE)
        val geometry =
            PoseGeometry(
                landmarks =
                    listOf(
                        PoseLandmarkPoint(ModalityHighlightTargets.LEFT_KNEE, 10f, 50f, 1f)
                    ),
                frameWidth = 80,
                frameHeight = 80,
                rotationDegrees = 0,
                mirrored = true
            )
        val composed =
            EvidenceHighlightCompositor.compose(frame, geometry, FormErrorCode.KNEES_IN, "SQUATS")
        assertNotNull(composed)
        val (hx, hy) =
            PoseOverlayRenderer.mapPoint(
                10f,
                50f,
                composed!!.width,
                composed.height,
                geometry,
                1f,
                1f
            )
        assertEquals(70, hx.roundToInt())
        assertTrue(
            "highlight must land on the mirrored x=$hx, not the raw landmark x=10",
            hasHighlightNear(composed, hx.roundToInt(), hy.roundToInt())
        )
        assertFalse(hasHighlightNear(composed, 10, 50, radius = 3))
        assertTrue(isMostlyBlue(composed.getPixel(2, 2)))
    }

    @Test
    fun rotationNinetyProducesUprightCanvasAlignedWithLandmarks() {
        val frame = solidFrame(80, 40, Color.BLUE)
        val geometry =
            squatGeometry(80, 40, mirrored = false).copy(rotationDegrees = 90)
        val oriented = CameraFrameOrientation.toStudentView(frame, 90, false)
        val composed =
            EvidenceHighlightCompositor.compose(frame, geometry, FormErrorCode.DEPTH_LOW, "SQUATS")
        assertNotNull(composed)
        assertEquals(oriented.width, composed!!.width)
        assertEquals(oriented.height, composed.height)
        assertEquals(40, composed.width)
        assertEquals(80, composed.height)
    }

    @Test
    fun emptyPoseDoesNotFabricateAHighlight() {
        val frame = solidFrame(40, 40, Color.GREEN)
        val empty =
            PoseGeometry(
                landmarks = emptyList(),
                frameWidth = 40,
                frameHeight = 40,
                rotationDegrees = 0,
                mirrored = false
            )
        assertNull(
            EvidenceHighlightCompositor.compose(frame, empty, FormErrorCode.KNEES_IN, "SQUATS")
        )
        assertNull(
            EvidenceHighlightCompositor.composeJpeg(frame, empty, FormErrorCode.KNEES_IN, "SQUATS")
        )
    }

    @Test
    fun pushUpPikeTargetsMatchOverlayAndStayOnTheFrame() {
        val target = PoseOverlayRenderer.highlightTarget(FormErrorCode.PIKE, "PUSH_UP")
        assertEquals(ModalityHighlightTargets.forError(FormErrorCode.PIKE, "PUSH_UP"), target)
        assertTrue(target.joints.contains(ModalityHighlightTargets.LEFT_SHOULDER))
        assertTrue(target.joints.contains(ModalityHighlightTargets.LEFT_HIP))
        val frame = solidFrame(64, 64, Color.RED)
        val geometry = squatGeometry(64, 64, mirrored = false)
        val composed =
            EvidenceHighlightCompositor.compose(frame, geometry, FormErrorCode.PIKE, "PUSH_UP")
        assertNotNull(composed)
        assertEquals(64, composed!!.width)
        assertEquals(Color.RED, composed.getPixel(1, 1))
    }

    private fun solidFrame(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private fun isHighlightLike(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 160 && g > 80 && b < 80
    }

    private fun isMostlyBlue(pixel: Int): Boolean = Color.blue(pixel) > 180 && Color.red(pixel) < 40

    private fun hasHighlightNear(bitmap: Bitmap, x: Int, y: Int, radius: Int = 6): Boolean {
        val minX = (x - radius).coerceAtLeast(0)
        val maxX = (x + radius).coerceAtMost(bitmap.width - 1)
        val minY = (y - radius).coerceAtLeast(0)
        val maxY = (y + radius).coerceAtMost(bitmap.height - 1)
        for (px in minX..maxX) {
            for (py in minY..maxY) {
                if (isHighlightLike(bitmap.getPixel(px, py))) return true
            }
        }
        return false
    }

    private fun squatGeometry(width: Int, height: Int, mirrored: Boolean) =
        PoseGeometry(
            landmarks =
                listOf(
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_HIP, 20f, 35f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_HIP, 60f, 35f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_KNEE, 20f, 50f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_KNEE, 60f, 50f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_ANKLE, 20f, 70f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_ANKLE, 60f, 70f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.LEFT_SHOULDER, 20f, 15f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.RIGHT_SHOULDER, 60f, 15f, 1f),
                    PoseLandmarkPoint(ModalityHighlightTargets.NOSE, 40f, 8f, 1f)
                ),
            frameWidth = width,
            frameHeight = height,
            rotationDegrees = 0,
            mirrored = mirrored
        )
}

@RunWith(RobolectricTestRunner::class)
class CameraFrameOrientationTest {

    @Test
    fun rotationNinetySwapsDimensions() {
        val source = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        source.setPixel(0, 0, Color.GREEN)
        val rotated = CameraFrameOrientation.toStudentView(source, 90, false)
        assertEquals(50, rotated.width)
        assertEquals(100, rotated.height)
    }

    @Test
    fun frontCameraMirrorFlipsHorizontalPixels() {
        val source = Bitmap.createBitmap(10, 4, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.BLUE)
        source.setPixel(0, 1, Color.YELLOW)
        val mirrored = CameraFrameOrientation.toStudentView(source, 0, true)
        assertEquals(10, mirrored.width)
        assertEquals(4, mirrored.height)
        assertEquals(Color.YELLOW, mirrored.getPixel(9, 1))
        assertFalse(mirrored.getPixel(0, 1) == Color.YELLOW)
    }
}

@RunWith(RobolectricTestRunner::class)
class PoseOverlaySharedHighlightTest {

    @Test
    fun overlayAndEvidenceShareModalityHighlightTargets() {
        val squatCodes =
            listOf(FormErrorCode.KNEES_IN, FormErrorCode.DEPTH_LOW, FormErrorCode.CHEST_UP)
        squatCodes.forEach { code ->
            assertEquals(
                ModalityHighlightTargets.forError(code, "SQUATS"),
                PoseOverlayRenderer.highlightTarget(code, "SQUATS")
            )
        }
        val pushCodes = listOf(FormErrorCode.PIKE, FormErrorCode.SAG, FormErrorCode.LOW_ROM)
        pushCodes.forEach { code ->
            assertEquals(
                ModalityHighlightTargets.forError(code, "PUSH_UP"),
                PoseOverlayRenderer.highlightTarget(code, "PUSH_UP")
            )
        }
        val live =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = "FI-1",
                modality = FeedbackModality.VERBAL_TTS,
                errorCode = FormErrorCode.KNEES_IN,
                message = "Knees out",
                exerciseType = "SQUATS"
            )
        val evidence = PoseOverlayRenderer.highlightTarget(FormErrorCode.KNEES_IN, "SQUATS")
        assertEquals(evidence.joints, live.highlightLandmarkIds)
        assertEquals(evidence.bones, live.highlightBones)
    }
}
