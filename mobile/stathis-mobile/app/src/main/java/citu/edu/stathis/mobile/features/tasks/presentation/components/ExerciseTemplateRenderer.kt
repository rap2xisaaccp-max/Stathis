package citu.edu.stathis.mobile.features.tasks.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import citu.edu.stathis.mobile.features.tasks.data.model.ExerciseTemplate
import citu.edu.stathis.mobile.features.tasks.data.model.ExercisePerformance
import citu.edu.stathis.mobile.features.exercise.ui.screens.ExerciseScreen
import citu.edu.stathis.mobile.features.vitals.ui.HealthConnectViewModel
import citu.edu.stathis.mobile.features.vitals.data.service.ExerciseVitalsMonitoringService
import citu.edu.stathis.mobile.features.vitals.data.repository.VitalsPostingState
import citu.edu.stathis.mobile.features.vitals.data.model.VitalSigns
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import citu.edu.stathis.mobile.core.data.AuthTokenManager
import kotlinx.coroutines.flow.firstOrNull
import citu.edu.stathis.mobile.features.exercise.data.ExerciseDetector
import citu.edu.stathis.mobile.features.exercise.data.ExerciseResult
import citu.edu.stathis.mobile.features.exercise.data.ExerciseType
import citu.edu.stathis.mobile.features.exercise.data.OnDeviceFeedback
import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseState
import citu.edu.stathis.mobile.features.tasks.presentation.TaskViewModel
import citu.edu.stathis.mobile.features.tasks.presentation.ExerciseSyncViewModel
import citu.edu.stathis.mobile.features.exercise.domain.ExerciseCalorieCalculator
import citu.edu.stathis.mobile.features.profile.ui.BodyMetricsGateViewModel
import com.google.mlkit.vision.pose.Pose
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState

private enum class IdentityPhase {
    /** Waiting for user to tap Verify */
    UNVERIFIED,
    /** FaceNet biometric scan in progress (once per session) */
    VERIFYING,
    /** Identity confirmed — Start/Stop/Complete unlocked */
    VERIFIED
}

private fun parseClassroomAndTaskId(encoded: String?): Pair<String?, String?> {
    if (encoded.isNullOrBlank()) return null to null
    val parts = encoded.split('|', limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else encoded to null
}

private fun buildExercisePerformance(
    template: ExerciseTemplate,
    classroomIdEncoded: String?,
    actualReps: Int,
    actualAccuracy: Float,
    actualTime: Int,
    weightKg: Double? = null
): ExercisePerformance {
    val (classroomId, taskId) = parseClassroomAndTaskId(classroomIdEncoded)
    val calories = ExerciseCalorieCalculator.calculate(template.exerciseType, actualReps, weightKg)
    return ExercisePerformance(
        taskId = taskId.orEmpty(),
        templateId = template.physicalId,
        actualReps = actualReps,
        actualAccuracy = actualAccuracy,
        actualTime = actualTime,
        goalReps = template.goalReps,
        goalAccuracy = template.goalAccuracy,
        goalTime = template.goalTime,
        isCompleted = actualReps >= template.goalReps && actualAccuracy >= template.goalAccuracy,
        score = calculateScore(actualReps, actualAccuracy, actualTime, template),
        caloriesBurned = calories,
        exerciseType = template.exerciseType,
        classroomId = classroomId
    )
}

@Composable
fun ExerciseTemplateRenderer(
    template: ExerciseTemplate,
    classroomId: String? = null,
    navController: NavHostController? = null,
    returnRouteAfterMetrics: String? = null,
    maxAttempts: Int = 0,
    attemptsUsed: Int = 0,
    onSessionFinished: (ExercisePerformance) -> Unit,
    onFinishSession: () -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isExerciseStarted by remember { mutableStateOf(false) }
    var isExerciseCompleted by remember { mutableStateOf(false) }
    var exercisePerformance by remember { mutableStateOf<ExercisePerformance?>(null) }
    var latestExerciseFeedback by remember { mutableStateOf<OnDeviceFeedback?>(null) }
    var isCheckingBodyMetrics by remember { mutableStateOf(false) }
    var bodyMetricsError by remember { mutableStateOf<String?>(null) }
    var displayedAttempts by remember { mutableIntStateOf(attemptsUsed) }
    var identityPhase by remember { mutableStateOf(IdentityPhase.UNVERIFIED) }
    var identityMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ensureBodyMetrics = hiltViewModel<BodyMetricsGateViewModel>()
    val faceIdentityViewModel: citu.edu.stathis.mobile.features.exercise.ui.viewmodel.FaceIdentityViewModel =
        hiltViewModel()
    val faceIdentityState by faceIdentityViewModel.state.collectAsState()

    LaunchedEffect(isExerciseStarted) {
        if (!isExerciseStarted) {
            identityPhase = IdentityPhase.UNVERIFIED
            identityMessage = null
            faceIdentityViewModel.clearSessionVerification()
        }
    }

    LaunchedEffect(attemptsUsed) {
        if (attemptsUsed > displayedAttempts) {
            displayedAttempts = attemptsUsed
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (!isExerciseStarted) {
            if (maxAttempts > 0 && attemptsUsed >= maxAttempts) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Maximum attempts reached",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You have used all $maxAttempts attempts for this exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onFinishSession,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Complete")
                    }
                }
            } else {
            // Show header and instructions when exercise hasn't started
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ExerciseHeader(
                    template = template,
                    isStarted = isExerciseStarted,
                    isCompleted = isExerciseCompleted,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ExerciseInstructions(
                        template = template,
                        isCheckingBodyMetrics = isCheckingBodyMetrics,
                        bodyMetricsError = bodyMetricsError,
                        onStart = {
                            scope.launch {
                                isCheckingBodyMetrics = true
                                bodyMetricsError = null
                                val complete = ensureBodyMetrics.ensureComplete()
                                isCheckingBodyMetrics = false
                                if (complete) {
                                    isExerciseStarted = true
                                    identityPhase = IdentityPhase.UNVERIFIED
                                    faceIdentityViewModel.clearSessionVerification()
                                } else {
                                    val destination = if (!returnRouteAfterMetrics.isNullOrBlank()) {
                                        "body_metrics_setup?returnRoute=${Uri.encode(returnRouteAfterMetrics)}"
                                    } else {
                                        "body_metrics_setup?returnRoute="
                                    }
                                    if (navController != null) {
                                        navController.navigate(destination)
                                    } else {
                                        bodyMetricsError = "Please complete your height, weight, age, and face registration in Profile before starting."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            }
        } else if (!isExerciseCompleted) {
            val isVerified = identityPhase == IdentityPhase.VERIFIED

            // Fullscreen exercise mode
            ExerciseScreen(
                navController = rememberNavController(),
                enableVitalsIndicator = false,
                enablePostureAnalysis = isVerified,
                exerciseType = resolveExerciseType(template.exerciseType),
                exerciseTitle = template.title,
                showExerciseFeedbackOverlay = false,
                onExerciseFeedback = { feedback -> latestExerciseFeedback = feedback },
                enableExerciseTracking = isVerified,
                verifyFace = identityPhase == IdentityPhase.VERIFYING,
                enrolledFaceEmbedding = faceIdentityState.enrolledEmbedding,
                onFaceVerified = {
                    identityPhase = IdentityPhase.VERIFIED
                    identityMessage = "You are now ready to start."
                },
                monitorSkeletonPresence = false,
                onSkeletonLeftFrame = null
            )

            ExerciseControlsOverlay(
                template = template,
                classroomId = classroomId,
                liveExerciseFeedback = latestExerciseFeedback,
                identityPhase = identityPhase,
                identityMessage = when (identityPhase) {
                    IdentityPhase.VERIFYING -> faceIdentityState.statusText.ifBlank { identityMessage }
                    IdentityPhase.VERIFIED -> identityMessage ?: "You are now ready to start."
                    IdentityPhase.UNVERIFIED -> identityMessage
                },
                onRequestVerify = {
                    if (faceIdentityState.faceRegistered && faceIdentityState.enrolledEmbedding != null) {
                        identityPhase = IdentityPhase.VERIFYING
                        identityMessage = "Look at the camera to verify your identity."
                        faceIdentityViewModel.resetVerification()
                    } else {
                        identityMessage = "Register your face in Profile before verifying."
                    }
                },
                onTrackingActive = isVerified,
                onComplete = { performance ->
                    exercisePerformance = performance
                    isExerciseCompleted = true
                    displayedAttempts = maxOf(displayedAttempts + 1, attemptsUsed + 1)
                    onSessionFinished(performance)
                },
                onCancel = {
                    isExerciseStarted = false
                    isExerciseCompleted = false
                    exercisePerformance = null
                    identityPhase = IdentityPhase.UNVERIFIED
                    faceIdentityViewModel.clearSessionVerification()
                    onCancel?.invoke()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Show results
            exercisePerformance?.let { performance ->
                ExerciseResults(
                    template = template,
                    performance = performance,
                    attemptsUsed = displayedAttempts,
                    maxAttempts = maxAttempts,
                    onRetry = {
                        isExerciseStarted = false
                        isExerciseCompleted = false
                        exercisePerformance = null
                    },
                    onComplete = onFinishSession,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ExerciseHeader(
    template: ExerciseTemplate,
    isStarted: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title
            Text(
                text = template.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Exercise Type and Difficulty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = template.exerciseType.replace("_", " "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = template.exerciseDifficulty,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseInstructions(
    template: ExerciseTemplate,
    onStart: () -> Unit,
    isCheckingBodyMetrics: Boolean = false,
    bodyMetricsError: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Exercise Icon
            Icon(
                imageVector = when (template.exerciseType) {
                    "PUSH_UP" -> Icons.Default.FitnessCenter
                    "SQUATS" -> Icons.Default.DirectionsRun
                    else -> Icons.Default.Sports
                },
                contentDescription = "Exercise",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions Title
            Text(
                text = "Exercise Instructions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Goals
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Your Goals:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    GoalItem(
                        icon = Icons.Default.Repeat,
                        label = "Repetitions",
                        value = "${template.goalReps} reps"
                    )

                    GoalItem(
                        icon = Icons.Default.Timer,
                        label = "Time",
                        value = "${template.goalTime} seconds"
                    )

                    GoalItem(
                        icon = Icons.Default.GpsFixed,
                        label = "Accuracy",
                        value = "${template.goalAccuracy}%"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (bodyMetricsError != null) {
                Text(
                    text = bodyMetricsError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Start Button
            Button(
                onClick = onStart,
                enabled = !isCheckingBodyMetrics,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
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
                    Text(
                        text = "Checking profile...",
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Exercise",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExerciseInProgress(
    template: ExerciseTemplate,
    onComplete: (ExercisePerformance) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentReps by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableIntStateOf(0) }
    var currentAccuracy by remember { mutableFloatStateOf(0f) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Simulate exercise progress (in real app, this would come from pose detection)
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (currentTime < template.goalTime && currentReps < template.goalReps) {
                kotlinx.coroutines.delay(1000)
                currentTime++
                if (currentTime % 3 == 0) { // Simulate rep every 3 seconds
                    currentReps++
                    currentAccuracy = (70f + (currentReps * 2f)).coerceAtMost(95f) // Simulate improving accuracy
                }
            }

            // Exercise completed
            val performance = buildExercisePerformance(
                template = template,
                classroomIdEncoded = null,
                actualReps = currentReps,
                actualAccuracy = currentAccuracy,
                actualTime = currentTime
            )
            onComplete(performance)
        }
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Exercise Status
            Text(
                text = "Exercise in Progress",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProgressIndicator(
                    label = "Reps",
                    current = currentReps,
                    goal = template.goalReps,
                    icon = Icons.Default.Repeat
                )

                ProgressIndicator(
                    label = "Time",
                    current = currentTime,
                    goal = template.goalTime,
                    icon = Icons.Default.Timer
                )

                ProgressIndicator(
                    label = "Accuracy",
                    current = currentAccuracy.toInt(),
                    goal = template.goalAccuracy,
                    icon = Icons.Default.GpsFixed
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start/Stop Button
            Button(
                onClick = { isTimerRunning = !isTimerRunning },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTimerRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = if (isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isTimerRunning) "Stop" else "Start",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTimerRunning) "Stop Exercise" else "Start Exercise",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    label: String,
    current: Int,
    goal: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceAtMost(1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$current/$goal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .width(60.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun CompactProgressIndicator(
    label: String,
    current: Int,
    goal: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceAtMost(1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$current/$goal",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(2.dp))

        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun MinimalProgressIndicator(
    label: String,
    current: Int,
    goal: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    animateProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceAtMost(1f) else 0f
    val animatedProgress = if (animateProgress) {
        animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(300),
            label = "progress"
        ).value
    } else {
        progress
    }
    
    Row(
        modifier = modifier.width(76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "$current/$goal",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun ExerciseControlsOverlay(
    template: ExerciseTemplate,
    classroomId: String?,
    liveExerciseFeedback: OnDeviceFeedback?,
    identityPhase: IdentityPhase,
    identityMessage: String?,
    onRequestVerify: () -> Unit,
    onTrackingActive: Boolean,
    onComplete: (ExercisePerformance) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    exerciseSyncViewModel: ExerciseSyncViewModel = hiltViewModel()
) {
    var currentReps by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableIntStateOf(0) }
    var currentAccuracy by remember { mutableFloatStateOf(0f) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var shouldRequestPermissions by remember { mutableStateOf(false) }
    var shouldStopWithPostActivity by remember { mutableStateOf(false) }
    val weightKg by exerciseSyncViewModel.weightKg.collectAsState()
    val (parsedClassroomId, parsedTaskId) = remember(classroomId) { parseClassroomAndTaskId(classroomId) }

    // Pose detection state
    val exerciseDetector = remember { ExerciseDetector() }
    var exerciseState by remember { mutableStateOf(ExerciseState.WAITING) }
    var exerciseConfidence by remember { mutableFloatStateOf(0f) }
    var exerciseFeedback by remember { mutableStateOf<List<String>>(emptyList()) }
    var latestPose by remember { mutableStateOf<Pose?>(null) }

    LaunchedEffect(liveExerciseFeedback) {
        liveExerciseFeedback?.let { feedback ->
            if (onTrackingActive && isTimerRunning) {
                currentReps = feedback.repCount
                exerciseState = feedback.exerciseState
                exerciseConfidence = feedback.confidence
                exerciseFeedback = feedback.formIssues
                currentAccuracy = (feedback.confidence * 100f).coerceAtMost(100f)
            }
        }
    }

    LaunchedEffect(identityPhase) {
        if (identityPhase != IdentityPhase.VERIFIED) {
            isTimerRunning = false
        }
    }

    // Publish live progress to teacher dashboard every 2s while running
    LaunchedEffect(isTimerRunning, currentReps, currentTime, currentAccuracy) {
        if (!isTimerRunning) return@LaunchedEffect
        kotlinx.coroutines.delay(2000)
        exerciseSyncViewModel.publishProgress(
            classroomId = parsedClassroomId,
            taskId = parsedTaskId,
            exerciseTemplateId = template.physicalId,
            exerciseType = template.exerciseType,
            reps = currentReps,
            goalReps = template.goalReps,
            accuracy = currentAccuracy.toDouble(),
            timeTakenSeconds = currentTime,
            completed = false
        )
    }

    // Function to handle pose detection
    val handlePoseDetection = remember(template.exerciseType) { { pose: Pose ->
        latestPose = pose
        val result: ExerciseResult = when (resolveExerciseType(template.exerciseType)) {
            ExerciseType.SQUAT -> exerciseDetector.analyzeSquat(pose)
            ExerciseType.PUSHUP -> exerciseDetector.analyzePushup(pose)
            ExerciseType.SIT_UP -> exerciseDetector.analyzeSitup(pose)
            ExerciseType.GLUTE_BRIDGE -> exerciseDetector.analyzeGluteBridge(pose)
            ExerciseType.STATIC_LUNGE -> exerciseDetector.analyzeStaticLunge(pose)
            ExerciseType.LYING_LEG_RAISE -> exerciseDetector.analyzeLyingLegRaise(pose)
            else -> ExerciseResult(ExerciseState.WAITING, emptyList(), false, 0f, currentReps)
        }

        currentReps = result.repCount
        exerciseState = result.state
        exerciseConfidence = result.confidence ?: 0f
        exerciseFeedback = result.feedback

        // Update accuracy based on confidence
        currentAccuracy = (exerciseConfidence * 100f).coerceAtMost(100f)
    } }

    val healthConnectViewModel: HealthConnectViewModel = hiltViewModel()
    val connectionState by healthConnectViewModel.connectionState.collectAsState()
    val vitalSigns by healthConnectViewModel.vitalSigns.collectAsState()

    // Get the exercise vitals monitoring service from the HealthConnectViewModel
    // Since ExerciseVitalsMonitoringService is not a ViewModel, we'll access it through the HealthConnectViewModel
    val exerciseVitalsMonitoringService = healthConnectViewModel.exerciseVitalsMonitoringService
    val isVitalsMonitoring by exerciseVitalsMonitoringService.isMonitoring.collectAsState()
    val vitalsPostingState by exerciseVitalsMonitoringService.postingState.collectAsState()

    // Initialize HealthConnect when the overlay is first created
    LaunchedEffect(Unit) {
        healthConnectViewModel.connect()
        healthConnectViewModel.startMonitoring()
    }

    // Get current user context through TaskViewModel
    val taskViewModel: TaskViewModel = hiltViewModel()
    val currentPhysicalId by taskViewModel.authTokenManager.physicalIdFlow.collectAsState(initial = null)

    // Remember the classroom ID for use in LaunchedEffect
    val rememberedClassroomId = remember(classroomId) { classroomId }

    // Health Connect permission launcher
    val requiredPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult<Set<String>, Set<String>>(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(requiredPermissions)) {
            healthConnectViewModel.onPermissionsGranted()
            healthConnectViewModel.startMonitoring()
        } else {
            // Some permissions denied, still allow exercise but without Health Connect
            healthConnectViewModel.disconnect()
        }
    }

    // Handle permission request when needed
    LaunchedEffect(shouldRequestPermissions) {
        if (shouldRequestPermissions) {
            kotlinx.coroutines.delay(1000)
            permissionLauncher.launch(requiredPermissions)
            shouldRequestPermissions = false
        }
    }

    // Handle stopping with post-activity vitals
    LaunchedEffect(shouldStopWithPostActivity) {
        if (shouldStopWithPostActivity) {
            val fallbackVitals = VitalSigns(
                userId = currentPhysicalId ?: "unknown_user",
                systolicBP = 120,
                diastolicBP = 80,
                heartRate = 70,
                respirationRate = 16,
                temperature = 36.5f,
                oxygenSaturation = 98.0f
            )
            exerciseVitalsMonitoringService.stopMonitoringWithPostActivity(vitalSigns ?: fallbackVitals)
            shouldStopWithPostActivity = false
        }
    }

    LaunchedEffect(isTimerRunning, rememberedClassroomId) {
        if (isTimerRunning) {
            // Start vitals monitoring when exercise starts
            if (connectionState == citu.edu.stathis.mobile.features.vitals.data.HealthConnectManager.ConnectionState.CONNECTED) {
                // Decode classroomId to extract both classroom and task physical IDs
                val (actualClassroomId, taskPhysicalId) = if (rememberedClassroomId?.contains("|") == true) {
                    val parts = rememberedClassroomId.split("|")
                    Pair(parts[0], parts.getOrNull(1))
                } else {
                    Pair(rememberedClassroomId, null)
                }

                exerciseVitalsMonitoringService.startMonitoring(
                    classroomId = actualClassroomId ?: "unknown_classroom", // Use decoded classroom ID
                    taskId = taskPhysicalId ?: template.physicalId, // Use decoded task physical ID (TASK-UUID), fallback to template
                    physicalId = currentPhysicalId ?: "unknown_user", // Use actual user physical ID
                    studentId = currentPhysicalId ?: "unknown_user", // Use actual user physical ID as student ID
                    vitalsProvider = { vitalSigns },
                    scope = this
                )
            }

            // Reset exercise detector when starting
            exerciseDetector.resetExercise()
            currentReps = 0
            currentTime = 0

            // Timer for exercise duration
            while (currentTime < template.goalTime && currentReps < template.goalReps) {
                kotlinx.coroutines.delay(1000)
                currentTime++
            }

            // Stop vitals monitoring with post-activity vitals when exercise completes
            val fallbackVitals = VitalSigns(
                userId = currentPhysicalId ?: "unknown_user",
                systolicBP = 120,
                diastolicBP = 80,
                heartRate = 70,
                respirationRate = 16,
                temperature = 36.5f,
                oxygenSaturation = 98.0f
            )
            exerciseVitalsMonitoringService.stopMonitoringWithPostActivity(vitalSigns ?: fallbackVitals)

            // Exercise completed
            val performance = buildExercisePerformance(
                template = template,
                classroomIdEncoded = classroomId,
                actualReps = currentReps,
                actualAccuracy = currentAccuracy,
                actualTime = currentTime,
                weightKg = weightKg
            )
            exerciseSyncViewModel.publishProgress(
                classroomId = parsedClassroomId,
                taskId = parsedTaskId,
                exerciseTemplateId = template.physicalId,
                exerciseType = template.exerciseType,
                reps = currentReps,
                goalReps = template.goalReps,
                accuracy = currentAccuracy.toDouble(),
                timeTakenSeconds = currentTime,
                completed = true
            )
            onComplete(performance)
        } else {
            // Stop vitals monitoring when timer stops
            exerciseVitalsMonitoringService.stopMonitoring()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (identityPhase == IdentityPhase.VERIFIED) {
        // Minimal progress overlay at the very top edge
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 8.dp, end = 8.dp, top = 88.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exercise type and difficulty
                Text(
                    text = "${template.exerciseType.replace("_", " ")} - ${template.exerciseDifficulty}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Progress indicators in a single row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinimalProgressIndicator(
                        label = "R",
                        current = currentReps,
                        goal = template.goalReps,
                        icon = Icons.Default.Repeat
                    )

                    MinimalProgressIndicator(
                        label = "T",
                        current = currentTime,
                        goal = template.goalTime,
                        icon = Icons.Default.Timer
                    )

                    MinimalProgressIndicator(
                        label = "A",
                        current = currentAccuracy.toInt(),
                        goal = template.goalAccuracy,
                        icon = Icons.Default.GpsFixed,
                        animateProgress = false
                    )
                }

                // Pose status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when (exerciseState) {
                            ExerciseState.WAITING -> Icons.Default.Person
                            ExerciseState.UP -> Icons.Default.TrendingUp
                            ExerciseState.DOWN -> Icons.Default.TrendingDown
                            ExerciseState.INVALID -> Icons.Default.Warning
                        },
                        contentDescription = "Pose Detection",
                        tint = when (exerciseState) {
                            ExerciseState.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
                            ExerciseState.UP -> MaterialTheme.colorScheme.primary
                            ExerciseState.DOWN -> MaterialTheme.colorScheme.secondary
                            ExerciseState.INVALID -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${(exerciseConfidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // Prominent health monitoring indicator in top-right corner
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 104.dp, end = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart Rate
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart rate",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = vitalSigns?.heartRate?.let { "$it" } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "BPM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.sp
                        )
                    }
                }

                // Divider
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(width = 1.dp, height = 20.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )

                // Oxygen Saturation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Oxygen saturation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = vitalSigns?.oxygenSaturation?.let { "${it.toInt()}" } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "SpO2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
        }

        // Identity / verification status banner
        if (
            identityPhase == IdentityPhase.VERIFYING ||
            (identityPhase == IdentityPhase.VERIFIED && !isTimerRunning) ||
            (identityPhase == IdentityPhase.UNVERIFIED && !identityMessage.isNullOrBlank())
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (identityPhase) {
                            IdentityPhase.VERIFIED -> "You are now ready to start."
                            IdentityPhase.VERIFYING -> "Biometric facial recognition"
                            IdentityPhase.UNVERIFIED -> "Identity verification required"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    if (!identityMessage.isNullOrBlank() && identityPhase != IdentityPhase.VERIFIED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = identityMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (identityPhase == IdentityPhase.VERIFYING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Only the registered student can pass this check.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (identityPhase == IdentityPhase.VERIFIED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap Start when you are ready to begin the exercise.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Controls: Verify first; after success → Cancel / Start|Stop / Complete
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        shouldStopWithPostActivity = true
                        onCancel?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Cancel", style = MaterialTheme.typography.bodyMedium)
                }

                when (identityPhase) {
                    IdentityPhase.UNVERIFIED, IdentityPhase.VERIFYING -> {
                        Button(
                            onClick = {
                                if (identityPhase == IdentityPhase.UNVERIFIED) {
                                    onRequestVerify()
                                }
                            },
                            enabled = identityPhase == IdentityPhase.UNVERIFIED,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                        ) {
                            if (identityPhase == IdentityPhase.VERIFYING) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Verifying", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Verify",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Verify", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    IdentityPhase.VERIFIED -> {
                        Button(
                            onClick = {
                                if (!isTimerRunning) {
                                    when (connectionState) {
                                        citu.edu.stathis.mobile.features.vitals.data.HealthConnectManager.ConnectionState.DISCONNECTED -> {
                                            healthConnectViewModel.connect()
                                            shouldRequestPermissions = true
                                        }
                                        citu.edu.stathis.mobile.features.vitals.data.HealthConnectManager.ConnectionState.CONNECTED -> {
                                            healthConnectViewModel.startMonitoring()
                                        }
                                        else -> healthConnectViewModel.connect()
                                    }
                                }
                                isTimerRunning = !isTimerRunning
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTimerRunning) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isTimerRunning) "Stop" else "Start",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTimerRunning) "Stop" else "Start",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                // Finish attempt Button
                Button(
                    onClick = {
                        // Stop vitals monitoring with post-activity vitals
                        shouldStopWithPostActivity = true
                        // End this attempt and show performance results
                        val performance = buildExercisePerformance(
                            template = template,
                            classroomIdEncoded = classroomId,
                            actualReps = currentReps,
                            actualAccuracy = currentAccuracy,
                            actualTime = currentTime,
                            weightKg = weightKg
                        )
                        exerciseSyncViewModel.publishProgress(
                            classroomId = parsedClassroomId,
                            taskId = parsedTaskId,
                            exerciseTemplateId = template.physicalId,
                            exerciseType = template.exerciseType,
                            reps = currentReps,
                            goalReps = template.goalReps,
                            accuracy = currentAccuracy.toDouble(),
                            timeTakenSeconds = currentTime,
                            completed = true
                        )
                        onComplete(performance)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Finish",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Finish",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun resolveExerciseType(rawType: String): ExerciseType? {
    val normalized = rawType.trim().lowercase().replace(' ', '_')
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

@Composable
private fun ExerciseResults(
    template: ExerciseTemplate,
    performance: ExercisePerformance,
    attemptsUsed: Int,
    maxAttempts: Int,
    onRetry: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canRetry = maxAttempts <= 0 || attemptsUsed < maxAttempts
    val attemptsLabel = if (maxAttempts <= 0) {
        "Attempt $attemptsUsed"
    } else {
        "Attempts: $attemptsUsed / $maxAttempts"
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Result Icon
            Icon(
                imageVector = if (performance.isCompleted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = "Exercise Result",
                modifier = Modifier.size(64.dp),
                tint = if (performance.isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Result Title
            Text(
                text = if (performance.isCompleted) "Exercise Completed!" else "Exercise Finished",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = attemptsLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Score
            Text(
                text = "Score: ${performance.score}/100",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Reps ${performance.actualReps}/${performance.goalReps} · ${(if (performance.goalReps > 0) (performance.actualReps * 100 / performance.goalReps).coerceAtMost(100) else 0)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Calories burned: ${"%.1f".format(performance.caloriesBurned)} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Performance Details
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Your Performance:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PerformanceItem(
                        label = "Repetitions",
                        actual = performance.actualReps,
                        goal = performance.goalReps,
                        isGood = performance.actualReps >= performance.goalReps
                    )

                    PerformanceItem(
                        label = "Accuracy",
                        actual = performance.actualAccuracy.toInt(),
                        goal = performance.goalAccuracy,
                        isGood = performance.actualAccuracy >= performance.goalAccuracy,
                        suffix = "%"
                    )

                    PerformanceItem(
                        label = "Time",
                        actual = performance.actualTime,
                        goal = performance.goalTime,
                        isGood = performance.actualTime <= performance.goalTime,
                        suffix = "s"
                    )

                    Text(
                        text = "Calories: ${"%.1f".format(performance.caloriesBurned)} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!canRetry) {
                Text(
                    text = "Maximum attempts reached. Tap Complete to finish.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Try Again
            Button(
                onClick = onRetry,
                enabled = canRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Complete — always available to exit regardless of remaining attempts
            OutlinedButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Complete",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete")
            }
        }
    }
}

@Composable
private fun PerformanceItem(
    label: String,
    actual: Int,
    goal: Int,
    isGood: Boolean,
    suffix: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$actual$suffix",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isGood) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Text(
                text = " / $goal$suffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isGood) Icons.Default.Check else Icons.Default.Close,
                contentDescription = if (isGood) "Goal achieved" else "Goal not achieved",
                modifier = Modifier.size(16.dp),
                tint = if (isGood) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

private fun calculateScore(
    actualReps: Int,
    actualAccuracy: Float,
    actualTime: Int,
    template: ExerciseTemplate
): Int {
    if (template.goalReps <= 0) return 0
    // Score = completed reps / target reps × 100 (capped at 100)
    return ((actualReps.toFloat() / template.goalReps) * 100f)
        .coerceIn(0f, 100f)
        .toInt()
}