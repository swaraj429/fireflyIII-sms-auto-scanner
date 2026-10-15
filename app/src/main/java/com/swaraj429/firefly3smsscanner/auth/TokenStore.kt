package com.swaraj429.firefly3smsscanner.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.swaraj429.firefly3smsscanner.debug.DebugLog

/**
 * Secure token storage for Firefly III authentication credentials.
 * Backed by EncryptedSharedPreferences with fallback to standard preferences if Keystore fails.
 */
class TokenStore(context: Context) {

    companion object {
        private const val TAG = "TokenStore"
        private const val PREFS_FILE = "firefly_secure_tokens"

        private const val KEY_AUTH_METHOD = "auth_method"
        private const val KEY_PAT = "pat_access_token"
        private const val KEY_OAUTH_ACCESS = "oauth_access_token"
        private const val KEY_OAUTH_REFRESH = "oauth_refresh_token"
        private const val KEY_OAUTH_EXPIRY = "oauth_token_expiry"
        private const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        private const val KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret"
        private const val KEY_OAUTH_CONFIDENTIAL = "oauth_confidential"
        private const val KEY_PENDING_STATE = "oauth_pending_state"
        private const val KEY_PENDING_REDIRECT_URI = "oauth_pending_redirect_uri"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to private prefs", e)
        DebugLog.log(TAG, "EncryptedSharedPreferences init failed: ${e.message}")
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    var authMethod: AuthMethod
        get() {
            val stored = prefs.getString(KEY_AUTH_METHOD, AuthMethod.PERSONAL_ACCESS_TOKEN.name)
            return try {
                AuthMethod.valueOf(stored ?: AuthMethod.PERSONAL_ACCESS_TOKEN.name)
            } catch (_: Exception) {
                AuthMethod.PERSONAL_ACCESS_TOKEN
            }
        }
        set(value) {
            prefs.edit().putString(KEY_AUTH_METHOD, value.name).apply()
            DebugLog.log(TAG, "Auth method updated: ${value.name}")
        }

    var patAccessToken: String
        get() = prefs.getString(KEY_PAT, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PAT, value.trim()).apply()
        }

    var oauthAccessToken: String
        get() = prefs.getString(KEY_OAUTH_ACCESS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_OAUTH_ACCESS, value.trim()).apply()
        }

    var oauthRefreshToken: String
        get() = prefs.getString(KEY_OAUTH_REFRESH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_OAUTH_REFRESH, value.trim()).apply()
        }

    var oauthTokenExpiry: Long
        get() = prefs.getLong(KEY_OAUTH_EXPIRY, 0L)
        set(value) {
            prefs.edit().putLong(KEY_OAUTH_EXPIRY, value).apply()
        }

    var oauthClientId: String
        get() = prefs.getString(KEY_OAUTH_CLIENT_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_OAUTH_CLIENT_ID, value.trim()).apply()
        }

    var oauthClientSecret: String
        get() = prefs.getString(KEY_OAUTH_CLIENT_SECRET, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_OAUTH_CLIENT_SECRET, value.trim()).apply()
        }

    var isConfidentialClient: Boolean
        get() = prefs.getBoolean(KEY_OAUTH_CONFIDENTIAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_OAUTH_CONFIDENTIAL, value).apply()
        }

    var pendingOAuthState: String?
        get() = prefs.getString(KEY_PENDING_STATE, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_PENDING_STATE).apply()
            } else {
                prefs.edit().putString(KEY_PENDING_STATE, value).apply()
            }
        }

    var pendingRedirectUri: String?
        get() = prefs.getString(KEY_PENDING_REDIRECT_URI, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_PENDING_REDIRECT_URI).apply()
            } else {
                prefs.edit().putString(KEY_PENDING_REDIRECT_URI, value).apply()
            }
        }

    /**
     * Returns the currently active Bearer token based on the selected AuthMethod.
     */
    fun getActiveAccessToken(): String {
        return when (authMethod) {
            AuthMethod.PERSONAL_ACCESS_TOKEN -> patAccessToken
            AuthMethod.OAUTH2 -> oauthAccessToken
        }
    }

    /**
     * Returns true if OAuth access token is expired or about to expire in the next 60 seconds.
     */
    fun isOAuthTokenExpired(): Boolean {
        if (authMethod != AuthMethod.OAUTH2) return false
        if (oauthAccessToken.isBlank()) return true
        if (oauthTokenExpiry <= 0L) return false
        return System.currentTimeMillis() >= (oauthTokenExpiry - 60_000)
    }

    /**
     * Stores incoming OAuth tokens and calculates expiration.
     */
    fun saveOAuthTokens(response: OAuthTokenResponse) {
        oauthAccessToken = response.accessToken
        if (!response.refreshToken.isNullOrBlank()) {
            oauthRefreshToken = response.refreshToken
        }
        if (response.expiresIn > 0) {
            oauthTokenExpiry = System.currentTimeMillis() + (response.expiresIn * 1000)
        }
        authMethod = AuthMethod.OAUTH2
        DebugLog.log(TAG, "OAuth tokens saved, expires in ${response.expiresIn}s")
    }

    /**
     * Clears OAuth tokens when disconnected.
     */
    fun clearOAuthTokens() {
        prefs.edit()
            .remove(KEY_OAUTH_ACCESS)
            .remove(KEY_OAUTH_REFRESH)
            .remove(KEY_OAUTH_EXPIRY)
            .apply()
        DebugLog.log(TAG, "OAuth tokens cleared")
    }

    /**
     * Resets all credentials.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        DebugLog.log(TAG, "All secure tokens cleared")
    }
}
