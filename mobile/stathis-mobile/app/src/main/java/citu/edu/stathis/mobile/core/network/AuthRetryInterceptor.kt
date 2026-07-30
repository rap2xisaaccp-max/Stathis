package citu.edu.stathis.mobile.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retries once after refreshing the access token when the server returns 403.
 *
 * Spring Security often responds with 403 (not 401) for anonymous/expired JWT requests,
 * which bypasses OkHttp's [okhttp3.Authenticator] (401-only). Without this retry,
 * exercise completion never persists and teacher Student Progress stays empty.
 */
@Singleton
class AuthRetryInterceptor @Inject constructor(
    private val tokenRefreshHelper: TokenRefreshHelper
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 403) return response
        if (request.header(RETRY_HEADER) != null) return response

        val failedToken = request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()
        if (failedToken.isBlank()) return response

        val newAccess = tokenRefreshHelper.refreshAccessToken(failedToken)
        if (newAccess.isNullOrBlank()) {
            Log.w(TAG, "403 received but token refresh failed for ${request.url}")
            return response
        }

        Log.i(TAG, "Retrying after 403 with refreshed token: ${request.url}")
        response.close()
        val retryRequest = request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .header(RETRY_HEADER, "1")
            .build()
        return chain.proceed(retryRequest)
    }

    companion object {
        private const val TAG = "AuthRetryInterceptor"
        private const val RETRY_HEADER = "X-Auth-Retry"
    }
}
