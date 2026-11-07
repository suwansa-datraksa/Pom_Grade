package com.pomgrade.omr.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Google Sign-In and OAuth 2.0 Access Token acquisition.
 * Provides Bearer token for Google Apps Script Web App authentication.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class GoogleAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("omr_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CACHED_TOKEN = "cached_access_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry_timestamp"
        private const val SPREADSHEETS_SCOPE = "oauth2:https://www.googleapis.com/auth/spreadsheets"
    }

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"))
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isUserSignedIn(): Boolean {
        return getSignedInAccount() != null
    }

    fun getCachedAccessToken(): String? {
        val token = prefs.getString(KEY_CACHED_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        if (token != null && System.currentTimeMillis() < expiry) {
            return token
        }
        return token
    }

    /**
     * Obtains a fresh OAuth 2.0 Access Token in a background thread.
     */
    suspend fun fetchFreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(IllegalStateException("No Google Account signed in"))

            val accountName = account.account
                ?: return@withContext Result.failure(IllegalStateException("Account object is null"))

            val token = GoogleAuthUtil.getToken(
                context,
                accountName,
                SPREADSHEETS_SCOPE
            )

            // Cache token for 55 minutes
            val expiry = System.currentTimeMillis() + (55 * 60 * 1000)
            prefs.edit()
                .putString(KEY_CACHED_TOKEN, token)
                .putLong(KEY_TOKEN_EXPIRY, expiry)
                .apply()

            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
        signInClient.signOut()
    }
}

