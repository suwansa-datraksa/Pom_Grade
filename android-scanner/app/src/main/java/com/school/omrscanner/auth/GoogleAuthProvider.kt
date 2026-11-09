package com.school.omrscanner.auth

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Google OAuth 2.0 Authorization and silent Bearer token caching.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class GoogleAuthProvider(private val context: Context) {

    private val prefs = context.getSharedPreferences("omr_auth_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val SPREADSHEETS_SCOPE = "oauth2:https://www.googleapis.com/auth/spreadsheets"
        private const val KEY_CACHED_TOKEN = "cached_bearer_token"
        private const val KEY_EXPIRY_TIME = "cached_token_expiry"
    }

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"))
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isUserSignedIn(): Boolean = getSignedInAccount() != null

    fun isSignedIn(): Boolean = isUserSignedIn()

    fun getSignInIntent(): android.content.Intent = signInClient.signInIntent

    fun getCurrentAccountEmail(): String? = getSignedInAccount()?.email

    fun getCachedToken(): String? {
        val token = prefs.getString(KEY_CACHED_TOKEN, null)
        val expiry = prefs.getLong(KEY_EXPIRY_TIME, 0L)
        if (token != null && System.currentTimeMillis() < expiry) {
            return token
        }
        return token
    }

    /**
     * Silently fetches a fresh access token on IO Dispatcher.
     */
    suspend fun fetchFreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(IllegalStateException("No Google Account currently signed in"))

            val accountName = account.account
                ?: return@withContext Result.failure(IllegalStateException("Android Account object is null"))

            // Clear cache from GoogleAuthUtil if previous token expired
            val oldToken = prefs.getString(KEY_CACHED_TOKEN, null)
            if (oldToken != null) {
                try {
                    GoogleAuthUtil.clearToken(context, oldToken)
                } catch (ignored: Exception) {}
            }

            // Silent fetch using Google Authorization API
            val token = GoogleAuthUtil.getToken(context, accountName, SPREADSHEETS_SCOPE)

            // Cache token for 55 minutes
            val expiry = System.currentTimeMillis() + (55 * 60 * 1000)
            prefs.edit()
                .putString(KEY_CACHED_TOKEN, token)
                .putLong(KEY_EXPIRY_TIME, expiry)
                .apply()

            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        signInClient.signOut()
    }
}
