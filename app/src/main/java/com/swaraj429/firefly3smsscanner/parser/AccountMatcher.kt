package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.FireflyAccount

enum class ConfidenceScore(val value: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3)
}

data class AccountMatchResult(
    val account: FireflyAccount,
    val confidence: ConfidenceScore,
    val reason: String
)

/**
 * Configuration for the AccountMatcher.
 * Allows users to define aliases, wallet keyword mappings, and bank keyword mappings.
 */
data class AccountMatcherConfig(
    val walletKeywords: Map<String, String> = mapOf(
        "AMAZON PAY" to "Amazon Wallet",
        "PAYTM" to "Paytm Wallet",
        "GPAY" to "Google Pay",
        "PHONEPE" to "PhonePe"
    ),
    val bankKeywords: Map<String, String> = mapOf(
        "HDFC" to "HDFC",
        "ICICI" to "ICICI",
        "SBI" to "SBI",
        "AXIS" to "Axis"
    ),
    // Map account alias in SMS to real Firefly account name
    val accountAliases: Map<String, String> = emptyMap()
)

/**
 * Intelligent Account Matching Engine.
 * Automatically detects which Firefly account a transaction belongs to by analyzing SMS content.
 */
class AccountMatcher(private val config: AccountMatcherConfig = AccountMatcherConfig()) {
    private val TAG = "AccountMatcher"

    // Patterns for matching account numbers: e.g., XX1234, **5678, ending 1234, a/c 1234
    private val accountMaskPatterns = listOf(
        Regex("""[Xx\*]+(\d{2,6})\b"""), // Matches XX1234, ***5678
        Regex("""(?i)(?:a/c|acct|account)[\s\w]*?(\d{2,6})\b"""), // Matches a/c 1234
        Regex("""(?i)(?:ending|ends)[\s\w]*?(\d{2,6})\b"""), // Matches ending 1234
        Regex("""(?i)(?:card|cc)[\s\w]*?(\d{2,6})\b""") // Matches card 1234
    )

    /**
     * Finds the best matching account for a given SMS body from a list of Firefly accounts.
     * Evaluates wallets, account numbers, aliases, and banks to score the best candidate.
     */
    fun findBestMatch(
        smsBody: String,
        accounts: List<FireflyAccount>
    ): AccountMatchResult? {
        val upperBody = smsBody.uppercase()
        val candidates = mutableListOf<AccountMatchResult>()

        DebugLog.log(TAG, "Starting matching engine for SMS: ${smsBody.take(40)}...")

        // 1. Wallet matching (Exact Keyword matching)
        for ((keyword, walletName) in config.walletKeywords) {
            if (upperBody.contains(keyword.uppercase())) {
                val matchedAccount = accounts.find { it.name.contains(walletName, ignoreCase = true) }
                if (matchedAccount != null) {
                    DebugLog.log(TAG, "  → Wallet match found: $keyword -> ${matchedAccount.name}")
                    candidates.add(AccountMatchResult(matchedAccount, ConfidenceScore.HIGH, "Wallet Keyword: $keyword"))
                }
            }
        }

        // 2. Account number fragment matching
        val extractedFragments = extractAccountFragments(smsBody)
        if (extractedFragments.isNotEmpty()) {
            DebugLog.log(TAG, "  → Extracted number fragments: $extractedFragments")
        }

        for (fragment in extractedFragments) {
            for (account in accounts) {
                val accNum = account.accountNumber?.replace(Regex("""\D"""), "") ?: ""
                if (accNum.isNotEmpty()) {
                    if (accNum.endsWith(fragment)) {
                        DebugLog.log(TAG, "  → Account suffix match: $fragment -> ${account.name}")
                        candidates.add(AccountMatchResult(account, ConfidenceScore.HIGH, "Account Suffix Match: $fragment"))
                    } else if (accNum.startsWith(fragment)) {
                        DebugLog.log(TAG, "  → Account prefix match: $fragment -> ${account.name}")
                        candidates.add(AccountMatchResult(account, ConfidenceScore.MEDIUM, "Account Prefix Match: $fragment"))
                    } else if (accNum.contains(fragment)) {
                        DebugLog.log(TAG, "  → Account partial match: $fragment -> ${account.name}")
                        candidates.add(AccountMatchResult(account, ConfidenceScore.LOW, "Account Partial Match: $fragment"))
                    }
                }
            }
        }

        // 3. Aliases
        for ((alias, realName) in config.accountAliases) {
            if (upperBody.contains(alias.uppercase())) {
                val matchedAccount = accounts.find { it.name.equals(realName, ignoreCase = true) }
                if (matchedAccount != null) {
                    DebugLog.log(TAG, "  → Alias match: $alias -> ${matchedAccount.name}")
                    candidates.add(AccountMatchResult(matchedAccount, ConfidenceScore.HIGH, "Alias Match: $alias"))
                }
            }
        }

        // 4. Bank/Credit Card identification (Fallback/Enhancement)
        for ((keyword, bankName) in config.bankKeywords) {
            if (upperBody.contains(keyword.uppercase())) {
                val matchedAccounts = accounts.filter { it.name.contains(bankName, ignoreCase = true) }
                // If only one account matches the bank name, we can use it with medium confidence
                if (matchedAccounts.size == 1) {
                    val account = matchedAccounts.first()
                    if (candidates.none { it.account.id == account.id }) {
                        DebugLog.log(TAG, "  → Bank keyword match: $keyword -> ${account.name}")
                        candidates.add(AccountMatchResult(account, ConfidenceScore.MEDIUM, "Bank Keyword Match: $keyword"))
                    }
                }
            }
        }

        // 5. Detect credit card specific strings (e.g., "credit card", "cc")
        val isCreditCardTxn = upperBody.contains("CREDIT CARD") || upperBody.contains(" CC ")
        if (isCreditCardTxn) {
            // Find matches that are actually credit cards (using accountRole or type)
            // If we have candidates, boost their confidence if they are CCs
            val ccAccounts = accounts.filter { 
                it.type.equals("asset", ignoreCase = true) && 
                (it.accountRole.equals("ccAsset", ignoreCase = true) || it.name.contains("credit", ignoreCase = true)) 
            }
            if (ccAccounts.size == 1 && candidates.isEmpty()) {
                DebugLog.log(TAG, "  → Single Credit Card account fallback -> ${ccAccounts.first().name}")
                candidates.add(AccountMatchResult(ccAccounts.first(), ConfidenceScore.LOW, "Credit Card Fallback"))
            }
        }

        // Resolution
        if (candidates.isEmpty()) {
            DebugLog.log(TAG, "  → No account match found.")
            return null
        }

        // Group by account to handle multiple matches. We will pick the account with the highest score
        val bestCandidate = candidates.maxByOrNull { it.confidence.value }

        if (bestCandidate != null) {
            DebugLog.log(TAG, "  → Best match chosen: ${bestCandidate.account.name} | Score: ${bestCandidate.confidence} | Reason: ${bestCandidate.reason}")
        }

        return bestCandidate
    }

    /**
     * Visible for testing
     */
    internal fun extractAccountFragments(body: String): Set<String> {
        val fragments = mutableSetOf<String>()
        for (pattern in accountMaskPatterns) {
            val matches = pattern.findAll(body)
            for (match in matches) {
                val fragment = match.groupValues[1]
                if (fragment.length >= 2) {
                    fragments.add(fragment)
                }
            }
        }
        return fragments
    }
}
