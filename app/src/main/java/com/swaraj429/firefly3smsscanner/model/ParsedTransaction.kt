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
    var status: SendStatus = SendStatus.PENDING,
    var dismissReason: DismissReason? = null,
    var dismissedAt: Long? = null,
    var fireflyTransactionId: String? = null,
    var fireflyTransactionJournalId: String? = null,
    var lastSyncedAt: Long? = null,
    var hasRemoteEdits: Boolean = false
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
    PENDING, SENDING, SENT, FAILED, DISMISSED
}

/**
 * Reasons why an SMS transaction might be dismissed by the user.
 */
enum class DismissReason(
    val title: String,
    val description: String,
    val badgeLabel: String
) {
    DUPLICATE(
        title = "Duplicate Transaction",
        description = "Same transaction reported by another SMS (e.g. Bank & UPI alerts)",
        badgeLabel = "Duplicate"
    ),
    CREDIT_CARD_ECHO(
        title = "Credit Card Bill Echo",
        description = "Bank debit alert that mirrors a credit card bill payment",
        badgeLabel = "CC Echo"
    ),
    UNRELATED(
        title = "Unrelated / Non-Expense",
        description = "Promotional message, OTP, balance inquiry, or personal text",
        badgeLabel = "Unrelated"
    ),
    OTHER(
        title = "Other",
        description = "Manually dismissed / ignored",
        badgeLabel = "Dismissed"
    );

    companion object {
        fun fromString(name: String?): DismissReason {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OTHER
        }
    }
}
