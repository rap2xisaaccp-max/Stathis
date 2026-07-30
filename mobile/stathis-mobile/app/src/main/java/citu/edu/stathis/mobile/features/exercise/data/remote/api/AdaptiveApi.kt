package citu.edu.stathis.mobile.features.exercise.data.remote.api

import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveBatchIngestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveBatchResultDto
import citu.edu.stathis.mobile.features.exercise.adaptive.RecommendationRequestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.RecommendationResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AdaptiveApi {
    @POST("api/adaptive/batch")
    suspend fun ingestBatch(@Body body: AdaptiveBatchIngestDto): AdaptiveBatchResultDto

    @POST("api/adaptive/recommend")
    suspend fun recommend(@Body body: RecommendationRequestDto): RecommendationResponseDto

    @POST("api/adaptive/mastery/{exerciseType}/session")
    suspend fun recordSession(@retrofit2.http.Path("exerciseType") exerciseType: String): Map<String, Any?>
}
