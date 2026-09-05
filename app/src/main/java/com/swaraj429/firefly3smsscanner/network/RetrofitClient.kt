package com.swaraj429.firefly3smsscanner.network

import com.swaraj429.firefly3smsscanner.debug.DebugLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds Retrofit client dynamically based on user config.
 * Heavy logging enabled for debugging.
 */
object RetrofitClient {
    private const val TAG = "RetrofitClient"

    fun create(baseUrl: String, accessToken: String): FireflyApi {
        DebugLog.log(TAG, "Creating Retrofit client for: $baseUrl")

        // Standard OkHttp logging interceptor (BASIC level)
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            DebugLog.log("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // Auth interceptor
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
}
