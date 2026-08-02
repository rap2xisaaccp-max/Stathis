package citu.edu.stathis.mobile.features.tasks.presentation

import citu.edu.stathis.mobile.features.tasks.data.model.Task
import citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse

/**
 * Shared helpers so Learn tab and Classroom Detail use the same completion rules.
 */
object TaskProgressCalculator {

    fun isTaskCompleted(
        task: Task,
        taskProgressMap: Map<String, TaskProgressResponse?>?
    ): Boolean {
        val progress = taskProgressMap?.get(task.physicalId)
        val hasQuizAttempts = (progress?.quizAttempts ?: 0) > 0 || progress?.quizCompleted == true
        val hasLessonAttempts = progress?.lessonCompleted == true ||
            LessonAttemptsCache.getAttempts(task.physicalId) > 0
        val hasExerciseAttempts = progress?.exerciseCompleted == true ||
            (progress?.exerciseAttempts ?: 0) > 0 ||
            progress?.completedExercises?.isNotEmpty() == true
        return hasQuizAttempts ||
            hasLessonAttempts ||
            hasExerciseAttempts ||
            progress?.isCompleted == true ||
            TaskCompletionCache.isCompleted(task.physicalId)
    }

    fun activeTasks(tasks: List<Task>): List<Task> =
        tasks.filter { it.isActive ?: true }

    /** 0f..1f fraction of completed active tasks. */
    fun progressFraction(
        tasks: List<Task>,
        taskProgressMap: Map<String, TaskProgressResponse?>? = null
    ): Float {
        val active = activeTasks(tasks)
        if (active.isEmpty()) return 0f
        val completed = active.count { isTaskCompleted(it, taskProgressMap) }
        return completed.toFloat() / active.size
    }

    /** Display label such as "42%". */
    fun progressPercentageLabel(
        tasks: List<Task>,
        taskProgressMap: Map<String, TaskProgressResponse?>? = null
    ): String = "${(progressFraction(tasks, taskProgressMap) * 100).toInt()}%"
}
