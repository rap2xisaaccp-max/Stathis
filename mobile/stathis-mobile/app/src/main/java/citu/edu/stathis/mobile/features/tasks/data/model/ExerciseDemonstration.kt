package citu.edu.stathis.mobile.features.tasks.data.model

data class ExerciseDemonstration(
    val available: Boolean = false,
    val physicalId: String? = null,
    val taskId: String? = null,
    val exerciseTemplateId: String? = null,
    val originalFilename: String? = null,
    val contentType: String? = null,
    val byteSize: Long? = null,
    val createdAt: String? = null
)
