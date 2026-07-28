package citu.edu.stathis.mobile.features.exercise.ui.viewmodel

import citu.edu.stathis.mobile.core.data.models.ClientResponse
import citu.edu.stathis.mobile.features.exercise.data.Exercise
import citu.edu.stathis.mobile.features.exercise.data.ExerciseSessionResult
import citu.edu.stathis.mobile.features.exercise.data.model.PerformanceSummaryDto
import citu.edu.stathis.mobile.features.exercise.data.model.PostureResponseDto
import citu.edu.stathis.mobile.features.exercise.data.remote.dto.ClassificationResultDto
import citu.edu.stathis.mobile.features.exercise.domain.model.PostureAnalysis
import citu.edu.stathis.mobile.features.exercise.domain.repository.ExerciseRepository
import citu.edu.stathis.mobile.features.exercise.domain.usecase.ClassifyPoseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseViewModelTest {

    @Test
    fun normalizeScores_keepsProbabilitiesBalancedAndSorted() {
        val rawScores = mapOf(
            "Push-up" to 0.6f,
            "Squat" to 0.3f,
            "Rest" to 0.1f
        )

        val vm = ExerciseViewModel(
            ClassifyPoseUseCase(object : ExerciseRepository {
                override fun analyzePostureInRealTime(exerciseId: String): Flow<PostureAnalysis> = flow { }
                override suspend fun saveAnalysisResult(exerciseId: String, analysis: PostureAnalysis) {}
                override suspend fun startExerciseSession(exerciseId: String) {}
                override suspend fun endExerciseSession(exerciseId: String) {}
                override suspend fun getAvailableExercises(): ClientResponse<List<Exercise>> = ClientResponse(true, "", emptyList())
                override suspend fun getExerciseDetails(exerciseId: String): ClientResponse<Exercise> = ClientResponse(false, "", null)
                override suspend fun analyzePostureWithBackend(landmarks: List<List<List<Float>>>): ClientResponse<PostureResponseDto> = ClientResponse(false, "", null)
                override suspend fun saveExerciseSession(sessionResult: ExerciseSessionResult): ClientResponse<Unit> = ClientResponse(true, "", Unit)
                override suspend fun getExerciseHistory(userId: String): ClientResponse<List<ExerciseSessionResult>> = ClientResponse(true, "", emptyList())
                override suspend fun getPerformanceSummary(userId: String, exerciseId: String?): ClientResponse<List<PerformanceSummaryDto>> = ClientResponse(true, "", emptyList())
                override suspend fun classify(window: Array<Array<FloatArray>>): ClassificationResultDto =
                    ClassificationResultDto("", 0f, emptyList(), emptyList(), null, null, null)
            })
        )

        val normalized = vm.normalizeScores(rawScores)

        assertEquals(3, normalized.size)
        assertTrue(normalized[0].second >= normalized[1].second)
        assertTrue(normalized[1].second >= normalized[2].second)
        assertEquals(1.0, normalized.sumOf { it.second.toDouble() }, 0.001)
    }
}
