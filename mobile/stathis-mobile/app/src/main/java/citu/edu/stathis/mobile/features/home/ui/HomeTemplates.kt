package citu.edu.stathis.mobile.features.home.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import citu.edu.stathis.mobile.core.learn.LearnPreferences
import citu.edu.stathis.mobile.features.exercise.data.ExerciseType
import citu.edu.stathis.mobile.features.exercise.data.templates.ExerciseTemplate
import citu.edu.stathis.mobile.features.exercise.data.templates.generateTemplatesForLevel
import citu.edu.stathis.mobile.features.onboarding.domain.model.ExperienceLevel
import citu.edu.stathis.mobile.features.profile.ui.BodyMetricsGateViewModel
import kotlinx.coroutines.launch

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
                onClick = { navController.navigate("practice_exercise_preview/${t.physicalId}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("${t.title}")
            }
            if (index < templates.lastIndex) Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun PracticeExercisePreviewScreen(
    exerciseId: String,
    navController: NavHostController,
    bodyMetricsGate: BodyMetricsGateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val learnPrefs = remember { LearnPreferences(context) }
    val level by learnPrefs.levelFlow.collectAsState(initial = ExperienceLevel.BEGINNER)
    val template = remember(level, exerciseId) { generateTemplatesForLevel(level).find { it.physicalId == exerciseId } }
    val scope = rememberCoroutineScope()
    var isCheckingBodyMetrics by remember { mutableStateOf(false) }
    var bodyMetricsError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = template?.title ?: "Exercise Preview",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = template?.description ?: "ID: $exerciseId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Make sure Health Connect is enabled to show vitals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (template != null) {
                Text(
                    text = "Goals: " + listOfNotNull(
                        template.goalReps?.let { "${it} reps" },
                        template.goalTime?.let { "${it}s" },
                        template.goalAccuracy?.let { "${it}% accuracy" }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (bodyMetricsError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = bodyMetricsError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    scope.launch {
                        isCheckingBodyMetrics = true
                        bodyMetricsError = null
                        val complete = bodyMetricsGate.ensureComplete()
                        isCheckingBodyMetrics = false
                        if (complete) {
                            navController.navigate("practice_session/$exerciseId")
                        } else {
                            val returnRoute = Uri.encode("practice_exercise_preview/$exerciseId")
                            navController.navigate("body_metrics_setup?returnRoute=$returnRoute")
                        }
                    }
                },
                enabled = !isCheckingBodyMetrics,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isCheckingBodyMetrics) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking profile...")
                } else {
                    Text("Start Exercise")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { navController.navigate("health_connect") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) { Text("Review Health Connect") }
        }
    }
}

@Composable
fun PracticeExerciseSessionScreen(exerciseId: String, navController: NavHostController) {
    val context = LocalContext.current
    val learnPrefs = remember { LearnPreferences(context) }
    val level by learnPrefs.levelFlow.collectAsState(initial = ExperienceLevel.BEGINNER)
    val template = remember(level, exerciseId) { generateTemplatesForLevel(level).find { it.physicalId == exerciseId } }
    val exerciseType = remember(template) { resolveExerciseType(template?.exerciseType) }
    val adaptiveSessionViewModel: citu.edu.stathis.mobile.features.exercise.ui.viewmodel.AdaptiveSessionViewModel =
        hiltViewModel()
    val adaptiveFeedback by adaptiveSessionViewModel.feedback.collectAsState()
    val adaptiveHighlight by adaptiveSessionViewModel.highlight.collectAsState()
    val adaptiveHighlightLandmarks by adaptiveSessionViewModel.highlightLandmarks.collectAsState()
    val adaptiveHighlightBones by adaptiveSessionViewModel.highlightBones.collectAsState()
    val adaptiveSessionSummary by adaptiveSessionViewModel.sessionSummary.collectAsState()
    var showAdaptiveSummary by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(exerciseType) {
        adaptiveSessionViewModel.startSession(
            exerciseType = exerciseType?.name ?: template?.exerciseType ?: "UNKNOWN",
            staticControl = citu.edu.stathis.mobile.features.exercise.adaptive.RctExperimentPrefs.isStaticControl(context),
            sessionContext = citu.edu.stathis.mobile.features.exercise.adaptive.RctExperimentPrefs.CONTEXT_PRACTICE
        )
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (!showAdaptiveSummary) {
                adaptiveSessionViewModel.flushAndEnd()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = !showAdaptiveSummary) {
        adaptiveSessionViewModel.flushAndEnd()
        showAdaptiveSummary = true
    }

    if (showAdaptiveSummary) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Practice complete",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                citu.edu.stathis.mobile.features.exercise.ui.components.AdaptiveSessionSummaryCard(
                    summary = adaptiveSessionSummary
                )
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
        return
    }

    citu.edu.stathis.mobile.features.exercise.ui.screens.ExerciseScreen(
        navController = navController,
        exerciseType = exerciseType,
        exerciseTitle = template?.title,
        // Practice still runs posture classify + adaptive logging for research volume.
        enablePostureAnalysis = true,
        enableExerciseTracking = true,
        onExerciseFeedback = { feedback ->
            adaptiveSessionViewModel.onExerciseFeedback(feedback)
        },
        adaptiveHighlight = adaptiveHighlight,
        adaptiveHighlightLandmarks = adaptiveHighlightLandmarks,
        adaptiveHighlightBones = adaptiveHighlightBones,
        adaptiveMessage = adaptiveFeedback?.message,
        adaptiveDeliveryChannel = adaptiveFeedback?.deliveryChannel
    )
}

private fun resolveExerciseType(rawType: String?): ExerciseType? {
    val normalized = rawType?.trim()?.lowercase()?.replace(' ', '_') ?: return null
    return when (normalized) {
        "squat", "squats" -> ExerciseType.SQUAT
        "sit_up", "sit_ups", "situp", "situps", "crunch", "crunches" -> ExerciseType.SIT_UP
        "pushup", "pushups", "push_up", "push_ups", "push-up", "wall_pushup", "wall_pushups" -> ExerciseType.PUSHUP
        "glute_bridge", "glute_bridges" -> ExerciseType.GLUTE_BRIDGE
        "static_lunge", "static_lunges", "lunge", "lunges" -> ExerciseType.STATIC_LUNGE
        "lying_leg_raise", "lying_leg_raises", "leg_raise", "leg_raises" -> ExerciseType.LYING_LEG_RAISE
        else -> null
    }
}
