package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.auth.AuthMethod
import com.swaraj429.firefly3smsscanner.auth.OAuthManager
import com.swaraj429.firefly3smsscanner.auth.TokenStore
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.network.RetrofitClient
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import kotlinx.coroutines.launch

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SetupVM"
    private val prefs = AppPrefs(application)
    private val tokenStore = TokenStore(application)
    val oauthManager = OAuthManager(application)

    var baseUrl by mutableStateOf(prefs.baseUrl)
    var accessToken by mutableStateOf(tokenStore.patAccessToken.ifBlank { prefs.accessToken })
    var accountId by mutableStateOf(prefs.accountId)
    var connectionStatus by mutableStateOf("")
    var isTesting by mutableStateOf(false)

    // Auth Method: "PAT" or "OAUTH"
    var authMethod by mutableStateOf(prefs.authMethod)

    // OAuth specific states
    var oauthClientId by mutableStateOf(tokenStore.oauthClientId.ifBlank { prefs.oauthClientId })
    var oauthClientSecret by mutableStateOf(tokenStore.oauthClientSecret)
    var isConfidentialClient by mutableStateOf(tokenStore.isConfidentialClient)
    var oauthStatus by mutableStateOf(
        if (prefs.authMethod == "OAUTH" && tokenStore.oauthAccessToken.isNotBlank()) {
            "✅ Connected via OAuth"
        } else ""
    )

    var redirectUriMode by mutableStateOf("HTTPS") // "HTTPS" (default, no server config needed) or "CUSTOM"

    fun getEffectiveRedirectUri(): String {
        return if (redirectUriMode == "CUSTOM") {
            OAuthManager.REDIRECT_URI_CUSTOM
        } else {
            OAuthManager.REDIRECT_URI_HTTPS
        }
    }

    fun saveConfig() {
        prefs.baseUrl = baseUrl
        prefs.accountId = accountId
        prefs.authMethod = authMethod

        if (authMethod == "PAT") {
            prefs.accessToken = accessToken
            tokenStore.patAccessToken = accessToken
            tokenStore.authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN
        } else {
            prefs.oauthClientId = oauthClientId
            tokenStore.oauthClientId = oauthClientId
            tokenStore.oauthClientSecret = oauthClientSecret
            tokenStore.isConfidentialClient = isConfidentialClient
            tokenStore.authMethod = AuthMethod.OAUTH2
        }
        DebugLog.log(TAG, "Config saved: method=$authMethod, url=$baseUrl, accountId=$accountId")
    }

    fun startOAuthFlow(context: Context) {
        if (baseUrl.isBlank() || oauthClientId.isBlank()) {
            oauthStatus = "❌ Please enter Server URL and Client ID first"
            return
        }

        saveConfig()

        try {
            val redirectUri = getEffectiveRedirectUri()
            val authUri = oauthManager.buildAuthorizationUri(baseUrl, oauthClientId, redirectUri)
            oauthStatus = "⏳ Opening Firefly III login..."

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(context, authUri)
        } catch (e: Exception) {
            oauthStatus = "❌ Could not launch browser: ${e.message}"
            DebugLog.log(TAG, "Failed to launch Custom Tab: ${e.message}")
        }
    }

    fun disconnectOAuth() {
        tokenStore.clearOAuthTokens()
        oauthStatus = "Disconnected"
        connectionStatus = ""
        DebugLog.log(TAG, "OAuth disconnected by user")
    }

    fun refreshOAuthStatus() {
        authMethod = prefs.authMethod
        if (prefs.authMethod == "OAUTH" && tokenStore.oauthAccessToken.isNotBlank()) {
            oauthStatus = "✅ Connected via OAuth"
        }
    }

    fun testConnection() {
        if (baseUrl.isBlank()) {
            connectionStatus = "❌ Please fill in Server URL"
            return
        }

        saveConfig()

        val token = tokenStore.getActiveAccessToken()
        if (token.isBlank()) {
            connectionStatus = if (authMethod == "OAUTH") {
                "❌ Please tap 'Authorize with Firefly III' first"
            } else {
                "❌ Please fill in Access Token"
            }
            return
        }

        isTesting = true
        connectionStatus = "⏳ Testing connection..."
        DebugLog.log(TAG, "Testing connection to: $baseUrl (method=$authMethod)")

        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(baseUrl, tokenStore)
                val response = api.getAbout()

                if (response.isSuccessful) {
                    val about = response.body()
                    connectionStatus = "✅ Connected! Firefly v${about?.data?.version ?: "?"}"
                    DebugLog.log(TAG, "Connection SUCCESS: ${about?.data?.version}")
                    saveConfig()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    connectionStatus = "❌ HTTP ${response.code()}: $errorBody"
                    DebugLog.log(TAG, "Connection FAILED: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                connectionStatus = "❌ Error: ${e.message}"
                DebugLog.log(TAG, "Connection ERROR: ${e.message}")
                Log.e(TAG, "Connection test failed", e)
            } finally {
                isTesting = false
            }
        }
    }
}
