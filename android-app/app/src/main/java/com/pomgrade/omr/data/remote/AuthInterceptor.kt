package com.pomgrade.omr.data.remote

import com.pomgrade.omr.auth.GoogleAuthManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp Interceptor responsible for:
 * 1. Injecting Google OAuth 2.0 Bearer token into Authorization header.
 * 2. Properly handling Google Apps Script 302 redirects while preserving POST method and body (equivalent to curl --post302).
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class AuthInterceptor(
    private val authManager: GoogleAuthManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 1. Inject Bearer Token if available
        val token = authManager.getCachedAccessToken()
        val requestWithAuth = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        // 2. Execute Request
        val response = chain.proceed(requestWithAuth)

        // 3. Handle Google Apps Script 302 Redirect with POST preservation
        if (response.code == 302 || response.code == 301 || response.code == 307) {
            val redirectUrl = response.header("Location")
            if (!redirectUrl.isNullOrBlank()) {
                response.close()

                val redirectRequestBuilder = originalRequest.newBuilder()
                    .url(redirectUrl)

                if (!token.isNullOrBlank()) {
                    redirectRequestBuilder.header("Authorization", "Bearer $token")
                }

                // If original was POST, preserve method and body
                if (originalRequest.method == "POST" && originalRequest.body != null) {
                    redirectRequestBuilder.post(originalRequest.body!!)
                }

                return chain.proceed(redirectRequestBuilder.build())
            }
        }

        return response
    }
}

