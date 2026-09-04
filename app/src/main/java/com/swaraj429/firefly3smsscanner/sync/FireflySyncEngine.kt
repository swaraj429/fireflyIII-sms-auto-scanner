package com.swaraj429.firefly3smsscanner.sync

import com.swaraj429.firefly3smsscanner.db.SmsRecordDao
import com.swaraj429.firefly3smsscanner.db.SmsRecordEntity
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.FireflyTransactionJournal
import com.swaraj429.firefly3smsscanner.network.FireflyApi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Core engine for bi-directional reconciliation between Firefly III and local Room DB.
 *
 * Plain Kotlin class without Android ViewModel / lifecycle dependencies so it can
 * be invoked from ViewModels, WorkManager workers, or background receivers.
 */
class FireflySyncEngine(
    private val dao: SmsRecordDao,
    private val api: FireflyApi
) {
    companion object {
        private const val TAG = "FireflySyncEngine"
        private val HASH_PATTERN = Regex("""smsHash=([a-f0-9]{64})""")

        fun extractHashFromNotes(notes: String?): String? {
            if (notes == null) return null
            return HASH_PATTERN.find(notes)?.groupValues?.get(1)
        }
    }

    data class SyncResult(
        val matched: Int,
        val totalRemote: Int,
        val totalLocal: Int,
        val newlyReconciled: Int,  // PENDING / FAILED -> SENT
        val updated: Int,          // SENT records refreshed with remote edits
        val errors: List<String> = emptyList()
    )

    data class RemoteTransactionWithGroup(
        val groupId: String,
        val journal: FireflyTransactionJournal
    )

    /**
     * Run full reconciliation for transactions between [startDate] and [endDate] (format "yyyy-MM-dd").
     *
     * [cutoffMillis] determines how far back to query local records.
     */
    suspend fun reconcile(
        startDate: String,
        endDate: String,
        cutoffMillis: Long
    ): SyncResult {
        DebugLog.log(TAG, "Starting reconciliation from $startDate to $endDate (cutoff: $cutoffMillis)")
        val errors = mutableListOf<String>()

        // 1. Fetch local records in range
        val localRecords = try {
            dao.getRecordsSince(cutoffMillis)
        } catch (e: Exception) {
            val err = "Failed to query local records: ${e.message}"
            DebugLog.log(TAG, err)
            return SyncResult(0, 0, 0, 0, 0, listOf(err))
        }
        val localByHash = localRecords.associateBy { it.smsHash }.toMutableMap()
        DebugLog.log(TAG, "Found ${localRecords.size} local records in sync range")

        // 2. Fetch remote transactions across all pages
        val remoteTransactions = fetchAllRemote(startDate, endDate, errors)
        DebugLog.log(TAG, "Fetched ${remoteTransactions.size} remote transactions from Firefly")

        var matched = 0
        var newlyReconciled = 0
        var updated = 0
        val matchedHashes = mutableSetOf<String>()

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // 3. Primary pass: match by smsHash in notes
        for (remoteItem in remoteTransactions) {
            val groupId = remoteItem.groupId
            val journal = remoteItem.journal
            val hash = extractHashFromNotes(journal.notes) ?: continue

            val localRecord = localByHash[hash] ?: continue
            matchedHashes.add(hash)
            matched++

            val remoteDesc = journal.description.ifBlank { null }
            val remoteTags = journal.tags?.takeIf { it.isNotEmpty() }?.joinToString(",")
            val remoteCat = journal.categoryName?.ifBlank { null }
            val remoteSourceId = journal.sourceId?.ifBlank { null }
            val remoteSourceName = journal.sourceName?.ifBlank { null }
            val remoteDestId = journal.destinationId?.ifBlank { null }
            val remoteDestName = journal.destinationName?.ifBlank { null }
            val remoteBudgetId = journal.budgetId?.ifBlank { null }
            val remoteBudgetName = journal.budgetName?.ifBlank { null }
            val remoteType = when (journal.type.lowercase()) {
                "withdrawal" -> "WITHDRAWAL"
                "deposit" -> "DEPOSIT"
                "transfer" -> "TRANSFER"
                else -> null
            }

            try {
                dao.updateFromFirefly(
                    hash = hash,
                    fireflyGroupId = groupId,
                    fireflyJournalId = journal.transactionJournalId,
                    remoteDescription = remoteDesc,
                    remoteTags = remoteTags,
                    remoteCategory = remoteCat,
                    sourceAccountId = remoteSourceId,
                    sourceAccountName = remoteSourceName,
                    destinationAccountId = remoteDestId,
                    destinationAccountName = remoteDestName,
                    budgetId = remoteBudgetId,
                    budgetName = remoteBudgetName,
                    transactionType = remoteType,
                    now = now
                )
                if (localRecord.syncStatus != "SENT") {
                    newlyReconciled++
                    DebugLog.log(TAG, "Reconciled PENDING -> SENT for hash ${hash.take(8)} (#$groupId, dest=$remoteDestName, cat=$remoteCat)")
                } else {
                    updated++
                    DebugLog.log(TAG, "Updated SENT record for hash ${hash.take(8)} (#$groupId, dest=$remoteDestName, cat=$remoteCat)")
                }
            } catch (e: Exception) {
                val err = "Error updating hash $hash: ${e.message}"
                DebugLog.log(TAG, err)
                errors.add(err)
            }
        }

        // 4. Fallback pass: For remote transactions without hash in notes,
        // match on amount + date + SMS snippet in notes
        val unmatchedLocalPending = localByHash.values.filter {
            it.syncStatus != "SENT" && !matchedHashes.contains(it.smsHash)
        }.toMutableList()

        for (remoteItem in remoteTransactions) {
            val groupId = remoteItem.groupId
            val journal = remoteItem.journal
            if (extractHashFromNotes(journal.notes) != null) continue // already processed

            val remoteAmount = journal.amount.toDoubleOrNull() ?: continue
            val remoteDate = journal.date.take(10) // "YYYY-MM-DD"
            val remoteNotes = journal.notes ?: ""

            val matchIndex = unmatchedLocalPending.indexOfFirst { local ->
                val localAmount = local.amount
                val localDate = dateFormat.format(Date(local.smsTimestamp))
                val amountMatches = abs(localAmount - remoteAmount) < 0.01
                val dateMatches = localDate == remoteDate
                val bodySnippet = local.body.trim().take(40)
                val notesContainSnippet = bodySnippet.isNotEmpty() && remoteNotes.contains(bodySnippet)

                amountMatches && dateMatches && notesContainSnippet
            }

            if (matchIndex >= 0) {
                val matchedLocal = unmatchedLocalPending.removeAt(matchIndex)
                matchedHashes.add(matchedLocal.smsHash)
                matched++

                val remoteDesc = journal.description.ifBlank { null }
                val remoteTags = journal.tags?.takeIf { it.isNotEmpty() }?.joinToString(",")
                val remoteCat = journal.categoryName?.ifBlank { null }
                val remoteSourceId = journal.sourceId?.ifBlank { null }
                val remoteSourceName = journal.sourceName?.ifBlank { null }
                val remoteDestId = journal.destinationId?.ifBlank { null }
                val remoteDestName = journal.destinationName?.ifBlank { null }
                val remoteBudgetId = journal.budgetId?.ifBlank { null }
                val remoteBudgetName = journal.budgetName?.ifBlank { null }
                val remoteType = when (journal.type.lowercase()) {
                    "withdrawal" -> "WITHDRAWAL"
                    "deposit" -> "DEPOSIT"
                    "transfer" -> "TRANSFER"
                    else -> null
                }

                try {
                    dao.updateFromFirefly(
                        hash = matchedLocal.smsHash,
                        fireflyGroupId = groupId,
                        fireflyJournalId = journal.transactionJournalId,
                        remoteDescription = remoteDesc,
                        remoteTags = remoteTags,
                        remoteCategory = remoteCat,
                        sourceAccountId = remoteSourceId,
                        sourceAccountName = remoteSourceName,
                        destinationAccountId = remoteDestId,
                        destinationAccountName = remoteDestName,
                        budgetId = remoteBudgetId,
                        budgetName = remoteBudgetName,
                        transactionType = remoteType,
                        now = now
                    )
                    newlyReconciled++
                    DebugLog.log(TAG, "Fallback match PENDING -> SENT for hash ${matchedLocal.smsHash.take(8)} (#$groupId, dest=$remoteDestName)")
                } catch (e: Exception) {
                    val err = "Error updating fallback match for ${matchedLocal.smsHash}: ${e.message}"
                    DebugLog.log(TAG, err)
                    errors.add(err)
                }
            }
        }

        val result = SyncResult(
            matched = matched,
            totalRemote = remoteTransactions.size,
            totalLocal = localRecords.size,
            newlyReconciled = newlyReconciled,
            updated = updated,
            errors = errors
        )
        DebugLog.log(TAG, "Reconciliation finished: matched=$matched, newlyReconciled=$newlyReconciled, updated=$updated, errors=${errors.size}")
        return result
    }

    private suspend fun fetchAllRemote(
        startDate: String,
        endDate: String,
        errors: MutableList<String>
    ): List<RemoteTransactionWithGroup> {
        val result = mutableListOf<RemoteTransactionWithGroup>()
        var page = 1
        var totalPages = 1

        while (page <= totalPages) {
            try {
                val response = api.listTransactions(
                    start = startDate,
                    end = endDate,
                    page = page,
                    limit = 50
                )
                if (!response.isSuccessful) {
                    val err = "Fetch page $page failed: HTTP ${response.code()} ${response.message()}"
                    DebugLog.log(TAG, err)
                    errors.add(err)
                    break
                }
                val body = response.body()
                if (body == null) {
                    errors.add("Page $page response body was null")
                    break
                }

                for (group in body.data) {
                    for (journal in group.attributes.transactions) {
                        result.add(RemoteTransactionWithGroup(group.id, journal))
                    }
                }

                totalPages = body.meta?.pagination?.totalPages ?: 1
                page++

                if (page <= totalPages) {
                    delay(200) // Polite backpressure to Firefly server
                }
            } catch (e: Exception) {
                val err = "Exception fetching page $page: ${e.message}"
                DebugLog.log(TAG, err)
                errors.add(err)
                break
            }
        }
        return result
    }
}
