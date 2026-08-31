package citu.edu.stathis.mobile.features.classroom.presentation

import citu.edu.stathis.mobile.features.tasks.data.model.Task
import citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse

/**
 * Shared utility for calculating a student's progress within a classroom.
 *
 * This is the single source of truth for classroom progress so that the
 * classroom card (Learn tab) and the in-classroom progress overview remain
 * consistent. It uses the per-task progress endpoint
 * (`GET /api/student/tasks/{taskId}/progress`) which is the reliable backend
 * data source used throughout the app.
 *
 * Rule: a started/active task is complete when every template it actually
 * has (lesson/quiz/exercise) is done on the progress DTO.
 */
object ClassroomProgressCalculator {

    /**
     * Returns the completion percentage (0.0f - 1.0f) for the given tasks
     * based on the provided per-task progress map.
     */
    fun calculateProgress(
        tasks: List<Task>,
        taskProgressMap: Map<String, TaskProgressResponse?>
    ): Float {
        if (tasks.isEmpty()) return 0f

        val activeTasks = tasks.filter { task ->
            val active = task.isActive ?: true
            val started = task.isStarted == true
            active && started
        }

        if (activeTasks.isEmpty()) return 0f

        val completed = activeTasks.count { task ->
            citu.edu.stathis.mobile.features.tasks.presentation.TaskCompletionTruth.isFullyComplete(
                task,
                taskProgressMap[task.physicalId]
            )
        }

        return completed.toFloat() / activeTasks.size
    }

    /**
     * Returns the completion percentage as a formatted string (e.g. "42%").
     */
    fun calculateProgressPercentage(
        tasks: List<Task>,
        taskProgressMap: Map<String, TaskProgressResponse?>
    ): String {
        val pct = calculateProgress(tasks, taskProgressMap)
        return "${(pct * 100).toInt()}%"
    }
}