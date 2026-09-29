package citu.edu.stathis.mobile.features.tasks.presentation

import citu.edu.stathis.mobile.features.tasks.data.model.Task
import citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse

/**
 * Required-component completion from backend progress flags + the task's actual templates.
 * Does not use in-memory caches or [TaskProgressResponse.completedExercises], which the API never sends.
 */
object TaskCompletionTruth {

    fun hasLesson(task: Task): Boolean =
        !task.lessonTemplateId.isNullOrBlank() || task.lessonTemplate != null

    fun hasQuiz(task: Task): Boolean =
        !task.quizTemplateId.isNullOrBlank() || task.quizTemplate != null

    fun hasExercise(task: Task): Boolean =
        !task.exerciseTemplateId.isNullOrBlank() ||
            task.exerciseTemplate != null ||
            !task.exercises.isNullOrEmpty() ||
            (task.exercisesRequired ?: 0) > 0

    fun isLessonDone(progress: TaskProgressResponse?): Boolean =
        progress?.lessonCompleted == true

    fun isQuizDone(progress: TaskProgressResponse?): Boolean =
        progress?.quizCompleted == true || (progress?.quizAttempts ?: 0) > 0

    /**
     * One assigned exercise keeps the legacy attempt-or-flag rule.
     * More than one assigned exercise is done only when every required exercise qualifies.
     * Aggregate [TaskProgressResponse.exerciseAttempts] stays unused in that case so an older
     * reading of "any attempt means the task exercise is done" cannot fire early.
     */
    fun isExerciseDone(progress: TaskProgressResponse?): Boolean {
        val required = progress?.exercisesRequired ?: 0
        if (required > 1) {
            val done = progress?.exercisesCompleted ?: 0
            return done >= required || progress?.exerciseCompleted == true
        }
        return progress?.exerciseCompleted == true || (progress?.exerciseAttempts ?: 0) > 0
    }

    fun attemptsForExercise(progress: TaskProgressResponse?, templateId: String?): Int {
        if (progress == null) return 0
        val required = progress.exercisesRequired ?: 0
        if (required > 1) {
            return progress.exercises
                ?.firstOrNull { it.exerciseTemplateId == templateId }
                ?.attempts
                ?: 0
        }
        return progress.exerciseAttempts ?: 0
    }

    /**
     * Fully complete iff every template on [task] is done. Exercise-only tasks
     * complete after a successful exercise POST; mixed tasks still require each
     * attached component.
     */
    fun isFullyComplete(task: Task, progress: TaskProgressResponse?): Boolean {
        val needsLesson = hasLesson(task)
        val needsQuiz = hasQuiz(task)
        val needsExercise = hasExercise(task)
        if (!needsLesson && !needsQuiz && !needsExercise) {
            return progress?.isCompleted == true
        }
        if (needsLesson && !isLessonDone(progress)) return false
        if (needsQuiz && !isQuizDone(progress)) return false
        if (needsExercise && !isExerciseDone(progress)) return false
        return true
    }

    fun isCompletedForStudentList(
        task: Task,
        progress: TaskProgressResponse?,
        unavailable: Boolean
    ): Boolean {
        if (unavailable) return false
        return isFullyComplete(task, progress)
    }
}
