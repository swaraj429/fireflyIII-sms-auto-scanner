package com.swaraj429.firefly3smsscanner.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swaraj429.firefly3smsscanner.MainActivity
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.TransactionType

object NotificationHelper {

    const val CHANNEL_ID = "firefly_transactions"
    const val CHANNEL_NAME = "Transaction Alerts"

    // Action identifiers
    const val ACTION_REVIEW_TRANSACTION = "com.swaraj429.firefly3smsscanner.ACTION_REVIEW_TRANSACTION"
    const val ACTION_SEND_NOW = "com.swaraj429.firefly3smsscanner.ACTION_SEND_NOW"
    const val ACTION_DISMISS = "com.swaraj429.firefly3smsscanner.ACTION_DISMISS"

    // Extras keys
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_TYPE = "extra_type"       // "DEBIT" | "CREDIT" | "UNKNOWN"
    const val EXTRA_SENDER = "extra_sender"
    const val EXTRA_RAW_MESSAGE = "extra_raw_message"
    const val EXTRA_TIMESTAMP = "extra_timestamp"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected transaction SMS messages"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showTransactionNotification(
        context: Context,
        transaction: ParsedTransaction,
        notificationId: Int
    ) {
        val typeEmoji = when (transaction.effectiveType) {
            TransactionType.DEBIT -> "🔴"
            TransactionType.CREDIT -> "🟢"
            TransactionType.UNKNOWN -> "⚪"
        }
        val typeLabel = when (transaction.effectiveType) {
            TransactionType.DEBIT -> "Debit"
            TransactionType.CREDIT -> "Credit"
            TransactionType.UNKNOWN -> "Transaction"
        }

        val amountStr = "₹%.2f".format(transaction.effectiveAmount)
        val title = "$typeEmoji $typeLabel Detected — $amountStr"
        val text = "From ${transaction.sender}: ${transaction.rawMessage.take(80)}"

        // "Review & Edit" pending intent — opens MainActivity with transaction data for editing
        val reviewIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_REVIEW_TRANSACTION
            putExtra(EXTRA_AMOUNT, transaction.effectiveAmount)
            putExtra(EXTRA_TYPE, transaction.effectiveType.name)
            putExtra(EXTRA_SENDER, transaction.sender)
            putExtra(EXTRA_RAW_MESSAGE, transaction.rawMessage)
            putExtra(EXTRA_TIMESTAMP, transaction.timestamp)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val reviewPending = PendingIntent.getActivity(
            context, notificationId,
            reviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Send Now" pending intent — triggers background send via BroadcastReceiver
        val sendIntent = Intent(context, SmsReceiver::class.java).apply {
            action = ACTION_SEND_NOW
            putExtra(EXTRA_AMOUNT, transaction.effectiveAmount)
            putExtra(EXTRA_TYPE, transaction.effectiveType.name)
            putExtra(EXTRA_SENDER, transaction.sender)
            putExtra(EXTRA_RAW_MESSAGE, transaction.rawMessage)
            putExtra(EXTRA_TIMESTAMP, transaction.timestamp)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val sendPending = PendingIntent.getBroadcast(
            context, notificationId + 10000,
            sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Dismiss" pending intent
        val dismissIntent = Intent(context, SmsReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, notificationId + 20000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(reviewPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "⚡ Send to Firefly",
                sendPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "✋ Dismiss",
                dismissPending
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted on Android 13+
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
