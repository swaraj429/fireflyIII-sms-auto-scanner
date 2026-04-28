package com.swaraj.fireflysmscanner.network

import com.swaraj.fireflysmscanner.model.*
import retrofit2.Response
import retrofit2.http.*

interface FireflyApi {

    @GET("api/v1/about")
    suspend fun getAbout(): Response<FireflyAboutResponse>

    @GET("api/v1/accounts")
    suspend fun getAccounts(
        @Query("type") type: String = "asset",
        @Query("limit") limit: Int = 100
    ): Response<FireflyAccountsResponse>

    @GET("api/v1/categories")
    suspend fun getCategories(
        @Query("limit") limit: Int = 100
    ): Response<FireflyCategoriesResponse>

    @GET("api/v1/tags")
    suspend fun getTags(
        @Query("limit") limit: Int = 100
    ): Response<FireflyTagsResponse>

    @GET("api/v1/budgets")
    suspend fun getBudgets(
        @Query("limit") limit: Int = 100
    ): Response<FireflyBudgetsResponse>

    @POST("api/v1/transactions")
    suspend fun createTransaction(
        @Body request: FireflyTransactionRequest
    ): Response<FireflyTransactionResponse>
}
