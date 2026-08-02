package citu.edu.stathis.mobile.features.exercise.data

import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState


data class ExerciseResult(
    val state: ExerciseState,
    val feedback: List<String> = emptyList(),
    val repCompleted: Boolean = false,
    /** Pose landmark detection confidence [0,1]; not form quality. */
    val confidence: Float? = null,
    val repCount: Int = 0,
    /**
     * Exercise form quality for this frame [0,1], or null when form cannot be assessed
     * (waiting / missing landmarks / low detection). Used for session accuracy.
     */
    val formScore: Float? = null
)
