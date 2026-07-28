package citu.edu.stathis.mobile.features.tasks.data.model

// Minimal model to accept backend TaskCompletion response
data class TaskCompletion(
    val physicalId: String,
    val studentId: String,
    val taskId: String,
    val lessonCompleted: Boolean,
    val quizCompleted: Boolean,
    val exerciseCompleted: Boolean,
    val isFullyCompleted: Boolean,
    val totalTimeTaken: Long?,
    val repsPerformed: Int?,
    val startedAt: String?,
    val completedAt: String?,
    val submittedForReview: Boolean,
    val submittedAt: String?
)
