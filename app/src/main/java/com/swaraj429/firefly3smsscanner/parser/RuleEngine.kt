package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rule Engine — matches SMS content against user-defined rules and
 * fills transaction metadata (category, destination account, tags,
 * interpolated description templates, and auto-send directives).
 *
 * Built-in auto-categorization is disabled per configuration so that
 * Firefly III rules handle categorization on the server side.
 * User-defined custom rules in the Rules tab are evaluated in order.
 */
object RuleEngine {
    private const val TAG = "RuleEngine"

    /**
     * Apply all matching user rules to a [ParsedTransaction].
     * Fills metadata, interpolates description templates, and activates auto-send flags.
     *
     * @return true if at least one rule matched
     */
    fun applyRules(transaction: ParsedTransaction, rules: List<ParsingRule>): Boolean {
        val raw = transaction.rawMessage
        val upperBody = raw.uppercase()
        var matched = false
        var descriptionApplied = false

        // User defined rules take highest priority
        for (rule in rules) {
            if (!rule.isEnabled) continue
            if (rule.keyword.isBlank()) continue
            if (!upperBody.contains(rule.keyword.uppercase())) continue

            DebugLog.log(TAG, "User rule matched: \"${rule.keyword}\" → cat=${rule.categoryName}, dest=${rule.destinationAccountName}, autoSend=${rule.autoSendToFirefly}")
            matched = true

            // Category — first match wins
            if (transaction.categoryName.isNullOrBlank() && rule.categoryName.isNotBlank()) {
                transaction.categoryName = rule.categoryName
            }

            // Budget — first match wins
            if (transaction.budgetId == null && rule.budgetId != null) {
                transaction.budgetId = rule.budgetId
                transaction.budgetName = rule.budgetName.ifBlank { null }
            }

            // Destination account — first match wins
            if (transaction.destinationAccountId == null && rule.destinationAccountId != null) {
                transaction.destinationAccountId = rule.destinationAccountId
                transaction.destinationAccountName = rule.destinationAccountName
            }

            // Description Template — first match with a template wins
            if (rule.descriptionTemplate.isNotBlank() && !descriptionApplied) {
                transaction.description = interpolateTemplate(rule.descriptionTemplate, transaction, rule)
                descriptionApplied = true
                DebugLog.log(TAG, "Applied description template: \"${transaction.description}\"")
            }

            // Auto-send flag — activates if any matched rule has it enabled
            if (rule.autoSendToFirefly) {
                transaction.autoSendToFirefly = true
            }

            if (transaction.matchedRuleKeyword == null) {
                transaction.matchedRuleKeyword = rule.keyword
            }

            // Tags — merge from all matches
            for (tag in rule.tags) {
                if (tag !in transaction.selectedTags) {
                    transaction.selectedTags.add(tag)
                }
            }
        }

        if (!matched) {
            DebugLog.log(TAG, "No user rules matched for: ${transaction.rawMessage.take(40)}...")
        }

        return matched
    }

    /**
     * Interpolates description template string with transaction variables.
     * Supported variables:
     * - {vendor} / {Vendor}: Detected vendor name or rule keyword fallback
     * - {amount} / {Amount} / {ammout} / {Ammout}: Formatted amount (e.g. "450.00")
     * - {account} / {Account}: Account name
     * - {category} / {Category}: Category name
     * - {date} / {Date}: Formatted transaction date ("dd/MM/yyyy")
     * - {time} / {Time}: Formatted transaction time ("HH:mm")
     * - {type} / {Type}: Transaction type display label ("Expense", "Income", "Transfer")
     * - {mode} / {payment_mode}: Payment mode ("UPI", "Card", etc.)
     * - {keyword} / {Keyword}: Matched rule keyword
     */
    fun interpolateTemplate(
        template: String,
        transaction: ParsedTransaction,
        rule: ParsingRule? = null
    ): String {
        if (template.isBlank()) return transaction.description

        val vendorVal = transaction.vendor?.ifBlank { null }
            ?: rule?.keyword?.ifBlank { null }
            ?: "Vendor"

        val amountVal = String.format(Locale.US, "%.2f", transaction.effectiveAmount)

        val accountVal = transaction.destinationAccountName?.ifBlank { null }
            ?: transaction.sourceAccountName?.ifBlank { null }
            ?: rule?.destinationAccountName?.ifBlank { null }
            ?: ""

        val categoryVal = rule?.categoryName?.ifBlank { null }
            ?: transaction.categoryName?.ifBlank { null }
            ?: ""

        val budgetVal = rule?.budgetName?.ifBlank { null }
            ?: transaction.budgetName?.ifBlank { null }
            ?: ""

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateVal = dateFormat.format(Date(transaction.timestamp))
        val timeVal = timeFormat.format(Date(transaction.timestamp))

        val typeVal = transaction.effectiveType.displayLabel()
        val modeVal = transaction.paymentMode ?: ""
        val keywordVal = rule?.keyword ?: ""

        var result = template
        val replacements = mapOf(
            "(?i)\\{vendor\\}" to vendorVal,
            "(?i)\\{amount\\}" to amountVal,
            "(?i)\\{ammout\\}" to amountVal, // forgiving alias for user typo
            "(?i)\\{account\\}" to accountVal,
            "(?i)\\{category\\}" to categoryVal,
            "(?i)\\{budget\\}" to budgetVal,
            "(?i)\\{date\\}" to dateVal,
            "(?i)\\{time\\}" to timeVal,
            "(?i)\\{type\\}" to typeVal,
            "(?i)\\{mode\\}" to modeVal,
            "(?i)\\{payment_mode\\}" to modeVal,
            "(?i)\\{keyword\\}" to keywordVal
        )

        for ((pattern, replacement) in replacements) {
            result = result.replace(Regex(pattern), replacement)
        }

        return result.trim().replace(Regex("""\s+"""), " ")
    }
}

