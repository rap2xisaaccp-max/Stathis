package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.ExerciseDetector
import citu.edu.stathis.mobile.features.exercise.data.ExerciseResult
import citu.edu.stathis.mobile.features.exercise.data.remote.api.AdaptiveApi
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveFeedbackEngineHeldErrorTest {

    @Test
    fun heldIncorrectFormPastWindowProducesOneInterventionAndOneCapture() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Push knees outward over toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        assertTrue(delivery.spokeCount >= 1)

        // Hold the same error for 40s (well past 10s window + 8s cooldown), no extra reps.
        while (now <= 41_000L) {
            engine.onFormSignal(
                formIssues = listOf("Push knees outward over toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        assertEquals(1, delivery.spokeCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
    }

    @Test
    fun startingAnotherExerciseResetsSessionAndLifecycle() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Push knees outward over toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        val squatSession = engine.currentSessionId()
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)

        engine.startSession("PUSH_UP", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        assertTrue(engine.currentSessionId().isNotBlank())
        assertTrue(engine.currentSessionId() != squatSession)
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertEquals(InterventionPhase.OBSERVING, engine.lifecyclePhase())
        assertEquals(1, capture.events.size)
    }

    @Test
    fun leavingOneExerciseDoesNotEndTheNextExerciseSession() = runBlocking {
        val api = FakeAdaptiveApi()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                api,
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                RecordingEvidenceCapture()
            )
        engine.startSession("PUSH_UP", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        val pushToken = engine.captureFlushToken()

        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        val squatSession = engine.currentSessionId()
        repeat(3) { index ->
            engine.onFormSignal(
                formIssues = listOf("Push knees outward over toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = 1_000L + index * 100L
            )
        }
        assertEquals(1, engine.sessionSummary().interventionCount)

        engine.flush(pushToken)
        engine.endSession(pushToken.sessionId)

        assertEquals(squatSession, engine.currentSessionId())
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
        assertEquals(listOf("PUSH_UP"), api.recordedSessions)
        assertTrue(engine.activeFeedback()?.speak == true)
    }

    @Test
    fun correctsThenRepeatsMistakeCreatesSecondCoachingAndSnapshot() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        suspend fun kneesIn(at: Long, reps: Int = 0) =
            engine.onFormSignal(
                formIssues = listOf("Push knees outward over toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = reps,
                now = at
            )

        suspend fun clear(at: Long, reps: Int = 0) =
            engine.onFormSignal(
                formIssues = emptyList(),
                flags = emptyList(),
                severity = 0.0,
                currentReps = reps,
                now = at
            )

        var now = 1_000L
        var last: DeliveredFeedback? = null
        repeat(3) {
            last = kneesIn(now)
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        val firstDelivery = last
        assertTrue(firstDelivery != null && firstDelivery.highlightJoints && firstDelivery.speak)
        val firstId = capture.events[0].interventionId
        assertTrue(firstId.startsWith("FI-"))
        assertEquals(
            FormErrorCopy.explanation(FormErrorCode.KNEES_IN, "SQUATS"),
            capture.events[0].errorDescription
        )
        assertTrue(capture.events[0].correctionText.isNotBlank())

        repeat(20) {
            kneesIn(now)
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)

        repeat(3) {
            clear(now)
            now += 100L
        }
        now += 9_000L
        last = null
        repeat(3) {
            last = kneesIn(now)
            now += 100L
        }

        assertEquals(2, engine.sessionSummary().interventionCount)
        assertEquals(2, capture.events.size)
        assertEquals(2, delivery.spokeCount)
        val secondId = capture.events[1].interventionId
        assertTrue(secondId.startsWith("FI-"))
        assertTrue(firstId != secondId)
        val secondDelivery = last
        assertTrue(secondDelivery != null && secondDelivery.highlightJoints && secondDelivery.speak)
        assertEquals(FormErrorCode.KNEES_IN, capture.events[1].errorCode)
        assertEquals(capture.events[0].errorDescription, capture.events[1].errorDescription)
        assertTrue(capture.events[1].correctionText.isNotBlank())
    }

    @Test
    fun errorCodeFlickerDoesNotCreateAdditionalCoachingOrSnapshots() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        val flickerFlags = listOf("KNEES_IN", "PIKE", "SAG", "DEPTH_LOW", "LOW_ROM", "CHEST_UP")
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.8,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        val firstId = capture.events.single().interventionId

        repeat(30) { i ->
            val flag = flickerFlags[i % flickerFlags.size]
            engine.onFormSignal(
                formIssues = listOf("form issue $flag"),
                flags = listOf(flag),
                severity = 0.8,
                currentReps = 0,
                now = now
            )
            now += 200L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        assertEquals(firstId, capture.events[0].interventionId)
        assertEquals(1, delivery.spokeCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
    }

    @Test
    fun genuineClearCooldownThenNewErrorCreatesSecondSnapshot() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        repeat(3) {
            engine.onFormSignal(
                formIssues = emptyList(),
                flags = emptyList(),
                severity = 0.0,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        now += 9_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Squat deeper."),
                flags = listOf("DEPTH_LOW"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(2, engine.sessionSummary().interventionCount)
        assertEquals(2, capture.events.size)
        assertTrue(capture.events[0].interventionId != capture.events[1].interventionId)
        assertEquals(FormErrorCode.KNEES_IN, capture.events[0].errorCode)
        assertEquals(FormErrorCode.DEPTH_LOW, capture.events[1].errorCode)
    }

    @Test
    fun technicalSignalsNeverClaimOrCapture() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS")
        var now = 1_000L
        repeat(20) {
            engine.onFormSignal(
                formIssues = listOf("Ensure major body parts are visible."),
                flags = listOf("LOW_CONFIDENCE"),
                severity = 0.9,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertTrue(capture.events.isEmpty())
    }

    @Test
    fun squatPikeFlagsDoNotCreateCoachingOrEvidence() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        var now = 1_000L
        repeat(10) {
            engine.onFormSignal(
                formIssues = listOf("Keep a straight line from head to heels."),
                flags = listOf("PIKE"),
                severity = 0.9,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(0, engine.sessionSummary().interventionCount)
        assertTrue(capture.events.isEmpty())
        assertTrue(engine.sessionSummary().errorCodes.none { it == "PIKE" })
    }

    @Test
    fun heldKneesInWithIncreasingRepsStaysOneInterventionAndOneTts() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        var now = 1_000L
        var reps = 0
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, delivery.spokeCount)

        while (reps <= 12) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = reps,
                now = now
            )
            now += 1_000L
            reps += 3
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        assertEquals(1, delivery.spokeCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
    }

    @Test
    fun heldSagWithIncreasingRepsStaysOneInterventionAndOneTts() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("PUSH_UP", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your hips level with your shoulders."),
                flags = listOf("SAG"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, delivery.spokeCount)

        var reps = 3
        while (reps <= 15) {
            engine.onFormSignal(
                formIssues = listOf("Keep your hips level with your shoulders."),
                flags = listOf("SAG"),
                severity = 0.8,
                currentReps = reps,
                now = now
            )
            now += 1_000L
            reps += 3
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(1, capture.events.size)
        assertEquals(1, delivery.spokeCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())
    }

    @Test
    fun startSessionRetryResetsLifecycleWithoutNeedingAClear() = runBlocking {
        val capture = RecordingEvidenceCapture()
        val delivery = RecordingCoachingDelivery()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                delivery,
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 4,
                now = now
            )
            now += 100L
        }
        assertEquals(1, delivery.spokeCount)
        assertEquals(InterventionPhase.RESPONSE_OBSERVATION, engine.lifecyclePhase())

        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 2)
        assertEquals(InterventionPhase.OBSERVING, engine.lifecyclePhase())
        assertEquals(0, engine.sessionSummary().interventionCount)

        now = 20_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your knees aligned with your toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, engine.sessionSummary().interventionCount)
        assertEquals(2, delivery.spokeCount)
        assertTrue(engine.activeFeedback()?.speak == true)
        assertTrue(engine.activeFeedback()?.highlightJoints == true)
    }

    @Test
    fun failedEvidenceUploadStaysQueuedAndSuccessfulUploadIsAcknowledged() = runBlocking {
        val queue = InMemoryEvidenceQueue()
        var uploads = 0
        val api =
            object : AdaptiveApi by FakeAdaptiveApi() {
                override suspend fun uploadEvidence(
                    interventionId: RequestBody,
                    sessionId: RequestBody,
                    taskId: RequestBody?,
                    classroomId: RequestBody?,
                    attemptNumber: RequestBody?,
                    exerciseType: RequestBody,
                    errorCode: RequestBody,
                    errorDescription: RequestBody,
                    correctionText: RequestBody,
                    capturedAt: RequestBody,
                    file: MultipartBody.Part
                ): Map<String, Any?> {
                    uploads += 1
                    if (uploads == 1) throw java.io.IOException("storage unavailable")
                    return mapOf("physicalId" to "FCE-OK")
                }
            }
        val engine =
            AdaptiveFeedbackEngine(
                api,
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                queue,
                RecordingEvidenceCapture()
            )
        queue.enqueue(queuedEvidence("FI-FAIL"), byteArrayOf(1, 2, 3))
        queue.enqueue(queuedEvidence("FI-OK"), byteArrayOf(4, 5, 6))

        engine.flush()

        assertEquals(listOf("FI-FAIL"), queue.pending().map { it.event.interventionId })
        assertEquals(2, uploads)

        engine.flush()
        assertTrue(queue.pending().isEmpty())
        assertEquals(3, uploads)
    }

    @Test
    fun twoDistinctCyclesReachTheQueueAndTechnicalSignalsDoNot() = runBlocking {
        val queue = InMemoryEvidenceQueue()
        val capture = EnqueueingEvidenceCapture(queue)
        val delivery = RecordingCoachingDelivery()
        var uploads = 0
        val api =
            object : AdaptiveApi by FakeAdaptiveApi() {
                override suspend fun uploadEvidence(
                    interventionId: RequestBody,
                    sessionId: RequestBody,
                    taskId: RequestBody?,
                    classroomId: RequestBody?,
                    attemptNumber: RequestBody?,
                    exerciseType: RequestBody,
                    errorCode: RequestBody,
                    errorDescription: RequestBody,
                    correctionText: RequestBody,
                    capturedAt: RequestBody,
                    file: MultipartBody.Part
                ): Map<String, Any?> {
                    uploads += 1
                    return mapOf("physicalId" to "FCE-$uploads")
                }
            }
        val engine =
            AdaptiveFeedbackEngine(
                api,
                delivery,
                AdaptiveOfflineQueue(),
                queue,
                capture
            )
        engine.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)

        var now = 1_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Push knees outward over toes."),
                flags = listOf("KNEES_IN"),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, capture.events.size)
        assertEquals(1, queue.pending().size)
        assertTrue(delivery.spokeCount >= 1)
        assertEquals(true, engine.activeFeedback()?.highlightJoints)
        assertEquals(true, engine.activeFeedback()?.speak)

        repeat(3) {
            engine.onFormSignal(emptyList(), emptyList(), 0.0, 0, now = now)
            now += 100L
        }
        now += 8_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Keep your torso upright as you squat."),
                flags = listOf("CHEST_UP"),
                severity = 0.7,
                currentReps = 1,
                now = now
            )
            now += 100L
        }
        assertEquals(2, capture.events.size)
        assertEquals(2, queue.pending().size)
        assertTrue(capture.events[0].interventionId != capture.events[1].interventionId)

        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Low detection confidence"),
                severity = 0.9,
                currentReps = 1,
                now = now
            )
            now += 100L
        }
        repeat(3) {
            engine.onFormSignal(
                formIssues = emptyList(),
                flags = listOf("PIKE"),
                severity = 0.7,
                currentReps = 1,
                now = now
            )
            now += 100L
        }
        assertEquals(2, capture.events.size)

        engine.flush()
        assertTrue(queue.isEmpty())
        assertEquals(2, uploads)
    }

    @Test
    fun sameSessionAndCycleReuseOneInterventionIdAfterEngineRestart() {
        val first =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                RecordingEvidenceCapture()
            )
        val second =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                RecordingEvidenceCapture()
            )
        val original =
            first.stableInterventionId("SES-SAME", "SQUATS", FormErrorCode.KNEES_IN, 1)
        val replayed =
            second.stableInterventionId("SES-SAME", "SQUATS", FormErrorCode.KNEES_IN, 1)
        val nextCycle =
            second.stableInterventionId("SES-SAME", "SQUATS", FormErrorCode.KNEES_IN, 2)
        assertEquals(original, replayed)
        assertTrue(original != nextCycle)

        second.startSession("SQUATS", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        assertTrue(second.currentSessionId() != "SES-SAME")
    }

    @Test
    fun fullDepthPushUpAndLungeReturnsDoNotCoachOrCapture() = runBlocking {
        val pushQueue = InMemoryEvidenceQueue()
        val pushCapture = EnqueueingEvidenceCapture(pushQueue)
        val pushEngine = engineWith(pushCapture, pushQueue)
        pushEngine.startSession("PUSH_UP", taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        val pushDetector = ExerciseDetector()
        var clock = 1_000L
        val pushFrames = mutableListOf<ExerciseResult>()
        repeat(3) { pushFrames += pushDetector.analyzePushupMetrics(170f, 0.9f, emptyList(), clock) }
        repeat(3) { pushFrames += pushDetector.analyzePushupMetrics(80f, 0.9f, emptyList(), clock) }
        repeat(4) { pushFrames += pushDetector.analyzePushupMetrics(120f, 0.9f, emptyList(), clock) }
        clock += 900L
        repeat(3) { pushFrames += pushDetector.analyzePushupMetrics(170f, 0.9f, emptyList(), clock) }
        var now = 1_000L
        for (frame in pushFrames) {
            assertFalse(frame.feedback.any { it.contains("Lower your chest", ignoreCase = true) })
            assertTrue(FormErrorMapper.resolve(emptyList(), frame.feedback, "PUSH_UP") != FormErrorCode.LOW_ROM)
            pushEngine.onFormSignal(frame.feedback, emptyList(), 0.7, frame.repCount, now = now)
            now += 100L
        }
        pushEngine.onFormSignal(listOf("Low detection confidence"), emptyList(), 0.9, 1, now = now)
        now += 100L
        pushEngine.onFormSignal(listOf("not a reviewed cue"), emptyList(), 0.7, 1, now = now)
        now += 100L
        pushEngine.onFormSignal(emptyList(), listOf("KNEES_IN"), 0.7, 1, now = now)
        assertTrue(pushCapture.events.isEmpty())
        assertTrue(pushQueue.pending().isEmpty())
        assertEquals(1, pushFrames.last().repCount)
        assertEquals(1, pushFrames.last().attemptedRepCount)
        assertFalse(pushFrames.last().repRejected)

        repeat(3) {
            pushEngine.onFormSignal(
                emptyList(),
                listOf("LOW_ROM"),
                0.7,
                1,
                now = now
            )
            now += 100L
        }
        assertEquals(1, pushCapture.events.size)
        assertEquals(FormErrorCode.LOW_ROM, pushCapture.events.single().errorCode)
        assertEquals(1, pushQueue.pending().size)

        val lungeQueue = InMemoryEvidenceQueue()
        val lungeCapture = EnqueueingEvidenceCapture(lungeQueue)
        val lungeEngine = engineWith(lungeCapture, lungeQueue)
        lungeEngine.startSession(
            "STATIC_LUNGES",
            taskId = "TASK-1",
            classroomId = "ROOM-1",
            attemptNumber = 1
        )
        val lungeDetector = ExerciseDetector()
        val lungeFrames = mutableListOf<ExerciseResult>()
        lungeFrames += lungeDetector.analyzeStaticLungeMetrics(100f, 160f, 400f, 300f, 0.9f)
        repeat(4) {
            lungeFrames += lungeDetector.analyzeStaticLungeMetrics(130f, 160f, 400f, 300f, 0.9f)
        }
        lungeFrames += lungeDetector.analyzeStaticLungeMetrics(160f, 160f, 300f, 300f, 0.9f)
        now = 20_000L
        for (frame in lungeFrames) {
            assertFalse(frame.feedback.any { it.contains("deeper", ignoreCase = true) })
            assertTrue(
                FormErrorMapper.resolve(emptyList(), frame.feedback, "STATIC_LUNGES") !=
                    FormErrorCode.DEPTH_LOW
            )
            lungeEngine.onFormSignal(frame.feedback, emptyList(), 0.7, frame.repCount, now = now)
            now += 100L
        }
        lungeEngine.onFormSignal(listOf("Low detection confidence"), emptyList(), 0.9, 1, now = now)
        now += 100L
        lungeEngine.onFormSignal(listOf("not a reviewed cue"), emptyList(), 0.7, 1, now = now)
        now += 100L
        lungeEngine.onFormSignal(emptyList(), listOf("PIKE"), 0.7, 1, now = now)
        assertTrue(lungeCapture.events.isEmpty())
        assertTrue(lungeQueue.pending().isEmpty())
        assertEquals(1, lungeFrames.last().repCount)
        assertEquals(1, lungeFrames.last().attemptedRepCount)
        assertFalse(lungeFrames.last().repRejected)

        repeat(6) {
            lungeEngine.onFormSignal(
                listOf("Bend the front knee deeper into the lunge."),
                listOf("DEPTH_LOW"),
                0.7,
                1,
                now = now
            )
            now += 100L
        }
        assertEquals(1, lungeCapture.events.size)
        assertEquals(FormErrorCode.DEPTH_LOW, lungeCapture.events.single().errorCode)
        assertEquals(1, lungeQueue.pending().size)
    }

    @Test
    fun pushUpLowRomAndLungeDepthEachStayOneEventWhileTheErrorHolds() {
        oneCoachingEventPerHold(
            exercise = "PUSH_UP",
            sentence = "Lower your chest closer to the ground.",
            flag = "LOW_ROM",
            crossExerciseFlag = "KNEES_IN"
        )
        oneCoachingEventPerHold(
            exercise = "STATIC_LUNGES",
            sentence = "Bend the front knee deeper into the lunge.",
            flag = "DEPTH_LOW",
            crossExerciseFlag = "PIKE"
        )
    }

    private fun engineWith(capture: FormEvidenceCapture, queue: InMemoryEvidenceQueue) =
        AdaptiveFeedbackEngine(
            FakeAdaptiveApi(),
            RecordingCoachingDelivery(),
            AdaptiveOfflineQueue(),
            queue,
            capture
        )

    private fun oneCoachingEventPerHold(
        exercise: String,
        sentence: String,
        flag: String,
        crossExerciseFlag: String
    ) = runBlocking {
        val capture = RecordingEvidenceCapture()
        val engine =
            AdaptiveFeedbackEngine(
                FakeAdaptiveApi(),
                RecordingCoachingDelivery(),
                AdaptiveOfflineQueue(),
                InMemoryEvidenceQueue(),
                capture
            )
        engine.startSession(exercise, taskId = "TASK-1", classroomId = "ROOM-1", attemptNumber = 1)
        var now = 1_000L
        repeat(6) {
            engine.onFormSignal(
                formIssues = listOf(sentence),
                flags = listOf(flag),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, capture.events.size)
        assertEquals(FormErrorCode.valueOf(flag), capture.events.single().errorCode)

        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf("Low detection confidence"),
                severity = 0.9,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        repeat(3) {
            engine.onFormSignal(
                formIssues = emptyList(),
                flags = listOf(crossExerciseFlag),
                severity = 0.7,
                currentReps = 0,
                now = now
            )
            now += 100L
        }
        assertEquals(1, capture.events.size)

        repeat(3) {
            engine.onFormSignal(emptyList(), emptyList(), 0.0, 0, now = now)
            now += 100L
        }
        now += 8_000L
        repeat(3) {
            engine.onFormSignal(
                formIssues = listOf(sentence),
                flags = listOf(flag),
                severity = 0.7,
                currentReps = 1,
                now = now
            )
            now += 100L
        }
        assertEquals(2, capture.events.size)
        assertTrue(capture.events[0].interventionId != capture.events[1].interventionId)
    }
}

private fun queuedEvidence(interventionId: String) =
    FormEvidenceEvent(
        interventionId = interventionId,
        sessionId = "SES-1",
        taskId = "TASK-1",
        classroomId = "ROOM-1",
        attemptNumber = 1,
        exerciseType = "SQUATS",
        errorCode = FormErrorCode.SAG,
        errorDescription = "Hips sagging",
        correctionText = "Keep hips level",
        capturedAtIso = "2026-08-21T00:00:00Z"
    )

private class EnqueueingEvidenceCapture(
    private val queue: EvidenceQueue
) : FormEvidenceCapture {
    val events = mutableListOf<FormEvidenceEvent>()

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        events += event
        queue.enqueue(event, ByteArray(16) { 4 })
    }
}

internal class RecordingEvidenceCapture : FormEvidenceCapture {
    val events = mutableListOf<FormEvidenceEvent>()

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        events.add(event)
    }
}

internal class RecordingCoachingDelivery : CoachingDelivery {
    var spokeCount = 0
    var technicalSpokeCount = 0
    var stopCount = 0
    var lastTechnicalMessage: String? = null
    val technicalMessages = mutableListOf<String>()
    private val speechGate = CoachingTtsSpeechGate()

    init {
        speechGate.markReady(0L)
    }

    override fun ensureInitialized() {
        speechGate.markReady(0L)
    }

    override fun resetSessionSpeech() {
        speechGate.resetAll()
        speechGate.markReady(0L)
    }

    override fun deliver(feedback: DeliveredFeedback, now: Long): DeliveredFeedback {
        val planned =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = feedback.interventionId,
                modality = feedback.modality,
                errorCode = feedback.errorCode,
                message = feedback.message,
                exerciseType = feedback.exerciseType
            )
        if (planned.speak) {
            val decision = speechGate.requestPhysical(planned.message, now)
            if (decision.action == CoachingTtsAction.SPEAK_NOW) {
                spokeCount++
                speechGate.markSpoken(CoachingTtsLane.PHYSICAL, now, planned.message)
            }
        }
        return planned
    }

    override fun speakTechnical(message: String, now: Long) {
        val decision = speechGate.requestTechnical(message, now)
        if (decision.action == CoachingTtsAction.SPEAK_NOW) {
            technicalSpokeCount++
            lastTechnicalMessage = decision.message
            technicalMessages += decision.message
            speechGate.markSpoken(CoachingTtsLane.TECHNICAL, now, decision.message)
        }
    }

    override fun onTechnicalConditionCleared() {
        speechGate.clearTechnical()
    }

    override fun stopSpeaking() {
        stopCount++
        speechGate.cancelPending()
        speechGate.resetAll()
        speechGate.markReady(0L)
    }
}

internal class FakeAdaptiveApi : AdaptiveApi {
    val recordedSessions = mutableListOf<String>()

    override suspend fun ingestBatch(body: AdaptiveBatchIngestDto) = AdaptiveBatchResultDto()

    override suspend fun uploadEvidence(
        interventionId: RequestBody,
        sessionId: RequestBody,
        taskId: RequestBody?,
        classroomId: RequestBody?,
        attemptNumber: RequestBody?,
        exerciseType: RequestBody,
        errorCode: RequestBody,
        errorDescription: RequestBody,
        correctionText: RequestBody,
        capturedAt: RequestBody,
        file: MultipartBody.Part
    ): Map<String, Any?> = emptyMap()

    override suspend fun recordSession(exerciseType: String): Map<String, Any?> {
        recordedSessions += exerciseType
        return emptyMap()
    }

    override suspend fun getOwnProfile() = StudentLearningProfileDto()

    override suspend fun getOwnMastery() = emptyList<ExerciseMasteryDto>()

    override suspend fun getOwnFormMastery() = emptyList<FormMasteryDto>()
}
