package citu.edu.stathis.mobile.features.exercise.adaptive

import citu.edu.stathis.mobile.features.exercise.data.remote.api.AdaptiveApi
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
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
}

internal class RecordingEvidenceCapture : FormEvidenceCapture {
    val events = mutableListOf<FormEvidenceEvent>()

    override fun onConfirmedCoaching(event: FormEvidenceEvent) {
        events.add(event)
    }
}

internal class RecordingCoachingDelivery : CoachingDelivery {
    var spokeCount = 0
    var stopCount = 0

    override fun ensureInitialized() = Unit

    override fun deliver(feedback: DeliveredFeedback, now: Long): DeliveredFeedback {
        val planned =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = feedback.interventionId,
                modality = feedback.modality,
                errorCode = feedback.errorCode,
                message = feedback.message,
                exerciseType = feedback.exerciseType
            )
        if (planned.speak) spokeCount++
        return planned
    }

    override fun stopSpeaking() {
        stopCount++
    }
}

internal class FakeAdaptiveApi : AdaptiveApi {
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

    override suspend fun recordSession(exerciseType: String) = emptyMap<String, Any?>()

    override suspend fun getOwnProfile() = StudentLearningProfileDto()

    override suspend fun getOwnMastery() = emptyList<ExerciseMasteryDto>()

    override suspend fun getOwnFormMastery() = emptyList<FormMasteryDto>()
}
