package com.swaraj429.firefly3smsscanner.parser

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
        "PHONEPE" to "PhonePe",
        "SIMPL" to "Simpl",
        "SLICE" to "Slice"
    ),
    val bankKeywords: Map<String, String> = mapOf(
        "HDFC" to "HDFC",
        "ICICI" to "ICICI",
        "SBI" to "SBI",
        "AXIS" to "Axis",
        "RBL" to "RBL",
        "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda",
        "KOTAK" to "Kotak",
        "STANCHART" to "Standard Chartered",
        "SCBANK" to "Standard Chartered",
        "PNB" to "PNB",
        "CANARA" to "Canara",
        "IDBI" to "IDBI",
        "UNION BANK" to "Union Bank"
    ),
    // Map account alias in SMS to real Firefly account name
    val accountAliases: Map<String, String> = emptyMap()
)

/**
 * Intelligent Account Matching Engine.
 * Automatically detects which Firefly account a transaction belongs to by analyzing SMS content.
 */
class AccountMatcher(private val config: AccountMatcherConfig = AccountMatcherConfig()) {

    // Patterns for matching account numbers: e.g., XX1234, **5678, ...6818, ending 1234, a/c 1234
    private val accountMaskPatterns = listOf(
        Regex("""[Xx\*]+(\d{2,6})\b"""), // Matches XX1234, ***5678
        Regex("""\.\.\.+(\d{2,6})\b"""), // Matches ...6818 (Bank of Baroda)
        Regex("""(?i)(?:a/c|acct|acc|account)[\s\w.:]*?(\d{2,6})\b"""), // Matches a/c 1234, A/c ...6818
        Regex("""(?i)(?:ending|ends|ending with|ending in)[\s\w]*?(\d{2,6})\b"""), // Matches ending 1234, ending with 2493
        Regex("""(?i)(?:card|cc)[\s\w]*?(\d{2,6})\b"""), // Matches card 1234
        Regex("""(?i)91[Xx*]+(\d{4})\b""") // Matches Paytm phone account 91XX6716
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

        // 1. Wallet matching (Exact Keyword matching)
        for ((keyword, walletName) in config.walletKeywords) {
            if (upperBody.contains(keyword.uppercase())) {
                val matchedAccount = accounts.find { it.name.contains(walletName, ignoreCase = true) }
                if (matchedAccount != null) {
                    candidates.add(AccountMatchResult(matchedAccount, ConfidenceScore.HIGH, "Wallet Keyword: $keyword"))
                }
            }
        }

        // 2. Account number fragment matching
        val extractedFragments = extractAccountFragments(smsBody)

        for (fragment in extractedFragments) {
            for (account in accounts) {
                val accNum = account.accountNumber?.replace(Regex("""\D"""), "") ?: ""
                if (accNum.isNotEmpty()) {
                    if (accNum.endsWith(fragment)) {
                        candidates.add(AccountMatchResult(account, ConfidenceScore.HIGH, "Account Suffix Match: $fragment"))
                    } else if (accNum.startsWith(fragment)) {
                        candidates.add(AccountMatchResult(account, ConfidenceScore.MEDIUM, "Account Prefix Match: $fragment"))
                    } else if (accNum.contains(fragment)) {
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
                candidates.add(AccountMatchResult(ccAccounts.first(), ConfidenceScore.LOW, "Credit Card Fallback"))
            }
        }

        // Resolution
        if (candidates.isEmpty()) {
            return null
        }

        // Group by account to handle multiple matches. We will pick the account with the highest score
        return candidates.maxByOrNull { it.confidence.value }
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
