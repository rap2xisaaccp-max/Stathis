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
        !task.exerciseTemplateId.isNullOrBlank() || task.exerciseTemplate != null

    fun isLessonDone(progress: TaskProgressResponse?): Boolean =
        progress?.lessonCompleted == true

    fun isQuizDone(progress: TaskProgressResponse?): Boolean =
        progress?.quizCompleted == true || (progress?.quizAttempts ?: 0) > 0

    fun isExerciseDone(progress: TaskProgressResponse?): Boolean =
        progress?.exerciseCompleted == true || (progress?.exerciseAttempts ?: 0) > 0

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
