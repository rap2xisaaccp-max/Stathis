package citu.edu.stathis.mobile.features.tasks.data.model

data class ScoreResponse(
    val physicalId: String? = null,
    val studentId: String? = null,
    val taskId: String? = null,
    val templateId: String? = null,
    val quizTemplateId: String? = null,
    val exerciseTemplateId: String? = null,
    val score: Int? = null,
    val maxScore: Int? = null,
    val attempts: Int? = null,
    val reps: Int? = null,
    val goalReps: Int? = null,
    val accuracy: Double? = null,
    val timeTaken: Long? = null,
    val completed: Boolean? = null,
    val isQuiz: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) 
