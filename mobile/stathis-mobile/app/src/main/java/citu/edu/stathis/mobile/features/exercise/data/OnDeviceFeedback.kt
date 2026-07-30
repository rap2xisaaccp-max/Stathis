package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState

data class OnDeviceFeedback(
    val exerciseType: ExerciseType,
    val exerciseState: ExerciseState,
    val repCount: Int,
    val formIssues: List<String>,
    val confidence: Float,
    val angleData: Map<String, Double>,
    /** Backend posture rule flags from /api/posture/classify (may be empty offline). */
    val backendFlags: List<String> = emptyList(),
    /** Rule-derived severity [0,1] from backend; null when not yet classified. */
    val ruleSeverity: Double? = null
)