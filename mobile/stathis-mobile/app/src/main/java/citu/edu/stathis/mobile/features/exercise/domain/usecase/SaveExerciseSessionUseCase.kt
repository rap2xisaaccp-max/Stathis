package citu.edu.stathis.mobile.features.exercise.domain.usecase

import citu.edu.stathis.mobile.core.data.models.ClientResponse
import citu.edu.stathis.mobile.features.exercise.data.Exercise
import citu.edu.stathis.mobile.features.exercise.domain.repository.ExerciseRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Legacy graded-session saver. The `/api/exercise/sessions` endpoint no longer exists.
 * Adaptive closed-loop telemetry is handled by [citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveFeedbackEngine];
 * classroom task scores use student task completeExercise.
 */
@Singleton
class SaveExerciseSessionUseCase @Inject constructor(
    private val repository: ExerciseRepository
) {
    suspend operator fun invoke(
        userId: String,
        exercise: Exercise,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        durationMs: Long,
        repCount: Int,
        averageAccuracy: Float,
        formIssues: List<String>? = null,
        classroomId: String? = null,
        taskId: String? = null
    ): ClientResponse<Unit> {
        Timber.w(
            "SaveExerciseSessionUseCase is a no-op (stale API). Prefer AdaptiveFeedbackEngine.flush / task completeExercise. user=%s exercise=%s",
            userId,
            exercise.id
        )
        return repository.saveExerciseSession(
            citu.edu.stathis.mobile.features.exercise.data.ExerciseSessionResult(
                sessionId = "${exercise.id}_${System.currentTimeMillis()}",
                userId = userId,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                startTime = startTime,
                endTime = endTime,
                durationMs = durationMs,
                repCount = repCount,
                averageAccuracy = averageAccuracy,
                issuesDetected = formIssues ?: emptyList(),
                classroomId = classroomId,
                taskId = taskId
            )
        )
    }
}
