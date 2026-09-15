package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.db.FireflyDatabase
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages unique detected vendors across SMS history and real-time scans.
 * Connects vendors with active parsing rules to allow 1-tap rule creation.
 */
class VendorsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "VendorsVM"
    private val db = FireflyDatabase.getDatabase(application)
    private val dao = db.smsRecordDao()

    var searchQuery by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    val vendors = mutableStateListOf<VendorDisplayItem>()

    val filteredVendors: List<VendorDisplayItem>
        get() {
            val query = searchQuery.trim().lowercase()
            return if (query.isBlank()) {
                vendors.toList()
            } else {
                vendors.filter { it.name.lowercase().contains(query) }
            }
        }

    val totalCount: Int get() = vendors.size
    val mappedCount: Int get() = vendors.count { it.hasActiveRule }
    val unmappedCount: Int get() = vendors.count { !it.hasActiveRule }

    init {
        loadVendors()
    }

    /**
     * Loads unique vendors from the database and correlates them with current rules.
     * Optionally includes currently active in-memory transactions from SmsViewModel.
     */
    fun loadVendors(
        rules: List<ParsingRule> = emptyList(),
        inMemoryTransactions: List<ParsedTransaction> = emptyList()
    ) {
        viewModelScope.launch {
            isLoading = true
            try {
                val dbVendors = withContext(Dispatchers.IO) {
                    dao.getDetectedVendors()
                }

                // Map of vendorName (lowercase) -> aggregate details
                val aggregateMap = mutableMapOf<String, VendorAccumulator>()

                // 1. Ingest from DB
                for (v in dbVendors) {
                    val clean = v.vendor.trim()
                    if (clean.isBlank()) continue
                    val key = clean.lowercase()
                    aggregateMap[key] = VendorAccumulator(
                        displayName = clean,
                        count = v.txnCount,
                        lastSeen = v.lastSeen,
                        lastAmount = v.lastAmount,
                        sampleMessage = v.sampleMessage
                    )
                }

                // 2. Merge in-memory transactions that might be newer or pending
                for (txn in inMemoryTransactions) {
                    val vName = txn.vendor?.trim() ?: continue
                    if (vName.isBlank()) continue
                    val key = vName.lowercase()
                    val existing = aggregateMap[key]
                    if (existing != null) {
                        val newCount = existing.count + 1
                        val newLastSeen = maxOf(existing.lastSeen, txn.timestamp)
                        val newLastAmount = if (txn.timestamp >= existing.lastSeen) txn.effectiveAmount else existing.lastAmount
                        val newSample = if (txn.timestamp >= existing.lastSeen) txn.rawMessage else existing.sampleMessage
                        aggregateMap[key] = existing.copy(
                            count = newCount,
                            lastSeen = newLastSeen,
                            lastAmount = newLastAmount,
                            sampleMessage = newSample
                        )
                    } else {
                        aggregateMap[key] = VendorAccumulator(
                            displayName = vName,
                            count = 1,
                            lastSeen = txn.timestamp,
                            lastAmount = txn.effectiveAmount,
                            sampleMessage = txn.rawMessage
                        )
                    }
                }

                // 3. Correlate with active rules
                val displayList = aggregateMap.values.map { acc ->
                    val matchedRule = rules.firstOrNull { rule ->
                        rule.isEnabled && (
                            rule.keyword.equals(acc.displayName, ignoreCase = true) ||
                            acc.displayName.contains(rule.keyword, ignoreCase = true) ||
                            rule.keyword.contains(acc.displayName, ignoreCase = true)
                        )
                    }

                    VendorDisplayItem(
                        name = acc.displayName,
                        transactionCount = acc.count,
                        lastSeen = acc.lastSeen,
                        lastAmount = acc.lastAmount,
                        sampleMessage = acc.sampleMessage,
                        hasActiveRule = matchedRule != null,
                        matchingRule = matchedRule
                    )
                }.sortedWith(
                    // Unmapped vendors first, then most recently seen
                    compareBy<VendorDisplayItem> { it.hasActiveRule }
                        .thenByDescending { it.lastSeen }
                )

                vendors.clear()
                vendors.addAll(displayList)
                DebugLog.log(TAG, "Loaded ${vendors.size} unique vendors (${mappedCount} with rules, ${unmappedCount} unmapped)")
            } catch (e: Exception) {
                DebugLog.log(TAG, "Error loading vendors: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
}

private data class VendorAccumulator(
    val displayName: String,
    val count: Int,
    val lastSeen: Long,
    val lastAmount: Double?,
    val sampleMessage: String?
)

data class VendorDisplayItem(
    val name: String,
    val transactionCount: Int,
    val lastSeen: Long,
    val lastAmount: Double?,
    val sampleMessage: String?,
    val hasActiveRule: Boolean,
    val matchingRule: ParsingRule?
)
