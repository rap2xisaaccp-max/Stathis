package citu.edu.stathis.mobile.features.tasks.navigation

import androidx.navigation.NavController

const val taskListRoute = "task_list/{classroomId}"
const val taskDetailRoute = "task_detail/{taskId}"
const val taskQuizRoute = "task_quiz/{taskId}/{templateId}"
const val taskLessonRoute = "task_lesson/{taskId}/{templateId}"
const val taskExerciseRoute = "task_exercise/{taskId}/{templateId}"

fun NavController.navigateToTaskList(classroomId: String) {
    navigate("task_list/$classroomId")
}

fun NavController.navigateToTaskDetail(taskId: String) {
    navigate("task_detail/$taskId")
}

// Live classroom task routes are registered in HomeNavHost (task_detail / task_exercise /
// task_lesson / task_quiz) so exercise always opens TaskTemplateScreen → ExerciseTemplateRenderer
// with submitExercise. The former duplicate taskGraph() builder was removed to avoid a second
// incomplete registration that omitted navController for body-metrics / face-verify return routes.
