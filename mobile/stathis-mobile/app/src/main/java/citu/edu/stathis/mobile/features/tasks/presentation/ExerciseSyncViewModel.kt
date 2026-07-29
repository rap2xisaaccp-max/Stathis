package citu.edu.stathis.mobile.features.tasks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.exercise.domain.ExerciseCalorieCalculator
import citu.edu.stathis.mobile.features.profile.data.repository.ProfileRepository
import citu.edu.stathis.mobile.features.tasks.data.model.ExerciseProgressPayload
import citu.edu.stathis.mobile.features.tasks.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ExerciseSyncViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _weightKg = MutableStateFlow<Double?>(null)
    val weightKg: StateFlow<Double?> = _weightKg

    init {
        viewModelScope.launch {
            val profile = profileRepository.getUserProfile()
            if (profile.success) {
                _weightKg.value = profile.data?.weightInKg
            }
        }
    }

    fun caloriesFor(exerciseType: String?, reps: Int): Double {
        return ExerciseCalorieCalculator.calculate(exerciseType, reps, _weightKg.value)
    }

    fun publishProgress(
        classroomId: String?,
        taskId: String?,
        exerciseTemplateId: String,
        exerciseType: String?,
        reps: Int,
        goalReps: Int,
        accuracy: Double,
        timeTakenSeconds: Int,
        completed: Boolean = false
    ) {
        viewModelScope.launch {
            val sessionCalories = caloriesFor(exerciseType, reps)
            val score = if (goalReps > 0) {
                ((reps.toDouble() / goalReps) * 100.0).coerceIn(0.0, 100.0).toInt()
            } else {
                0
            }
            taskRepository.publishExerciseProgress(
                ExerciseProgressPayload(
                    classroomId = classroomId,
                    taskId = taskId,
                    exerciseTemplateId = exerciseTemplateId,
                    exerciseType = exerciseType,
                    reps = reps,
                    goalReps = goalReps,
                    accuracy = accuracy,
                    timeTakenMs = timeTakenSeconds * 1000L,
                    sessionCaloriesBurned = sessionCalories,
                    score = score,
                    completed = completed
                )
            )
        }
    }
}
