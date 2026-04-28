package com.swaraj.fireflysmscanner.parser

import com.swaraj.fireflysmscanner.debug.DebugLog
import com.swaraj.fireflysmscanner.model.ParsedTransaction
import com.swaraj.fireflysmscanner.model.SmsMessage
import com.swaraj.fireflysmscanner.model.TransactionType

/**
 * Regex-based SMS parser for Indian banking messages.
 * Deliberately kept simple — handles ~60-70% of common formats.
 */
object SmsParser {
    private const val TAG = "SmsParser"

    // Amount patterns (handles Rs, INR, Rs., ₹)
    private val amountPatterns = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+\.?\d*)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE),
        Regex("""(?:amount|amt)\s*(?:of\s*)?(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
    )

    // Debit keywords
    private val debitKeywords = listOf(
        "debited", "deducted", "withdrawn", "sent", "paid",
        "purchase", "spent", "debit", "transferred", "txn of",
        "payment of", "charged"
    )

    // Credit keywords
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refund", "cashback",
        "credit", "reversed", "added"
    )

    // Sender patterns that are typically banks
    private val bankSenderPatterns = listOf(
        Regex("""^[A-Z]{2}-[A-Z]+"""), // e.g., VD-HDFCBK, AD-SBIINB
        Regex("""^[A-Z]{6,}"""),         // e.g., HDFCBK
    )

    fun parse(sms: SmsMessage): ParsedTransaction? {
        val body = sms.body
        DebugLog.log(TAG, "Parsing: ${body.take(80)}...")

        // 1. Extract amount
        val amount = extractAmount(body)
        if (amount == null || amount <= 0) {
            DebugLog.log(TAG, "  → No valid amount found, skipping")
            return null
        }

        // 2. Determine type
        val type = determineType(body)

        DebugLog.log(TAG, "  → Parsed: amount=$amount, type=$type")

        return ParsedTransaction(
            amount = amount,
            type = type,
            rawMessage = body,
            sender = sms.sender,
            timestamp = sms.timestamp
        )
    }

    fun parseAll(messages: List<SmsMessage>): List<ParsedTransaction> {
        DebugLog.log(TAG, "Parsing ${messages.size} messages...")

        val results = messages.mapNotNull { parse(it) }

        DebugLog.log(TAG, "Successfully parsed ${results.size}/${messages.size} messages")
        return results
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val rawAmount = match.groupValues[1].replace(",", "")
                try {
                    val amount = rawAmount.toDouble()
                    if (amount > 0) {
                        DebugLog.log(TAG, "  → Amount matched by pattern: ${pattern.pattern}")
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
            hasDebit && !hasCredit -> TransactionType.DEBIT
            hasCredit && !hasDebit -> TransactionType.CREDIT
            hasDebit && hasCredit -> {
                // Both present — use position (first keyword wins)
                val debitPos = debitKeywords.mapNotNull {
                    val idx = lowerBody.indexOf(it)
                    if (idx >= 0) idx else null
                }.minOrNull() ?: Int.MAX_VALUE

                val creditPos = creditKeywords.mapNotNull {
                    val idx = lowerBody.indexOf(it)
                    if (idx >= 0) idx else null
                }.minOrNull() ?: Int.MAX_VALUE

                if (debitPos < creditPos) TransactionType.DEBIT else TransactionType.CREDIT
            }
            else -> TransactionType.UNKNOWN
        }
    }

    /**
     * Sample Indian banking SMS messages for testing
     */
    fun getSampleMessages(): List<SmsMessage> = listOf(
        SmsMessage(
            sender = "VD-HDFCBK",
            body = "Alert: INR 2,500.00 debited from a/c **1234 on 15-Jan-25 at POS AMAZON.IN. Avl Bal: INR 45,231.50",
            timestamp = System.currentTimeMillis(),
            dateString = "15/01/2025 10:30"
        ),
        SmsMessage(
            sender = "AD-SBIINB",
            body = "Your a/c X5678 is credited with Rs.15,000.00 on 14JAN25 by NEFT-Ref No: N01234. Avl Bal Rs.62,500.00-SBI",
            timestamp = System.currentTimeMillis() - 86400000,
            dateString = "14/01/2025 14:20"
        ),
        SmsMessage(
            sender = "VM-ICICIB",
            body = "Rs 1,299.00 spent on ICICI Bank Card XX9012 at SWIGGY on 13-Jan-25. If not you, call 18002662.",
            timestamp = System.currentTimeMillis() - 172800000,
            dateString = "13/01/2025 19:45"
        ),
        SmsMessage(
            sender = "BZ-AXISBK",
            body = "INR 500.00 sent via UPI from A/C XX4567 to paytm@upi on 12-Jan-25. UPI Ref: 501234567890",
            timestamp = System.currentTimeMillis() - 259200000,
            dateString = "12/01/2025 08:15"
        ),
        SmsMessage(
            sender = "JD-KOTAKB",
            body = "Amt of Rs.3,450.00 debited from your Kotak A/c 12XX4567 for purchase at FLIPKART on 11-Jan-25",
            timestamp = System.currentTimeMillis() - 345600000,
            dateString = "11/01/2025 16:00"
        ),
        SmsMessage(
            sender = "AD-PNBSMS",
            body = "Dear Customer, Rs.25000.00 has been credited to your a/c XXXXXXX1234 on 10-01-2025. Your available balance is Rs.87,500.00-PNB",
            timestamp = System.currentTimeMillis() - 432000000,
            dateString = "10/01/2025 11:30"
        ),
        SmsMessage(
            sender = "AX-BOIIND",
            body = "You have received Rs.5,200.50 in your BOI A/c XX6789 from NEFT on 09-Jan-25. Balance: Rs.32,100.50",
            timestamp = System.currentTimeMillis() - 518400000,
            dateString = "09/01/2025 09:00"
        ),
        SmsMessage(
            sender = "VM-UNIONB",
            body = "Rs 750.00 withdrawn from ATM using your Union Bank card XX3456 on 08-Jan-25 at SBI ATM DELHI. Bal: Rs.12,450.00",
            timestamp = System.currentTimeMillis() - 604800000,
            dateString = "08/01/2025 20:30"
        ),
        SmsMessage(
            sender = "DZ-CANBNK",
            body = "Refund of Rs.999.00 credited to your Canara Bank A/c XX7890 on 07-Jan-25. Ref: RFD2025010700123",
            timestamp = System.currentTimeMillis() - 691200000,
            dateString = "07/01/2025 13:15"
        ),
        SmsMessage(
            sender = "HP-IDBIBK",
            body = "INR 12,000.00 transferred from your IDBI A/c XX2345 to XXXXX6789 on 06-Jan-25. Avl bal: INR 8,500.00",
            timestamp = System.currentTimeMillis() - 777600000,
            dateString = "06/01/2025 17:45"
        ),
    )
}
