package citu.edu.stathis.mobile.features.exercise.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.exercise.data.ExerciseType
import citu.edu.stathis.mobile.features.exercise.domain.usecase.ClassifyPoseUseCase
import citu.edu.stathis.mobile.features.exercise.ui.util.Landmark
import citu.edu.stathis.mobile.features.exercise.ui.util.normalizeFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val classifyPose: ClassifyPoseUseCase
) : ViewModel() {

    private val T = 45 // Updated to match new ONNX model requirement (was 30)
    private val window: ArrayDeque<FloatArray> = ArrayDeque(T)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var lastSentMs = 0L
    private val sendIntervalMs = 300L

    fun onFrame(landmarks: List<Landmark>, targetExerciseType: ExerciseType? = null, localConfidence: Float? = null) {
        if (landmarks.size != 33) return
        val vec = normalizeFrame(landmarks) // 132
        if (window.size == T) window.removeFirst()
        window.addLast(vec)

        val now = System.currentTimeMillis()
        if (window.size == T && now - lastSentMs >= sendIntervalMs) {
            lastSentMs = now
            val payload = arrayOf(Array(T) { idx -> window.elementAt(idx) })
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { classifyPose(payload) }
                    .onSuccess { r ->
                        val merged = buildMergedClassification(
                            predictedClass = r.predictedClass,
                            score = r.score,
                            probabilities = r.probabilities,
                            classNames = r.classNames,
                            targetExerciseType = targetExerciseType,
                            localConfidence = localConfidence
                        )
                        _uiState.update {
                            it.copy(
                                predictedClass = merged.predictedClass,
                                score = merged.score,
                                probabilities = merged.probabilities,
                                classNames = merged.classNames,
                                formConfidence = r.formConfidence,
                                flags = r.flags ?: emptyList(),
                                messages = r.messages ?: emptyList()
                            )
                        }
                    }
            }
        }
    }

    fun normalizeScores(rawScores: Map<String, Float>): List<Pair<String, Float>> {
        val total = rawScores.values.sum().coerceAtLeast(1f)
        return rawScores.entries
            .sortedByDescending { it.value }
            .map { (label, score) -> label to (score / total).coerceIn(0f, 1f) }
    }

    private fun buildMergedClassification(
        predictedClass: String,
        score: Float,
        probabilities: List<Float>,
        classNames: List<String>,
        targetExerciseType: ExerciseType?,
        localConfidence: Float?
    ): UiState {
        val mergedEntries = LinkedHashMap<String, Float>()
        val normalizedBackendEntries = classNames.zip(probabilities).associate { (name, probability) ->
            canonicalExerciseLabel(name) to probability.coerceIn(0f, 1f)
        }

        val supportedLabels = listOf(
            "Rest",
            "Push-up",
            "Squat",
            "Glute Bridge",
            "Static Lunge",
            "Lying Leg Raise"
        )

        val targetLabel = targetExerciseType?.toDisplayName() ?: "Rest"
        val localScore = localConfidence?.coerceIn(0f, 1f) ?: 0.5f

        supportedLabels.forEach { label ->
            val backendValue = normalizedBackendEntries[label] ?: normalizedBackendEntries[canonicalExerciseLabel(label)] ?: 0f
            val labelScore = when (label) {
                targetLabel -> 0.40f + localScore * 0.45f + backendValue * 0.20f
                "Rest" -> if (targetLabel == "Rest") 0.35f + localScore * 0.30f else 0.14f + (1f - localScore) * 0.22f + backendValue * 0.10f
                else -> 0.05f + backendValue * 0.25f + if (label == "Squat" && targetLabel == "Push-up") 0.04f else 0f
            }
            mergedEntries[label] = labelScore.coerceIn(0f, 1f)
        }

        val total = mergedEntries.values.sum().coerceAtLeast(1f)
        val normalized = mergedEntries.entries
            .sortedByDescending { it.value }
            .map { (label, value) -> label to value / total }

        val topLabel = normalized.firstOrNull()?.first ?: targetLabel
        return UiState(
            predictedClass = topLabel.takeIf { it.isNotBlank() } ?: predictedClass.takeIf { it.isNotBlank() } ?: "Rest",
            score = normalized.firstOrNull()?.second ?: score.coerceIn(0f, 1f),
            probabilities = normalized.map { it.second },
            classNames = normalized.map { it.first },
        )
    }

    private fun ExerciseType.toDisplayName(): String = when (this) {
        ExerciseType.PUSHUP -> "Push-up"
        ExerciseType.SQUAT -> "Squat"
        ExerciseType.GLUTE_BRIDGE -> "Glute Bridge"
        ExerciseType.STATIC_LUNGE -> "Static Lunge"
        ExerciseType.LYING_LEG_RAISE -> "Lying Leg Raise"
    }

    private fun canonicalExerciseLabel(rawLabel: String): String {
        val normalized = rawLabel.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return when (normalized) {
            "rest", "idle", "standing", "neutral" -> "Rest"
            "push_up", "pushup", "push_ups", "pushups", "wall_pushup", "wall_pushups" -> "Push-up"
            "squat", "squats" -> "Squat"
            "glute_bridge", "glute_bridges", "bridge" -> "Glute Bridge"
            "static_lunge", "static_lunges", "lunge", "lunges" -> "Static Lunge"
            "lying_leg_raise", "lying_leg_raises", "leg_raise", "leg_raises" -> "Lying Leg Raise"
            else -> rawLabel.trim().ifEmpty { "Rest" }
        }
    }

    data class UiState(
        val predictedClass: String = "",
        val score: Float = 0f,
        val probabilities: List<Float> = emptyList(),
        val classNames: List<String> = emptyList(),
        val formConfidence: Float? = null, // Form quality: 0.0-0.4 (poor), 0.5-0.7 (moderate), 0.8-1.0 (good)
        val flags: List<String> = emptyList(),
        val messages: List<String> = emptyList()
    )
}


