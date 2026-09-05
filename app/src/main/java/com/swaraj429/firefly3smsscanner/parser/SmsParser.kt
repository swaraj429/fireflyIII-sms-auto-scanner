package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import com.swaraj429.firefly3smsscanner.model.TransactionType

/**
 * Advanced Regex-based SMS parser for Indian banking & payment messages.
 * Handles UPI, Credit Cards, Netbanking, Wallets (Paytm, Simpl, Slice),
 * NEFT, IMPS, ATM, POS, and auto-filters OTP/spam/bill reminders.
 */
object SmsParser {
    private const val TAG = "SmsParser"

    // Amount patterns (handles Rs, INR, Rs., ₹, with commas and decimals)
    private val amountPatterns = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE),
        Regex("""(?:amount|amt|debited by|credited by|debited for|credited for)\s*(?:of\s*)?(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:for\s+)(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:paid|spent|transferred)\s+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    // Known spam shortcodes
    private val spamSenders = listOf("55256", "50404", "55315", "53111", "55111")

    // Non-transaction discard patterns (collect requests, broker balance reports, limit updates, bill reminders, OTPs, promotional loan ads)
    private val discardPatterns = listOf(
        Regex("""\b(otp|verification code|secret code|one time password|passcode|auth code|verification pin)\b""", RegexOption.IGNORE_CASE),
        Regex("""reported your (fund balance|securities balance|collateral)""", RegexOption.IGNORE_CASE),
        Regex("""(limit has been updated|changed the spend limit|spend limit on your|contactless transaction limit|credit limit increase)""", RegexOption.IGNORE_CASE),
        Regex("""(has requested money|request to pay|requested payment|mandate request|on approving.*will be debited)""", RegexOption.IGNORE_CASE),
        Regex("""(you have setup a recurring payment|mandate created|standing instruction.*registered)""", RegexOption.IGNORE_CASE),
        Regex("""(regret to inform you|could not be processed|txn failed|unsuccessful|transaction failed)""", RegexOption.IGNORE_CASE),
        Regex("""(energy bill for cons|bill of rs\.?\s*[\d,]+(?:\.\d+)? is due|bill is pending|total amt due|minimum amt due|statement generated|payment is due on|to avoid late fee)""", RegexOption.IGNORE_CASE),
        Regex("""(pre-approved|preapproved|loan up to|apply now|borrow on mpokket|borrow rs|open axis bank ekyc|amazon voucher\*|welcome bonus|disconnection at a charge|जीतें|जिंका|कहाँ है\?|कुठे आहे\?|ecash to your account|games bonus|play your favourite games|maintain an amb|average monthly balance|call_chrg:|call_durn:|as per income tax provision)""", RegexOption.IGNORE_CASE)
    )

    // Debit keywords
    private val debitKeywords = listOf(
        "debited", "deducted", "withdrawn", "sent", "paid",
        "purchase", "spent", "debit", "transferred", "txn of",
        "payment of", "charged", "debited from", "debited to"
    )

    // Credit keywords
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refund", "cashback",
        "credit", "reversed", "reversal", "added", "credited to",
        "credited from", "has been received"
    )



    fun isNonTransaction(body: String, sender: String = ""): Boolean {
        // 1. Check known spam senders
        if (spamSenders.any { sender.contains(it) }) {
            return true
        }

        val lowerBody = body.lowercase()
        val isConfirmedPayment = lowerBody.contains("debited") ||
                lowerBody.contains("credited") ||
                lowerBody.contains("spent on") ||
                lowerBody.contains("transaction of inr") ||
                lowerBody.contains("transaction of rs") ||
                lowerBody.contains("paid rs.") ||
                lowerBody.contains("paid inr") ||
                lowerBody.contains("repayment was a success") ||
                lowerBody.contains("sent via upi")

        for (pattern in discardPatterns) {
            if (pattern.containsMatchIn(body)) {
                if (lowerBody.contains("otp") ||
                    lowerBody.contains("secret code") ||
                    lowerBody.contains("requested money") ||
                    lowerBody.contains("on approving") ||
                    lowerBody.contains("reported your fund balance") ||
                    lowerBody.contains("limit has been updated") ||
                    lowerBody.contains("could not be processed")) {
                    return true
                }
                if (!isConfirmedPayment) {
                    return true
                }
            }
        }
        return false
    }

    fun parse(sms: SmsMessage): ParsedTransaction? {
        val body = sms.body

        // 1. Filter out non-transactional messages / spam
        if (isNonTransaction(body, sms.sender)) {
            return null
        }

        // 2. Extract amount
        val amount = extractAmount(body)
        if (amount == null || amount <= 0) {
            return null
        }

        // 3. Determine transaction type (Withdrawal vs Deposit)
        val type = determineType(body)

        // 4. Extract sensible description (payee, merchant, or contextual bank detail)
        val isExpense = type == TransactionType.WITHDRAWAL
        val description = DescriptionExtractor.extractDescription(body, sms.sender, isExpense)

        // 5. Detect payment mode (UPI vs Card vs ATM vs NetBanking)
        val paymentMode = determinePaymentMode(body)
        val tags = mutableListOf<String>()
        if (paymentMode != null) {
            tags.add(paymentMode)
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            rawMessage = body,
            sender = sms.sender,
            timestamp = sms.timestamp,
            description = description,
            paymentMode = paymentMode,
            selectedTags = tags
        )
    }

    fun parseAll(messages: List<SmsMessage>): List<ParsedTransaction> {
        DebugLog.log(TAG, "Parsing ${messages.size} messages...")
        val results = messages.mapNotNull { parse(it) }
        DebugLog.log(TAG, "Successfully parsed ${results.size}/${messages.size} messages")
        return results
    }

    private fun determinePaymentMode(body: String): String? {
        val lower = body.lowercase()
        return when {
            lower.contains("upi") || lower.contains("vpa") || lower.contains("@ok") ||
            lower.contains("@paytm") || lower.contains("@ybl") || lower.contains("@axis") ||
            lower.contains("@icici") || lower.contains("@barodampay") || lower.contains("pingpay") -> "UPI"

            lower.contains("credit card") || lower.contains("debit card") || lower.contains("card") ||
            lower.contains("pos ") || lower.contains("ending ") || lower.contains("spent on") -> "Card"

            lower.contains("atm") || lower.contains("cash withdrawal") -> "ATM"

            lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs") -> "NetBanking"

            else -> null
        }
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                val rawAmount = match.groupValues[1].replace(",", "")
                try {
                    val amount = rawAmount.toDouble()
                    if (amount > 0) {
                        return amount
                    }
                } catch (e: NumberFormatException) {
                    DebugLog.log(TAG, "  → Failed to parse amount '$rawAmount': ${e.message}")
                }
            }
        }
        return null
    }

    private fun determineType(body: String): TransactionType {
        val lowerBody = body.lowercase()

        val hasDebit = debitKeywords.any { lowerBody.contains(it) }
        val hasCredit = creditKeywords.any { lowerBody.contains(it) }

        return when {
            hasDebit && !hasCredit -> TransactionType.WITHDRAWAL
            hasCredit && !hasDebit -> TransactionType.DEPOSIT
            hasDebit && hasCredit -> {
                // Both present — first keyword wins
                val debitPos = debitKeywords.mapNotNull {
                    val idx = lowerBody.indexOf(it)
                    if (idx >= 0) idx else null
                }.minOrNull() ?: Int.MAX_VALUE

                val creditPos = creditKeywords.mapNotNull {
                    val idx = lowerBody.indexOf(it)
                    if (idx >= 0) idx else null
                }.minOrNull() ?: Int.MAX_VALUE

                if (debitPos < creditPos) TransactionType.WITHDRAWAL else TransactionType.DEPOSIT
            }
            else -> TransactionType.WITHDRAWAL
        }
    }
}
