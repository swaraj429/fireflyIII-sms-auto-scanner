package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.*
import com.swaraj429.firefly3smsscanner.network.FireflyApi
import com.swaraj429.firefly3smsscanner.network.RetrofitClient
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import kotlinx.coroutines.launch

/**
 * Fetches and caches Firefly III metadata (categories, tags, budgets, accounts).
 * Shared across screens so data is loaded once and available everywhere.
 */
class FireflyDataViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "FireflyDataVM"
    private val prefs = AppPrefs(application)

    val categories = mutableStateListOf<FireflyCategory>()
    val tags = mutableStateListOf<FireflyTag>()
    val budgets = mutableStateListOf<FireflyBudget>()
    val assetAccounts = mutableStateListOf<FireflyAccount>()
    val expenseAccounts = mutableStateListOf<FireflyAccount>()
    val revenueAccounts = mutableStateListOf<FireflyAccount>()

    var isLoading by mutableStateOf(false)
    var lastSyncStatus by mutableStateOf("")
    var hasSynced by mutableStateOf(false)

    fun refreshAll() {
        if (!prefs.isConfigured) {
            lastSyncStatus = "❌ Not configured — go to Setup"
            return
        }

        isLoading = true
        lastSyncStatus = "⏳ Syncing Firefly data..."
        DebugLog.log(TAG, "Refreshing all Firefly metadata...")

        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(prefs.baseUrl, prefs.accessToken)

                fetchCategories(api)
                fetchTags(api)
                fetchBudgets(api)
                fetchAccounts(api, "asset", assetAccounts)
                fetchAccounts(api, "expense", expenseAccounts)
                fetchAccounts(api, "revenue", revenueAccounts)

                hasSynced = true
                lastSyncStatus = "✅ Synced: ${categories.size} categories, ${tags.size} tags, " +
                        "${budgets.size} budgets, ${assetAccounts.size + expenseAccounts.size + revenueAccounts.size} accounts"
                DebugLog.log(TAG, lastSyncStatus)
            } catch (e: Exception) {
                lastSyncStatus = "❌ Sync failed: ${e.message}"
                DebugLog.log(TAG, "Sync ERROR: ${e.message}")
                Log.e(TAG, "Failed to sync Firefly data", e)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun fetchCategories(api: FireflyApi) {
        try {
            val response = api.getCategories()
            if (response.isSuccessful) {
                val items = response.body()?.data?.map {
                    FireflyCategory(id = it.id, name = it.attributes.name)
                } ?: emptyList()
                categories.clear()
                categories.addAll(items)
                DebugLog.log(TAG, "Fetched ${items.size} categories")
            } else {
                DebugLog.log(TAG, "Categories fetch failed: ${response.code()}")
            }
        } catch (e: Exception) {
            DebugLog.log(TAG, "Categories error: ${e.message}")
        }
    }

    private suspend fun fetchTags(api: FireflyApi) {
        try {
            val response = api.getTags()
            if (response.isSuccessful) {
                val items = response.body()?.data?.map {
                    FireflyTag(id = it.id, name = it.attributes.tag)
                } ?: emptyList()
                tags.clear()
                tags.addAll(items)
                DebugLog.log(TAG, "Fetched ${items.size} tags")
            } else {
                DebugLog.log(TAG, "Tags fetch failed: ${response.code()}")
            }
        } catch (e: Exception) {
            DebugLog.log(TAG, "Tags error: ${e.message}")
        }
    }

    private suspend fun fetchBudgets(api: FireflyApi) {
        try {
            val response = api.getBudgets()
            if (response.isSuccessful) {
                val items = response.body()?.data
                    ?.filter { it.attributes.active != false }
                    ?.map { FireflyBudget(id = it.id, name = it.attributes.name) }
                    ?: emptyList()
                budgets.clear()
                budgets.addAll(items)
                DebugLog.log(TAG, "Fetched ${items.size} active budgets")
            } else {
                DebugLog.log(TAG, "Budgets fetch failed: ${response.code()}")
            }
        } catch (e: Exception) {
            DebugLog.log(TAG, "Budgets error: ${e.message}")
        }
    }

    private suspend fun fetchAccounts(
        api: FireflyApi,
        type: String,
        target: SnapshotStateList<FireflyAccount>
    ) {
        try {
            val response = api.getAccounts(type = type)
            if (response.isSuccessful) {
                val items = response.body()?.data?.map {
                    FireflyAccount(
                        id = it.id,
                        name = it.attributes.name,
                        type = it.attributes.type,
                        accountNumber = it.attributes.accountNumber,
                        accountRole = it.attributes.accountRole
                    )
                } ?: emptyList()
                target.clear()
                target.addAll(items)
                DebugLog.log(TAG, "Fetched ${items.size} $type accounts")
            } else {
                DebugLog.log(TAG, "$type accounts fetch failed: ${response.code()}")
            }
        } catch (e: Exception) {
            DebugLog.log(TAG, "$type accounts error: ${e.message}")
        }
    }
}
