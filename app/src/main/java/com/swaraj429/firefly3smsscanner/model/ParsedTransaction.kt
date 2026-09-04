package com.swaraj429.firefly3smsscanner.model

/**
 * Parsed transaction from SMS with enrichable Firefly III metadata.
 */
data class ParsedTransaction(
    val amount: Double,
    val type: TransactionType, // WITHDRAWAL, DEPOSIT, or TRANSFER
    val rawMessage: String,
    val sender: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    // Payment mode (e.g. "UPI", "Card", "ATM", "NetBanking")
    var paymentMode: String? = null,
    // Mutable for user corrections
    var correctedAmount: Double? = null,
    var correctedType: TransactionType? = null,
    // Firefly metadata (user-selected)
    var description: String = "",
    var categoryName: String? = null,
    var selectedTags: MutableList<String> = mutableListOf(),
    var budgetId: String? = null,
    var budgetName: String? = null,
    var sourceAccountId: String? = null,
    var sourceAccountName: String? = null,
    var destinationAccountId: String? = null,
    var destinationAccountName: String? = null,
    // Tracking
    var status: SendStatus = SendStatus.PENDING
) {
    val effectiveAmount: Double get() = correctedAmount ?: amount
    val effectiveType: TransactionType get() = correctedType ?: type

    /** Whether this transaction moves money OUT of user's account */
    val isExpense: Boolean get() = effectiveType == TransactionType.WITHDRAWAL
}

/**
 * Transaction types aligned with Firefly III:
 *   - WITHDRAWAL = Expense (money goes out)
 *   - DEPOSIT    = Revenue/Income (money comes in)
 *   - TRANSFER   = Between own accounts
 */
enum class TransactionType {
    WITHDRAWAL, DEPOSIT, TRANSFER;

    /** Returns the Firefly III API type string */
    fun toFireflyType(): String = name.lowercase()

    /** Human-readable label for UI display */
    fun displayLabel(): String = when (this) {
        WITHDRAWAL -> "Expense"
        DEPOSIT -> "Income"
        TRANSFER -> "Transfer"
    }

    /** Emoji for notifications */
    fun emoji(): String = when (this) {
        WITHDRAWAL -> "🔴"
        DEPOSIT -> "🟢"
        TRANSFER -> "🔄"
    }
}

enum class SendStatus {
    PENDING, SENDING, SENT, FAILED
}
