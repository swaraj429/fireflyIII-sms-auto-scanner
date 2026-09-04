package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.*
import com.swaraj429.firefly3smsscanner.network.RetrofitClient
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import com.swaraj429.firefly3smsscanner.util.SmsHasher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TransactionVM"
    private val prefs = AppPrefs(application)

    var lastResult by mutableStateOf("")

    /**
     * Send a transaction to Firefly III and notify the history ViewModel
     * so it can update the sync status in the local DB.
     *
     * @param historyViewModel optional — when provided, the record in the
     *        Room DB will be marked as SENT or FAILED automatically.
     */
    fun sendTransaction(
        transaction: ParsedTransaction,
        historyViewModel: SmsHistoryViewModel? = null,
        onComplete: (Boolean) -> Unit
    ) {
        if (!prefs.isConfigured) {
            lastResult = "❌ Firefly not configured — go to Setup"
            DebugLog.log(TAG, "Cannot send — not configured")
            onComplete(false)
            return
        }

        transaction.status = SendStatus.SENDING
        DebugLog.log(TAG, "Sending transaction: ${transaction.effectiveAmount} ${transaction.effectiveType}")

        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(prefs.baseUrl, prefs.accessToken)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val dateStr = dateFormat.format(Date(transaction.timestamp))

                val fireflyType = transaction.effectiveType.toFireflyType()
                val description = transaction.description.ifBlank {
                    "SMS Transaction: ${transaction.rawMessage.take(100)}"
                }

                val hash = SmsHasher.hash(transaction.sender, transaction.rawMessage)

                val existingId = transaction.fireflyTransactionId
                val isUpdate = !existingId.isNullOrBlank()

                // Build transaction split with enriched metadata
                val split = when (fireflyType) {
                    "withdrawal" -> FireflyTransactionSplit(
                        type = fireflyType,
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId ?: prefs.accountId,
                        destinationId = transaction.destinationAccountId,
                        destinationName = transaction.destinationAccountName
                            ?: if (transaction.destinationAccountId == null) {
                                if (description.isNotBlank() && !description.startsWith("SMS Transaction:")) description else "SMS Expense"
                            } else null,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\nsmsHash=$hash\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId,
                        transactionJournalId = transaction.fireflyTransactionJournalId
                    )
                    "deposit" -> FireflyTransactionSplit(
                        type = fireflyType,
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId,
                        sourceName = transaction.sourceAccountName
                            ?: if (transaction.sourceAccountId == null) {
                                if (description.isNotBlank() && !description.startsWith("SMS Transaction:")) description else "SMS Income"
                            } else null,
                        destinationId = transaction.destinationAccountId ?: prefs.accountId,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\nsmsHash=$hash\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId,
                        transactionJournalId = transaction.fireflyTransactionJournalId
                    )
                    "transfer" -> FireflyTransactionSplit(
                        type = fireflyType,
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId ?: prefs.accountId,
                        destinationId = transaction.destinationAccountId,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\nsmsHash=$hash\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId,
                        transactionJournalId = transaction.fireflyTransactionJournalId
                    )
                    else -> FireflyTransactionSplit(
                        type = "withdrawal",
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId ?: prefs.accountId,
                        destinationName = if (description.isNotBlank() && !description.startsWith("SMS Transaction:")) description else "SMS Expense",
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\nsmsHash=$hash\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId,
                        transactionJournalId = transaction.fireflyTransactionJournalId
                    )
                }

                val request = FireflyTransactionRequest(
                    transactions = listOf(split)
                )

                val response = if (isUpdate) {
                    DebugLog.log(TAG, "PUT /api/v1/transactions/$existingId — type=$fireflyType, amount=${transaction.effectiveAmount}" +
                            ", category=${transaction.categoryName}, tags=${transaction.selectedTags}")
                    val updateResp = api.updateTransaction(existingId!!, request)
                    if (!updateResp.isSuccessful && updateResp.code() == 404) {
                        DebugLog.log(TAG, "Transaction #$existingId not found on Firefly (404), falling back to POST create")
                        api.createTransaction(request)
                    } else {
                        updateResp
                    }
                } else {
                    DebugLog.log(TAG, "POST /api/v1/transactions — type=$fireflyType, amount=${transaction.effectiveAmount}" +
                            ", category=${transaction.categoryName}, tags=${transaction.selectedTags}")
                    api.createTransaction(request)
                }

                if (response.isSuccessful) {
                    val id = response.body()?.data?.id ?: existingId ?: "?"
                    transaction.status = SendStatus.SENT
                    transaction.fireflyTransactionId = id
                    lastResult = if (isUpdate) "✅ Updated transaction #$id" else "✅ Created transaction #$id"
                    DebugLog.log(TAG, "Transaction ${if (isUpdate) "updated" else "created"} successfully: #$id")

                    // Update the history record in Room
                    historyViewModel?.markSent(transaction, id)

                    onComplete(true)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    transaction.status = SendStatus.FAILED
                    lastResult = "❌ HTTP ${response.code()}: ${errorBody.take(200)}"
                    DebugLog.log(TAG, "Transaction FAILED: ${response.code()} - $errorBody")

                    // Update the history record in Room
                    historyViewModel?.markFailed(transaction)

                    onComplete(false)
                }
            } catch (e: Exception) {
                transaction.status = SendStatus.FAILED
                lastResult = "❌ Error: ${e.message}"
                DebugLog.log(TAG, "Transaction ERROR: ${e.message}")
                Log.e(TAG, "Failed to create transaction", e)

                // Update the history record in Room
                historyViewModel?.markFailed(transaction)

                onComplete(false)
            }
        }
    }
}
