package com.swaraj429.firefly3smsscanner.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads SMS from ContentResolver. Requires READ_SMS permission.
 */
object SmsReader {
    private const val TAG = "SmsReader"

    /**
     * Read SMS messages within a date range.
     * @param fromDate start timestamp (inclusive), null = no lower bound
     * @param toDate end timestamp (inclusive), null = no upper bound
     */
    fun readMessagesByDateRange(
        contentResolver: ContentResolver,
        fromDate: Long? = null,
        toDate: Long? = null
    ): List<SmsMessage> {
        val rangeDesc = buildString {
            append("Reading SMS")
            if (fromDate != null || toDate != null) {
                append(" from ${fromDate?.let { formatDate(it) } ?: "beginning"}")
                append(" to ${toDate?.let { formatDate(it) } ?: "now"}")
            } else {
                append(" (all)")
            }
        }
        DebugLog.log(TAG, rangeDesc)

        val messages = mutableListOf<SmsMessage>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Build selection clause for date range
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (fromDate != null) {
            selectionParts.add("${Telephony.Sms.DATE} >= ?")
            selectionArgs.add(fromDate.toString())
        }
        if (toDate != null) {
            selectionParts.add("${Telephony.Sms.DATE} <= ?")
            selectionArgs.add(toDate.toString())
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                selection,
                args,
                "${Telephony.Sms.DATE} DESC"
            )

            if (cursor == null) {
                DebugLog.log(TAG, "ERROR: Cursor is null — SMS permission missing?")
                return emptyList()
            }

            DebugLog.log(TAG, "Query returned ${cursor.count} messages")

            while (cursor.moveToNext()) {
                val sender = cursor.getString(0) ?: "Unknown"
                val body = cursor.getString(1) ?: ""
                val timestamp = cursor.getLong(2)
                val dateString = dateFormat.format(Date(timestamp))

                messages.add(
                    SmsMessage(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        dateString = dateString
                    )
                )
            }

            DebugLog.log(TAG, "Read ${messages.size} SMS messages successfully")

        } catch (e: Exception) {
            DebugLog.log(TAG, "ERROR reading SMS: ${e.message}")
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return messages
    }

    /**
     * Legacy method — reads last N messages (kept for backward compat)
     */
    fun readLastMessages(
        contentResolver: ContentResolver,
        count: Int = 50
    ): List<SmsMessage> {
        DebugLog.log(TAG, "Reading last $count SMS messages...")

        val messages = mutableListOf<SmsMessage>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            if (cursor == null) {
                DebugLog.log(TAG, "ERROR: Cursor is null — SMS permission missing?")
                return emptyList()
            }

            DebugLog.log(TAG, "Cursor has ${cursor.count} total messages")

            var readCount = 0
            while (cursor.moveToNext() && readCount < count) {
                val sender = cursor.getString(0) ?: "Unknown"
                val body = cursor.getString(1) ?: ""
                val timestamp = cursor.getLong(2)
                val dateString = dateFormat.format(Date(timestamp))

                messages.add(
                    SmsMessage(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        dateString = dateString
                    )
                )
                readCount++
            }

            DebugLog.log(TAG, "Read $readCount SMS messages successfully")

        } catch (e: Exception) {
            DebugLog.log(TAG, "ERROR reading SMS: ${e.message}")
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return messages
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
