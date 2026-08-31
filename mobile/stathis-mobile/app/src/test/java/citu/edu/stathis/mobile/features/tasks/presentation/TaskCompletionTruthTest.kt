package citu.edu.stathis.mobile.features.tasks.presentation

import citu.edu.stathis.mobile.features.tasks.data.model.Task
import citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionTruthTest {

    @Test
    fun exerciseOnlyTaskCompletesFromExerciseFlagsWithoutCache() {
        val task = sampleTask(exercise = "EX-1")
        val progress = TaskProgressResponse(
            exerciseCompleted = true,
            exerciseAttempts = 1,
            isCompleted = true
        )
        assertTrue(TaskCompletionTruth.isExerciseDone(progress))
        assertTrue(TaskCompletionTruth.isFullyComplete(task, progress))
        assertTrue(TaskCompletionTruth.isCompletedForStudentList(task, progress, unavailable = false))
        assertFalse(TaskCompletionTruth.isCompletedForStudentList(task, progress, unavailable = true))
    }

    @Test
    fun completedExercisesListIsIgnored() {
        val task = sampleTask(exercise = "EX-1")
        val progress = TaskProgressResponse(
            completedExercises = listOf("EX-1"),
            exerciseCompleted = false,
            exerciseAttempts = 0,
            isCompleted = false
        )
        assertFalse(TaskCompletionTruth.isExerciseDone(progress))
        assertFalse(TaskCompletionTruth.isFullyComplete(task, progress))
    }

    @Test
    fun mixedTaskRequiresQuizAndExercise() {
        val task = sampleTask(quiz = "Q-1", exercise = "EX-1")
        val exerciseOnly = TaskProgressResponse(
            exerciseCompleted = true,
            exerciseAttempts = 1,
            quizCompleted = false,
            quizAttempts = 0
        )
        assertFalse(TaskCompletionTruth.isFullyComplete(task, exerciseOnly))
        val both = TaskProgressResponse(
            exerciseCompleted = true,
            exerciseAttempts = 1,
            quizCompleted = true,
            quizAttempts = 1
        )
        assertTrue(TaskCompletionTruth.isFullyComplete(task, both))
    }

    @Test
    fun classroomPercentUsesRequiredComponents() {
        val exerciseTask = sampleTask(id = "T1", exercise = "EX-1")
        val mixedTask = sampleTask(id = "T2", quiz = "Q-1", exercise = "EX-1")
        val map = mapOf(
            "T1" to TaskProgressResponse(exerciseCompleted = true, exerciseAttempts = 1),
            "T2" to TaskProgressResponse(exerciseCompleted = true, exerciseAttempts = 1)
        )
        val pct = citu.edu.stathis.mobile.features.classroom.presentation.ClassroomProgressCalculator
            .calculateProgress(listOf(exerciseTask, mixedTask), map)
        assertEquals(0.5f, pct)
    }

    private fun sampleTask(
        id: String = "TASK-1",
        lesson: String? = null,
        quiz: String? = null,
        exercise: String? = null
    ) = Task(
        physicalId = id,
        name = "Task",
        description = "",
        submissionDate = "",
        closingDate = "",
        imageUrl = null,
        classroomPhysicalId = "ROOM-1",
        exerciseTemplateId = exercise,
        lessonTemplateId = lesson,
        quizTemplateId = quiz,
        maxAttempts = 3,
        createdAt = "",
        updatedAt = "",
        isActive = true,
        isStarted = true
    )
}
