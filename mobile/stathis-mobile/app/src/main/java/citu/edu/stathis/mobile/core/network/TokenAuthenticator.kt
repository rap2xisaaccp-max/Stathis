package citu.edu.stathis.mobile.core.network

import android.util.Log
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator that refreshes access tokens on 401 responses and retries once.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenRefreshHelper: TokenRefreshHelper
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            return null
        }

        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val newAccess = tokenRefreshHelper.refreshAccessToken(requestToken)
        if (newAccess.isNullOrBlank()) {
            Log.w("TokenAuthenticator", "Unable to refresh access token after 401")
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
