package com.swaraj429.firefly3smsscanner.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swaraj429.firefly3smsscanner.MainActivity
import com.swaraj429.firefly3smsscanner.db.FireflyDatabase
import com.swaraj429.firefly3smsscanner.db.SmsRecordEntity
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.swaraj429.firefly3smsscanner.model.FireflyTransactionRequest
import com.swaraj429.firefly3smsscanner.model.FireflyTransactionSplit
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import com.swaraj429.firefly3smsscanner.model.SendStatus
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.network.RetrofitClient
import com.swaraj429.firefly3smsscanner.parser.RuleEngine
import com.swaraj429.firefly3smsscanner.parser.SmsParser
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import com.swaraj429.firefly3smsscanner.util.SmsHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dual-purpose BroadcastReceiver:
 *   1. Receives live incoming SMS → parses for transactions → shows notification
 *   2. Handles "Send Now" notification action → sends transaction to Firefly in background
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private val notificationCounter = AtomicInteger(1000)
        fun nextNotificationId() = notificationCounter.incrementAndGet()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleIncomingSms(context, intent)
            NotificationHelper.ACTION_SEND_NOW -> handleSendNow(context, intent)
            NotificationHelper.ACTION_DISMISS -> {
                val id = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
                if (id != -1) NotificationHelper.cancelNotification(context, id)
            }
        }
    }

    // ─── Incoming SMS ─────────────────────────────────────────────────────────

    private fun handleIncomingSms(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress ?: continue
            val body = smsMessage.messageBody ?: continue
            val timestamp = smsMessage.timestampMillis

            DebugLog.log(TAG, "SMS received from $sender: ${body.take(60)}...")

            val sms = SmsMessage(
                sender = sender,
                body = body,
                timestamp = timestamp,
                dateString = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(Date(timestamp))
            )

            val transaction = SmsParser.parse(sms)
            if (transaction == null) {
                DebugLog.log(TAG, "  → Not a transaction SMS, skipping")
                continue
            }

            // Load and apply smart rules
            val rulesPrefs = context.getSharedPreferences("firefly_rules", Context.MODE_PRIVATE)
            val rulesJson = rulesPrefs.getString("rules_json", null)
            val rules: List<ParsingRule> = if (rulesJson != null) {
                try {
                    val type = object : TypeToken<List<ParsingRule>>() {}.type
                    Gson().fromJson(rulesJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            RuleEngine.applyRules(transaction, rules)

            DebugLog.log(TAG, "  → Transaction: ₹${transaction.effectiveAmount} ${transaction.effectiveType}, autoSend=${transaction.autoSendToFirefly}, vendor=${transaction.vendor}")

            val prefs = AppPrefs(context)
            if (transaction.autoSendToFirefly) {
                if (prefs.isConfigured) {
                    autoSendTransaction(context, transaction)
                } else {
                    DebugLog.log(TAG, "Auto-send rule matched but Firefly not configured")
                    savePendingToDb(context, transaction)
                    NotificationHelper.showTransactionNotification(context, transaction, nextNotificationId())
                }
            } else {
                savePendingToDb(context, transaction)
                NotificationHelper.showTransactionNotification(context, transaction, nextNotificationId())
            }
        }
    }

    private fun savePendingToDb(context: Context, transaction: ParsedTransaction) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FireflyDatabase.getDatabase(context)
                val smsDao = db.smsRecordDao()
                val hash = SmsHasher.hash(transaction.sender, transaction.rawMessage)
                smsDao.insertRecord(
                    SmsRecordEntity(
                        smsHash = hash,
                        sender = transaction.sender,
                        body = transaction.rawMessage,
                        smsTimestamp = transaction.timestamp,
                        amount = transaction.effectiveAmount,
                        transactionType = transaction.effectiveType.name,
                        vendor = transaction.vendor,
                        description = transaction.description,
                        categoryName = transaction.categoryName,
                        destinationAccountId = transaction.destinationAccountId,
                        destinationAccountName = transaction.destinationAccountName,
                        selectedTagsCommaSeparated = transaction.selectedTags.joinToString(","),
                        syncStatus = "PENDING"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving pending record", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun autoSendTransaction(context: Context, transaction: ParsedTransaction) {
        val pendingResult = goAsync()
        val prefs = AppPrefs(context)

        CoroutineScope(Dispatchers.IO).launch {
            val db = FireflyDatabase.getDatabase(context)
            val smsDao = db.smsRecordDao()
            val hash = SmsHasher.hash(transaction.sender, transaction.rawMessage)

            // Ensure record exists
            smsDao.insertRecord(
                SmsRecordEntity(
                    smsHash = hash,
                    sender = transaction.sender,
                    body = transaction.rawMessage,
                    smsTimestamp = transaction.timestamp,
                    amount = transaction.effectiveAmount,
                    transactionType = transaction.effectiveType.name,
                    vendor = transaction.vendor,
                    description = transaction.description,
                    categoryName = transaction.categoryName,
                    destinationAccountId = transaction.destinationAccountId,
                    destinationAccountName = transaction.destinationAccountName,
                    selectedTagsCommaSeparated = transaction.selectedTags.joinToString(","),
                    syncStatus = "PENDING"
                )
            )

            try {
                val api = RetrofitClient.create(prefs.baseUrl, prefs.accessToken)
                val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    .format(Date(transaction.timestamp))
                val fireflyType = transaction.effectiveType.toFireflyType()
                val amountStr = "%.2f".format(transaction.effectiveAmount)
                val desc = transaction.description.ifBlank { "SMS: ${transaction.rawMessage.take(100)}" }

                val split = if (fireflyType == "withdrawal") {
                    FireflyTransactionSplit(
                        type = fireflyType,
                        description = desc,
                        amount = amountStr,
                        sourceId = prefs.accountId,
                        destinationId = transaction.destinationAccountId,
                        destinationName = if (transaction.destinationAccountId == null) {
                            transaction.destinationAccountName?.ifBlank { null } ?: transaction.vendor ?: "SMS Expense"
                        } else null,
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        date = dateStr,
                        notes = "Auto-sent via Rule [${transaction.matchedRuleKeyword ?: "Rule"}]:\nsmsHash=$hash\n${transaction.rawMessage}"
                    )
                } else {
                    FireflyTransactionSplit(
                        type = fireflyType,
                        description = desc,
                        amount = amountStr,
                        sourceName = transaction.vendor ?: "SMS Income",
                        destinationId = prefs.accountId,
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.ifEmpty { null },
                        date = dateStr,
                        notes = "Auto-sent via Rule [${transaction.matchedRuleKeyword ?: "Rule"}]:\nsmsHash=$hash\n${transaction.rawMessage}"
                    )
                }

                val response = api.createTransaction(
                    FireflyTransactionRequest(transactions = listOf(split))
                )

                if (response.isSuccessful) {
                    val id = response.body()?.data?.id ?: "?"
                    smsDao.markSentWithMetadata(
                        hash = hash,
                        fireflyId = id,
                        amount = transaction.effectiveAmount,
                        transactionType = transaction.effectiveType.name,
                        description = desc,
                        categoryName = transaction.categoryName,
                        tags = transaction.selectedTags.joinToString(","),
                        sourceAccountId = prefs.accountId,
                        sourceAccountName = null,
                        destinationAccountId = transaction.destinationAccountId,
                        destinationAccountName = transaction.destinationAccountName,
                        budgetId = null,
                        budgetName = null
                    )
                    val msg = "⚡ Auto-sent: $desc — ₹$amountStr (#$id)"
                    DebugLog.log(TAG, msg)
                    showResultNotification(context, msg, nextNotificationId())
                } else {
                    smsDao.markFailed(hash)
                    val err = response.errorBody()?.string()?.take(150) ?: "HTTP ${response.code()}"
                    val msg = "❌ Auto-send failed: $err"
                    DebugLog.log(TAG, msg)
                    showResultNotification(context, msg, nextNotificationId())
                }
            } catch (e: Exception) {
                smsDao.markFailed(hash)
                val msg = "❌ Auto-send error: ${e.message}"
                DebugLog.log(TAG, msg)
                Log.e(TAG, "Auto-send failed", e)
                showResultNotification(context, msg, nextNotificationId())
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ─── "Send Now" from notification action ─────────────────────────────────

    private fun handleSendNow(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val amount = intent.getDoubleExtra(NotificationHelper.EXTRA_AMOUNT, 0.0)
        val typeStr = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: "UNKNOWN"
        val sender = intent.getStringExtra(NotificationHelper.EXTRA_SENDER) ?: ""
        val rawMessage = intent.getStringExtra(NotificationHelper.EXTRA_RAW_MESSAGE) ?: ""
        val timestamp = intent.getLongExtra(NotificationHelper.EXTRA_TIMESTAMP, System.currentTimeMillis())

        val type = try {
            TransactionType.valueOf(typeStr)
        } catch (e: Exception) {
            TransactionType.WITHDRAWAL
        }

        DebugLog.log(TAG, "Send Now: ₹$amount $type from notification #$notifId")

        if (notifId != -1) NotificationHelper.cancelNotification(context, notifId)

        val prefs = AppPrefs(context)
        if (!prefs.isConfigured) {
            DebugLog.log(TAG, "Not configured — cannot auto-send")
            showResultNotification(
                context,
                "❌ Firefly not configured. Open the app to set up your connection.",
                nextNotificationId()
            )
            return
        }

        // goAsync() lets us run a coroutine without the system killing the receiver
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            val db = FireflyDatabase.getDatabase(context)
            val smsDao = db.smsRecordDao()
            val hash = SmsHasher.hash(sender, rawMessage)

            // Ensure the record exists in Room (covers case where Send Now tapped before app scanned)
            smsDao.insertRecord(
                SmsRecordEntity(
                    smsHash = hash,
                    sender = sender,
                    body = rawMessage,
                    smsTimestamp = timestamp,
                    amount = amount,
                    transactionType = type.name,
                    description = "SMS: ${rawMessage.take(100)}",
                    syncStatus = "PENDING"
                )
            )

            try {
                val api = RetrofitClient.create(prefs.baseUrl, prefs.accessToken)
                val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    .format(Date(timestamp))
                val fireflyType = type.toFireflyType()
                val amountStr = "%.2f".format(amount)

                val split = if (fireflyType == "withdrawal") {
                    FireflyTransactionSplit(
                        type = fireflyType,
                        description = "SMS: ${rawMessage.take(100)}",
                        amount = amountStr,
                        sourceId = prefs.accountId,
                        destinationName = "SMS Expense",
                        date = dateStr,
                        notes = "Auto-sent from notification:\nsmsHash=$hash\n$rawMessage"
                    )
                } else {
                    FireflyTransactionSplit(
                        type = fireflyType,
                        description = "SMS: ${rawMessage.take(100)}",
                        amount = amountStr,
                        sourceName = "SMS Income",
                        destinationId = prefs.accountId,
                        date = dateStr,
                        notes = "Auto-sent from notification:\nsmsHash=$hash\n$rawMessage"
                    )
                }

                val response = api.createTransaction(
                    FireflyTransactionRequest(transactions = listOf(split))
                )

                if (response.isSuccessful) {
                    val id = response.body()?.data?.id ?: "?"
                    smsDao.markSent(hash, id)
                    val msg = "✅ ₹$amountStr ${type.name.lowercase()} added to Firefly (#$id)"
                    DebugLog.log(TAG, msg)
                    showResultNotification(context, msg, nextNotificationId())
                } else {
                    smsDao.markFailed(hash)
                    val err = response.errorBody()?.string()?.take(150) ?: "Unknown error"
                    val msg = "❌ Send failed (${response.code()}): $err"
                    DebugLog.log(TAG, msg)
                    showResultNotification(context, msg, nextNotificationId())
                }
            } catch (e: Exception) {
                smsDao.markFailed(hash)
                val msg = "❌ Error: ${e.message}"
                DebugLog.log(TAG, msg)
                Log.e(TAG, "Auto-send failed", e)
                showResultNotification(context, msg, nextNotificationId())
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ─── Result notification (success / failure) ──────────────────────────────

    private fun showResultNotification(context: Context, message: String, notifId: Int) {
        val isSuccess = message.startsWith("✅")
        val title = if (isSuccess) "Firefly — Transaction Added ✓" else "Firefly — Send Failed"

        // Tap → open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(
                if (isSuccess) android.R.drawable.ic_dialog_info
                else android.R.drawable.ic_dialog_alert
            )
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — silently ignore
        }
    }
}
