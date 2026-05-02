package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import com.swaraj429.firefly3smsscanner.parser.SmsParser
import com.swaraj429.firefly3smsscanner.sms.SmsReader
import java.util.*

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SmsVM"

    val smsMessages = mutableStateListOf<SmsMessage>()
    val parsedTransactions = mutableStateListOf<ParsedTransaction>()
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("")
    var usingSampleData by mutableStateOf(false)

    // Date range state — default to last 7 days
    var fromDate by mutableStateOf(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
    )
    var toDate by mutableStateOf(System.currentTimeMillis())

    fun loadSmsByDateRange() {
        isLoading = true
        statusMessage = "Reading SMS..."
        DebugLog.log(TAG, "Loading SMS by date range...")

        try {
            val contentResolver = getApplication<Application>().contentResolver
            val messages = SmsReader.readMessagesByDateRange(
                contentResolver,
                fromDate = fromDate,
                toDate = toDate
            )

            smsMessages.clear()
            smsMessages.addAll(messages)

            statusMessage = "Loaded ${messages.size} messages"
            usingSampleData = false
            DebugLog.log(TAG, "Loaded ${messages.size} SMS messages for date range")
        } catch (e: Exception) {
            statusMessage = "❌ Error: ${e.message}"
            DebugLog.log(TAG, "ERROR loading SMS: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    /**
     * Legacy method kept for backward compatibility
     */
    fun loadSms() {
        loadSmsByDateRange()
    }

    fun loadSampleSms() {
        DebugLog.log(TAG, "Loading sample SMS data for testing...")

        val samples = SmsParser.getSampleMessages()
        smsMessages.clear()
        smsMessages.addAll(samples)

        statusMessage = "Loaded ${samples.size} sample messages"
        usingSampleData = true
        DebugLog.log(TAG, "Loaded ${samples.size} sample messages")
    }

    fun parseMessages(accounts: List<com.swaraj429.firefly3smsscanner.model.FireflyAccount> = emptyList()) {
        DebugLog.log(TAG, "Parsing ${smsMessages.size} messages...")

        val results = SmsParser.parseAll(smsMessages)
        val matcher = com.swaraj429.firefly3smsscanner.parser.AccountMatcher()

        results.forEach { txn ->
            val match = matcher.findBestMatch(txn.rawMessage, accounts)
            if (match != null) {
                // Determine source or destination based on transaction type
                if (txn.effectiveType == com.swaraj429.firefly3smsscanner.model.TransactionType.DEBIT) {
                    txn.sourceAccountId = match.account.id
                    txn.sourceAccountName = match.account.name
                } else if (txn.effectiveType == com.swaraj429.firefly3smsscanner.model.TransactionType.CREDIT) {
                    txn.destinationAccountId = match.account.id
                    txn.destinationAccountName = match.account.name
                }
            }
        }

        parsedTransactions.clear()
        parsedTransactions.addAll(results)

        statusMessage = "Parsed ${results.size}/${smsMessages.size} messages"
        DebugLog.log(TAG, "Parse complete: ${results.size}/${smsMessages.size}")
    }

    /**
     * Called when the user taps a transaction notification.
     * Inserts the transaction at the top of the list so it's immediately visible
     * on the Transactions screen.
     * Returns true if it was a new transaction (not a duplicate).
     */
    fun addTransactionFromNotification(transaction: ParsedTransaction): Boolean {
        // Avoid duplicates: same timestamp + amount already in list
        val isDuplicate = parsedTransactions.any {
            it.timestamp == transaction.timestamp && it.amount == transaction.amount
        }
        if (isDuplicate) {
            DebugLog.log(TAG, "Notification transaction already in list, skipping duplicate")
            return false
        }
        // Prepend so it appears at top
        parsedTransactions.add(0, transaction)
        statusMessage = "📩 New transaction from notification"
        DebugLog.log(TAG, "Added notification transaction: ₹${transaction.effectiveAmount} ${transaction.effectiveType}")
        return true
    }
}
