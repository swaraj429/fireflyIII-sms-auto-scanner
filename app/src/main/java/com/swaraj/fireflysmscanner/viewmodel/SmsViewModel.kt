package com.swaraj.fireflysmscanner.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.swaraj.fireflysmscanner.debug.DebugLog
import com.swaraj.fireflysmscanner.model.ParsedTransaction
import com.swaraj.fireflysmscanner.model.SmsMessage
import com.swaraj.fireflysmscanner.parser.SmsParser
import com.swaraj.fireflysmscanner.sms.SmsReader

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SmsVM"

    val smsMessages = mutableStateListOf<SmsMessage>()
    val parsedTransactions = mutableStateListOf<ParsedTransaction>()
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("")
    var usingSampleData by mutableStateOf(false)

    fun loadSms() {
        isLoading = true
        statusMessage = "Reading SMS..."
        DebugLog.log(TAG, "Loading SMS from device...")

        try {
            val contentResolver = getApplication<Application>().contentResolver
            val messages = SmsReader.readLastMessages(contentResolver, 50)

            smsMessages.clear()
            smsMessages.addAll(messages)

            statusMessage = "Loaded ${messages.size} messages"
            usingSampleData = false
            DebugLog.log(TAG, "Loaded ${messages.size} SMS messages")
        } catch (e: Exception) {
            statusMessage = "❌ Error: ${e.message}"
            DebugLog.log(TAG, "ERROR loading SMS: ${e.message}")
        } finally {
            isLoading = false
        }
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

    fun parseMessages() {
        DebugLog.log(TAG, "Parsing ${smsMessages.size} messages...")

        val results = SmsParser.parseAll(smsMessages)

        parsedTransactions.clear()
        parsedTransactions.addAll(results)

        statusMessage = "Parsed ${results.size}/${smsMessages.size} messages"
        DebugLog.log(TAG, "Parse complete: ${results.size}/${smsMessages.size}")
    }
}
