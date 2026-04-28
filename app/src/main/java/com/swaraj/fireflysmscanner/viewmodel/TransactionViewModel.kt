package com.swaraj.fireflysmscanner.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj.fireflysmscanner.debug.DebugLog
import com.swaraj.fireflysmscanner.model.*
import com.swaraj.fireflysmscanner.network.RetrofitClient
import com.swaraj.fireflysmscanner.prefs.AppPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TransactionVM"
    private val prefs = AppPrefs(application)

    var lastResult by mutableStateOf("")

    fun sendTransaction(transaction: ParsedTransaction, onComplete: (Boolean) -> Unit) {
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

                // Build transaction split
                val split = if (fireflyType == "withdrawal") {
                    FireflyTransactionSplit(
                        type = fireflyType,
                        description = "SMS Transaction: ${transaction.rawMessage.take(100)}",
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceId = prefs.accountId,
                        destinationName = "SMS Expense",
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\n${transaction.rawMessage}"
                    )
                } else {
                    FireflyTransactionSplit(
                        type = fireflyType,
                        description = "SMS Transaction: ${transaction.rawMessage.take(100)}",
                        amount = String.format(Locale.US, "%.2f", transaction.effectiveAmount),
                        sourceName = "SMS Income",
                        destinationId = prefs.accountId,
                        date = dateStr,
                        notes = "Auto-parsed from SMS:\n${transaction.rawMessage}"
                    )
                }

                val request = FireflyTransactionRequest(
                    transactions = listOf(split)
                )

                DebugLog.log(TAG, "POST /api/v1/transactions — type=$fireflyType, amount=${transaction.effectiveAmount}")

                val response = api.createTransaction(request)

                if (response.isSuccessful) {
                    val id = response.body()?.data?.id ?: "?"
                    transaction.status = SendStatus.SENT
                    lastResult = "✅ Created transaction #$id"
                    DebugLog.log(TAG, "Transaction created successfully: #$id")
                    onComplete(true)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    transaction.status = SendStatus.FAILED
                    lastResult = "❌ HTTP ${response.code()}: ${errorBody.take(200)}"
                    DebugLog.log(TAG, "Transaction FAILED: ${response.code()} - $errorBody")
                    onComplete(false)
                }
            } catch (e: Exception) {
                transaction.status = SendStatus.FAILED
                lastResult = "❌ Error: ${e.message}"
                DebugLog.log(TAG, "Transaction ERROR: ${e.message}")
                Log.e(TAG, "Failed to create transaction", e)
                onComplete(false)
            }
        }
    }
}
