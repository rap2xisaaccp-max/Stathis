package citu.edu.stathis.mobile.features.exercise.domain

import citu.edu.stathis.mobile.features.exercise.data.model.ExerciseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Exercise catalog endpoints that still exist on the backend.
 *
 * Stale session/history/performance paths were removed in Phase 10 —
 * use AdaptiveApi.ingestBatch, /api/v1/scores, and /api/adaptive/insights instead.
 */
interface ExerciseApiService {
    @GET("api/exercises")
    suspend fun getAvailableExercises(@Query("userId") userId: String): Response<List<ExerciseDto>>

    @GET("api/exercises/templates")
    suspend fun getAvailableExercisesNoEnrollment(): Response<List<ExerciseDto>>

    @GET("api/exercises/{id}")
    suspend fun getExerciseDetails(
        @Path("id") exerciseId: String,
        @Query("userId") userId: String
    ): Response<ExerciseDto>

    @GET("api/templates/exercises/{id}")
    suspend fun getExerciseTemplateDetails(@Path("id") exerciseId: String): Response<ExerciseDto>
}
