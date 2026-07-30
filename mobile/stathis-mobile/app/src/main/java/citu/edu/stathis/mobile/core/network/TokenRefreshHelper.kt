package citu.edu.stathis.mobile.core.network

import android.util.Log
import citu.edu.stathis.mobile.core.data.AuthTokenManager
import cit.edu.stathis.mobile.BuildConfig
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared access-token refresh used by [TokenAuthenticator] and [AuthRetryInterceptor].
 */
@Singleton
class TokenRefreshHelper @Inject constructor(
    private val authTokenManager: AuthTokenManager
) {
    private val refreshLock = Any()

    /**
     * Returns a usable access token, refreshing when [failedAccessToken] matches the stored token
     * (or when no access token is stored). Returns null if refresh fails.
     */
    fun refreshAccessToken(failedAccessToken: String?): String? {
        synchronized(refreshLock) {
            val currentAccess: String? = runBlocking { authTokenManager.accessTokenFlow.firstOrNull() }
            if (!currentAccess.isNullOrBlank() && currentAccess != failedAccessToken) {
                return currentAccess
            }

            val refreshToken: String? = runBlocking { authTokenManager.refreshTokenFlow.firstOrNull() }
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "No refresh token; cannot refresh.")
                runBlocking { authTokenManager.clearAuthData() }
                return null
            }

            return try {
                val refreshed = performRefresh(refreshToken)
                if (refreshed != null) {
                    runBlocking {
                        authTokenManager.updateTokens(refreshed.accessToken, refreshed.refreshToken)
                    }
                    refreshed.accessToken
                } else {
                    runBlocking { authTokenManager.clearAuthData() }
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed: ${e.message}")
                runBlocking { authTokenManager.clearAuthData() }
                null
            }
        }
    }

    private data class RefreshDto(val accessToken: String, val refreshToken: String)

    private fun performRefresh(refreshToken: String): RefreshDto? {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') +
            "/api/auth/refresh?refreshToken=" + refreshToken
        val client = okhttp3.OkHttpClient.Builder().build()
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val access = json.get("accessToken")?.asString ?: return null
            val refresh = json.get("refreshToken")?.asString ?: refreshToken
            return RefreshDto(access, refresh)
        }
    }

    companion object {
        private const val TAG = "TokenRefreshHelper"
    }
}
