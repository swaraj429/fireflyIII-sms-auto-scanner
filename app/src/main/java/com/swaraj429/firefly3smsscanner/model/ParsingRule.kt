package com.swaraj429.firefly3smsscanner.model

/**
 * Represents a parsing rule: IF SMS contains [keyword] THEN apply metadata.
 * Persisted as JSON in SharedPreferences.
 */
data class ParsingRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val keyword: String = "",
    val categoryName: String = "",
    val destinationAccountId: String? = null,
    val destinationAccountName: String = "",
    val budgetId: String? = null,
    val budgetName: String = "",
    val tags: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val descriptionTemplate: String = "",
    val autoSendToFirefly: Boolean = false
)

