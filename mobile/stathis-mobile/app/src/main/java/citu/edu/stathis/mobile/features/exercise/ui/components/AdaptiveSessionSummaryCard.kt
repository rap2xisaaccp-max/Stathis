package citu.edu.stathis.mobile.features.exercise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveSessionSummary
import citu.edu.stathis.mobile.features.exercise.adaptive.ExerciseMasteryDto
import citu.edu.stathis.mobile.features.exercise.adaptive.StudentLearningProfileDto

@Composable
fun AdaptiveSessionSummaryCard(
    summary: AdaptiveSessionSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Adaptive coaching summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Coaching cues this session: ${summary.interventionCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary.modalitiesUsed.isNotEmpty()) {
                Text(
                    text = "Channels used: ${
                        summary.modalitiesUsed.joinToString(", ") {
                            it.replace('_', ' ').lowercase()
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.errorCodes.isNotEmpty()) {
                Text(
                    text = "Form focus: ${
                        summary.errorCodes.take(3).joinToString(", ") {
                            it.replace('_', ' ').lowercase()
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.syncPending) {
                Text(
                    text = "Sync pending — coaching logs will upload when you’re back online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else if (summary.interventionCount == 0) {
                Text(
                    text = "No form corrections were needed this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StudentMasterySection(
    profile: StudentLearningProfileDto?,
    mastery: List<ExerciseMasteryDto>,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Adaptive learning",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            loading -> {
                Text(
                    text = "Loading coaching profile…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Overall preferred feedback: ${
                                profile?.preferredModality?.replace('_', ' ') ?: "Insufficient data"
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val total = profile?.totalInterventions ?: 0
                        val success = profile?.totalSuccessfulInterventions ?: 0
                        Text(
                            text = "Successful corrections: $success / $total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        profile?.consistencyScore?.let { score ->
                            Text(
                                text = "Consistency: ${kotlin.math.round(score * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (mastery.isEmpty()) {
                    Text(
                        text = "No exercise mastery yet. Complete a practice or task session to build your profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        mastery.forEach { item ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.exerciseType.replace('_', ' '),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Mastery ${kotlin.math.round(item.masteryLevel * 100).toInt()}% · ${item.sessionsCount ?: 0} sessions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = preferredByExerciseLabel(
                                            profile?.preferredModalityByExercise,
                                            item.exerciseType
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    item.recommendedDifficulty?.let { difficulty ->
                                        Text(
                                            text = "Soft tip: $difficulty · ~${item.recommendedGoalReps ?: 8} reps (teacher approves)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun preferredByExerciseLabel(
    preferredByExercise: Map<String, Any?>?,
    exerciseType: String
): String {
    val row = preferredByExercise?.get(exerciseType) as? Map<*, *>
        ?: preferredByExercise?.entries?.firstOrNull {
            it.key.equals(exerciseType, ignoreCase = true)
        }?.value as? Map<*, *>
    if (row == null) {
        return "Preferred modality: Insufficient data"
    }
    val source = row["source"]?.toString()?.uppercase() ?: "DEFAULT"
    val modality = row["modality"]?.toString()?.replace('_', ' ') ?: "—"
    return when (source) {
        "LEARNED" -> "Preferred modality: $modality"
        "EXPLORING" -> "Preferred modality: Learning ($modality)"
        else -> "Preferred modality: Insufficient data"
    }
}
