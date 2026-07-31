package citu.edu.stathis.mobile.features.exercise.data.templates

import citu.edu.stathis.mobile.features.onboarding.domain.model.ExperienceLevel

data class ExerciseTemplate(
    val physicalId: String,
    val title: String,
    val description: String,
    val exerciseType: String,
    val exerciseDifficulty: ExperienceLevel,
    val goalReps: Int?,
    val goalTime: Int?,
    val goalAccuracy: Int?
)

/** Legacy practice IDs that still appear in bookmarks / older builds. */
private val PRACTICE_TEMPLATE_ID_ALIASES = mapOf(
    "EXERCISE-BEG-002" to "EXERCISE-PRACTICE-PUSHUP",
    "EXERCISE-BEG-003" to "EXERCISE-PRACTICE-GLUTE-BRIDGE",
    "EXERCISE-BEG-004" to "EXERCISE-PRACTICE-STATIC-LUNGES",
    "EXERCISE-BEG-005" to "EXERCISE-PRACTICE-LEG-RAISES"
)

/**
 * Resolve a practice catalog template by physical id across all levels,
 * including legacy BEG-* aliases (especially Lying Leg Raises).
 */
fun findPracticeTemplate(exerciseId: String): ExerciseTemplate? {
    val resolvedId = PRACTICE_TEMPLATE_ID_ALIASES[exerciseId] ?: exerciseId
    return ExperienceLevel.entries
        .asSequence()
        .flatMap { generateTemplatesForLevel(it).asSequence() }
        .firstOrNull { it.physicalId == resolvedId || it.physicalId == exerciseId }
}

/**
 * Local practice catalog. exerciseType values match backend [edu.cit.stathis.task.enums.ExerciseType]
 * so pose detection and scoring aliases resolve the same way as classroom Push-up/Squat tasks.
 */
fun generateTemplatesForLevel(level: ExperienceLevel): List<ExerciseTemplate> {
    return when (level) {
        ExperienceLevel.BEGINNER -> listOf(
            ExerciseTemplate(
                physicalId = "EXERCISE-25-6994-096",
                title = "Bodyweight Squats",
                description = "Learn proper squat form with bodyweight reps.",
                exerciseType = "SQUATS",
                exerciseDifficulty = ExperienceLevel.BEGINNER,
                goalReps = 10,
                goalTime = 120,
                goalAccuracy = 90
            ),
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-PUSHUP",
                title = "Wall Push-ups",
                description = "Build pushing strength with safe wall push-ups.",
                exerciseType = "PUSH_UP",
                exerciseDifficulty = ExperienceLevel.BEGINNER,
                goalReps = 8,
                goalTime = 90,
                goalAccuracy = 85
            ),
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-GLUTE-BRIDGE",
                title = "Glute Bridge",
                description = "Strengthen your glutes and core with controlled bridges.",
                exerciseType = "GLUTE_BRIDGE",
                exerciseDifficulty = ExperienceLevel.BEGINNER,
                goalReps = 10,
                goalTime = 90,
                goalAccuracy = 85
            ),
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-STATIC-LUNGES",
                title = "Static Lunges",
                description = "Build balance and leg strength with split stance lunges.",
                exerciseType = "STATIC_LUNGES",
                exerciseDifficulty = ExperienceLevel.BEGINNER,
                goalReps = 10,
                goalTime = 90,
                goalAccuracy = 85
            ),
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-LEG-RAISES",
                title = "Lying Leg Raises",
                description = "Train your lower abs with slow, controlled leg raises.",
                exerciseType = "LYING_LEG_RAISES",
                exerciseDifficulty = ExperienceLevel.BEGINNER,
                goalReps = 10,
                goalTime = 90,
                goalAccuracy = 85
            )
        )
        ExperienceLevel.INTERMEDIATE -> listOf(
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-LUNGES-INT",
                title = "Alternating Lunges",
                description = "Improve balance and strength with lunges.",
                exerciseType = "STATIC_LUNGES",
                exerciseDifficulty = ExperienceLevel.INTERMEDIATE,
                goalReps = 12,
                goalTime = 120,
                goalAccuracy = 90
            ),
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-PUSHUP-INT",
                title = "Knee Push-ups",
                description = "Scaled push-ups focusing on controlled reps.",
                exerciseType = "PUSH_UP",
                exerciseDifficulty = ExperienceLevel.INTERMEDIATE,
                goalReps = 10,
                goalTime = 120,
                goalAccuracy = 90
            )
        )
        ExperienceLevel.ADVANCED -> listOf(
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-PUSHUP-ADV",
                title = "Full Push-ups",
                description = "Standard push-ups emphasizing full ROM.",
                exerciseType = "PUSH_UP",
                exerciseDifficulty = ExperienceLevel.ADVANCED,
                goalReps = 15,
                goalTime = 120,
                goalAccuracy = 92
            ),
            ExerciseTemplate(
                physicalId = "EXERCISE-PRACTICE-SQUAT-ADV",
                title = "Jump Squats",
                description = "Power variation to challenge explosiveness.",
                exerciseType = "SQUATS",
                exerciseDifficulty = ExperienceLevel.ADVANCED,
                goalReps = 12,
                goalTime = 90,
                goalAccuracy = 92
            )
        )
    }
}
