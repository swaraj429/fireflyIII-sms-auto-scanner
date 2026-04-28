package com.swaraj.fireflysmscanner.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.swaraj.fireflysmscanner.debug.DebugLog
import com.swaraj.fireflysmscanner.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads SMS from ContentResolver. Requires READ_SMS permission.
 */
object SmsReader {
    private const val TAG = "SmsReader"

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
}
