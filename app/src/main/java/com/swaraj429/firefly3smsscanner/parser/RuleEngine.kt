package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule

/**
 * Rule Engine — matches SMS content against user-defined rules and
 * fills transaction metadata (category, destination account, tags).
 *
 * Rules are evaluated in order; **all matching rules accumulate** so
 * that a keyword "SWIGGY" can set category while "UPI" can add tags.
 * The FIRST match for each field wins (category, account).
 * Tags are merged from all matches.
 */
object RuleEngine {
    private const val TAG = "RuleEngine"

    /**
     * Apply all matching rules to a [ParsedTransaction].
     * Only fills fields that are currently empty/null — never overrides
     * user-provided or auto-detected values.
     *
     * @return true if at least one rule matched
     */
    fun applyRules(transaction: ParsedTransaction, rules: List<ParsingRule>): Boolean {
        val body = transaction.rawMessage.uppercase()
        var matched = false

        for (rule in rules) {
            if (!rule.isEnabled) continue
            if (rule.keyword.isBlank()) continue
            if (!body.contains(rule.keyword.uppercase())) continue

            DebugLog.log(TAG, "Rule matched: \"${rule.keyword}\" → cat=${rule.categoryName}, dest=${rule.destinationAccountName}, tags=${rule.tags}")
            matched = true

            // Category — first match wins
            if (transaction.categoryName.isNullOrBlank() && rule.categoryName.isNotBlank()) {
                transaction.categoryName = rule.categoryName
            }

            // Destination account — first match wins
            if (transaction.destinationAccountId == null && rule.destinationAccountId != null) {
                transaction.destinationAccountId = rule.destinationAccountId
                transaction.destinationAccountName = rule.destinationAccountName
            }

            // Tags — merge from all matches
            for (tag in rule.tags) {
                if (tag !in transaction.selectedTags) {
                    transaction.selectedTags.add(tag)
                }
            }
        }

        if (!matched) {
            DebugLog.log(TAG, "No rules matched for: ${transaction.rawMessage.take(40)}...")
        }

        return matched
    }
}
