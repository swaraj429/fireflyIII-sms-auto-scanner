package com.swaraj429.firefly3smsscanner.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import java.security.SecureRandom

/**
 * Orchestrates the Firefly III OAuth2 Authorization Code flow.
 */
class OAuthManager(context: Context) {

    companion object {
        private const val TAG = "OAuthManager"

        /**
         * HTTPS redirect URI — accepted out-of-the-box by Firefly III without server configuration changes.
         */
        const val REDIRECT_URI_HTTPS = "https://firefly3smsscanner.swaraj429.com/oauth/callback"

        /**
         * Custom scheme redirect URI — requires adding 'firefly3sms' in Firefly III Options > Administration > Configuration.
         */
        const val REDIRECT_URI_CUSTOM = "firefly3sms://oauth/callback"

        /**
         * Default redirect URI to prevent 'The redirect uris format is invalid' error on standard Firefly instances.
         */
        const val DEFAULT_REDIRECT_URI = REDIRECT_URI_HTTPS
    }

    private val tokenStore = TokenStore(context)

    /**
     * Builds the authorization URL to open in Chrome Custom Tabs.
     */
    fun buildAuthorizationUri(baseUrl: String, clientId: String, redirectUri: String = DEFAULT_REDIRECT_URI): Uri {
        val state = generateState()
        tokenStore.pendingOAuthState = state
        tokenStore.pendingRedirectUri = redirectUri

        val cleanBaseUrl = baseUrl.trimEnd('/')

        return Uri.parse("$cleanBaseUrl/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "")
            .appendQueryParameter("state", state)
            .build()
            .also {
                DebugLog.log(TAG, "Authorization URL generated for client: $clientId, redirect: $redirectUri")
            }
    }

    /**
     * Handles the redirect from Firefly III, validates state CSRF, and exchanges code for tokens.
     */
    suspend fun handleAuthorizationResponse(
        code: String,
        state: String,
        baseUrl: String,
        redirectUri: String? = null
    ): Result<OAuthTokenResponse> {
        val expectedState = tokenStore.pendingOAuthState
        if (expectedState.isNullOrBlank() || state != expectedState) {
            DebugLog.log(TAG, "State mismatch or expired: expected=$expectedState, got=$state")
            tokenStore.pendingOAuthState = null
            tokenStore.pendingRedirectUri = null
            return Result.failure(SecurityException("OAuth state mismatch. Verification failed."))
        }
        tokenStore.pendingOAuthState = null

        val effectiveRedirectUri = redirectUri ?: tokenStore.pendingRedirectUri ?: DEFAULT_REDIRECT_URI
        tokenStore.pendingRedirectUri = null

        return try {
            val service = OAuthTokenService.create(baseUrl)
            val secret = if (tokenStore.isConfidentialClient && tokenStore.oauthClientSecret.isNotBlank()) {
                tokenStore.oauthClientSecret
            } else null

            val response = service.exchangeAuthorizationCode(
                grantType = "authorization_code",
                clientId = tokenStore.oauthClientId,
                clientSecret = secret,
                code = code,
                redirectUri = effectiveRedirectUri
            )

            if (response.isSuccessful && response.body() != null) {
                val tokens = response.body()!!
                tokenStore.saveOAuthTokens(tokens)
                DebugLog.log(TAG, "Token exchange successful! Expiry: ${tokens.expiresIn}s")
                Result.success(tokens)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Empty error response"
                DebugLog.log(TAG, "Token exchange failed: HTTP ${response.code()} - $errorBody")
                Result.failure(RuntimeException("Token exchange failed (HTTP ${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            DebugLog.log(TAG, "Token exchange error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Retrieves a valid access token, auto-refreshing if expired.
     */
    suspend fun getValidAccessToken(baseUrl: String): String {
        if (tokenStore.authMethod != AuthMethod.OAUTH2) {
            return tokenStore.getActiveAccessToken()
        }

        if (!tokenStore.isOAuthTokenExpired() && tokenStore.oauthAccessToken.isNotBlank()) {
            return tokenStore.oauthAccessToken
        }

        return refreshAccessToken(baseUrl)
    }

    /**
     * Refreshes access token using stored refresh token.
     */
    suspend fun refreshAccessToken(baseUrl: String): String {
        val refreshToken = tokenStore.oauthRefreshToken
        if (refreshToken.isBlank()) {
            throw IllegalStateException("No refresh token available. Re-authorization required.")
        }

        val service = OAuthTokenService.create(baseUrl)
        val secret = if (tokenStore.isConfidentialClient && tokenStore.oauthClientSecret.isNotBlank()) {
            tokenStore.oauthClientSecret
        } else null

        val response = service.refreshToken(
            grantType = "refresh_token",
            clientId = tokenStore.oauthClientId,
            clientSecret = secret,
            refreshToken = refreshToken
        )

        if (response.isSuccessful && response.body() != null) {
            val tokens = response.body()!!
            tokenStore.saveOAuthTokens(tokens)
            DebugLog.log(TAG, "Token refresh successful!")
            return tokens.accessToken
        } else {
            val errorBody = response.errorBody()?.string() ?: "Unknown error"
            DebugLog.log(TAG, "Token refresh failed: HTTP ${response.code()} - $errorBody")
            throw RuntimeException("Token refresh failed (HTTP ${response.code()}): $errorBody")
        }
    }

    private fun generateState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
