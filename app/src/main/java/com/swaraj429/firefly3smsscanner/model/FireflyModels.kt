package com.swaraj429.firefly3smsscanner.model

import com.google.gson.annotations.SerializedName

// ---- Request models ----

data class FireflyTransactionRequest(
    @SerializedName("error_if_duplicate_hash") val errorIfDuplicate: Boolean = false,
    @SerializedName("apply_rules") val applyRules: Boolean = true,
    val transactions: List<FireflyTransactionSplit>
)

data class FireflyTransactionSplit(
    val type: String, // "withdrawal" or "deposit"
    val description: String,
    val amount: String,
    @SerializedName("source_id") val sourceId: String? = null,
    @SerializedName("destination_id") val destinationId: String? = null,
    @SerializedName("source_name") val sourceName: String? = null,
    @SerializedName("destination_name") val destinationName: String? = null,
    val date: String, // "2024-01-15T12:00:00+05:30"
    val notes: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    val tags: List<String>? = null,
    @SerializedName("budget_id") val budgetId: String? = null
)

// ---- Response models ----

// About
data class FireflyAboutResponse(
    val data: FireflyAboutData
)

data class FireflyAboutData(
    val version: String,
    @SerializedName("api_version") val apiVersion: String,
    val os: String
)

// Transaction
data class FireflyTransactionResponse(
    val data: FireflyTransactionData?
)

data class FireflyTransactionData(
    val id: String,
    val type: String
)

data class FireflyErrorResponse(
    val message: String?,
    val errors: Map<String, List<String>>?
)

// ---- Account models ----

data class FireflyAccountsResponse(
    val data: List<FireflyAccountWrapper>
)

data class FireflyAccountWrapper(
    val id: String,
    val attributes: FireflyAccountAttributes
)

data class FireflyAccountAttributes(
    val name: String,
    val type: String, // "asset", "expense", "revenue"
    @SerializedName("account_number") val accountNumber: String?,
    @SerializedName("account_role") val accountRole: String?,
    @SerializedName("current_balance") val currentBalance: String?
)

// ---- Category models ----

data class FireflyCategoriesResponse(
    val data: List<FireflyCategoryWrapper>
)

data class FireflyCategoryWrapper(
    val id: String,
    val attributes: FireflyCategoryAttributes
)

data class FireflyCategoryAttributes(
    val name: String
)

// ---- Tag models ----

data class FireflyTagsResponse(
    val data: List<FireflyTagWrapper>
)

data class FireflyTagWrapper(
    val id: String,
    val attributes: FireflyTagAttributes
)

data class FireflyTagAttributes(
    val tag: String
)

// ---- Budget models ----

data class FireflyBudgetsResponse(
    val data: List<FireflyBudgetWrapper>
)

data class FireflyBudgetWrapper(
    val id: String,
    val attributes: FireflyBudgetAttributes
)

data class FireflyBudgetAttributes(
    val name: String,
    val active: Boolean? = true
)

// ---- Simplified models for UI ----

data class FireflyCategory(val id: String, val name: String)
data class FireflyTag(val id: String, val name: String)
data class FireflyBudget(val id: String, val name: String)
data class FireflyAccount(
    val id: String, 
    val name: String, 
    val type: String,
    val accountNumber: String? = null,
    val accountRole: String? = null
)
