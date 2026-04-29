package com.swaraj429.firefly3smsscanner.model

/**
 * Raw SMS data from ContentResolver
 */
data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val dateString: String
)
