package com.school.omrscanner.sync

import com.school.omrscanner.auth.GoogleAuthProvider
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Factory for OkHttpClient with Custom Redirect Interceptor.
 *
 * Crucial Requirement (Architecture v6 Section 5.1):
 *  Handles Google Apps Script HTTP 302 / 307 / 308 redirects without stripping
 *  the Authorization: Bearer <token> header or losing the POST method & body.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
object OkHttpProvider {

    /**
     * Creates an OkHttpClient configured for Google Apps Script Web App ingestion.
     */
    fun createClient(authProvider: GoogleAuthProvider? = null): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(false) // Disable default redirect to prevent Header stripping
            .followSslRedirects(false)
            .addInterceptor(createAuthAndRedirectInterceptor(authProvider))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Custom Interceptor preserving Authorization across 302/307 redirects.
     */
    fun createAuthAndRedirectInterceptor(authProvider: GoogleAuthProvider?): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()

            // 1. Inject Bearer token if not already present
            val token = authProvider?.getCachedToken()
            val requestWithAuth = if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }

            // 2. Execute primary request
            val response = chain.proceed(requestWithAuth)

            // 3. Handle 302 / 307 / 308 Redirect preserving POST method & Bearer token
            if (response.code == 302 || response.code == 307 || response.code == 308) {
                val redirectUrl = response.header("Location")
                if (!redirectUrl.isNullOrEmpty()) {
                    response.close()

                    val newRequestBuilder = originalRequest.newBuilder()
                        .url(redirectUrl)

                    // Retain Authorization Header
                    val authHeader = requestWithAuth.header("Authorization")
                    if (!authHeader.isNullOrEmpty()) {
                        newRequestBuilder.header("Authorization", authHeader)
                    }

                    // Preserve POST method and request body
                    if (originalRequest.method == "POST" && originalRequest.body != null) {
                        newRequestBuilder.post(originalRequest.body!!)
                    }

                    newRequestBuilder.header("Content-Type", "application/json")

                    return@Interceptor chain.proceed(newRequestBuilder.build())
                }
            }

            response
        }
    }
}
