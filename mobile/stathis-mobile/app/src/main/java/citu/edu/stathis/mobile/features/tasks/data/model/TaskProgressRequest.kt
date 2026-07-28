package citu.edu.stathis.mobile.features.tasks.data.model

// Request payload sent to backend to update TaskCompletion progress
data class TaskProgressRequest(
    val lessonCompleted: Boolean? = null,
    val exerciseCompleted: Boolean? = null,
    val quizCompleted: Boolean? = null,
    val quizScore: Int? = null,
    val maxQuizScore: Int? = null,
    val quizAttempts: Int? = null,
    val totalTimeTaken: Long? = null,
    val repsPerformed: Int? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val submittedForReview: Boolean? = null,
    val submittedAt: String? = null
)