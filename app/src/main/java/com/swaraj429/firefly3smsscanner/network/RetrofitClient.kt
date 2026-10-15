package com.swaraj429.firefly3smsscanner.network

import com.swaraj429.firefly3smsscanner.auth.AuthMethod
import com.swaraj429.firefly3smsscanner.auth.OAuthTokenService
import com.swaraj429.firefly3smsscanner.auth.TokenStore
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds Retrofit client dynamically based on user config.
 * Supports both PAT and OAuth2 authentication with automatic token refresh.
 */
object RetrofitClient {
    private const val TAG = "RetrofitClient"

    /**
     * Legacy PAT-based client creation — retains backward compatibility.
     */
    fun create(baseUrl: String, accessToken: String): FireflyApi {
        DebugLog.log(TAG, "Creating legacy PAT Retrofit client for: $baseUrl")

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            DebugLog.log("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FireflyApi::class.java)
    }

    /**
     * TokenStore-aware client creation.
     * Evaluates current active token on each request and automatically refreshes OAuth token on HTTP 401.
     */
    fun create(baseUrl: String, tokenStore: TokenStore): FireflyApi {
        DebugLog.log(TAG, "Creating TokenStore-aware Retrofit client for: $baseUrl (${tokenStore.authMethod.name})")

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            DebugLog.log("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val token = tokenStore.getActiveAccessToken()
            val request = chain.request().newBuilder()
                .apply {
                    if (token.isNotBlank()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            chain.proceed(request)
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (tokenStore.authMethod == AuthMethod.OAUTH2) {
            clientBuilder.authenticator(TokenRefreshAuthenticator(baseUrl, tokenStore))
        }

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(url)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FireflyApi::class.java)
    }
}

/**
 * OkHttp Authenticator that intercepts HTTP 401 Unauthorized responses and attempts an OAuth token refresh.
 */
private class TokenRefreshAuthenticator(
    private val baseUrl: String,
    private val tokenStore: TokenStore
) : Authenticator {

    companion object {
        private const val TAG = "TokenRefreshAuth"
        private const val HEADER_REFRESHED = "X-Token-Refreshed"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header(HEADER_REFRESHED) != null) {
            DebugLog.log(TAG, "Token refresh retry already failed previously. Aborting retry.")
            return null
        }

        if (tokenStore.authMethod != AuthMethod.OAUTH2 || tokenStore.oauthRefreshToken.isBlank()) {
            return null
        }

        DebugLog.log(TAG, "Received 401 Unauthorized. Attempting OAuth token refresh...")

        val refreshedToken = synchronized(this) {
            // Check if another concurrent thread already refreshed the token
            val currentHeader = response.request.header("Authorization")
            val activeToken = tokenStore.getActiveAccessToken()
            if (currentHeader != null && currentHeader != "Bearer $activeToken" && activeToken.isNotBlank()) {
                DebugLog.log(TAG, "Token was already refreshed by another thread. Re-using active token.")
                return@synchronized activeToken
            }

            try {
                runBlocking(Dispatchers.IO) {
                    val service = OAuthTokenService.create(baseUrl)
                    val secret = if (tokenStore.isConfidentialClient && tokenStore.oauthClientSecret.isNotBlank()) {
                        tokenStore.oauthClientSecret
                    } else null

                    val refreshResponse = service.refreshToken(
                        grantType = "refresh_token",
                        clientId = tokenStore.oauthClientId,
                        clientSecret = secret,
                        refreshToken = tokenStore.oauthRefreshToken
                    )

                    if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                        val body = refreshResponse.body()!!
                        tokenStore.saveOAuthTokens(body)
                        DebugLog.log(TAG, "Token refresh successful via Authenticator.")
                        body.accessToken
                    } else {
                        val err = refreshResponse.errorBody()?.string() ?: ""
                        DebugLog.log(TAG, "Token refresh failed in Authenticator: HTTP ${refreshResponse.code()} - $err")
                        null
                    }
                }
            } catch (e: Exception) {
                DebugLog.log(TAG, "Token refresh exception in Authenticator: ${e.message}")
                null
            }
        }

        return if (!refreshedToken.isNullOrBlank()) {
            response.request.newBuilder()
                .header("Authorization", "Bearer $refreshedToken")
                .header(HEADER_REFRESHED, "true")
                .build()
        } else {
            null
        }
    }
}
