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

/**
 * Retrofit client for `/api/adaptive/*`.
 *
 * Backend parity (student self):
 * - POST batch / recommend / mastery/{type}/session — live closed-loop write path
 * - GET profile / GET mastery — read path for Profile mastery UI (Phase B)
 *
 * Teacher-only GETs (insights, evaluation, difficulty-recommendations) stay on the web client;
 * mobile does not call them.
 */
interface AdaptiveApi {
    @POST("api/adaptive/batch")
    suspend fun ingestBatch(@Body body: AdaptiveBatchIngestDto): AdaptiveBatchResultDto

    @POST("api/adaptive/recommend")
    suspend fun recommend(@Body body: RecommendationRequestDto): RecommendationResponseDto

    @POST("api/adaptive/mastery/{exerciseType}/session")
    suspend fun recordSession(@retrofit2.http.Path("exerciseType") exerciseType: String): Map<String, Any?>

    /** Student self: learning profile from closed-loop evidence (`GET /api/adaptive/profile`). */
    @GET("api/adaptive/profile")
    suspend fun getOwnProfile(): StudentLearningProfileDto

    /** Student self: exercise mastery list (`GET /api/adaptive/mastery`). */
    @GET("api/adaptive/mastery")
    suspend fun getOwnMastery(): List<ExerciseMasteryDto>
}
