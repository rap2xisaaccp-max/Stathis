package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * End-of-attempt sentences derived from this attempt's valid reps and latched errors.
 */
object AttemptResultCopy {
    fun didWell(validReps: Int): String =
        if (validReps > 0) {
            "You completed $validReps clean ${if (validReps == 1) "rep" else "reps"}."
        } else {
            "Keep working through the full movement."
        }

    fun improve(exerciseType: String?, errorCodes: List<String>): String {
        val exercise = CoachingInstructionCatalog.normalizeExercise(exerciseType)
        val counts = linkedMapOf<String, Int>()
        for (raw in errorCodes) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            counts[name] = (counts[name] ?: 0) + 1
        }
        val top =
            counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.key
        if (top == null) return "No form corrections this attempt."
        val code = runCatching { FormErrorCode.valueOf(top) }.getOrNull() ?: return "No form corrections this attempt."
        if (!FormErrorClassifier.isCoachableForExercise(exercise, code)) {
            return "No form corrections this attempt."
        }
        val reminder =
            CoachingInstructionCatalog.messageText(exercise, code, InstructionIntensity.REMINDER)
        return reminder.ifBlank { "No form corrections this attempt." }
    }

    fun notCountedLine(exerciseType: String?, error: FormErrorCode): String {
        val reminder =
            CoachingInstructionCatalog.messageText(
                exerciseType,
                error,
                InstructionIntensity.REMINDER
            ).trim()
        return if (reminder.isEmpty()) "Rep not counted." else "Rep not counted. $reminder"
    }
}
