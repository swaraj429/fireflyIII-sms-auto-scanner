package com.swaraj429.firefly3smsscanner.network

import com.swaraj429.firefly3smsscanner.model.*
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

    @PUT("api/v1/transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: String,
        @Body request: FireflyTransactionRequest
    ): Response<FireflyTransactionResponse>

    /**
     * List transactions with date filtering and pagination.
     * Used by the reconciliation engine to fetch remote transactions.
     */
    @GET("api/v1/transactions")
    suspend fun listTransactions(
        @Query("start") start: String,   // "YYYY-MM-DD"
        @Query("end") end: String,       // "YYYY-MM-DD"
        @Query("type") type: String = "all",  // "all", "withdrawal", "deposit", "transfer"
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<FireflyTransactionListResponse>

    /**
     * Get a single transaction by ID.
     * Used to fetch updated details for a known transaction.
     */
    @GET("api/v1/transactions/{id}")
    suspend fun getTransaction(
        @Path("id") id: String
    ): Response<FireflyTransactionDetailResponse>
}
