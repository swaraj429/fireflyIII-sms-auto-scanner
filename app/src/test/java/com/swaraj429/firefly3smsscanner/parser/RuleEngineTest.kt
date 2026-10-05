package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import com.swaraj429.firefly3smsscanner.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    @Test
    fun `test description template interpolation with user variables`() {
        val txn = ParsedTransaction(
            amount = 450.0,
            type = TransactionType.WITHDRAWAL,
            rawMessage = "Spent Rs. 450 at Mayur Super Market on ICICI Card",
            vendor = "Mayur Super Market",
            destinationAccountName = "HDFC Bank"
        )

        val rule = ParsingRule(
            keyword = "MAYUR",
            descriptionTemplate = "{Vendor} spent {ammout} for glosaries",
            categoryName = "Groceries",
            autoSendToFirefly = true
        )

        val matched = RuleEngine.applyRules(txn, listOf(rule))

        assertTrue(matched)
        assertEquals("Mayur Super Market spent 450.00 for glosaries", txn.description)
        assertEquals("Groceries", txn.categoryName)
        assertTrue(txn.autoSendToFirefly)
    }

    @Test
    fun `test description template with amount, account and vendor fallback`() {
        val txn = ParsedTransaction(
            amount = 120.50,
            type = TransactionType.WITHDRAWAL,
            rawMessage = "Debited INR 120.50 for SWIGGY order",
            vendor = null,
            sourceAccountName = "ICICI Bank"
        )

        val rule = ParsingRule(
            keyword = "SWIGGY",
            descriptionTemplate = "{vendor} order via {account} costing {amount}",
            categoryName = "Food & Dining"
        )

        RuleEngine.applyRules(txn, listOf(rule))

        // When vendor is null, {vendor} gracefully falls back to rule keyword
        assertEquals("SWIGGY order via ICICI Bank costing 120.50", txn.description)
    }

    @Test
    fun `test autoSendToFirefly flag is set when matched rule has autoSend enabled`() {
        val txn = ParsedTransaction(
            amount = 299.0,
            type = TransactionType.WITHDRAWAL,
            rawMessage = "Netflix subscription debited INR 299",
            vendor = "Netflix"
        )

        val rule = ParsingRule(
            keyword = "NETFLIX",
            descriptionTemplate = "{vendor} Monthly Plan",
            categoryName = "Entertainment",
            autoSendToFirefly = true
        )

        RuleEngine.applyRules(txn, listOf(rule))

        assertTrue(txn.autoSendToFirefly)
        assertEquals("Netflix Monthly Plan", txn.description)
    }

    @Test
    fun `test multiple rules merge tags and first rule description template wins`() {
        val txn = ParsedTransaction(
            amount = 500.0,
            type = TransactionType.WITHDRAWAL,
            rawMessage = "Paid Rs 500 at Zomato with UPI",
            vendor = "Zomato",
            paymentMode = "UPI"
        )

        val rule1 = ParsingRule(
            keyword = "ZOMATO",
            descriptionTemplate = "{vendor} Dinner",
            categoryName = "Food",
            tags = listOf("food-delivery"),
            autoSendToFirefly = true
        )

        val rule2 = ParsingRule(
            keyword = "PAID",
            descriptionTemplate = "Generic Paid",
            categoryName = "Other",
            tags = listOf("upi-spend"),
            autoSendToFirefly = false
        )

        RuleEngine.applyRules(txn, listOf(rule1, rule2))

        assertEquals("Zomato Dinner", txn.description)
        assertEquals("Food", txn.categoryName)
        assertTrue(txn.selectedTags.contains("food-delivery"))
        assertTrue(txn.selectedTags.contains("upi-spend"))
        assertTrue(txn.autoSendToFirefly)
    }
}
