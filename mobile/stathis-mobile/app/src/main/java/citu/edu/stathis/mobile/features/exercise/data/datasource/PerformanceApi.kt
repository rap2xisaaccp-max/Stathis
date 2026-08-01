package citu.edu.stathis.mobile.features.exercise.data.datasource

import citu.edu.stathis.mobile.features.exercise.domain.model.PerformanceMetrics
import citu.edu.stathis.mobile.features.exercise.domain.model.PerformanceProgress
import citu.edu.stathis.mobile.features.exercise.domain.model.TeacherWebhookData
import retrofit2.http.*
import java.time.LocalDateTime

/**
 * Legacy performance endpoints — not backed by Spring controllers in the current API.
 * Prefer AdaptiveApi + scores endpoints. Callers should treat these as best-effort / no-op.
 */
@Deprecated("Stale performance surface; use AdaptiveApi and /api/v1/scores")
interface PerformanceApi {
    @Deprecated("No backend controller")
    @POST("performance/metrics")
    suspend fun savePerformanceMetrics(@Body metrics: PerformanceMetrics)

    @Deprecated("No backend controller")
    @GET("performance/progress/{exerciseId}")
    suspend fun getPerformanceProgress(
        @Path("exerciseId") exerciseId: String,
        @Query("startDate") startDate: LocalDateTime,
        @Query("endDate") endDate: LocalDateTime
    ): PerformanceProgress

    @Deprecated("No backend controller")
    @POST("performance/webhook/teacher")
    suspend fun sendTeacherWebhook(@Body webhookData: TeacherWebhookData)

    @Deprecated("No backend controller")
    @GET("performance/accuracy/{exerciseId}")
    suspend fun getAverageAccuracy(
        @Path("exerciseId") exerciseId: String,
        @Query("lastNSessions") lastNSessions: Int
    ): Float

    @Deprecated("No backend controller; repository returns simulated metrics")
    @GET("performance/realtime/{sessionId}")
    suspend fun getRealtimePerformance(@Path("sessionId") sessionId: String): PerformanceMetrics
}
