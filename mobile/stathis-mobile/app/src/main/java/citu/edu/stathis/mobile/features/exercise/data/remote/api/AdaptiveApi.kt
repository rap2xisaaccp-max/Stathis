package citu.edu.stathis.mobile.features.exercise.data.remote.api

import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveBatchIngestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveBatchResultDto
import citu.edu.stathis.mobile.features.exercise.adaptive.ExerciseMasteryDto
import citu.edu.stathis.mobile.features.exercise.adaptive.RecommendationRequestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.RecommendationResponseDto
import citu.edu.stathis.mobile.features.exercise.adaptive.StudentLearningProfileDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AdaptiveApi {

    @POST("api/adaptive/batch")
    suspend fun ingestBatch(
        @Body body: AdaptiveBatchIngestDto
    ): AdaptiveBatchResultDto

    @POST("api/adaptive/recommend")
    suspend fun recommend(
        @Body body: RecommendationRequestDto
    ): RecommendationResponseDto

    @POST("api/adaptive/mastery/{exerciseType}/session")
    suspend fun recordSession(
        @Path("exerciseType")
        exerciseType: String
    ): Map<String, Any?>

    @GET("api/adaptive/profile")
    suspend fun getOwnProfile(): StudentLearningProfileDto

    @GET("api/adaptive/mastery")
    suspend fun getOwnMastery(): List<ExerciseMasteryDto>
}
