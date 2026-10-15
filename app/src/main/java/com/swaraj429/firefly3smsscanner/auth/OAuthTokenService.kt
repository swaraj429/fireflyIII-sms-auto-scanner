package com.swaraj429.firefly3smsscanner.auth

import com.swaraj429.firefly3smsscanner.debug.DebugLog
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OAuthTokenService {

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeAuthorizationCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String
    ): Response<OAuthTokenResponse>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("refresh_token") refreshToken: String
    ): Response<OAuthTokenResponse>

    companion object {
        /**
         * Creates an OAuthTokenService without Bearer authentication interceptor.
         */
        fun create(baseUrl: String): OAuthTokenService {
            val logging = HttpLoggingInterceptor { message ->
                DebugLog.log("OAuthHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
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
                .create(OAuthTokenService::class.java)
        }
    }
}
