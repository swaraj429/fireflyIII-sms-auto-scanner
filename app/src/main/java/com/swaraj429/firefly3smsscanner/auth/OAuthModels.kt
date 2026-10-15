package com.swaraj429.firefly3smsscanner.auth

import com.google.gson.annotations.SerializedName

/**
 * Represents the token response from Firefly III's /oauth/token endpoint.
 */
data class OAuthTokenResponse(
    @SerializedName("token_type") val tokenType: String = "Bearer",
    @SerializedName("expires_in") val expiresIn: Long = 0L,
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String? = null
)

/**
 * Supported authentication methods.
 */
enum class AuthMethod {
    PERSONAL_ACCESS_TOKEN,
    OAUTH2
}

/**
 * Holds user OAuth client configuration.
 */
data class OAuthClientConfig(
    val clientId: String,
    val clientSecret: String = "",
    val isConfidential: Boolean = false
)
