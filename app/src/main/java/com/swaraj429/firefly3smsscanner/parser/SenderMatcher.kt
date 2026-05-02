package com.swaraj429.firefly3smsscanner.parser

import android.content.Context
import com.swaraj429.firefly3smsscanner.model.AppDatabase
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SenderConfig
import com.swaraj429.firefly3smsscanner.model.TransactionType

object SenderMatcher {
    suspend fun applyConfigMatch(context: Context, transaction: ParsedTransaction) {
        val db = AppDatabase.getDatabase(context)
        val configs = db.senderConfigDao().getActiveConfigs()

        val matchedConfig = findBestMatch(transaction.sender, configs)
        if (matchedConfig != null) {
            applyConfigToTransaction(transaction, matchedConfig)
        }
    }

    private fun findBestMatch(sender: String, configs: List<SenderConfig>): SenderConfig? {
        val normalizedSender = sender.trim().uppercase()

        // 1. Exact match
        val exactMatch = configs.find { it.senderPattern.trim().uppercase() == normalizedSender }
        if (exactMatch != null) return exactMatch

        // 2. Regex/Pattern match
        val regexMatch = configs.find {
            try {
                val regex = Regex(it.senderPattern, RegexOption.IGNORE_CASE)
                regex.matches(sender) || regex.containsMatchIn(sender)
            } catch (e: Exception) {
                false
            }
        }
        return regexMatch
    }

    private fun applyConfigToTransaction(transaction: ParsedTransaction, config: SenderConfig) {
        val fireflyType = config.transactionType.lowercase()

        // Set type (withdrawal, deposit, transfer)
        when (fireflyType) {
            "withdrawal" -> transaction.correctedType = TransactionType.DEBIT
            "deposit" -> transaction.correctedType = TransactionType.CREDIT
            "transfer" -> {
                // If we need a new enum value TRANSFER we can add it, 
                // but DEBIT is usually mapped to withdrawal and CREDIT to deposit.
                // We'll just map transfer to DEBIT for local UI, and maybe rely on source/dest fields.
            }
        }

        // Set account ID based on type
        when (fireflyType) {
            "withdrawal" -> transaction.sourceAccountId = config.accountId
            "deposit" -> transaction.destinationAccountId = config.accountId
            "transfer" -> {
                // For transfer, we don't know the other account without more context, 
                // but we can set sourceAccountId to this account.
                transaction.sourceAccountId = config.accountId
            }
        }

        // Description Template (Replace placeholders if needed, e.g., {amount})
        if (config.descriptionTemplate.isNotBlank()) {
            transaction.description = config.descriptionTemplate
                .replace("{amount}", transaction.amount.toString())
                .replace("{sender}", transaction.sender)
        }

        // Tags
        if (config.tags.isNotEmpty()) {
            transaction.selectedTags.clear()
            transaction.selectedTags.addAll(config.tags)
        }

        // Category
        if (config.category.isNotBlank()) {
            transaction.categoryName = config.category
        }
    }
}
