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

                // Build transaction split with enriched metadata
                val split = when (fireflyType) {
                    "withdrawal" -> FireflyTransactionSplit(
                        type = fireflyType,
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId ?: prefs.accountId,
                        destinationId = transaction.destinationAccountId,
                        destinationName = transaction.destinationAccountName
                            ?: if (transaction.destinationAccountId == null) "SMS Expense" else null,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId
                    )
                    "deposit" -> FireflyTransactionSplit(
                        type = fireflyType,
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId,
                        sourceName = transaction.sourceAccountName
                            ?: if (transaction.sourceAccountId == null) "SMS Income" else null,
                        destinationId = transaction.destinationAccountId ?: prefs.accountId,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId
                    )
                    "transfer" -> FireflyTransactionSplit(
                        type = fireflyType,
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId ?: prefs.accountId,
                        destinationId = transaction.destinationAccountId,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId
                    )
                    else -> FireflyTransactionSplit(
                        type = "withdrawal",
                        description = description,
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = transaction.sourceAccountId ?: prefs.accountId,
                        destinationName = "SMS Expense",
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\n${transaction.rawMessage}",
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        budgetId = transaction.budgetId
                    )
                }

                val request = FireflyTransactionRequest(
                    transactions = listOf(split)
                )

                DebugLog.log(TAG, "POST /api/v1/transactions — type=$fireflyType, amount=${transaction.effectiveAmount}" +
                        ", category=${transaction.categoryName}, tags=${transaction.selectedTags}")

                val response = api.createTransaction(request)

                if (response.isSuccessful) {
                    val id = response.body()?.data?.id ?: "?"
                    transaction.status = SendStatus.SENT
                    lastResult = "✅ Created transaction #$id"
                    DebugLog.log(TAG, "Transaction created successfully: #$id")

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
