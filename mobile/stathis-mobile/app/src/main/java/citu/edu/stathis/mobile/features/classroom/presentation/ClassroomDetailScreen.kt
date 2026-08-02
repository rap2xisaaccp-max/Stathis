package citu.edu.stathis.mobile.features.classroom.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import citu.edu.stathis.mobile.features.tasks.navigation.navigateToTaskList
import androidx.navigation.NavController
import citu.edu.stathis.mobile.features.classroom.presentation.viewmodel.ClassroomViewModel

/**
 * Calculates the classroom progress percentage based on completed tasks.
 * A task counts as completed when the student has any attempt/completion
 * recorded via API progress or local completion caches.
 */
private fun calculateProgressPercentage(
    tasks: List<citu.edu.stathis.mobile.features.tasks.data.model.Task>,
    taskProgressMap: Map<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>? = null
): String {
    if (tasks.isEmpty()) return "0%"

    val activeTasks = tasks.filter { task -> task.isActive ?: true }
    if (activeTasks.isEmpty()) return "0%"

    val completed = activeTasks.count { task -> isTaskCompletedForProgress(task, taskProgressMap) }
    val percentage = ((completed.toFloat() / activeTasks.size) * 100).toInt()
    return "${percentage}%"
}

private fun isTaskCompletedForProgress(
    task: citu.edu.stathis.mobile.features.tasks.data.model.Task,
    taskProgressMap: Map<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>?
): Boolean {
    val progress = taskProgressMap?.get(task.physicalId)
    val hasQuizAttempts = (progress?.quizAttempts ?: 0) > 0 || progress?.quizCompleted == true
    val hasLessonAttempts = progress?.lessonCompleted == true ||
        citu.edu.stathis.mobile.features.tasks.presentation.LessonAttemptsCache.getAttempts(task.physicalId) > 0
    val hasExerciseAttempts = progress?.exerciseCompleted == true ||
        (progress?.exerciseAttempts ?: 0) > 0 ||
        progress?.completedExercises?.isNotEmpty() == true
    return hasQuizAttempts ||
        hasLessonAttempts ||
        hasExerciseAttempts ||
        progress?.isCompleted == true ||
        citu.edu.stathis.mobile.features.tasks.presentation.TaskCompletionCache.isCompleted(task.physicalId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomDetailScreen(
    classroomId: String,
    navController: NavController,
    viewModel: ClassroomViewModel = hiltViewModel()
) {
    val verifiedMapState = viewModel.verifiedMap.collectAsState()
    val classroom by viewModel.selectedClassroom.collectAsState()
    val tasksState by viewModel.tasksState.collectAsState()

    LaunchedEffect(classroomId) {
        viewModel.loadClassroomDetails(classroomId)
        viewModel.loadClassroomTasks(classroomId)
    }
    LaunchedEffect(verifiedMapState.value.isEmpty()) {
        if (verifiedMapState.value.isEmpty()) viewModel.loadStudentClassrooms()
    }

    val verificationStatus = verifiedMapState.value[classroomId]
    
    // Extract tasks from tasksState
    val currentTasksState = tasksState
    val classroomTasks = when (currentTasksState) {
        is citu.edu.stathis.mobile.features.classroom.presentation.viewmodel.TasksState.Success -> currentTasksState.tasks
        else -> emptyList()
    }

    val taskViewModel: citu.edu.stathis.mobile.features.tasks.presentation.TaskViewModel = hiltViewModel()
    var taskProgressMap by remember {
        mutableStateOf<Map<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>>(emptyMap())
    }

    LaunchedEffect(classroomTasks) {
        if (classroomTasks.isEmpty()) {
            taskProgressMap = emptyMap()
            return@LaunchedEffect
        }
        val map = mutableMapOf<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>()
        classroomTasks.forEach { task ->
            runCatching {
                map[task.physicalId] = taskViewModel.getTaskProgress(task.physicalId, suppressError = true)
            }
        }
        taskProgressMap = map
    }

    val completionUpdates by citu.edu.stathis.mobile.features.tasks.presentation.TaskCompletionCache.completionUpdates.collectAsState()
    LaunchedEffect(completionUpdates, classroomTasks) {
        if (completionUpdates > 0 && classroomTasks.isNotEmpty()) {
            val map = mutableMapOf<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>()
            classroomTasks.forEach { task ->
                runCatching {
                    map[task.physicalId] = taskViewModel.getTaskProgress(task.physicalId, suppressError = true)
                }
            }
            taskProgressMap = map
        }
    }
    
    if (verificationStatus == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Loading…") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    } else if (verificationStatus == false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pending Verification") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Your enrollment is pending teacher verification.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("You will gain access once verified.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = classroom?.name ?: "Classroom",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Hero Section
                item {
                    ClassroomHeroSection(
                        classroom = classroom,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Quick Stats
                item {
                    QuickStatsSection(
                        classroom = classroom,
                        classroomTasks = classroomTasks,
                        taskProgressMap = taskProgressMap,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Progress Overview
                item {
                    ProgressOverviewSection(
                        classroom = classroom,
                        classroomTasks = classroomTasks,
                        taskProgressMap = taskProgressMap,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Action Buttons
                item {
                    ActionButtonsSection(
                        classroomId = classroomId,
                        navController = navController,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Recent Activities
                item {
                    RecentActivitiesSection(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Achievements Preview
                item {
                    AchievementsPreviewSection(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Floating Action Button (navigation guarded in LearnScreen and HomeNavHost)
            FloatingActionButton(
                onClick = { navController.navigateToTaskList(classroomId) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Exercising")
            }
        }
    }
    }
}

@Composable
private fun ClassroomHeroSection(
    classroom: citu.edu.stathis.mobile.features.classroom.data.model.Classroom?,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = tween(1000),
        label = "heroScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Classroom Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = "Classroom",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Classroom Name
            Text(
                text = classroom?.name ?: "Loading...",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = classroom?.description ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Teacher Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Teacher",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Taught by ${classroom?.teacherName ?: "Unknown"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickStatsSection(
    classroom: citu.edu.stathis.mobile.features.classroom.data.model.Classroom?,
    classroomTasks: List<citu.edu.stathis.mobile.features.tasks.data.model.Task>,
    taskProgressMap: Map<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        item {
            StatCard(
                title = "Students",
                value = "${classroom?.studentCount ?: 0}",
                icon = Icons.Default.Group,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            StatCard(
                title = "Tasks",
                value = "${classroomTasks.size}",
                icon = Icons.Default.Assignment,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        item {
            StatCard(
                title = "Progress",
                value = calculateProgressPercentage(classroomTasks, taskProgressMap),
                icon = Icons.Default.TrendingUp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .heightIn(min = 100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 0.9
            )
        }
    }
}

@Composable
private fun ProgressOverviewSection(
    classroom: citu.edu.stathis.mobile.features.classroom.data.model.Classroom?,
    classroomTasks: List<citu.edu.stathis.mobile.features.tasks.data.model.Task>,
    taskProgressMap: Map<String, citu.edu.stathis.mobile.features.tasks.data.model.TaskProgressResponse?>,
    modifier: Modifier = Modifier
) {
    // Filter out deactivated tasks for student-centric progress calculation
    val activeTasks = classroomTasks.filter { task ->
        val active = task.isActive ?: true
        active
    }
    val activeTotalTasks = activeTasks.size
    
    val completedTasks = activeTasks.count { task ->
        isTaskCompletedForProgress(task, taskProgressMap)
    }
    val remainingTasks = (activeTotalTasks - completedTasks).coerceAtLeast(0)
    val progressPercentage = if (activeTotalTasks > 0) completedTasks.toFloat() / activeTotalTasks else 0f
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(progressPercentage * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progressPercentage },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ProgressItem(
                    label = "Completed",
                    value = "$completedTasks",
                    color = MaterialTheme.colorScheme.primary
                )
                ProgressItem(
                    label = "Remaining",
                    value = "$remainingTasks",
                    color = MaterialTheme.colorScheme.secondary
                )
                ProgressItem(
                    label = "Total",
                    value = "$activeTotalTasks",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun ProgressItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButtonsSection(
    classroomId: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Removed redundant primary button per spec

        // Secondary Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { navController.navigateToTaskList(classroomId) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("View Tasks")
                }
            }
            OutlinedButton(
                onClick = { /* TODO: Implement achievements */ },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Achievements")
                }
            }
        }
    }
}

@Composable
private fun RecentActivitiesSection(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Recent Activities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sample activities
            val activities = listOf(
                "Completed 'Basic Exercises' task",
                "Earned 'First Steps' achievement",
                "Started 'Advanced Techniques' lesson"
            )

            activities.forEach { activity ->
                ActivityItem(activity = activity)
                if (activity != activities.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    activity: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
        Text(
            text = activity,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AchievementsPreviewSection(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { /* TODO: Navigate to achievements */ }) {
                    Text("View All")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(3) { index ->
                    AchievementCard(
                        title = "Achievement ${index + 1}",
                        isUnlocked = index < 2
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    title: String,
    isUnlocked: Boolean
) {
    Card(
        modifier = Modifier
            .width(90.dp)
            .height(90.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = "Achievement",
                tint = if (isUnlocked) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUnlocked) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}