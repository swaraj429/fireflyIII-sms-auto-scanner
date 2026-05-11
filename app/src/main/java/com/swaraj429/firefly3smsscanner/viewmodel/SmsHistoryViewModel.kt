package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.db.FireflyDatabase
import com.swaraj429.firefly3smsscanner.db.SmsRecordEntity
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SendStatus
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.util.SmsHasher
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Manages the 30-day SMS record history stored in Room.
 *
 * Responsibilities:
 *  1. Save parsed transactions as [SmsRecordEntity] with hash-based dedup.
 *  2. Load records from the last 30 days and expose them as [ParsedTransaction].
 *  3. Track sync status (PENDING / SENT / FAILED) per record.
 *  4. Purge records older than 30 days on every load.
 */
class SmsHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SmsHistoryVM"
    private val dao = FireflyDatabase.getDatabase(application).smsRecordDao()

    /** All records from the last 30 days, converted to ParsedTransactions. */
    val historyTransactions = mutableStateListOf<ParsedTransaction>()

    /** Raw entities from DB (useful for hash lookups). */
    private val _entities = mutableListOf<SmsRecordEntity>()

    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("")

    // Summary counts
    var pendingCount by mutableStateOf(0)
    var sentCount by mutableStateOf(0)
    var failedCount by mutableStateOf(0)
    var totalCount by mutableStateOf(0)

    init {
        loadHistory()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Load all records from the last 30 days, purging older data first.
     */
    fun loadHistory() {
        viewModelScope.launch {
            isLoading = true
            try {
                // 1. Purge expired records
                val cutoff30d = cutoffMillis(30)
                val deleted = dao.deleteOlderThan(cutoff30d)
                if (deleted > 0) {
                    DebugLog.log(TAG, "Purged $deleted records older than 30 days")
                }

                // 2. Fetch surviving records
                val records = dao.getRecordsSince(cutoff30d)
                _entities.clear()
                _entities.addAll(records)

                // 3. Convert to ParsedTransactions for UI
                historyTransactions.clear()
                historyTransactions.addAll(records.map { it.toParsedTransaction() })

                // 4. Summary counts
                pendingCount = records.count { it.syncStatus == "PENDING" }
                sentCount = records.count { it.syncStatus == "SENT" }
                failedCount = records.count { it.syncStatus == "FAILED" }
                totalCount = records.size

                statusMessage = "$totalCount records · $pendingCount pending · $sentCount sent"
                DebugLog.log(TAG, "Loaded $totalCount history records (pending=$pendingCount, sent=$sentCount, failed=$failedCount)")
            } catch (e: Exception) {
                statusMessage = "❌ ${e.message}"
                DebugLog.log(TAG, "Error loading history: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Persist a list of parsed transactions to the history DB.
     * Duplicates (by hash) are silently ignored.
     * Returns the number of NEW records actually inserted.
     */
    fun saveTransactions(transactions: List<ParsedTransaction>): Int {
        var inserted = 0
        viewModelScope.launch {
            try {
                val entities = transactions.map { it.toEntity() }
                dao.insertRecords(entities)

                // Count how many were genuinely new
                inserted = entities.count { dao.existsByHash(it.smsHash) > 0 }
                DebugLog.log(TAG, "Saved ${entities.size} records (dedup may have skipped some)")

                // Refresh the list
                loadHistory()
            } catch (e: Exception) {
                DebugLog.log(TAG, "Error saving transactions: ${e.message}")
            }
        }
        return inserted
    }

    /**
     * Mark a transaction as SENT in the DB.
     */
    fun markSent(transaction: ParsedTransaction, fireflyId: String) {
        viewModelScope.launch {
            try {
                val hash = SmsHasher.hash(transaction.sender, transaction.rawMessage)
                dao.markSent(hash, fireflyId)
                transaction.status = SendStatus.SENT

                // Update the local list item
                refreshTransactionStatus(hash, "SENT")
                updateCounts()
                DebugLog.log(TAG, "Marked record as SENT: $hash → Firefly #$fireflyId")
            } catch (e: Exception) {
                DebugLog.log(TAG, "Error marking sent: ${e.message}")
            }
        }
    }

    /**
     * Mark a transaction as FAILED in the DB.
     */
    fun markFailed(transaction: ParsedTransaction) {
        viewModelScope.launch {
            try {
                val hash = SmsHasher.hash(transaction.sender, transaction.rawMessage)
                dao.markFailed(hash)
                transaction.status = SendStatus.FAILED

                refreshTransactionStatus(hash, "FAILED")
                updateCounts()
                DebugLog.log(TAG, "Marked record as FAILED: $hash")
            } catch (e: Exception) {
                DebugLog.log(TAG, "Error marking failed: ${e.message}")
            }
        }
    }

    /**
     * Check whether a given SMS already exists in the history.
     */
    suspend fun alreadyExists(sender: String, body: String): Boolean {
        val hash = SmsHasher.hash(sender, body)
        return dao.existsByHash(hash) > 0
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun cutoffMillis(days: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.timeInMillis
    }

    private fun refreshTransactionStatus(hash: String, newStatus: String) {
        val idx = _entities.indexOfFirst { it.smsHash == hash }
        if (idx >= 0 && idx < historyTransactions.size) {
            val old = historyTransactions[idx]
            historyTransactions[idx] = old.copy(
                status = SendStatus.valueOf(newStatus)
            )
        }
    }

    private fun updateCounts() {
        viewModelScope.launch {
            val cutoff = cutoffMillis(30)
            pendingCount = dao.countByStatus("PENDING", cutoff)
            sentCount = dao.countByStatus("SENT", cutoff)
            failedCount = dao.countByStatus("FAILED", cutoff)
            totalCount = pendingCount + sentCount + failedCount
            statusMessage = "$totalCount records · $pendingCount pending · $sentCount sent"
        }
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private fun SmsRecordEntity.toParsedTransaction(): ParsedTransaction {
        val txType = try {
            TransactionType.valueOf(transactionType)
        } catch (_: Exception) {
            TransactionType.WITHDRAWAL
        }
        val sendSt = try {
            SendStatus.valueOf(syncStatus)
        } catch (_: Exception) {
            SendStatus.PENDING
        }
        val tagList = if (selectedTagsCommaSeparated.isNotBlank()) {
            selectedTagsCommaSeparated.split(",").map { it.trim() }.toMutableList()
        } else {
            mutableListOf()
        }
        return ParsedTransaction(
            amount = amount,
            type = txType,
            rawMessage = body,
            sender = sender,
            timestamp = smsTimestamp,
            description = description,
            status = sendSt,
            sourceAccountId = sourceAccountId,
            sourceAccountName = sourceAccountName,
            destinationAccountId = destinationAccountId,
            destinationAccountName = destinationAccountName,
            categoryName = categoryName,
            budgetId = budgetId,
            budgetName = budgetName,
            selectedTags = tagList
        )
    }

    private fun ParsedTransaction.toEntity(): SmsRecordEntity {
        val hash = SmsHasher.hash(sender, rawMessage)
        return SmsRecordEntity(
            smsHash = hash,
            sender = sender,
            body = rawMessage,
            smsTimestamp = timestamp,
            amount = effectiveAmount,
            transactionType = effectiveType.name,
            description = description,
            syncStatus = status.name,
            sourceAccountId = sourceAccountId,
            sourceAccountName = sourceAccountName,
            destinationAccountId = destinationAccountId,
            destinationAccountName = destinationAccountName,
            categoryName = categoryName,
            budgetId = budgetId,
            budgetName = budgetName,
            selectedTagsCommaSeparated = selectedTags.joinToString(",")
        )
    }
}
