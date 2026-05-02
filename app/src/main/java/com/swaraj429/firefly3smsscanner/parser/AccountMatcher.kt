package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.model.AccountIndex
import com.swaraj429.firefly3smsscanner.model.FireflyAccount

object AccountMatcher {

    // Regex patterns for extracting last digits from SMS
    private val extractors = listOf(
        Regex("(?i)(?:a/c|acct|account)[^0-9]{0,10}(\\d{3,6})"),
        Regex("(?i)(?:card)[^0-9]{0,10}(\\d{3,6})"),
        Regex("(?:\\*{2,}|X{2,})(\\d{3,6})", RegexOption.IGNORE_CASE),
        // generic fallback: 4 to 6 digits, if context keywords present
        Regex("(?i)(?:ending|no\\.?|number)[^0-9]{0,10}(\\d{4,6})")
    )

    fun buildIndex(accounts: List<FireflyAccount>): List<AccountIndex> {
        val indices = mutableListOf<AccountIndex>()
        for (account in accounts) {
            val accNum = account.accountNumber ?: continue
            val digitsOnly = accNum.replace(Regex("[^0-9]"), "")
            if (digitsOnly.length >= 3) {
                // Store the last 4 digits (or up to 6 if we want to be safe, let's keep the last up to 6 digits)
                val lastDigits = if (digitsOnly.length > 6) digitsOnly.takeLast(6) else digitsOnly
                indices.add(
                    AccountIndex(
                        accountId = account.id,
                        name = account.name,
                        lastDigits = lastDigits,
                        type = account.type
                    )
                )
            }
        }
        return indices
    }

    fun extractDigits(smsBody: String): List<String> {
        val candidates = mutableSetOf<String>()
        for (pattern in extractors) {
            val matches = pattern.findAll(smsBody)
            for (match in matches) {
                candidates.add(match.groupValues[1])
            }
        }
        return candidates.toList()
    }

    fun matchAccounts(smsBody: String, index: List<AccountIndex>): List<AccountIndex> {
        val digitsList = extractDigits(smsBody)
        if (digitsList.isEmpty()) return emptyList()

        val matches = mutableListOf<AccountIndex>()

        for (digits in digitsList) {
            val potentialMatches = index.filter { it.lastDigits.endsWith(digits) }
            matches.addAll(potentialMatches)
        }

        // De-duplicate
        var distinctMatches = matches.distinctBy { it.accountId }

        // Apply rules if multiple matches
        if (distinctMatches.size > 1) {
            val lowerBody = smsBody.lowercase()
            val mentionsCard = lowerBody.contains("card")
            val mentionsAcct = lowerBody.contains("a/c") || lowerBody.contains("acct") || lowerBody.contains("account")

            if (mentionsCard && !mentionsAcct) {
                // Prefer credit card accounts if type is known or name has "card"
                val filtered = distinctMatches.filter {
                    it.name.lowercase().contains("card") || it.type == "liability" || it.type == "credit"
                }
                if (filtered.isNotEmpty()) distinctMatches = filtered
            } else if (mentionsAcct && !mentionsCard) {
                val filtered = distinctMatches.filter {
                    !it.name.lowercase().contains("card")
                }
                if (filtered.isNotEmpty()) distinctMatches = filtered
            }
        }

        return distinctMatches
    }
}
