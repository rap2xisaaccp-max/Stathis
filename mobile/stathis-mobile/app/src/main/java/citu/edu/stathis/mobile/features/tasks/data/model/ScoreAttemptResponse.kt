package citu.edu.stathis.mobile.features.tasks.data.model

/**
 * One row per attempt for a student on a task.
 * Mirrors the backend ScoreAttemptResponseDTO exposed by
 * GET /api/v1/scores/student/{studentId}/task/{taskId}/attempts
 */
data class ScoreAttemptResponse(
    val physicalId: String? = null,
    val scorePhysicalId: String? = null,
    val studentId: String? = null,
    val taskId: String? = null,
    val quizTemplateId: String? = null,
    val exerciseTemplateId: String? = null,
    val attemptNumber: Int = 0,
    val score: Int = 0,
    val maxScore: Int = 0,
    val accuracy: Double? = null,
    val reps: Int? = null,
    val goalReps: Int? = null,
    val caloriesBurned: Double? = null,
    val timeTaken: Long? = null,
    val completedAt: String? = null,
    val createdAt: String? = null
)