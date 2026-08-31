package citu.edu.stathis.mobile.features.exercise.data.remote.api

import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveBatchIngestDto
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveBatchResultDto
import citu.edu.stathis.mobile.features.exercise.adaptive.ExerciseMasteryDto
import citu.edu.stathis.mobile.features.exercise.adaptive.FormMasteryDto
import citu.edu.stathis.mobile.features.exercise.adaptive.StudentLearningProfileDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit client for the adaptive API under api/adaptive/.
 */
interface AdaptiveApi {
    @POST("api/adaptive/batch")
    suspend fun ingestBatch(@Body body: AdaptiveBatchIngestDto): AdaptiveBatchResultDto

    @Multipart
    @POST("api/adaptive/evidence")
    suspend fun uploadEvidence(
        @Part("interventionId") interventionId: RequestBody,
        @Part("sessionId") sessionId: RequestBody,
        @Part("taskId") taskId: RequestBody?,
        @Part("classroomId") classroomId: RequestBody?,
        @Part("attemptNumber") attemptNumber: RequestBody?,
        @Part("exerciseType") exerciseType: RequestBody,
        @Part("errorCode") errorCode: RequestBody,
        @Part("errorDescription") errorDescription: RequestBody,
        @Part("correctionText") correctionText: RequestBody,
        @Part("capturedAt") capturedAt: RequestBody,
        @Part file: MultipartBody.Part
    ): Map<String, Any?>

    @POST("api/adaptive/mastery/{exerciseType}/session")
    suspend fun recordSession(@retrofit2.http.Path("exerciseType") exerciseType: String): Map<String, Any?>

    @GET("api/adaptive/profile")
    suspend fun getOwnProfile(): StudentLearningProfileDto

    @GET("api/adaptive/mastery")
    suspend fun getOwnMastery(): List<ExerciseMasteryDto>

    @GET("api/adaptive/form-mastery")
    suspend fun getOwnFormMastery(): List<FormMasteryDto>
}
