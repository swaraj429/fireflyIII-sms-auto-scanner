package com.swaraj.fireflysmscanner.model

/**
 * Parsed transaction from SMS with enrichable Firefly III metadata.
 */
data class ParsedTransaction(
    val amount: Double,
    val type: TransactionType, // DEBIT or CREDIT
    val rawMessage: String,
    val sender: String = "",
    val timestamp: Long = System.currentTimeMillis(),
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
}

enum class TransactionType {
    DEBIT, CREDIT, UNKNOWN;

    fun toFireflyType(): String = when (this) {
        DEBIT -> "withdrawal"
        CREDIT -> "deposit"
        UNKNOWN -> "withdrawal" // default to withdrawal
    }
}

enum class SendStatus {
    PENDING, SENDING, SENT, FAILED
}
