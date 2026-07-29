package citu.edu.stathis.mobile.features.tasks.data.model

data class ExerciseResultSubmission(
    val reps: Int,
    val accuracy: Double,
    val timeTaken: Long,
    val goalReps: Int? = null,
    val caloriesBurned: Double? = null,
    val exerciseType: String? = null,
    val classroomId: String? = null
)

data class ExerciseProgressPayload(
    val classroomId: String? = null,
    val taskId: String? = null,
    val exerciseTemplateId: String? = null,
    val exerciseType: String? = null,
    val reps: Int = 0,
    val goalReps: Int? = null,
    val accuracy: Double = 0.0,
    val timeTakenMs: Long = 0,
    val sessionCaloriesBurned: Double? = null,
    val score: Int? = null,
    val completed: Boolean = false
)
