package citu.edu.stathis.mobile.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import citu.edu.stathis.mobile.core.learn.LearnPreferences
import citu.edu.stathis.mobile.features.exercise.data.templates.ExerciseTemplate
import citu.edu.stathis.mobile.features.exercise.data.templates.findPracticeTemplate
import citu.edu.stathis.mobile.features.exercise.data.templates.generateTemplatesForLevel
import citu.edu.stathis.mobile.features.onboarding.domain.model.ExperienceLevel

// Note: LearnScreen and PracticeScreen are now defined in their own files:
// - LearnScreen.kt (with classroom enrollment features)
// - PracticeScreen.kt (with dashboard features)

// --- Practice Subscreens ---
@Composable
fun PracticeExercisesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val learnPrefs = remember { LearnPreferences(context) }
    val level by learnPrefs.levelFlow.collectAsState(initial = ExperienceLevel.BEGINNER)
    val templates: List<ExerciseTemplate> = remember(level) { generateTemplatesForLevel(level) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Exercises",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Level: ${level.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        templates.forEachIndexed { index, t ->
            Button(
                // Same session path as Push-up / Squat / Glute / Lunges (no legacy preview UI).
                onClick = { navController.navigate("practice_session/${t.physicalId}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(t.title)
            }
            if (index < templates.lastIndex) Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Legacy route: older builds navigated here for a Health-Connect-centric preview.
 * Keep the route for deep links, but always use the shared attempt UI.
 */
@Composable
fun PracticeExercisePreviewScreen(
    exerciseId: String,
    navController: NavHostController
) {
    PracticeExerciseSessionScreen(exerciseId = exerciseId, navController = navController)
}

@Composable
fun PracticeExerciseSessionScreen(exerciseId: String, navController: NavHostController) {
    val practiceTemplate = remember(exerciseId) { findPracticeTemplate(exerciseId) }

    if (practiceTemplate == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Exercise not found",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
        return
    }

    // Same pre-attempt + camera attempt UI as classroom Push-up / Squat / Glute / Lunges.
    val taskTemplate = remember(practiceTemplate) {
        citu.edu.stathis.mobile.features.tasks.data.model.ExerciseTemplate(
            physicalId = practiceTemplate.physicalId,
            title = practiceTemplate.title,
            description = practiceTemplate.description,
            exerciseType = practiceTemplate.exerciseType,
            exerciseDifficulty = practiceTemplate.exerciseDifficulty.name,
            goalReps = practiceTemplate.goalReps ?: 10,
            goalAccuracy = practiceTemplate.goalAccuracy ?: 80,
            goalTime = practiceTemplate.goalTime ?: 60
        )
    }

    citu.edu.stathis.mobile.features.tasks.presentation.components.ExerciseTemplateRenderer(
        template = taskTemplate,
        classroomId = null,
        navController = navController,
        returnRouteAfterMetrics = "practice_session/${practiceTemplate.physicalId}",
        maxAttempts = 0,
        attemptsUsed = 0,
        // Practice is ungraded: adaptive flush stays inside the renderer; skip Complete API.
        onSessionFinished = { },
        onFinishSession = { navController.popBackStack() },
        onCancel = { navController.popBackStack() },
        modifier = Modifier.fillMaxSize()
    )
}
